package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.blog.BlogService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BreakReadingServiceTest {

    private static BlogService.PublicPost post(String slug, String title, String tags, long publishedAt) {
        return new BlogService.PublicPost(1L, slug, title, "", null, publishedAt, tags, null, null);
    }

    @Test
    void postsAboutTheRoomComeFirstThenTheField() {
        List<BlogService.PublicPost> posts = List.of(
                post("budget", "بودجهٔ خانواده", "مالی", 3),
                post("work", "وقتی کار سخت است و حرفش را به همسر می‌زنیم", "رابطه", 1),
                post("fight", "دعوا بعد از یک روز بد", "رابطه", 2));

        List<BlogService.PublicPost> ranked = BreakReadingService.rank(posts,
                BreakReadingService.words("فکر استعفا و کار سخت"), BreakReadingService.words("رابطه همسر دعوا"));

        assertThat(ranked).extracting(BlogService.PublicPost::slug).containsExactly("work", "fight", "budget");
    }

    @Test
    void withNothingInCommonTheNewestComeFirst() {
        List<BlogService.PublicPost> posts = List.of(post("old", "الف", "", 1), post("new", "ب", "", 5));

        assertThat(BreakReadingService.rank(posts, Set.of(), Set.of()))
                .extracting(BlogService.PublicPost::slug).containsExactly("new", "old");
    }

    @Test
    void wordsIgnoreJoinersAndCommonWords() {
        assertThat(BreakReadingService.words("می‌خواهم با همسرم حرف بزنم"))
                .contains("میخواهم", "همسرم").doesNotContain("با");
    }
}
