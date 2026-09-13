package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.Invitation.AcceptInvitationResponse;
import com.coupleai.coupleai.beinema.DTO.Invitation.InvitationPreviewResponse;
import com.coupleai.coupleai.beinema.DTO.Invitation.InvitePartnerResponse;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomInvitation;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.InvitationStatus;
import com.coupleai.coupleai.beinema.Enum.ParticipantRole;
import com.coupleai.coupleai.beinema.Exception.SmsDeliveryException;
import com.coupleai.coupleai.beinema.Repository.ChatRoomInvitationRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import com.coupleai.coupleai.beinema.Service.sms.SmsProvider;
import com.coupleai.coupleai.beinema.Util.PhoneNumbers;
import lombok.RequiredArgsConstructor;
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

    private static final int MAX_HUMAN_PARTICIPANTS = 2;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CurrentUserService currentUserService;

    private final ChatRoomRepository chatRoomRepository;

    private final ChatRoomParticipantRepository participantRepository;

    private final ChatRoomInvitationRepository invitationRepository;

    private final UserRepository userRepository;

    private final SmsProvider smsProvider;

    @Value("${app.frontend-url:${app.frontend-base-url:http://localhost:3000}}")
    private String frontendUrl;

    @Value("${app.invitation-expiry-hours:72}")
    private long invitationExpiryHours;

    @Transactional
    public InvitePartnerResponse invitePartner(Long chatRoomId, String rawPhone) {
        User inviter = currentUserService.getCurrentUser();
        ChatRoom chatRoom = getAuthorizedChatRoom(chatRoomId, inviter);
        String phone = PhoneNumbers.normalize(rawPhone);

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

        String link = frontendUrl.replaceAll("/$", "")
                + "/invite/"
                + invitation.getToken();

        try {
            smsProvider.send(
                    phone,
                    "دعوت به گفتگوی مشترک در بین‌ما:\n" + link
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
                .inviterName(invitation.getInvitedBy().getName())
                .status(effectiveStatus(invitation).name())
                .build();
    }

    @Transactional
    public AcceptInvitationResponse accept(String token) {
        User user = currentUserService.getCurrentUser();
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

        if (participantRepository.countByChatRoom(chatRoom) >= MAX_HUMAN_PARTICIPANTS) {
            throw new IllegalArgumentException("ظرفیت این گفتگو تکمیل شده است.");
        }

       /* if (user.getPhoneNumber() != null
                && !user.getPhoneNumber().isBlank()
                && !user.getPhoneNumber().equals(invitation.getPhoneNumber())) {
            throw new IllegalArgumentException(
                    "این دعوت برای شماره دیگری است."
            );
        }*/

        if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
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

        participantRepository.save(
                ChatRoomParticipant.builder()
                        .chatRoom(chatRoom)
                        .user(user)
                        .role(ParticipantRole.MEMBER)
                        .build()
        );
        markAccepted(invitation, user);

        return AcceptInvitationResponse.builder()
                .chatRoomId(chatRoom.getId())
                .chatRoomTitle(chatRoom.getTitle())
                .message("با موفقیت به گفتگو اضافه شدید.")
                .build();
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
