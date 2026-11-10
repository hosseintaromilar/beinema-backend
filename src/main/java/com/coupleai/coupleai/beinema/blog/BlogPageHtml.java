package com.coupleai.coupleai.beinema.blog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The site's page with a blog page already written in it, so a search engine reads the text
 * without running the app. It follows the site's renderer (utils/markdown.jsx): everything is
 * escaped and only links inside the site become links. The app replaces this when it starts.
 */
public final class BlogPageHtml {

    private static final String LIST_TITLE = "بلاگ بین‌ما | رابطه و گفتگو";
    private static final String LIST_DESCRIPTION =
            "نوشته‌های کوتاه و کاربردی برای زوج‌ها: گفتگو، نیازها و قدم‌های کوچک برای رابطهٔ بهتر.";
    private static final Pattern INLINE = Pattern.compile("\\*\\*([^*]+)\\*\\*|\\[([^\\]]+)\\]\\((/[^)\\s]*)\\)");
    private static final Pattern HEADING = Pattern.compile("^(#{2,4})\\s+(.*)$");
    private static final Pattern BULLET = Pattern.compile("^[-*]\\s+(.*)$");
    private static final Pattern NUMBERED = Pattern.compile("^(?:\\d+|[۰-۹]+)[.)]\\s+(.*)$");
    private static final ObjectMapper JSON = new ObjectMapper();

    private BlogPageHtml() {
    }

    public static String list(String shell, String siteUrl, List<BlogService.PublicPost> posts) {
        StringBuilder body = new StringBuilder("<main class=\"blog-page\"><header class=\"blog-hero\"><h1>بلاگ بین‌ما</h1>")
                .append("<p>نوشته‌های کوتاه و کاربردی برای اینکه با هم بهتر حرف بزنید و همدیگر را بهتر بفهمید.</p></header>")
                .append("<div class=\"blog-grid\">");
        for (BlogService.PublicPost post : posts) {
            body.append("<a class=\"blog-card\" href=\"/blog/").append(escape(post.slug())).append("\">");
            if (post.imagePath() != null) {
                body.append("<img src=\"").append(escape(post.imagePath())).append("\" alt=\"\" loading=\"lazy\">");
            }
            body.append("<div><h2>").append(escape(post.title())).append("</h2><p>")
                    .append(escape(post.summary())).append("</p></div></a>");
        }
        body.append("</div></main>");

        Map<String, Object> seed = new LinkedHashMap<>();
        seed.put("path", "/blog");
        seed.put("posts", posts);
        return page(shell, LIST_TITLE, LIST_DESCRIPTION, siteUrl + "/blog", null, "website", "", body.toString(), seed);
    }

    public static String post(String shell, String siteUrl, BlogService.PublicPost post,
                              List<BlogService.PublicPost> others) {
        String address = siteUrl + "/blog/" + post.slug();
        String image = post.imagePath() == null ? null : siteUrl + post.imagePath();
        StringBuilder body = new StringBuilder("<main class=\"blog-page\">")
                .append("<a class=\"blog-back\" href=\"/blog\">→ همهٔ نوشته‌ها</a><article class=\"blog-article\"><h1>")
                .append(escape(post.title())).append("</h1>");
        if (post.imagePath() != null) {
            body.append("<img src=\"").append(escape(post.imagePath())).append("\" alt=\"\">");
        }
        body.append("<div class=\"blog-text\">").append(markdown(post.bodyMarkdown())).append("</div>");
        // the admin's choice, in its order; without one, the first others
        List<String> chosen = post.related() == null ? List.of() : post.related();
        List<BlogService.PublicPost> related = chosen.isEmpty()
                ? others.stream().filter(other -> !other.slug().equals(post.slug())).limit(3).toList()
                : chosen.stream().flatMap(slug -> others.stream().filter(other -> other.slug().equals(slug)).limit(1)).toList();
        if (!related.isEmpty()) {
            body.append("<nav class=\"blog-related\"><h2>").append(chosen.isEmpty() ? "این‌ها را هم بخوانید" : "پیشنهاد خواندن")
                    .append("</h2><ul>");
            related.forEach(other -> body.append("<li><a href=\"/blog/").append(escape(other.slug())).append("\">")
                    .append(escape(other.title())).append("</a></li>"));
            body.append("</ul></nav>");
        }
        body.append("</article></main>");

        Map<String, Object> article = new LinkedHashMap<>();
        article.put("@context", "https://schema.org");
        article.put("@type", "BlogPosting");
        article.put("headline", post.title());
        article.put("description", post.summary());
        article.put("mainEntityOfPage", address);
        article.put("inLanguage", "fa");
        if (post.publishedAt() != null) {
            article.put("datePublished", Instant.ofEpochMilli(post.publishedAt()).toString());
        }
        if (image != null) {
            article.put("image", image);
        }
        article.put("author", Map.of("@type", "Organization", "name", "بین‌ما"));
        article.put("publisher", Map.of("@type", "Organization", "name", "بین‌ما"));
        String structured = "<script type=\"application/ld+json\">" + json(article) + "</script>\n";

        Map<String, Object> seed = new LinkedHashMap<>();
        seed.put("path", "/blog/" + post.slug());
        seed.put("post", post);
        return page(shell, post.title() + " | بلاگ بین‌ما", post.summary(), address, image, "article", structured,
                body.toString(), seed);
    }

    public static String missing(String shell) {
        String head = replace(shell, "<title>[^<]*</title>", "<title>این نوشته پیدا نشد | بلاگ بین‌ما</title>");
        return head.replace("</head>", "<meta name=\"robots\" content=\"noindex\">\n</head>");
    }

