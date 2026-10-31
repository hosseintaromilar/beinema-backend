package com.coupleai.coupleai.beinema.Config;

import com.coupleai.coupleai.beinema.Entity.Agent;
import com.coupleai.coupleai.beinema.Enum.AgentStatus;
import com.coupleai.coupleai.beinema.Enum.AgentType;
import com.coupleai.coupleai.beinema.Repository.AgentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The companions that run on their own copy of the v3 workflow (finance, parenting,
 * before marriage, the families, intimacy).
 * Each is added once, when no agent uses its workflow yet; after that the admin
 * panel owns it (name, photo, texts, active or not) and nothing here touches it.
 * Runs before AgentPlanSeeder so a new companion gets its access plans too.
 */
@Component
@Order(15)
public class CompanionAgentSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CompanionAgentSeeder.class);

    private record Companion(String workflowId, AgentType type, String name, String headline,
                             String description, String bio, String specialties, String photo) {
    }

    private static final List<Companion> COMPANIONS = List.of(
            new Companion(
                    "be7ef82f-15de-4e0d-82a9-19e0cccd560d",
                    AgentType.FINANCIAL,
                    "همراه مالی زوج‌ها",
                    "پول را از موضوع دعوا به تصمیم مشترک تبدیل کنید",
                    "برای گفتگوی آرام دو نفر دربارهٔ خرج، پس‌انداز، بدهی و تصمیم‌های مالی مشترک.",
                    "همراه مالی بین‌ما کنار هر دو نفر می‌نشیند تا دربارهٔ پول بدون دعوا حرف بزنید: هر کدام از زندگی مالی مشترک چه می‌خواهید، الان با پول چه می‌کنید، و چه قدم کوچکی از دست هر کدام برمی‌آید. "
                            + "بودجهٔ ماهانه، هدف‌های مشترک، بدهی و قسط، سهم هر نفر از هزینه‌ها و نقش خانواده‌ها از موضوع‌های این گفتگوست. "
                            + "این همراه توصیهٔ سرمایه‌گذاری، خرید و فروش یا مشاورهٔ مالیاتی و حقوقی نمی‌دهد.",
                    "بودجهٔ مشترک، تعارض مالی، پس‌انداز و هدف‌های مشترک، بدهی و قسط",
                    "/agents/finance-companion.webp"
            ),
            new Companion(
                    "d5385904-1504-4a34-9898-a0e9d152d96a",
                    AgentType.PARENTING,
                    "همراه تربیت فرزند",
                    "برای تربیت بچه‌ها، یک راه مشترک پیدا کنید",
                    "برای پدر و مادرهایی که می‌خواهند در تربیت بچه‌ها هم‌راه شوند: قانون‌ها، روزمرگی، رفتار بچه و اختلاف سلیقهٔ دو والد.",
                    "همراه تربیت فرزند بین‌ما به پدر و مادر کمک می‌کند به‌جای دعوا سر روش تربیت، به یک راه مشترک برسند: هر کدام برای بچه و خانواده چه می‌خواهید، در لحظه‌های سخت با بچه چه می‌کنید، و چه قدم کوچکی جواب می‌دهد. "
                            + "خواب و برنامهٔ روزانه، گوشی و صفحه‌نمایش، درس و مدرسه، لجبازی و قهر، و نوجوانی از موضوع‌های این گفتگوست. "
                            + "این همراه برای بچه تشخیص پزشکی یا روان‌پزشکی نمی‌گذارد و هر جا لازم باشد، دیدن متخصص کودک را پیشنهاد می‌کند.",
                    "تربیت مشترک، قانون و مرزهای خانه، رفتار و لجبازی کودک، نوجوانی",
                    "/agents/parenting-companion.webp"
            ),
            new Companion(
                    "1a3db484-876e-49e8-88fa-67410b6689da",
                    AgentType.PREMARITAL,
                    "همراه پیش از ازدواج",
                    "قبل از تصمیم، انتظارهایتان را روشن کنید",
                    "برای دو نفری که در حال آشنایی یا فکر ازدواج‌اند و می‌خواهند پیش از تصمیم، انتظارها و تفاوت‌هایشان را روشن ببینند.",
                    "همراه پیش از ازدواج بین‌ما کمک می‌کند دو نفر پیش از تصمیم بزرگ، روشن حرف بزنند: هر کدام از زندگی مشترک چه انتظاری دارید، دربارهٔ پول و کار، محل زندگی، بچه، خانواده‌ها و ارزش‌ها چه فکری دارید، و اختلاف‌ها را چطور حل می‌کنید. "
                            + "این همراه نمی‌گوید ازدواج کنید یا نکنید و به تناسبتان نمره نمی‌دهد؛ فقط کمک می‌کند هر دو با چشم باز تصمیم بگیرید. "
                            + "دربارهٔ عقدنامه، مهریه و شرط‌ها مشاورهٔ حقوقی نمی‌دهد.",
                    "انتظارهای پیش از ازدواج، تفاوت ارزش‌ها، پول و کار، خانواده‌ها و بچه",
                    "/agents/premarital-companion.webp"
            ),
            new Companion(
                    "e337ebf5-b96b-48dc-9b32-bd8e36428ddc",
                    AgentType.IN_LAWS,
                    "همراه رابطه با خانواده‌ها",
                    "با احترام به هر دو خانواده، یک تیم بمانید",
                    "برای زوج‌هایی که رفت‌وآمد، دخالت، توقع‌ها یا مقایسه‌های خانواده‌ها روی زندگی مشترکشان سایه انداخته است.",
                    "همراه رابطه با خانواده‌ها به زوج کمک می‌کند جای خانواده‌ها را در زندگی مشترک با هم روشن کنند: رفت‌وآمدها، نظر دادن و دخالت، کمک مالی، مقایسه‌ها و وفاداری به پدر و مادر و به همسر. "
                            + "این همراه طرف هیچ خانواده‌ای را نمی‌گیرد و کسی را مقصر نمی‌کند؛ کمک می‌کند با احترام به هر دو خانواده، مرزهای زندگی خودتان را با هم بگذارید.",
                    "خانوادهٔ همسر، مرزهای محترمانه، رفت‌وآمد و دخالت، تیم بودن زوج",
                    "/agents/family-companion.webp"
            ),
            new Companion(
                    "309faed0-908f-4a7d-b826-c11e605fe379",
                    AgentType.SEXOLOGY,
                    "همراه صمیمیت",
                    "دربارهٔ نزدیکی، آرام و بی‌خجالت حرف بزنید",
                    "برای زوج‌هایی که می‌خواهند دربارهٔ نزدیکی عاطفی و جسمی، فاصله و تفاوت خواسته‌ها، محترمانه و امن حرف بزنند.",
                    "همراه صمیمیت بین‌ما فضایی امن و محترمانه است برای حرف زدن دربارهٔ نزدیکی: محبت و توجه روزمره، فاصله‌ای که با خستگی، بچه یا دلخوری‌های قدیمی پیش آمده، و تفاوت خواسته‌های دو نفر. "
                            + "گفتگو بی‌پرده نمی‌شود و هیچ‌کس برای گفتن بیشتر از آنچه می‌خواهد زیر فشار نمی‌رود. "
                            + "این همراه توصیهٔ پزشکی نمی‌دهد و هر جا لازم باشد، دیدن پزشک یا درمانگر جنسی را پیشنهاد می‌کند.",
                    "نزدیکی عاطفی، فاصله در رابطه، تفاوت خواسته‌ها، گفتگوی امن",
                    "/agents/intimacy-companion.webp"
            )
    );

    private final AgentRepository agentRepository;
    private final String coupleWorkflowId;

    public CompanionAgentSeeder(AgentRepository agentRepository,
                                @Value("${metis.couple-workflow-id}") String coupleWorkflowId) {
        this.agentRepository = agentRepository;
        this.coupleWorkflowId = coupleWorkflowId;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Agent> agents = agentRepository.findAll();
        // a new database starts without the couple counselor
        if (agents.stream().noneMatch(agent -> agent.getType() == AgentType.RELATIONSHIP)) {
            agentRepository.save(Agent.builder()
                    .name("نینا")
                    .type(AgentType.RELATIONSHIP)
                    .headline("فضای امن برای شنیده‌شدن هر دو نفر")
                    .description("نینا همراه هوشمند زوج‌هاست؛ کمک می‌کند گفتگو از سرزنش فاصله بگیرد و به فهمیدن هم برسد.")
                    .photoUrl("/agents/companion.webp")
                    .avatar("/agents/companion.webp")
                    .aiBotId(coupleWorkflowId)
                    .status(AgentStatus.ACTIVE)
                    .build());
            log.info("Couple counselor added");
        }
        for (Companion companion : COMPANIONS) {
            boolean present = agents.stream().anyMatch(agent -> companion.workflowId().equals(agent.getAiBotId()));
            if (present) {
                continue;
            }
            agentRepository.save(Agent.builder()
                    .name(companion.name())
                    .type(companion.type())
                    .headline(companion.headline())
                    .description(companion.description())
                    .bio(companion.bio())
                    .specialties(companion.specialties())
                    .photoUrl(companion.photo())
                    .avatar(companion.photo())
                    .aiBotId(companion.workflowId())
                    .status(AgentStatus.ACTIVE)
                    .build());
            log.info("Companion added: {}", companion.name());
        }
    }
}
