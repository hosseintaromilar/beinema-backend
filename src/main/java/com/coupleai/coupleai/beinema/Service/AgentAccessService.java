package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.Agent.AgentPlanResponse;
import com.coupleai.coupleai.beinema.DTO.ChatRoom.AgentAccessResponse;
import com.coupleai.coupleai.beinema.DTO.ChatRoom.PurchaseAgentAccessRequest;
import com.coupleai.coupleai.beinema.Entity.Agent;
import com.coupleai.coupleai.beinema.Entity.AgentAccess;
import com.coupleai.coupleai.beinema.Entity.AgentPlan;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Entity.Wallet;
import com.coupleai.coupleai.beinema.Enum.AgentAccessStatus;
import com.coupleai.coupleai.beinema.Enum.AgentStatus;
import com.coupleai.coupleai.beinema.Enum.WalletTransactionType;
import com.coupleai.coupleai.beinema.Exception.AgentAccessExpiredException;
import com.coupleai.coupleai.beinema.Exception.AgentPurchaseForbiddenException;
import com.coupleai.coupleai.beinema.Exception.InsufficientWalletBalanceException;
import com.coupleai.coupleai.beinema.Repository.AgentAccessRepository;
import com.coupleai.coupleai.beinema.Repository.AgentPlanRepository;
import com.coupleai.coupleai.beinema.Repository.AgentRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AgentAccessService {

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final ChatRoomAgentRepository chatRoomAgentRepository;
    private final AgentRepository agentRepository;
    private final AgentPlanRepository agentPlanRepository;
    private final AgentAccessRepository agentAccessRepository;
    private final WalletService walletService;

    @Value("${beinema.agent-plan.default-duration-days:30}")
    private int defaultDurationDays;

    @Transactional(readOnly = true)
    public List<AgentPlanResponse> getPlans(Long agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("ایجنت پیدا نشد"));

        return agentPlanRepository
                .findByAgentAndActiveTrueOrderByDurationDaysAsc(agent)
                .stream()
                .map(this::toPlanResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AgentAccessResponse getActiveAccess(String email, Long chatRoomId) {
        User user = requireUser(email);
        ChatRoom chatRoom = requireAccessibleRoom(chatRoomId, user);
        Long agentId = resolveAgentId(chatRoom.getId());
        if (agentId == null) {
            return null;
        }

        return findActive(chatRoom.getId(), agentId)
                .map(this::toAccessResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean hasValidAccess(Long chatRoomId, Long agentId) {
        if (chatRoomId == null || agentId == null || agentId <= 0) {
            return true;
        }
        return findActive(chatRoomId, agentId).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<AgentAccess> findActive(Long chatRoomId, Long agentId) {
        if (chatRoomId == null || agentId == null) {
            return Optional.empty();
        }
        return agentAccessRepository
                .findFirstByChatRoom_IdAndAgent_IdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                        chatRoomId,
                        agentId,
                        AgentAccessStatus.ACTIVE,
                        LocalDateTime.now()
                );
    }

    @Transactional
    public AgentAccessResponse purchase(
            String email,
            Long chatRoomId,
            PurchaseAgentAccessRequest request
    ) {
        if (request == null || request.getPlanId() == null) {
            throw new IllegalArgumentException("پلن دسترسی را انتخاب کنید");
        }

        User user = requireUser(email);
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("گفتگو پیدا نشد"));

        if (!participantRepository.existsByChatRoomAndUser(chatRoom, user)) {
            throw new AgentPurchaseForbiddenException("به این گفتگو دسترسی ندارید");
        }

        if (!user.getId().equals(chatRoom.getCreatedBy())) {
            throw new AgentPurchaseForbiddenException(
                    "فقط سازنده گفتگو می‌تواند دسترسی مشاور را خریداری کند"
            );
        }

        AgentPlan plan = agentPlanRepository.findById(request.getPlanId())
                .orElseThrow(() -> new IllegalArgumentException("پلن معتبر نیست"));

        if (!plan.isActive() || plan.getDurationDays() == null || plan.getDurationDays() <= 0
                || plan.getPrice() == null || plan.getPrice() < 0) {
            throw new IllegalArgumentException("پلن معتبر نیست");
        }

        Agent planAgent = plan.getAgent();
        if (planAgent == null || planAgent.getStatus() != AgentStatus.ACTIVE) {
            throw new IllegalArgumentException("این مشاور در حال حاضر فعال نیست");
        }

        Long roomAgentId = resolveAgentId(chatRoom.getId());
        if (roomAgentId == null || !roomAgentId.equals(planAgent.getId())) {
            throw new IllegalArgumentException("این پلن برای مشاور این گفتگو نیست");
        }

        Wallet wallet = walletService.lockByUserId(user.getId());

        if (hasValidAccess(chatRoom.getId(), planAgent.getId())) {
            throw new IllegalArgumentException(
                    "برای این گفتگو قبلاً دسترسی فعال خریداری شده است"
            );
        }

        if (wallet.getBalance() < plan.getPrice()) {
            throw new InsufficientWalletBalanceException(
                    "موجودی کیف پول کافی نیست. لطفاً حساب خود را شارژ کنید."
            );
        }

        LocalDateTime now = LocalDateTime.now();
        AgentAccess access = agentAccessRepository.save(
                AgentAccess.builder()
                        .payer(user)
                        .chatRoom(chatRoom)
                        .agent(planAgent)
                        .plan(plan)
                        .price(plan.getPrice())
                        .startsAt(now)
                        .expiresAt(now.plusDays(plan.getDurationDays()))
                        .status(AgentAccessStatus.ACTIVE)
                        .build()
        );

        walletService.debit(
                user,
                wallet,
                plan.getPrice(),
                WalletTransactionType.AGENT_PURCHASE,
                "هزینه دسترسی " + plan.getTitle(),
                "ACCESS-" + access.getId(),
                chatRoom.getId(),
                access.getId()
        );

        return toAccessResponse(access);
    }

    @Transactional(readOnly = true)
    public AgentPlan requireDefaultPlan(Agent agent) {
        AgentPlan plan = pickDefaultPlan(
                agentPlanRepository.findByAgentAndActiveTrueOrderByDurationDaysAsc(agent)
        );
        if (plan == null || plan.getPrice() == null || plan.getPrice() < 0
                || plan.getDurationDays() == null || plan.getDurationDays() <= 0) {
            throw new IllegalArgumentException("هزینه دسترسی این مشاور تعریف نشده است");
        }
        return plan;
    }

    public void assertCanAfford(User user, AgentPlan plan) {
        walletService.assertCanAfford(user, plan == null ? 0L : plan.getPrice());
    }

    @Transactional
    public void grantForNewRoom(User user, ChatRoom chatRoom, Agent agent, AgentPlan plan) {
        if (plan == null || !plan.isActive() || plan.getDurationDays() == null
                || plan.getDurationDays() <= 0 || plan.getPrice() == null || plan.getPrice() < 0) {
            throw new IllegalArgumentException("هزینه دسترسی این مشاور تعریف نشده است");
        }

        Wallet wallet = walletService.lockByUserId(user.getId());
        if (plan.getPrice() > 0 && wallet.getBalance() < plan.getPrice()) {
            throw new InsufficientWalletBalanceException(
                    "موجودی کیف پول کافی نیست. لطفاً حساب خود را شارژ کنید."
            );
        }

        LocalDateTime now = LocalDateTime.now();
        AgentAccess access = agentAccessRepository.save(
                AgentAccess.builder()
                        .payer(user)
                        .chatRoom(chatRoom)
                        .agent(agent)
                        .plan(plan)
                        .price(plan.getPrice())
                        .startsAt(now)
                        .expiresAt(now.plusDays(plan.getDurationDays()))
                        .status(AgentAccessStatus.ACTIVE)
                        .build()
        );

        if (plan.getPrice() > 0) {
            walletService.debit(
                    user,
                    wallet,
                    plan.getPrice(),
                    WalletTransactionType.AGENT_PURCHASE,
                    "هزینه دسترسی " + plan.getTitle(),
                    "ACCESS-" + access.getId(),
                    chatRoom.getId(),
                    access.getId()
            );
        }
    }

    private AgentPlan pickDefaultPlan(List<AgentPlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return null;
        }
        return plans.stream()
                .filter(item -> item.getDurationDays() != null
                        && item.getDurationDays() == defaultDurationDays)
                .findFirst()
                .orElse(plans.get(0));
    }

    public void requireValidAccess(Long chatRoomId, Long agentId) {
        if (!hasValidAccess(chatRoomId, agentId)) {
            throw new AgentAccessExpiredException(
                    "دسترسی شما به این مشاور به پایان رسیده است."
            );
        }
    }

    private ChatRoom requireAccessibleRoom(Long chatRoomId, User user) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("گفتگو پیدا نشد"));
        if (!participantRepository.existsByChatRoomAndUser(chatRoom, user)) {
            throw new AgentPurchaseForbiddenException("به این گفتگو دسترسی ندارید");
        }
        return chatRoom;
    }

    private Long resolveAgentId(Long chatRoomId) {
        return chatRoomAgentRepository.findActiveAgentIds(chatRoomId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("کاربر پیدا نشد"));
    }

    private AgentPlanResponse toPlanResponse(AgentPlan plan) {
        return AgentPlanResponse.builder()
                .id(plan.getId())
                .agentId(plan.getAgent().getId())
                .title(plan.getTitle())
                .durationDays(plan.getDurationDays())
                .price(plan.getPrice())
                .active(plan.isActive())
                .build();
    }

    private AgentAccessResponse toAccessResponse(AgentAccess access) {
        boolean active = access.getStatus() == AgentAccessStatus.ACTIVE
                && access.getExpiresAt() != null
                && access.getExpiresAt().isAfter(LocalDateTime.now());

        return AgentAccessResponse.builder()
                .id(access.getId())
                .chatRoomId(access.getChatRoom().getId())
                .agentId(access.getAgent().getId())
                .planId(access.getPlan().getId())
                .planTitle(access.getPlan().getTitle())
                .price(access.getPrice())
                .payerId(access.getPayer().getId())
                .status(access.getStatus())
                .startsAt(toEpoch(access.getStartsAt()))
                .expiresAt(toEpoch(access.getExpiresAt()))
                .active(active)
                .build();
    }

    private static Long toEpoch(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