    private static String page(String shell, String title, String description, String address, String image,
                               String type, String structured, String body, Map<String, Object> seed) {
        String safeDescription = escape(description == null ? "" : description);
        String html = replace(shell, "<title>[^<]*</title>", "<title>" + escape(title) + "</title>");
        html = replace(html, "<meta name=\"description\" content=\"[^\"]*\"\\s*/?>",
                "<meta name=\"description\" content=\"" + safeDescription + "\">");
        html = replace(html, "<meta property=\"og:type\" content=\"[^\"]*\"\\s*/?>",
                "<meta property=\"og:type\" content=\"" + type + "\">");
        html = replace(html, "<meta property=\"og:title\" content=\"[^\"]*\"\\s*/?>",
                "<meta property=\"og:title\" content=\"" + escape(title) + "\">");
        html = replace(html, "<meta property=\"og:description\" content=\"[^\"]*\"\\s*/?>",
                "<meta property=\"og:description\" content=\"" + safeDescription + "\">");
        String extra = "<link rel=\"canonical\" href=\"" + escape(address) + "\">\n"
                + "<meta property=\"og:url\" content=\"" + escape(address) + "\">\n"
                + (image == null ? "" : "<meta property=\"og:image\" content=\"" + escape(image) + "\">\n")
                + structured;
        html = html.replace("</head>", extra + "</head>");
        return html.replace("<div id=\"root\"></div>", "<div id=\"root\">" + body + "</div>\n"
                + "<script type=\"application/json\" id=\"blog-seed\">" + json(seed) + "</script>");
    }

    /** A line of its own: a picture from our own uploads, with its caption. */
    private static final Pattern PICTURE = Pattern.compile("^!\\[([^\\]]*)\\]\\((/uploads/[^)\\s]+)\\)$");

    static String markdown(String source) {
        StringBuilder html = new StringBuilder();
        List<String> paragraph = new ArrayList<>();
        List<String> items = new ArrayList<>();
        boolean ordered = false;
        for (String raw : (source == null ? "" : source).replace("\r", "").split("\n")) {
            String line = raw.trim();
            Matcher heading = HEADING.matcher(line);
            Matcher bullet = BULLET.matcher(line);
            Matcher numbered = NUMBERED.matcher(line);
            Matcher picture = PICTURE.matcher(line);
            if (picture.matches()) {
                flushParagraph(html, paragraph);
                flushList(html, items, ordered);
                html.append("<figure class=\"blog-figure\"><img src=\"").append(escape(picture.group(2)))
                        .append("\" alt=\"").append(escape(picture.group(1))).append("\" loading=\"lazy\">");
                if (!picture.group(1).isBlank()) {
                    html.append("<figcaption>").append(escape(picture.group(1))).append("</figcaption>");
                }
                html.append("</figure>");
            } else if (line.isEmpty()) {
                flushParagraph(html, paragraph);
                flushList(html, items, ordered);
            } else if (heading.matches()) {
                flushParagraph(html, paragraph);
                flushList(html, items, ordered);
                String tag = heading.group(1).length() == 2 ? "h2" : "h3";
                html.append('<').append(tag).append('>').append(inline(heading.group(2)))
                        .append("</").append(tag).append('>');
            } else if (bullet.matches() || numbered.matches()) {
                flushParagraph(html, paragraph);
                boolean isNumbered = !bullet.matches();
                if (!items.isEmpty() && ordered != isNumbered) {
                    flushList(html, items, ordered);
                }
                ordered = isNumbered;
                items.add(isNumbered ? numbered.group(1) : bullet.group(1));
            } else {
                flushList(html, items, ordered);
                paragraph.add(line);
            }
        }
        flushParagraph(html, paragraph);
        flushList(html, items, ordered);
        return html.toString();
    }

    private static void flushParagraph(StringBuilder html, List<String> paragraph) {
        if (!paragraph.isEmpty()) {
            html.append("<p>").append(inline(String.join(" ", paragraph))).append("</p>");
            paragraph.clear();
        }
    }

    private static void flushList(StringBuilder html, List<String> items, boolean ordered) {
        if (!items.isEmpty()) {
            String tag = ordered ? "ol" : "ul";
            html.append('<').append(tag).append('>');
            items.forEach(item -> html.append("<li>").append(inline(item)).append("</li>"));
            html.append("</").append(tag).append('>');
            items.clear();
        }
    }

    private static String inline(String text) {
        StringBuilder html = new StringBuilder();
        Matcher match = INLINE.matcher(text);
        int at = 0;
        while (match.find()) {
            html.append(escape(text.substring(at, match.start())));
            if (match.group(1) != null) {
                html.append("<strong>").append(inline(match.group(1))).append("</strong>");
            } else if (match.group(3).startsWith("//")) {
                html.append(escape(match.group()));
            } else {
                html.append("<a href=\"").append(escape(match.group(3))).append("\">")
                        .append(escape(match.group(2))).append("</a>");
            }
            at = match.end();
        }
        return html.append(escape(text.substring(at))).toString();
    }

    private static String replace(String html, String pattern, String replacement) {
        return Pattern.compile(pattern).matcher(html).replaceFirst(Matcher.quoteReplacement(replacement));
    }

    private static String json(Object value) {
        try {
            // inside a script tag, so nothing in a post can close it
            return JSON.writeValueAsString(value).replace("<", "\\u003c");
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
