package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.Invitation.AcceptInvitationResponse;
import com.coupleai.coupleai.beinema.DTO.Invitation.InvitationPreviewResponse;
import com.coupleai.coupleai.beinema.DTO.Invitation.InvitePartnerResponse;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomInvitation;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.ChatRoomStatus;
import com.coupleai.coupleai.beinema.Enum.InvitationStatus;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Enum.MessageStatus;
import com.coupleai.coupleai.beinema.Enum.ParticipantRole;
import com.coupleai.coupleai.beinema.Exception.SmsDeliveryException;
import com.coupleai.coupleai.beinema.Repository.ChatRoomInvitationRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.MessageRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import com.coupleai.coupleai.beinema.Service.sms.SmsProvider;
import com.coupleai.coupleai.beinema.Util.PhoneNumbers;
import com.coupleai.coupleai.beinema.governance.ParticipationPauseService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InvitationService {

    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);

    private static final int MAX_HUMAN_PARTICIPANTS = 2;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CurrentUserService currentUserService;

    private final ChatRoomRepository chatRoomRepository;

    private final ChatRoomParticipantRepository participantRepository;

    private final ChatRoomInvitationRepository invitationRepository;

    private final UserRepository userRepository;

    private final MessageRepository messageRepository;

    private final ChatRealtimeService chatRealtimeService;

    private final ParticipationPauseService participationPauseService;

    private final SmsProvider smsProvider;

    @Value("${app.frontend-url:${app.frontend-base-url:http://localhost:3000}}")
    private String frontendUrl;

    @Value("${app.invitation-expiry-hours:72}")
    private long invitationExpiryHours;

    @Value("${beinema.otp-bypass-phones:}")
    private String directAddPhones;

    /** The invitation link the owner shares with any app they like (no SMS for now). */
    public record InviteLink(String token, String link, Long expiresAt, String chatRoomTitle, String inviterName) {
    }

    /**
     * A link for the partner: the waiting one if there is one, otherwise a new
     * one. Whoever opens it signs up (number and password, or Google), gives
     * the name and number, and joins; one person can use it.
     */
    @Transactional
    public InviteLink inviteLink(Long chatRoomId) {
        User inviter = currentUserService.getCurrentUser();
        ChatRoom chatRoom = getAuthorizedChatRoom(chatRoomId, inviter);
        if (chatRoom.getStatus() != ChatRoomStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "این گفتگو پایان یافته است و نمی‌توان کسی را به آن دعوت کرد."
            );
        }
        if (participantRepository.countByChatRoom(chatRoom) >= MAX_HUMAN_PARTICIPANTS) {
            throw new IllegalArgumentException("این گفتگو در حال حاضر دو نفره است.");
        }

        ChatRoomInvitation invitation = invitationRepository
                .findFirstByChatRoomAndPhoneNumberIsNullAndStatusOrderByIdDesc(chatRoom, InvitationStatus.PENDING)
                .filter(item -> !isExpired(item))
                .orElseGet(() -> invitationRepository.save(
                        ChatRoomInvitation.builder()
                                .chatRoom(chatRoom)
                                .invitedBy(inviter)
                                .token(newToken())
                                .status(InvitationStatus.PENDING)
                                .expiresAt(LocalDateTime.now().plusHours(invitationExpiryHours))
                                .build()
                ));

        Long expires = invitation.getExpiresAt() == null ? null
                : invitation.getExpiresAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        return new InviteLink(invitation.getToken(), inviteLink(invitation.getToken()), expires,
                chatRoom.getTitle(), inviter.visibleName());
    }

    @Transactional
    public InvitePartnerResponse invitePartner(Long chatRoomId, String rawPhone) {
        User inviter = currentUserService.getCurrentUser();
        ChatRoom chatRoom = getAuthorizedChatRoom(chatRoomId, inviter);
        String phone = PhoneNumbers.normalize(rawPhone);

        if (chatRoom.getStatus() != ChatRoomStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "این گفتگو پایان یافته است و نمی‌توان کسی را به آن دعوت کرد."
            );
        }

        if (Objects.equals(phone, inviter.getPhoneNumber())) {
            throw new IllegalArgumentException(
                    "نمی‌توانید خودتان را به گفتگو اضافه کنید."
            );
        }

        userRepository.findByPhoneNumber(phone).ifPresent(existing -> {
            if (participantRepository.existsByChatRoomAndUser(chatRoom, existing)) {
                throw new IllegalArgumentException(
                        "این فرد از قبل در گفتگو حضور دارد."
                );
            }
        });

        if (participantRepository.countByChatRoom(chatRoom) >= MAX_HUMAN_PARTICIPANTS) {
            throw new IllegalArgumentException(
                    "این گفتگو در حال حاضر دو نفره است."
            );
        }

        User existingUser = userRepository.findByPhoneNumber(phone).orElse(null);
        if (existingUser != null) {
            addMember(chatRoom, existingUser);
            ChatRoomInvitation invitation = invitationRepository
                    .findFirstByChatRoomAndPhoneNumberAndStatus(
                            chatRoom,
                            phone,
                            InvitationStatus.PENDING
                    )
                    .orElseGet(() -> invitationRepository.save(
                            ChatRoomInvitation.builder()
                                    .chatRoom(chatRoom)
                                    .invitedBy(inviter)
                                    .phoneNumber(phone)
                                    .token(newToken())
                                    .status(InvitationStatus.PENDING)
                                    .expiresAt(LocalDateTime.now().plusHours(invitationExpiryHours))
                                    .build()
                    ));
            markAccepted(invitation, existingUser);
            announceJoin(chatRoom, existingUser);
            participationPauseService.startConsentGate(chatRoom, existingUser);

            if (!bothCanJoinWithoutSms(inviter.getPhoneNumber(), phone)) {
                String link = inviteLink(invitation.getToken());
                try {
                    smsProvider.send(
                            phone,
                            inviter.visibleName()
                                    + " شما را به گفتگوی «"
                                    + chatRoom.getTitle()
                                    + "» در بین‌ما دعوت کرد.\n"
                                    + link
                    );
                } catch (SmsDeliveryException exception) {
                    log.warn("SMS invite failed for {}: {}", phone, exception.getMessage());
                }
            }

            return InvitePartnerResponse.builder()
                    .chatRoomId(chatRoom.getId())
                    .message("پارتنر به گفتگو اضافه شد")
                    .build();
        }

        ChatRoomInvitation invitation = invitationRepository
                .findFirstByChatRoomAndPhoneNumberAndStatus(
                        chatRoom,
                        phone,
                        InvitationStatus.PENDING
                )
                .filter(item -> !isExpired(item))
                .orElseGet(() -> invitationRepository.save(
                        ChatRoomInvitation.builder()
                                .chatRoom(chatRoom)
                                .invitedBy(inviter)
                                .phoneNumber(phone)
                                .token(newToken())
                                .status(InvitationStatus.PENDING)
                                .expiresAt(LocalDateTime.now().plusHours(invitationExpiryHours))
                                .build()
                ));

        String link = inviteLink(invitation.getToken());

        try {
            smsProvider.send(
                    phone,
                    inviter.visibleName()
                            + " شما را به گفتگوی مشترک در بین‌ما دعوت کرد:\n"
                            + link
            );
        } catch (SmsDeliveryException exception) {
            throw new IllegalArgumentException("ارسال پیامک ناموفق بود.");
        }

        return InvitePartnerResponse.builder()
                .chatRoomId(chatRoom.getId())
                .message("دعوت ارسال شد")
                .build();
    }

    @Transactional(readOnly = true)
    public InvitationPreviewResponse preview(String token) {
        ChatRoomInvitation invitation = loadInvitation(token);

        return InvitationPreviewResponse.builder()
                .token(invitation.getToken())
                .chatRoomId(invitation.getChatRoom().getId())
                .chatRoomTitle(invitation.getChatRoom().getTitle())
                .inviterName(invitation.getInvitedBy().visibleName())
                .status(effectiveStatus(invitation).name())
                .build();
    }

    @Transactional
    public AcceptInvitationResponse accept(String token) {
        return accept(token, false);
    }

    /** rulesAccepted: the invited person ticked «I accept the rules» on the invitation page. */
    public AcceptInvitationResponse accept(String token, boolean rulesAccepted) {
        User user = currentUserService.getCurrentUser();
        if (!user.hasRegisteredName()) {
            throw new IllegalArgumentException(
                    "قبل از ادامه، نام و نام خانوادگی خود را ثبت کنید."
            );
        }
        ChatRoomInvitation invitation = loadInvitation(token);
        ChatRoom chatRoom = invitation.getChatRoom();

        if (participantRepository.existsByChatRoomAndUser(chatRoom, user)) {
            markAccepted(invitation, user);
            return AcceptInvitationResponse.builder()
                    .chatRoomId(chatRoom.getId())
                    .chatRoomTitle(chatRoom.getTitle())
                    .message("شما از قبل در این گفتگو هستید.")
                    .build();
        }

        if (invitation.getInvitedBy() != null
                && invitation.getInvitedBy().getId().equals(user.getId())) {
            throw new IllegalArgumentException(
                    "دعوت‌کننده نمی‌تواند دعوت خودش را بپذیرد."
            );
        }

        InvitationStatus status = effectiveStatus(invitation);
        if (status == InvitationStatus.EXPIRED) {
            throw new IllegalArgumentException("مهلت این دعوت‌نامه به پایان رسیده است.");
        }
        if (status != InvitationStatus.PENDING) {
            throw new IllegalArgumentException("این دعوت‌نامه دیگر معتبر نیست.");
        }

        if (chatRoom.getStatus() != ChatRoomStatus.ACTIVE) {
            throw new IllegalArgumentException("این گفتگو پایان یافته است و دیگر نمی‌توان به آن پیوست.");
        }

        if (participantRepository.countByChatRoom(chatRoom) >= MAX_HUMAN_PARTICIPANTS) {
            throw new IllegalArgumentException("ظرفیت این گفتگو تکمیل شده است.");
        }

        if (!rulesAccepted) {
            throw new IllegalArgumentException("برای ورود به گروه، قوانین گروه و چارچوب جلسه را بپذیر.");
        }

        // a shared link has no number: who opens it gives theirs (the name step asks for it)
        if (invitation.getPhoneNumber() == null) {
            if (!user.hasPhoneNumber()) {
                throw new IllegalArgumentException("قبل از ادامه، شماره موبایلت را ثبت کن.");
            }
        } else if (user.hasPhoneNumber() && !user.getPhoneNumber().equals(invitation.getPhoneNumber())) {
            throw new IllegalArgumentException(
                    "این دعوت برای شماره دیگری است."
            );
        }

        if (!user.hasPhoneNumber()) {
            userRepository.findByPhoneNumber(invitation.getPhoneNumber())
                    .ifPresent(owner -> {
                        if (!owner.getId().equals(user.getId())) {
                            throw new IllegalArgumentException(
                                    "این دعوت مربوط به حساب دیگری است. با همان شماره وارد شوید."
                            );
                        }
                    });
            user.setPhoneNumber(invitation.getPhoneNumber());
            userRepository.save(user);
        }

        addMember(chatRoom, user, java.time.Instant.now());
        markAccepted(invitation, user);
        announceJoin(chatRoom, user);
        participationPauseService.startConsentGate(chatRoom, user);

        return AcceptInvitationResponse.builder()
                .chatRoomId(chatRoom.getId())
                .chatRoomTitle(chatRoom.getTitle())
                .message("با موفقیت به گفتگو اضافه شدید.")
                .build();
    }

    private void addMember(ChatRoom chatRoom, User user) {
        addMember(chatRoom, user, null);
    }

    /** rulesAcceptedAt: set when the rules were accepted before joining (then the first entry is the consent). */
    private void addMember(ChatRoom chatRoom, User user, java.time.Instant rulesAcceptedAt) {
        if (participantRepository.existsByChatRoomAndUser(chatRoom, user)) {
            return;
        }

        participantRepository.saveAndFlush(
                ChatRoomParticipant.builder()
                        .chatRoom(chatRoom)
                        .user(user)
                        .role(ParticipantRole.MEMBER)
                        .sessionConsented(false)
                        .consentOffered(false)
                        .rulesAcceptedAt(rulesAcceptedAt)
                        .build()
        );
    }

    private void announceJoin(ChatRoom chatRoom, User user) {
        int nextSequence = messageRepository
                .findMaxSequenceNumberByChatRoom(chatRoom)
                .orElse(0)
                + 1;

        Message event = Message.builder()
                .chatRoom(chatRoom)
                .senderType(MessageSenderType.SYSTEM)
                .senderId(user.getId())
                .content(user.visibleName() + " با لینک دعوت به گفتگو اضافه شد")
                .status(MessageStatus.SENT)
                .sequenceNumber(nextSequence)
                .build();

        messageRepository.saveAndFlush(event);
        chatRealtimeService.publish(chatRoom.getId(), event);
    }

    private boolean bothCanJoinWithoutSms(String inviterPhone, String inviteePhone) {
        return isDirectAddPhone(inviterPhone) && isDirectAddPhone(inviteePhone);
    }

    private boolean isDirectAddPhone(String phone) {
        if (directAddPhones == null || directAddPhones.isBlank() || phone == null) {
            return false;
        }
        String normalized;
        try {
            normalized = PhoneNumbers.normalize(phone);
        } catch (RuntimeException exception) {
            return false;
        }
        for (String item : directAddPhones.split(",")) {
            String candidate = item.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            try {
                if (normalized.equals(PhoneNumbers.normalize(candidate))) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                /* شمارهٔ تنظیمات نامعتبر نادیده گرفته می‌شود */
            }
        }
        return false;
    }

    private String inviteLink(String token) {
        return frontendUrl.replaceAll("/$", "")
                + "/invite/"
                + token;
    }

    private ChatRoom getAuthorizedChatRoom(Long chatRoomId, User user) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("گفتگو پیدا نشد."));

        if (!participantRepository.existsByChatRoomAndUser(chatRoom, user)) {
            throw new IllegalArgumentException("دسترسی به این گفتگو ندارید.");
        }

        return chatRoom;
    }

    private ChatRoomInvitation loadInvitation(String token) {
        String normalized = token == null ? "" : token.trim();

        return invitationRepository.findByToken(normalized)
                .orElseThrow(() -> new IllegalArgumentException("لینک دعوت معتبر نیست."));
    }

    private InvitationStatus effectiveStatus(ChatRoomInvitation invitation) {
        if (invitation.getStatus() == InvitationStatus.PENDING && isExpired(invitation)) {
            return InvitationStatus.EXPIRED;
        }
        return invitation.getStatus();
    }

    private boolean isExpired(ChatRoomInvitation invitation) {
        return invitation.getExpiresAt() != null
                && invitation.getExpiresAt().isBefore(LocalDateTime.now());
    }

    private void markAccepted(ChatRoomInvitation invitation, User user) {
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(LocalDateTime.now());
        invitation.setAcceptedBy(user);
        invitationRepository.save(invitation);
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
