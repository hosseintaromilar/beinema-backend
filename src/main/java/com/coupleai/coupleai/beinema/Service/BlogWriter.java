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
    private static final String PRIORITY_COLUMN = "seo";

    public record Topic(String key, String column, String subject) {
    }

    /** What the admin panel shows about the writer: busy, and how the last try ended. */
    public record Status(boolean writing, Long startedAt, Long lastPostId, String lastError, Long lastFinishedAt) {
    }

    private volatile boolean writing;
    private volatile Long startedAt;
    private volatile Long lastPostId;
    private volatile String lastError;
    private volatile Long lastFinishedAt;

    private final BlogService blogService;
    private final BlogPostRepository postRepository;
    private final com.coupleai.coupleai.beinema.Repository.AgentRepository agentRepository;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final boolean dailyEnabled;
    private final String systemPrompt;
    private final List<Topic> topics;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public BlogWriter(BlogService blogService,
                      BlogPostRepository postRepository,
                      com.coupleai.coupleai.beinema.Repository.AgentRepository agentRepository,
                      ObjectMapper mapper,
                      @Value("${metis.base-url}") String baseUrl,
                      @Value("${metis.api-key}") String apiKey,
                      @Value("${beinema.blog.writer-model:deepseek-v4-1-flash-1017}") String model,
                      @Value("${beinema.blog.daily-enabled:false}") boolean dailyEnabled) throws IOException {
        this.blogService = blogService;
        this.postRepository = postRepository;
        this.agentRepository = agentRepository;
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
            startWriting(null);
        } catch (RuntimeException exception) {
            log.info("The daily blog post was not started: {}", exception.getMessage());
        }
    }

    public Status status() {
        return new Status(writing, startedAt, lastPostId, lastError, lastFinishedAt);
    }

    /**
     * The admin's «نوشتن مطلب تازه»: writing and the pictures take about a minute,
     * longer than a request may stay open behind the proxies, so it runs in the
     * background and the panel follows {@link #status()}. With a topic from the admin
     * the post is about that; without one, the next topic of the plan.
     */
    public synchronized Status startWriting(Topic chosen) {
        if (writing) {
            throw new IllegalArgumentException("نویسنده همین الان در حال نوشتن است.");
        }
        writing = true;
        startedAt = System.currentTimeMillis();
        lastError = null;
        Thread thread = new Thread(() -> {
            try {
                lastPostId = writeNew(chosen).getId();
            } catch (RuntimeException exception) {
                lastError = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                log.warn("The blog post could not be written", exception);
            } finally {
                lastFinishedAt = System.currentTimeMillis();
                writing = false;
            }
        }, "beinema-blog-writer");
        thread.setDaemon(true);
        thread.start();
        return status();
    }

    /** A topic the admin wrote in the panel; without an address the writer's own suggestion is used. */
    public static Topic adminTopic(String subject, String slug) {
        if (subject == null || subject.isBlank()) {
            return null;
        }
        String key = slug == null || slug.isBlank() ? "" : BlogService.slugOf(slug, null);
        return new Topic(key, "admin", subject.trim());
    }

    /** Writes a new post now (the daily run, and the background job of the admin's button). */
    public BlogPost writeNew(Topic chosen) {
        List<BlogPost> recent = postRepository.findTop60ByOrderByIdDesc();
        Topic topic = chosen != null ? chosen : nextTopic(topics, recent);
        StringBuilder user = new StringBuilder()
                .append("Subject (Persian): ").append(topic.subject()).append('\n')
                .append("Do not repeat these earlier titles or their angle:\n");
        recent.stream().limit(30).forEach(post -> user.append("- ").append(post.getTitle()).append('\n'));
        Map<String, String> pages = linkablePages(null);
        BlogService.Draft draft = withKnownLinks(ask(user.append(linksBlock(pages)).toString(), topic.key()), pages);
        // the address comes from the plan or the admin; the model's only when neither gave one
        String slug = topic.key().isBlank() ? draft.slug() : topic.key();
        return blogService.createDraft(new BlogService.Draft(draft.title(), slug, draft.summary(),
                draft.bodyMarkdown(), draft.tags(), draft.imagePrompt(), topic.key().isBlank() ? null : topic.key()));
    }

    /** The rewrite after the admin's notes, in the background; a failure shows in {@link #status()}. */
    public void startRevising(Long postId) {
        Thread thread = new Thread(() -> {
            try {
                revise(postId);
            } catch (RuntimeException exception) {
                lastError = "بازنویسی مطلب " + postId + ": " + exception.getMessage();
                lastFinishedAt = System.currentTimeMillis();
                log.warn("Blog post {} could not be rewritten", postId, exception);
            }
        }, "beinema-blog-" + postId);
        thread.setDaemon(true);
        thread.start();
    }

    /** The next version of a post the admin sent back with notes. */
    public BlogPost revise(Long postId) {
        BlogPost post = blogService.view(postId).post();
        BlogPostVersion current = blogService.currentVersion(post);
        String user = "Rewrite this post following the editor's notes. Keep what the notes do not ask to change.\n\n"
                + "Editor's notes (Persian):\n" + current.getAdminNote() + "\n\n"
                + "Current title: " + current.getTitle() + "\n"
                + "Current body:\n" + current.getBodyMarkdown();
        Map<String, String> pages = linkablePages(post.getSlug());
        BlogService.Draft draft = withKnownLinks(ask(user + linksBlock(pages), post.getTopicKey()), pages);
        return blogService.addRevision(postId, draft);
    }

    /** The pages a post may link to: published posts and the companions. Path to its title. */
    private Map<String, String> linkablePages(String ownSlug) {
        Map<String, String> pages = new java.util.LinkedHashMap<>();
        postRepository.findAllByStatusOrderByPublishedAtDesc(BlogPostStatus.PUBLISHED).stream()
                .filter(post -> !post.getSlug().equals(ownSlug))
                .limit(40)
                .forEach(post -> pages.put("/blog/" + post.getSlug(), post.getTitle()));
        agentRepository.findAll().forEach(agent -> pages.put("/agents/" + agent.getId(), agent.getName()));
        return pages;
    }

    private static String linksBlock(Map<String, String> pages) {
        StringBuilder out = new StringBuilder("\nPages you may link to (path | title):\n");
        pages.forEach((path, title) -> out.append(path).append(" | ").append(title).append('\n'));
        return out.toString();
    }

    private static BlogService.Draft withKnownLinks(BlogService.Draft draft, Map<String, String> pages) {
        return new BlogService.Draft(draft.title(), draft.slug(), draft.summary(),
                keepKnownLinks(draft.bodyMarkdown(), pages.keySet()), draft.tags(), draft.imagePrompt(), draft.topicKey());
    }

    /** A link to a page that does not exist would hurt the site: only its words stay. */
    static String keepKnownLinks(String body, Set<String> known) {
        if (body == null) {
            return null;
        }
        return java.util.regex.Pattern.compile("\\[([^\\]\\n]+)\\]\\(([^)\\s]+)\\)").matcher(body).replaceAll(match ->
                java.util.regex.Matcher.quoteReplacement(known.contains(match.group(2)) ? match.group() : match.group(1)));
    }

    /** Another column than the last post's, then the first subject of it not written yet (or written longest ago). */
    static Topic nextTopic(List<Topic> topics, List<BlogPost> recentNewestFirst) {
        List<String> usedKeys = recentNewestFirst.stream().map(BlogPost::getTopicKey).toList();
        // the researched subjects come before the rotation of the columns
        java.util.Optional<Topic> priority = topics.stream()
                .filter(topic -> PRIORITY_COLUMN.equals(topic.column()) && !usedKeys.contains(topic.key()))
                .findFirst();
        if (priority.isPresent()) {
            return priority.get();
        }
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
