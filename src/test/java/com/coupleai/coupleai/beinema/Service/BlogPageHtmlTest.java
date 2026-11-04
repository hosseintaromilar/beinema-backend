package com.coupleai.coupleai.beinema.Service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlogPageHtmlTest {

    private static final String SHELL = """
            <html><head><meta name="robots" content="noindex, nofollow" /><title>site</title>
            <meta name="description" content="old" /><meta property="og:type" content="website" />
            <meta property="og:title" content="old" /><meta property="og:description" content="old" /></head>
            <body><div id="root"></div></body></html>""";

    private static BlogService.PublicPost post(String slug, String title, String body) {
        return new BlogService.PublicPost(1L, slug, title, "خلاصه", "/uploads/a.png", 0L, "t", body, List.of());
    }

    @Test
    void writesThePostAndItsHeadIntoThePage() {
        String html = BlogPageHtml.post(SHELL, "https://x.ir", post("a", "عنوان", "## تیتر\n\nمتن **پررنگ**"),
                List.of(post("a", "عنوان", null), post("b", "دیگری", null)));

        assertTrue(html.contains("<title>عنوان | بلاگ بین‌ما</title>"));
        assertTrue(html.contains("<link rel=\"canonical\" href=\"https://x.ir/blog/a\">"));
        assertTrue(html.contains("<meta property=\"og:image\" content=\"https://x.ir/uploads/a.png\">"));
        assertTrue(html.contains("\"@type\":\"BlogPosting\""));
        assertTrue(html.contains("<h2>تیتر</h2><p>متن <strong>پررنگ</strong></p>"));
        assertTrue(html.contains("href=\"/blog/b\""));
        assertFalse(html.contains("<li><a href=\"/blog/a\">"));
        assertTrue(html.contains("id=\"blog-seed\""));
        assertTrue(html.contains("noindex"), "the shell's own robots line decides");
    }

    @Test
    void theAdminsChoiceIsSuggestedInItsOrder() {
        BlogService.PublicPost chosen = new BlogService.PublicPost(1L, "a", "عنوان", "خلاصه", null, 0L, "t", "متن",
                List.of("c", "b"));
        String html = BlogPageHtml.post(SHELL, "https://x.ir", chosen,
                List.of(post("a", "عنوان", null), post("b", "دومی", null), post("c", "سومی", null), post("d", "چهارمی", null)));

        assertTrue(html.contains("<h2>پیشنهاد خواندن</h2>"));
        assertTrue(html.indexOf("href=\"/blog/c\"") < html.indexOf("href=\"/blog/b\""));
        assertFalse(html.contains("href=\"/blog/d\""));
    }

    @Test
    void aPictureInTheTextIsShownOnlyFromOurUploads() {
        String html = BlogPageHtml.markdown("متن\n\n![دو صندلی](/uploads/blog/a.webp)\n\n![بد](https://evil.example/x.png)");

        assertTrue(html.contains("<figure class=\"blog-figure\"><img src=\"/uploads/blog/a.webp\" alt=\"دو صندلی\""));
        assertTrue(html.contains("<figcaption>دو صندلی</figcaption>"));
        assertFalse(html.contains("<img src=\"https://evil.example"));
    }

    @Test
    void aPostCannotInjectMarkup() {
        String html = BlogPageHtml.post(SHELL, "https://x.ir",
                post("a", "<b>t</b>", "<script>x</script> [بد](//evil.com) [بد](https://evil.com) </script><script>y"),
                List.of());

        assertFalse(html.contains("<script>x"));
        assertFalse(html.contains("<script>y"));
        assertFalse(html.contains("href=\"//evil"));
        assertFalse(html.contains("href=\"https://evil"));
        assertFalse(html.contains("<b>t</b>"));
    }

    @Test
    void markdownFollowsTheSite() {
        assertEquals("<ul><li>a</li><li>b</li></ul><ol><li>c</li></ol><p>d e</p>",
                BlogPageHtml.markdown("- a\n* b\n۱. c\n\nd\ne"));
        assertEquals("<p><a href=\"/blog/x\">لینک</a></p>", BlogPageHtml.markdown("[لینک](/blog/x)"));
    }

    @Test
    void aMissingPostIsKeptOutOfTheIndex() {
        String html = BlogPageHtml.missing(SHELL);
        assertTrue(html.contains("<title>این نوشته پیدا نشد"));
        assertTrue(html.contains("<meta name=\"robots\" content=\"noindex\">"));
    }
}
