package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.Agent.AdminAgentResponse;
import com.coupleai.coupleai.beinema.DTO.Agent.AgentRequest;
import com.coupleai.coupleai.beinema.DTO.Agent.AgentResponse;
import com.coupleai.coupleai.beinema.Entity.Agent;
import com.coupleai.coupleai.beinema.Entity.AgentPlan;
import com.coupleai.coupleai.beinema.Enum.AgentStatus;
import com.coupleai.coupleai.beinema.Enum.AgentType;
import com.coupleai.coupleai.beinema.Repository.AgentPlanRepository;
import com.coupleai.coupleai.beinema.Repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentService {


    private final AgentRepository agentRepository;
    private final AgentPlanRepository agentPlanRepository;

    @Value("${beinema.agent-plan.default-duration-days:30}")
    private int defaultDurationDays;

    @Value("${beinema.agent-plan.price.7:100000}")
    private long price7;

    @Value("${beinema.agent-plan.price.30:250000}")
    private long price30;

    @Value("${beinema.agent-plan.price.90:500000}")
    private long price90;


    @Transactional(readOnly = true)
    public List<AgentResponse> getActiveAgents() {

        List<Agent> agents = agentRepository.findByStatus(AgentStatus.ACTIVE);
        if (agents.isEmpty()) {
            return List.of();
        }

        Map<Long, List<AgentPlan>> plansByAgent = agentPlanRepository
                .findByAgentInAndActiveTrue(agents)
                .stream()
                .collect(Collectors.groupingBy(plan -> plan.getAgent().getId()));

        return agents.stream()
                .map(agent -> AgentResponse.from(
                        agent,
                        pickDefaultPlan(plansByAgent.getOrDefault(agent.getId(), List.of()))
                ))
                .toList();

    }

    @Transactional(readOnly = true)
    public AgentResponse getAgent(Long id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ایجنت پیدا نشد"));
        if (agent.getStatus() != AgentStatus.ACTIVE) {
            throw new RuntimeException("این مشاور در حال حاضر فعال نیست");
        }
        return AgentResponse.from(
                agent,
                pickDefaultPlan(
                        agentPlanRepository.findByAgentAndActiveTrueOrderByDurationDaysAsc(agent)
                )
        );
    }

    private AgentPlan pickDefaultPlan(List<AgentPlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return null;
        }
        return plans.stream()
                .filter(plan -> plan.getDurationDays() != null
                        && plan.getDurationDays() == defaultDurationDays)
                .findFirst()
                .orElse(plans.get(0));
    }

    @Transactional(readOnly = true)
    public List<AdminAgentResponse> listForAdmin() {
        return agentRepository.findAll().stream()
                .map(agent -> AdminAgentResponse.from(agent, priceOf(agent)))
                .toList();
    }

    @Transactional
    public AdminAgentResponse create(AgentRequest request) {
        Agent agent = Agent.builder()
                .name(requireText(request == null ? null : request.getName(), "نام ایجنت"))
                .type(requireType(request))
                .description(clean(request.getDescription()))
                .systemPrompt(requireText(request.getSystemPrompt(), "دستور سیستم"))
                .avatar(avatarOf(request))
                .headline(clean(request.getHeadline()))
                .bio(clean(request.getBio()))
                .specialties(clean(request.getSpecialties()))
                .photoUrl(clean(request.getPhotoUrl()))
                .aiBotId(requireText(request.getAiBotId(), "شناسه بات"))
                .status(request.getStatus() == null ? AgentStatus.ACTIVE : request.getStatus())
                .build();

        Agent saved = agentRepository.save(agent);
        long monthlyPrice = request.getPrice() == null ? price30 : requirePrice(request.getPrice());
        seedPlans(saved, monthlyPrice);
        return AdminAgentResponse.from(saved, monthlyPrice);
    }

    @Transactional
    public AdminAgentResponse update(Long id, AgentRequest request) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ایجنت پیدا نشد"));

        agent.setName(requireText(request == null ? null : request.getName(), "نام ایجنت"));
        agent.setType(requireType(request));
        agent.setDescription(clean(request.getDescription()));
        agent.setSystemPrompt(requireText(request.getSystemPrompt(), "دستور سیستم"));
        agent.setAvatar(avatarOf(request));
        agent.setHeadline(clean(request.getHeadline()));
        agent.setBio(clean(request.getBio()));
        agent.setSpecialties(clean(request.getSpecialties()));
        agent.setPhotoUrl(clean(request.getPhotoUrl()));
        agent.setAiBotId(requireText(request.getAiBotId(), "شناسه بات"));
        agent.setStatus(request.getStatus() == null ? AgentStatus.ACTIVE : request.getStatus());
        Agent saved = agentRepository.save(agent);

        long monthlyPrice = request.getPrice() == null ? price30 : requirePrice(request.getPrice());
        seedPlans(saved, monthlyPrice);
        return AdminAgentResponse.from(saved, monthlyPrice);
    }

    public AgentResponse setActiveAgents(AgentRequest request) {
        AdminAgentResponse created = create(request);
        Agent agent = agentRepository.findById(created.id())
                .orElseThrow(() -> new IllegalArgumentException("ایجنت پیدا نشد"));
        return AgentResponse.from(agent, pickDefaultPlan(
                agentPlanRepository.findByAgentAndActiveTrueOrderByDurationDaysAsc(agent)
        ));
    }

    private void seedPlans(Agent agent, long monthlyPrice) {
        savePlan(agent, 7, "دسترسی ۷ روزه", price7);
        savePlan(agent, 30, "دسترسی ۳۰ روزه", monthlyPrice);
        savePlan(agent, 90, "دسترسی ۹۰ روزه", price90);
    }

    private void savePlan(Agent agent, int days, String title, long price) {
        List<AgentPlan> plans = agentPlanRepository
                .findByAgentAndActiveTrueOrderByDurationDaysAsc(agent);
        for (AgentPlan plan : plans) {
            if (plan.getDurationDays() != null && plan.getDurationDays() == days) {
                plan.setPrice(price);
                plan.setTitle(title);
                agentPlanRepository.save(plan);
                return;
            }
        }
        agentPlanRepository.save(
                AgentPlan.builder()
                        .agent(agent)
                        .title(title)
                        .durationDays(days)
                        .price(price)
                        .active(true)
                        .build()
        );
    }

    private Long priceOf(Agent agent) {
        AgentPlan plan = pickDefaultPlan(
                agentPlanRepository.findByAgentAndActiveTrueOrderByDurationDaysAsc(agent)
        );
        return plan == null ? null : plan.getPrice();
    }

    private static AgentType requireType(AgentRequest request) {
        if (request == null || request.getType() == null) {
            throw new IllegalArgumentException("نوع ایجنت را انتخاب کنید.");
        }
        return request.getType();
    }

    private static String requireText(String raw, String label) {
        String value = clean(raw);
        if (value == null) {
            throw new IllegalArgumentException(label + " را وارد کنید.");
        }
        return value;
    }

    private static long requirePrice(Long price) {
        if (price < 0) {
            throw new IllegalArgumentException("هزینه دسترسی نمی‌تواند منفی باشد.");
        }
        return price;
    }

    // the small picture next to the counselor's messages: the photo unless one is given
    private static String avatarOf(AgentRequest request) {
        String avatar = clean(request.getAvatar());
        return avatar != null ? avatar : clean(request.getPhotoUrl());
    }

    private static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }
}
