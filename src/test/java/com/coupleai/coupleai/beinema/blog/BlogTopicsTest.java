package com.coupleai.coupleai.beinema.blog;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BlogTopicsTest {

    private static BlogService.PublicPost post(String title, String summary, String tags) {
        return new BlogService.PublicPost(1L, "p", title, summary, null, 1L, tags, null, null);
    }

    @Test
    void aPostAboutMoneyLeadsToPouria() {
        BlogService.PublicPost money = post("وقتی سر خرج‌ها دعوا می‌شود", "بودجهٔ ماهانه و پس‌انداز", "مالی");
        assertThat(BlogTopics.of(money).get(0).slug()).isEqualTo("money");
        assertThat(BlogTopics.companionOf(money).slug()).isEqualTo("couples-finance");
    }

    @Test
    void aPostWithoutATopicLeadsToNina_andTheJoinerDoesNotMatter() {
        assertThat(BlogTopics.companionOf(post("یک نوشته", "", "")).slug()).isEqualTo("couple-relationship");
        assertThat(BlogTopics.of(post("کارهای\u200cخانه", "", ""))).extracting(BlogTopics.Topic::slug).contains("daily-life");
    }

    @Test
    void theTopicPageListsOnlyItsPosts_andLinksItsCompanion() {
        List<BlogService.PublicPost> posts = List.of(post("تربیت فرزند نوجوان", "", ""), post("آشتی بعد از قهر", "", ""));
        String html = BlogPageHtml.topic("<html><head><title>x</title></head><body><div id=\"root\"></div></body></html>",
                "https://beinema.ir", BlogTopics.find("parenting").orElseThrow(), posts);
        // what a reader and a search engine see (the app's data block after it holds every post)
        String page = html.substring(0, html.indexOf("<script type=\"application/json\""));
        assertThat(page).contains("تربیت فرزند نوجوان").doesNotContain("آشتی بعد از قهر").contains("/agents/parenting");
    }
}
