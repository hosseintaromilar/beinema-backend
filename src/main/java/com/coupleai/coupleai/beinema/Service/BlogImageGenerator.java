package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.Entity.BlogPost;
import com.coupleai.coupleai.beinema.Entity.BlogPostVersion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Makes the four picture variants of a post version with a Metis generation
 * (flux-schnell: four outputs in one request). The generation is asynchronous:
 * it is created, then read every few seconds until it is COMPLETED.
 */
@Service
public class BlogImageGenerator {

    private static final Logger log = LoggerFactory.getLogger(BlogImageGenerator.class);
    private static final String STYLE = ", warm natural light, soft muted earthy colors, calm and hopeful mood, "
            + "editorial illustration, no text, no letters, no words, no watermark";

    private final BlogService blogService;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String apiKey;
    private final String provider;
    private final String model;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public BlogImageGenerator(BlogService blogService,
                              ObjectMapper mapper,
                              @Value("${metis.base-url}") String baseUrl,
                              @Value("${metis.api-key}") String apiKey,
                              @Value("${beinema.blog.image-provider:black-forest-labs}") String provider,
                              @Value("${beinema.blog.image-model:flux-schnell}") String model) {
        this.blogService = blogService;
        this.mapper = mapper;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.provider = provider;
        this.model = model;
    }

    /** Makes the four pictures of the post's current version and stores their links; returns the cost in cents. */
    public double generate(Long postId) {
        BlogPost post = blogService.view(postId).post();
        BlogPostVersion version = blogService.currentVersion(post);
        String prompt = version.getImagePrompt() == null || version.getImagePrompt().isBlank()
                ? "A couple sitting together at a kitchen table, talking calmly"
                : version.getImagePrompt();
        try {
            JsonNode created = send("POST", "/api/v2/generate", mapper.writeValueAsString(Map.of(
                    "model", Map.of("name", provider, "model", model),
                    "operation", "Imagine",
                    "args", Map.of(
                            "prompt", prompt + STYLE,
                            "aspect_ratio", "16:9",
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
            blogService.addImages(postId, version.getVersionNumber(), id, urls);
            double cost = done.path("usage").path("cost").asDouble(0);
            log.info("Blog post {} v{}: {} pictures, cost {}", postId, version.getVersionNumber(), urls.size(), cost);
            return cost;
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("ارتباط با سرویس تصویر ممکن نشد.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("ساخت تصویر قطع شد.");
        }
    }

    private JsonNode waitFor(String id) throws java.io.IOException, InterruptedException {
        for (int i = 0; i < 60; i++) {
            Thread.sleep(3000);
            JsonNode state = send("GET", "/api/v2/generate/" + id, null);
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

    private JsonNode send(String method, String path, String body) throws java.io.IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json");
        request = body == null ? request.GET() : request.method(method, HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            log.warn("Metis generation {} {} -> {}: {}", method, path, response.statusCode(), response.body());
            throw new IllegalArgumentException("سرویس تصویر خطا داد (" + response.statusCode() + ").");
        }
        return mapper.readTree(response.body());
    }
}
