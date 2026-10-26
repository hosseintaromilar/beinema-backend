package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.Entity.BlogPost;
import com.coupleai.coupleai.beinema.Entity.BlogPostVersion;
import com.coupleai.coupleai.beinema.Enum.BlogPostStatus;
import com.coupleai.coupleai.beinema.Repository.BlogPostRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The daily blog writer: picks the next subject of the calendar (another column
 * than yesterday's, a subject not written yet), asks a Metis text model for the
 * post, saves it as a draft for the admin and makes its four pictures. It also
 * writes the next version of a post the admin sent back with notes.
 */
@Service
public class BlogWriter {

    private static final Logger log = LoggerFactory.getLogger(BlogWriter.class);
    /** More drafts than this waiting for the admin: the writer skips a day. */
    private static final int MAX_WAITING = 3;

    public record Topic(String key, String column, String subject) {
    }

    private final BlogService blogService;
    private final BlogImageGenerator imageGenerator;
    private final BlogPostRepository postRepository;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final boolean dailyEnabled;
    private final String systemPrompt;
    private final List<Topic> topics;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public BlogWriter(BlogService blogService,
                      BlogImageGenerator imageGenerator,
                      BlogPostRepository postRepository,
                      ObjectMapper mapper,
                      @Value("${metis.base-url}") String baseUrl,
                      @Value("${metis.api-key}") String apiKey,
                      @Value("${beinema.blog.writer-model:deepseek-v4-1-flash-1017}") String model,
                      @Value("${beinema.blog.daily-enabled:false}") boolean dailyEnabled) throws IOException {
        this.blogService = blogService;
        this.imageGenerator = imageGenerator;
        this.postRepository = postRepository;
        this.mapper = mapper;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.dailyEnabled = dailyEnabled;
        this.systemPrompt = resource("blog/writer-prompt.txt");
        this.topics = parseTopics(resource("blog/topics.txt"));
    }

    /** Every morning at 9 (Tehran), when switched on with BEINEMA_BLOG_DAILY_ENABLED=true. */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Tehran")
    public void daily() {
        if (!dailyEnabled) {
            return;
        }
        long waiting = blogService.all().stream().filter(post -> post.getStatus() == BlogPostStatus.DRAFT).count();
        if (waiting >= MAX_WAITING) {
            log.info("Blog writer skips today: {} drafts are waiting for the admin", waiting);
            return;
        }
        try {
            writeNew();
        } catch (RuntimeException exception) {
            log.warn("The daily blog post could not be written: {}", exception.getMessage());
        }
    }

    /** Writes a new post now (also the admin's «نوشتن مطلب تازه»). */
    public BlogPost writeNew() {
        List<BlogPost> recent = postRepository.findTop60ByOrderByIdDesc();
        Topic topic = nextTopic(topics, recent);
        StringBuilder user = new StringBuilder()
                .append("Subject (Persian): ").append(topic.subject()).append('\n')
                .append("Do not repeat these earlier titles or their angle:\n");
        recent.stream().limit(30).forEach(post -> user.append("- ").append(post.getTitle()).append('\n'));
        BlogService.Draft draft = ask(user.toString(), topic.key());
        BlogPost post = blogService.createDraft(draft);
        pictures(post.getId());
        return post;
    }

    /** The next version of a post the admin sent back with notes. */
    public BlogPost revise(Long postId) {
        BlogPost post = blogService.view(postId).post();
        BlogPostVersion current = blogService.currentVersion(post);
        String user = "Rewrite this post following the editor's notes. Keep what the notes do not ask to change.\n\n"
                + "Editor's notes (Persian):\n" + current.getAdminNote() + "\n\n"
                + "Current title: " + current.getTitle() + "\n"
                + "Current body:\n" + current.getBodyMarkdown();
        BlogService.Draft draft = ask(user, post.getTopicKey());
        BlogPost revised = blogService.addRevision(postId, draft);
        pictures(postId);
        return revised;
    }

    /** Pictures are a bonus: a failure leaves the text and the admin can make them again. */
    private void pictures(Long postId) {
        try {
            imageGenerator.generate(postId);
        } catch (RuntimeException exception) {
            log.warn("Pictures of blog post {} could not be made: {}", postId, exception.getMessage());
        }
    }

    /** Another column than the last post's, then the first subject of it not written yet (or written longest ago). */
    static Topic nextTopic(List<Topic> topics, List<BlogPost> recentNewestFirst) {
        List<String> usedKeys = recentNewestFirst.stream().map(BlogPost::getTopicKey).toList();
        String lastColumn = topics.stream()
                .filter(topic -> !usedKeys.isEmpty() && topic.key().equals(usedKeys.get(0)))
                .map(Topic::column).findFirst().orElse(null);
        Set<String> columns = new LinkedHashSet<>(topics.stream().map(Topic::column).toList());
        // the column that waited longest since its last post comes first
        List<String> order = new ArrayList<>(columns);
        order.sort((a, b) -> Integer.compare(lastUse(b, topics, usedKeys), lastUse(a, topics, usedKeys)));
        for (String column : order) {
            if (column.equals(lastColumn) && columns.size() > 1) {
                continue;
            }
            List<Topic> inColumn = topics.stream().filter(topic -> topic.column().equals(column)).toList();
            return inColumn.stream().filter(topic -> !usedKeys.contains(topic.key())).findFirst()
                    .orElseGet(() -> inColumn.stream()
                            .max((a, b) -> Integer.compare(usedKeys.indexOf(a.key()), usedKeys.indexOf(b.key())))
                            .orElseThrow());
        }
        return topics.get(0);
    }

    /** How long ago a column was used (index in newest-first list; never used = very long ago). */
    private static int lastUse(String column, List<Topic> topics, List<String> usedKeys) {
        for (int i = 0; i < usedKeys.size(); i++) {
            String key = usedKeys.get(i);
            if (topics.stream().anyMatch(topic -> topic.key().equals(key) && topic.column().equals(column))) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private BlogService.Draft ask(String userText, String topicKey) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "model", model,
                    "temperature", 0.7,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userText))));
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(
                            URI.create(baseUrl + "/api/v1/wrapper/deepseek/chat/completions"))
                    .timeout(Duration.ofSeconds(180))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 300) {
                log.warn("Blog writer model -> {}: {}", response.statusCode(), response.body());
                throw new IllegalArgumentException("نویسنده جواب نداد (" + response.statusCode() + ").");
            }
            String content = mapper.readTree(response.body()).path("choices").path(0).path("message").path("content").asText("");
            JsonNode post = mapper.readTree(content.replaceAll("(?s)^```(json)?|```$", "").trim());
            return new BlogService.Draft(
                    post.path("title").asText(""),
                    post.path("slug").asText(""),
                    post.path("summary").asText(""),
                    post.path("body_markdown").asText(""),
                    post.path("tags").asText(""),
                    post.path("image_prompt").asText(""),
                    topicKey);
        } catch (IOException exception) {
            throw new IllegalArgumentException("خروجی نویسنده خوانده نشد.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("نوشتن مطلب قطع شد.");
        }
    }

    static List<Topic> parseTopics(String text) {
        List<Topic> list = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String[] parts = line.split("\\|", 3);
            if (line.isBlank() || line.startsWith("#") || parts.length < 3) {
                continue;
            }
            list.add(new Topic(parts[0].trim(), parts[1].trim(), parts[2].trim()));
        }
        return list;
    }

    private static String resource(String path) throws IOException {
        return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
