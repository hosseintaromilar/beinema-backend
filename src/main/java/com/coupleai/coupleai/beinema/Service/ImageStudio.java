package com.coupleai.coupleai.beinema.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pictures made with a Metis generation (flux-schnell, four options in one
 * request). Every kind of picture has its own fixed base style; the admin's
 * description of what they want comes in front of it. Only links this service
 * made can later be stored as our own file.
 */
@Service
public class ImageStudio {

    private static final Logger log = LoggerFactory.getLogger(ImageStudio.class);

    public enum Kind {
        /** The counselor's portrait in the app (square). */
        AGENT("1:1"),
        /** The picture on top of a blog post. */
        BLOG("16:9");

        final String aspect;

        Kind(String aspect) {
            this.aspect = aspect;
        }
    }

    public record Result(String generationId, List<String> urls, double cost) {
    }

    private final ObjectMapper mapper;
    private final FileStorageService fileStorageService;
    private final String baseUrl;
    private final String apiKey;
    private final String provider;
    private final String model;
    private final String textModel;
    private final Map<Kind, String> styles;
    /** Links made recently, the only ones that may be stored. */
    private final Set<String> issued = Collections.synchronizedSet(Collections.newSetFromMap(
            new LinkedHashMap<>(16, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > 400;
                }
            }));
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public ImageStudio(ObjectMapper mapper,
                       FileStorageService fileStorageService,
                       @Value("${metis.base-url}") String baseUrl,
                       @Value("${metis.api-key}") String apiKey,
                       @Value("${beinema.blog.image-provider:black-forest-labs}") String provider,
                       @Value("${beinema.blog.image-model:flux-schnell}") String model,
                       @Value("${beinema.images.agent-style}") String agentStyle,
                       @Value("${beinema.images.blog-style}") String blogStyle,
                       @Value("${beinema.blog.writer-model:deepseek-v4-1-flash-1017}") String textModel) {
        this.textModel = textModel;
        this.mapper = mapper;
        this.fileStorageService = fileStorageService;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.provider = provider;
        this.model = model;
        this.styles = Map.of(Kind.AGENT, agentStyle, Kind.BLOG, blogStyle);
    }

    /** Four options for the admin's description in the base style of this kind of picture. */
    public Result imagine(Kind kind, String description) {
        String wanted = description == null ? "" : description.trim();
        if (wanted.isEmpty()) {
            throw new IllegalArgumentException("توصیف تصویر را بنویس.");
        }
        if (wanted.length() > 1000) {
            throw new IllegalArgumentException("توصیف تصویر خیلی طولانی است.");
        }
        String prompt = inEnglish(wanted) + ". " + styles.get(kind);
        try {
            JsonNode created = send("/api/v2/generate", mapper.writeValueAsString(Map.of(
                    "model", Map.of("name", provider, "model", model),
                    "operation", "Imagine",
                    "args", Map.of(
                            "prompt", prompt,
                            "aspect_ratio", kind.aspect,
                            "num_outputs", 4,
                            "num_inference_steps", 4,
                            "megapixels", "1",
                            "output_format", "webp"))));
            String id = created.path("id").asText("");
            if (id.isEmpty()) {
                throw new IllegalArgumentException("ساخت تصویر شروع نشد.");
            }
            JsonNode done = waitFor(id);
            List<String> urls = new ArrayList<>();
            for (JsonNode item : done.path("generations")) {
                String url = item.path("url").asText("");
                if (!url.isEmpty()) {
                    urls.add(url);
                }
            }
            if (urls.isEmpty()) {
                throw new IllegalArgumentException("تصویری ساخته نشد.");
            }
            issued.addAll(urls);
            double cost = done.path("usage").path("cost").asDouble(0);
            log.info("{} pictures ({}): {} made, cost {}", kind, id, urls.size(), cost);
            return new Result(id, urls, cost);
        } catch (IOException exception) {
            throw new IllegalArgumentException("ارتباط با سرویس تصویر ممکن نشد.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("ساخت تصویر قطع شد.");
        }
    }

    /**
     * The image model understands English only: a Persian description is first
     * turned into a short English picture prompt by the text model. If that
     * fails the description goes as it is.
     */
    String inEnglish(String description) {
        if (!description.matches("(?s).*[\\u0600-\\u06FF].*")) {
            return description;
        }
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "model", textModel,
                    "temperature", 0.2,
                    "messages", List.of(
                            Map.of("role", "system", "content", "Translate the user's Persian picture description into one "
                                    + "concise English prompt for an image generator. Keep every object, person, color and "
                                    + "place it names; add nothing else. Answer with the English prompt only."),
                            Map.of("role", "user", "content", description))));
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(
                            URI.create(baseUrl + "/api/v1/wrapper/deepseek/chat/completions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String english = response.statusCode() < 300
                    ? mapper.readTree(response.body()).path("choices").path(0).path("message").path("content").asText("").trim()
                    : "";
            if (english.isEmpty()) {
                log.warn("Picture description not translated ({}); sent as it is", response.statusCode());
                return description;
            }
            log.info("Picture description in English: {}", english);
            return english;
        } catch (IOException exception) {
            return description;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return description;
        }
    }

    /** Copies one of the options this service made into /uploads/{folder}. */
    public String store(String url, String folder) {
        if (url == null || !issued.contains(url)) {
            throw new IllegalArgumentException("این تصویر از گزینه‌های ساخته‌شده نیست؛ دوباره بساز.");
        }
        return download(url, folder);
    }

    /** Copies a picture whose link we keep ourselves (a blog option saved in the database). */
    String download(String url, String folder) {
        if (url == null || !url.startsWith("https://")) {
            throw new IllegalArgumentException("آدرس تصویر معتبر نیست.");
        }
        try {
            HttpResponse<byte[]> response = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IllegalArgumentException("دریافت تصویر ممکن نشد (" + response.statusCode() + ").");
            }
            String type = response.headers().firstValue("Content-Type").orElse("");
            return fileStorageService.storeGeneratedImage(folder, response.body(), type);
        } catch (IOException exception) {
            throw new IllegalArgumentException("دریافت تصویر ممکن نشد.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("دریافت تصویر قطع شد.");
        }
    }

    private JsonNode waitFor(String id) throws IOException, InterruptedException {
        for (int i = 0; i < 60; i++) {
            Thread.sleep(3000);
            JsonNode state = send("/api/v2/generate/" + id, null);
            String status = state.path("status").asText("");
            if ("COMPLETED".equals(status)) {
                return state;
            }
            if ("ERROR".equals(status) || "CANCELLED".equals(status)) {
                throw new IllegalArgumentException("ساخت تصویر ناموفق بود: " + state.path("error").asText(""));
            }
        }
        throw new IllegalArgumentException("ساخت تصویر بیش از حد طول کشید.");
    }

    private JsonNode send(String path, String body) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json");
        request = body == null ? request.GET() : request.POST(HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            log.warn("Metis generation {} -> {}: {}", path, response.statusCode(), response.body());
            throw new IllegalArgumentException("سرویس تصویر خطا داد (" + response.statusCode() + ").");
        }
        return mapper.readTree(response.body());
    }
}
