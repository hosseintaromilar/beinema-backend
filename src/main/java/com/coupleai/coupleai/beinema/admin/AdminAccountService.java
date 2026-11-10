package com.coupleai.coupleai.beinema.admin;

import com.coupleai.coupleai.beinema.auth.AuthService;
import com.coupleai.coupleai.beinema.billing.SessionBillingService;
import com.coupleai.coupleai.beinema.billing.WalletService;

import com.coupleai.coupleai.beinema.Entity.AdminAccessLog;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Enum.UserRole;
import com.coupleai.coupleai.beinema.Enum.UserStatus;
import com.coupleai.coupleai.beinema.Repository.AdminAccessLogRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository;
import com.coupleai.coupleai.beinema.Repository.SessionRecordRepository;
import com.coupleai.coupleai.beinema.Repository.TopUpReceiptRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.MessageRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.Repository.WalletTransactionRepository;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import com.coupleai.coupleai.beinema.counseling.MessageTexts;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The supervisor's admins, and support's read-only view of people's accounts. An account can
 * be looked at for support; a chat can be read only when every person who has been in that
 * room allowed it, because their partner's words are in it too. Every look is logged.
 */
@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private static final Pattern USERNAME = Pattern.compile("[a-z0-9_.]{3,30}");
    private static final int MIN_PASSWORD = 8;
    private static final int PAGE = 50;

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final WalletTransactionRepository transactionRepository;
    private final TopUpReceiptRepository receiptRepository;
    private final ChatRoomAgentRepository roomAgentRepository;
    private final SessionRecordRepository sessionRecordRepository;
    private final WalletService walletService;
    private final SessionBillingService billingService;
    private final AdminAccessLogRepository logRepository;
    private final CurrentUserService currentUserService;
    private final PasswordEncoder passwordEncoder;

    // ------------------------------------------------------------------ admins

    @Transactional(readOnly = true)
    public List<Map<String, Object>> admins() {
        return userRepository.findAllByRole(UserRole.ADMIN).stream().map(AdminAccountService::adminView).toList();
    }

    @Transactional
    public Map<String, Object> createAdmin(String rawUsername, String firstName, String lastName, String password) {
        String username = rawUsername == null ? "" : rawUsername.trim().toLowerCase();
        if (!USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException("نام کاربری باید ۳ تا ۳۰ حرف انگلیسی کوچک، عدد، نقطه یا خط زیر باشد.");
        }
        if (password == null || password.length() < MIN_PASSWORD) {
            throw new IllegalArgumentException("رمز باید دست‌کم ۸ نویسه باشد.");
        }
        String email = username + "@admin.beinema.local";
        if (userRepository.existsByUsername(username) || userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("این نام کاربری قبلاً گرفته شده است.");
        }
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        User admin = userRepository.save(User.builder()
                .name((first + " " + last).trim())
                .firstName(first)
                .lastName(last)
                .email(email)
                .username(username)
                .password(passwordEncoder.encode(password))
                .passwordSet(true)
                .status(UserStatus.ACTIVE)
                .role(UserRole.ADMIN)
                .emailVerified(false)
                .build());
        return adminView(admin);
    }

    /** An admin goes back to an ordinary account at once; the supervisor cannot be removed this way. */
    @Transactional
    public void removeAdmin(Long id) {
        User admin = userRepository.findById(id)
                .filter(user -> user.getRole() == UserRole.ADMIN)
                .orElseThrow(() -> new IllegalArgumentException("این ادمین پیدا نشد."));
        if (Boolean.TRUE.equals(admin.getSupervisor())) {
            throw new IllegalArgumentException("حساب سوپروایزر را نمی‌شود حذف کرد.");
        }
        admin.setRole(UserRole.USER);
        userRepository.save(admin);
    }

    private static Map<String, Object> adminView(User user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.getId());
        view.put("username", user.getUsername());
        view.put("name", user.visibleName());
        view.put("supervisor", Boolean.TRUE.equals(user.getSupervisor()));
        view.put("lastLoginAt", user.getLastLoginAt());
        return view;
    }

    // ------------------------------------------------------------------ accounts

    @Transactional(readOnly = true)
    public List<Map<String, Object>> users(String query) {
        String q = query == null ? "" : query.trim().toLowerCase().replace("#", "");
        List<User> found = new ArrayList<>(userRepository.searchForAdmin(q, PageRequest.of(0, PAGE)));
        if (q.matches("[0-9]{1,12}")) {
            userRepository.findById(Long.parseLong(q)).filter(user -> !user.isDeleted())
                    .filter(user -> found.stream().noneMatch(other -> other.getId().equals(user.getId())))
                    .ifPresent(user -> found.add(0, user));
        }
        return found.stream().map(AdminAccountService::userRow).toList();
    }

    @Transactional
    public Map<String, Object> account(Long userId) {
        User user = person(userId);
        log(AdminAccessLog.VIEW_ACCOUNT, userId, null);
        Map<String, Object> view = userRow(user);
        view.put("firstName", user.getFirstName());
        view.put("lastName", user.getLastName());
        view.put("createdAt", user.getCreatedAt());
        view.put("emailVerified", user.isEmailVerified());
        view.put("contactEmail", user.getContactEmail());
        view.put("phoneVerified", Boolean.TRUE.equals(user.getPhoneVerified()));
        view.put("google", user.getGoogleSubject() != null);
        view.put("passwordSet", Boolean.TRUE.equals(user.getPasswordSet()));
        view.put("emailOptOut", Boolean.TRUE.equals(user.getEmailOptOut()));
        view.put("referralCode", user.getReferralCode());
        view.put("avatarUrl", user.getAvatarUrl());
        view.put("giftedSessions", user.getGiftedSessions() == null ? 0 : user.getGiftedSessions());
        view.put("freeSessionsUsed", user.getFreeSessionsUsed() == null ? 0 : user.getFreeSessionsUsed());
        view.put("balance", walletService.getOrCreate(user).getBalance());
        view.put("freeLeft", billingService.freeLeft(user));
        view.put("packages", billingService.offerFor(user).myPackages());
        view.put("cancelledPackages", billingService.cancelledOf(userId));
        view.put("purchases", billingService.purchasesOf(userId));
        view.put("topUps", receiptRepository.findTop30ByUserIdOrderByCreatedAtDesc(userId).stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("amount", item.getAmount());
            row.put("approvedAmount", item.getApprovedAmount());
            row.put("trackingCode", item.getTrackingCode());
            row.put("status", item.getStatus());
            row.put("adminNote", item.getAdminNote());
            row.put("createdAt", item.getCreatedAt());
            return row;
        }).toList());
        view.put("transactions", transactionRepository.findTop50ByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(item -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("type", item.getType());
                    row.put("amount", item.getAmount());
                    row.put("status", item.getStatus());
                    row.put("description", item.getDescription());
                    row.put("createdAt", item.getCreatedAt());
                    return row;
                }).toList());
        List<Map<String, Object>> rooms = new ArrayList<>();
        for (ChatRoomParticipant mine : participantRepository.findAllForUser(userId)) {
            ChatRoom room = mine.getChatRoom();
            List<ChatRoomParticipant> people = participantRepository.findAllByChatRoom(room);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", room.getId());
            row.put("title", room.getTitle());
            row.put("status", room.getStatus());
            row.put("createdAt", room.getCreatedAt());
            row.put("people", people.stream().map(person -> person.getUser().visibleName()).toList());
            row.put("readable", chatAllowed(people));
            row.put("mode", room.getSessionMode());
            row.put("agents", roomAgentRepository.findActiveAgentNames(room.getId()));
            row.put("sessions", sessionRecordRepository.countByChatRoomIdAndCompletedTrue(room.getId()));
            row.put("sessionActive", Boolean.TRUE.equals(room.getSessionActive()));
            row.put("lastActivityAt", messageRepository.findFirstByChatRoomAndDeletedFalseOrderBySequenceNumberDesc(room)
                    .map(Message::getCreatedAt).orElse(null));
            row.put("left", mine.getLeftAt() != null);
            rooms.add(row);
        }
        view.put("rooms", rooms);
        return view;
    }

    /** A room everyone in it opened to review; otherwise nothing of it is returned. */
    @Transactional
    public Map<String, Object> chat(Long userId, Long roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("این گفتگو پیدا نشد."));
        List<ChatRoomParticipant> people = participantRepository.findAllByChatRoom(room);
        boolean member = people.stream().anyMatch(person -> person.getUser().getId().equals(userId));
        if (!member) {
            throw new IllegalArgumentException("این گفتگو پیدا نشد.");
        }
        if (!chatAllowed(people)) {
            throw new IllegalArgumentException("همهٔ افراد این گفتگو اجازهٔ بررسی را نداده‌اند.");
        }
        log(AdminAccessLog.READ_CHAT, userId, roomId);
        Map<Long, String> names = new LinkedHashMap<>();
        people.forEach(person -> names.put(person.getUser().getId(), person.getUser().visibleName()));
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Message message : messageRepository.findByChatRoomIdAndDeletedFalseOrderBySequenceNumberAsc(roomId)) {
            if (message.getVisibleToUserId() != null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", message.getId());
            row.put("kind", message.getSenderType());
            row.put("sender", message.getSenderType() == MessageSenderType.USER
                    ? names.getOrDefault(message.getSenderId(), "کاربر")
                    : message.getSenderType() == MessageSenderType.AGENT ? "همراه" : "سیستم");
            row.put("content", MessageTexts.visible(message));
            row.put("createdAt", message.getCreatedAt());
            messages.add(row);
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("title", room.getTitle());
        view.put("messages", messages);
        return view;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> accessLog() {
        return logRepository.findTop200ByOrderByIdDesc().stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("admin", nameOf(item.getAdminUserId()));
            row.put("target", nameOf(item.getTargetUserId()));
            row.put("roomId", item.getChatRoomId());
            row.put("action", item.getAction());
            row.put("createdAt", item.getCreatedAt());
            return row;
        }).toList();
    }

    /** Everyone who has been in the room must have allowed it, also someone who has left. */
    static boolean chatAllowed(List<ChatRoomParticipant> people) {
        return !people.isEmpty() && people.stream()
                .allMatch(person -> person.getUser() != null && Boolean.TRUE.equals(person.getUser().getAdminChatAccess()));
    }

    private String nameOf(Long id) {
        return userRepository.findById(id).map(User::visibleName).orElse("—");
    }

    private User person(Long id) {
        return userRepository.findById(id)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("این کاربر پیدا نشد."));
    }

    private void log(String action, Long target, Long roomId) {
        AdminAccessLog entry = new AdminAccessLog();
        entry.setAdminUserId(currentUserService.getCurrentUser().getId());
        entry.setTargetUserId(target);
        entry.setChatRoomId(roomId);
        entry.setAction(action);
        logRepository.save(entry);
    }

    /** The address a person really gave; the placeholders of phone-only and admin accounts are not one. */
    private static String realEmail(String email) {
        return email == null || email.endsWith(AuthService.PHONE_EMAIL_SUFFIX) || email.endsWith(".local") ? null : email;
    }

    private static Map<String, Object> userRow(User user) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getId());
        row.put("username", user.getUsername());
        row.put("name", user.visibleName());
        row.put("role", user.getRole());
        row.put("status", user.getStatus());
        row.put("phoneNumber", user.getPhoneNumber());
        row.put("email", realEmail(user.getEmail()));
        row.put("lastLoginAt", user.getLastLoginAt());
        row.put("chatAccess", Boolean.TRUE.equals(user.getAdminChatAccess()));
        return row;
    }
}
