package com.coupleai.coupleai.beinema.Config;

import com.coupleai.coupleai.beinema.Entity.Agent;
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

import static com.coupleai.coupleai.beinema.Enum.AgentType.MENTAL_HEALTH;

@Component
@Order(25)
public class AgentContentSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentContentSeeder.class);

    private final AgentRepository agentRepository;
    private final String coupleWorkflowId;

    public AgentContentSeeder(
            AgentRepository agentRepository,
            @Value("${metis.couple-workflow-id}") String coupleWorkflowId
    ) {
        this.agentRepository = agentRepository;
        this.coupleWorkflowId = coupleWorkflowId;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Agent> agents = agentRepository.findAll();
        for (Agent agent : agents) {
            if (isCoupleAgent(agent) && coupleWorkflowId != null && !coupleWorkflowId.isBlank()) {
                agent.setAiBotId(coupleWorkflowId);
            }
            Profile profile = profileFor(agent);
            if (profile == null) {
                if (isCoupleAgent(agent)) {
                    agentRepository.save(agent);
                }
                continue;
            }
            // only fills what is empty: what the admin set in the panel survives every restart
            if (isBlank(agent.getHeadline())) {
                agent.setHeadline(profile.headline);
            }
            if (isBlank(agent.getBio())) {
                agent.setBio(profile.bio);
            }
            if (isBlank(agent.getSpecialties())) {
                agent.setSpecialties(profile.specialties);
            }
            if (isBlank(agent.getPhotoUrl())) {
                agent.setPhotoUrl(profile.photoUrl);
            }
            if (isBlank(agent.getAvatar())) {
                agent.setAvatar(agent.getPhotoUrl());
            }
            if (agent.getDescription() == null || agent.getDescription().isBlank()
                    || agent.getDescription().contains("مبتنی بر هوش مصنوعی")) {
                agent.setDescription(profile.description);
            }
            agentRepository.save(agent);
        }
        log.info("Agent profile photos and bios are ready");
    }

    private Profile profileFor(Agent agent) {
        if (agent.getType() != null && agent.getName() != null && !agent.getName().isBlank()) {
            // the type decides; a name like «همراه مالی زوج‌ها» must not become the couple counselor
            return switch (agent.getType()) {
                case RELATIONSHIP -> profileForName("زوج");
                case MENTAL_HEALTH -> profileForName("روان");
                case FINANCIAL -> profileForName("مالی");
                case SEXOLOGY, PARENTING, PREMARITAL, IN_LAWS -> null;
            };
        }
        String name = agent.getName() == null ? "" : agent.getName();
        if (name.contains("زوج")) {
            return new Profile(
                    "فضای امن برای شنیده‌شدن هر دو نفر",
                    "زوج‌درمانگر بین‌ما کمک می‌کند گفتگو از سرزنش فاصله بگیرد و به درک متقابل نزدیک شود. این همراه برای زوج‌هایی طراحی شده که می‌خواهند تعارض‌های تکراری، فاصله عاطفی و الگوهای بحث را بهتر بشناسند.",
                    "زوج‌درمانگر هوشمند بین‌ما برای گفتگوهای مشترک زوج‌ها؛ بدون طرفداری، با تمرکز روی رابطه.",
                    "ارتباط زوجین، تعارض‌های تکراری، فاصله عاطفی، گفتگوی سازنده",
                    "/agents/couple-therapist.webp"
            );
        }
        if (name.contains("روان")) {
            return new Profile(
                    "آرام‌تر دیدن احساسات، دقیق‌تر فهمیدن الگوها",
                    "روانشناس بین‌ما کنار شما می‌ماند تا اضطراب، فشارهای هیجانی و الگوهای فکری را با زبانی ساده و بدون قضاوت بررسی کنید. هدف، برچسب‌زدن نیست؛ شناخت احساسات و ساختن مسیر آرام‌تر برای رابطه و خودتان است.",
                    "روانشناس همراه برای شناخت احساسات، اضطراب و الگوهای فکری در فضای امن گفتگو.",
                    "سلامت روان، اضطراب، تنظیم هیجان، خودآگاهی",
                    "/agents/psychologist.jpg"
            );
        }
        if (name.contains("مالی")) {
            return new Profile(
                    "پول را از منبع تنش، به موضوع گفتگو تبدیل کنید",
                    "مشاور مالی بین‌ما به زوج‌ها کمک می‌کند درباره هزینه‌ها، پس‌انداز، اولویت‌ها و نگرانی‌های مالی شفاف حرف بزنند. این همراه قضاوت نمی‌کند؛ کمک می‌کند تصمیم‌های مالی مشترک، قابل‌فهم و عادلانه‌تر شوند.",
                    "مشاور مالی برای گفتگوی آرام درباره پول، هزینه‌ها و تصمیم‌های مشترک زندگی.",
                    "بودجه‌بندی، تعارض مالی، پس‌انداز، تصمیم مشترک",
                    "/agents/financial-advisor.jpg"
            );
        }
        if (agent.getType() != null) {
            return switch (agent.getType()) {
                case RELATIONSHIP -> profileForName("زوج");
                case MENTAL_HEALTH -> profileForName("روان");
                case FINANCIAL -> profileForName("مالی");
                case PARENTING, PREMARITAL, IN_LAWS -> null;
                case SEXOLOGY -> new Profile(
                        "صمیمیت را با احترام و گفتگوی امن باز کنید",
                        "این همراه برای زوج‌هایی است که می‌خواهند درباره صمیمیت، نیازها و فاصله جسمی یا عاطفی با زبانی محترمانه حرف بزنند. هدف، فشار یا شرم نیست؛ فهمیدن یکدیگر و ساختن نزدیکی سالم‌تر است.",
                        "همراه تخصصی برای گفتگوی محترمانه درباره صمیمیت و نیازهای رابطه‌ای.",
                        "صمیمیت، نیازهای عاطفی، گفتگوی امن، نزدیکی",
                        "/agents/couple-therapist.webp"
                );
            };
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isCoupleAgent(Agent agent) {
        String name = agent.getName() == null ? "" : agent.getName();
        return agent.getType() == AgentType.RELATIONSHIP || (agent.getType() == null && name.contains("زوج"));
    }

    private Profile profileForName(String token) {
        Agent probe = new Agent();
        probe.setName(token);
        return profileFor(probe);
    }

    private record Profile(
            String headline,
            String bio,
            String description,
            String specialties,
            String photoUrl
    ) {
    }
}
