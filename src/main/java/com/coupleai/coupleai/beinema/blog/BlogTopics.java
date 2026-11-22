package com.coupleai.coupleai.beinema.blog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The blog's topics: each has its own page (/blog/topic/{slug}) and the companion who works on it, so a
 * post leads its reader to that companion's page. A post is in a topic when its title, summary or tags
 * hold one of the topic's words. The same list is in the site (src/content/blogTopics.js); keep both alike.
 */
public final class BlogTopics {

    public record Companion(String slug, String label) {
    }

    public record Topic(String slug, String title, String description, List<String> words, Companion companion) {
    }

    static final Companion NINA = new Companion("couple-relationship", "نینا، همراه رابطهٔ زوج‌ها");
    static final Companion POURIA = new Companion("couples-finance", "پوریا، همراه گفتگوی مالی زوج‌ها");
    static final Companion RAHA = new Companion("parenting", "رها، همراه والدین در تربیت فرزند");
    static final Companion ELIKA = new Companion("premarital", "الیکا، همراه گفتگوهای پیش از ازدواج");
    static final Companion NADER = new Companion("in-laws", "نادر، همراه رابطه با خانواده‌ها");
    static final Companion RAHELEH = new Companion("intimacy", "راحله، همراه صمیمیت و رابطهٔ زناشویی");

    public static final List<Topic> ALL = List.of(
            new Topic("fights-and-repair", "دعوا و آشتی",
                    "دعواهای تکراری زن و شوهر از کجا می‌آیند و چطور بعد از دعوا دوباره به هم نزدیک شویم.",
                    List.of("دعوا", "آشتی", "قهر", "اختلاف", "خشم"), NINA),
            new Topic("talking-together", "گفتگو با همسر",
                    "چطور با همسرمان حرف بزنیم که شنیده شویم، و چطور گوش بدهیم که او هم حس کند شنیده شده.",
                    List.of("گفتگو", "حرف زدن", "گوش دادن", "صحبت"), NINA),
            new Topic("distance", "سردی و فاصله",
                    "وقتی رابطه سرد می‌شود و حس می‌کنیم از هم دور شده‌ایم: نشانه‌ها و قدم‌های کوچک برای برگشتن.",
                    List.of("سرد", "فاصله", "بی‌تفاوتی", "دلسردی", "تفاهم"), NINA),
            new Topic("daily-life", "زندگی روزمره",
                    "کارهای خانه، قدردانی و جزئیات روزمره‌ای که رابطه را گرم‌تر یا سردتر می‌کنند.",
                    List.of("کار خانه", "کارهای خانه", "قدردانی", "زندگی مشترک"), NINA),
            new Topic("before-marriage", "پیش از ازدواج",
                    "گفتگوهایی که بهتر است پیش از ازدواج انجام شود: خواسته‌ها، تفاوت‌ها و انتظارها.",
                    List.of("خواستگاری", "نامزدی", "قبل از ازدواج", "پیش از ازدواج"), ELIKA),
            new Topic("families", "خانواده‌ها",
                    "رابطه با خانوادهٔ همسر و خانوادهٔ خودمان، و مرزهایی که هم احترام می‌گذارند و هم از رابطه محافظت می‌کنند.",
                    List.of("خانواده", "والدین", "مادرشوهر", "مادرزن", "پدرشوهر", "پدرزن"), NADER),
            new Topic("parenting", "فرزند و نوجوان",
                    "تربیت فرزند وقتی پدر و مادر نظر یکسانی ندارند، و راه‌های مشترک برای رفتارهای سخت بچه‌ها.",
                    List.of("نوجوان", "فرزند", "تربیت", "کودک"), RAHA),
            new Topic("money", "پول و خرج‌ها",
                    "حرف زدن دربارهٔ پول، خرج‌ها، بدهی و پس‌انداز، بدون اینکه به دعوا برسد.",
                    List.of("پول", "خرج", "بودجه", "پس‌انداز", "مالی", "درآمد", "بدهی"), POURIA),
            new Topic("intimacy", "صمیمیت و نزدیکی",
                    "نزدیکی عاطفی و رابطهٔ زناشویی: حرف زدن دربارهٔ خواسته‌ها و فاصله‌ها، با احترام و بی‌قضاوت.",
                    List.of("صمیمیت", "نزدیکی", "زناشویی", "رابطه جنسی"), RAHELEH));

    private BlogTopics() {
    }

    public static Optional<Topic> find(String slug) {
        return ALL.stream().filter(topic -> topic.slug().equals(slug)).findFirst();
    }

    /** The topics of a post, the one with the most of its words first. */
    public static List<Topic> of(BlogService.PublicPost post) {
        // spaces and joiners do not count: «کارهای خانه» and «کارهای‌خانه» are the same words
        String text = tight(String.join(" ", nullToEmpty(post.title()), nullToEmpty(post.summary()), nullToEmpty(post.tags())));
        List<Topic> found = new ArrayList<>();
        List<Integer> hits = new ArrayList<>();
        for (Topic topic : ALL) {
            int count = (int) topic.words().stream().map(BlogTopics::tight).filter(text::contains).count();
            if (count > 0) {
                int at = 0;
                while (at < hits.size() && hits.get(at) >= count) {
                    at++;
                }
                found.add(at, topic);
                hits.add(at, count);
            }
        }
        return found;
    }

    public static List<BlogService.PublicPost> postsOf(Topic topic, List<BlogService.PublicPost> posts) {
        return posts.stream().filter(post -> of(post).contains(topic)).toList();
    }

    /** The companion a post leads to: of its strongest topic, else Nina. */
    public static Companion companionOf(BlogService.PublicPost post) {
        if ("helping-spouses-family-financially".equals(post.slug())) {
            return POURIA;
        }
        List<Topic> topics = of(post);
        return topics.isEmpty() ? NINA : topics.get(0).companion();
    }

    static String plain(String text) {
        return nullToEmpty(text).replace('ي', 'ی').replace('ك', 'ک').replace("‌", "")
                .replaceAll("[\\u064b-\\u065f]", "").replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    static String tight(String text) {
        return plain(text).replace(" ", "");
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
