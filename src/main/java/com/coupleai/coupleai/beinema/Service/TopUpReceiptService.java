package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.Entity.TopUpReceipt;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.TopUpReceiptStatus;
import com.coupleai.coupleai.beinema.Enum.WalletTransactionType;
import com.coupleai.coupleai.beinema.Repository.TopUpReceiptRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Card-to-card top-up: the person pays to the card on the wallet page and
 * sends the receipt; an admin looks at it and approves (the wallet is charged)
 * or rejects it with a reason. Receipts live under uploads/receipts, which is
 * not served publicly: only their owner and the admins read them, through the API.
 */
@Service
public class TopUpReceiptService {

    private static final long MIN_AMOUNT = 10_000;
    private static final long MAX_AMOUNT = 50_000_000;
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_PENDING = 3;
    private static final Map<String, String> TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/jpg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "application/pdf", ".pdf");

    private final TopUpReceiptRepository receiptRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final WalletService walletService;
    private final Path uploadRoot;

    public TopUpReceiptService(
            TopUpReceiptRepository receiptRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService,
            WalletService walletService,
            @Value("${beinema.upload.dir:uploads}") String uploadDir
    ) {
        this.receiptRepository = receiptRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.walletService = walletService;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    /** One receipt; the person's name and phone only in the admins' list. */
    public record ReceiptView(Long id, long amount, Long approvedAmount, String trackingCode, String status,
                              String adminNote, Long createdAt, Long decidedAt, boolean pdf,
                              Long userId, String userName, String userPhone) {
    }

    public record ReceiptFile(Resource resource, String contentType) {
    }

    @Transactional
    public ReceiptView send(MultipartFile file, Long amount, String trackingCode) {
        User user = currentUserService.getCurrentUser();
        if (amount == null || amount < MIN_AMOUNT) {
            throw new IllegalArgumentException("مبلغ واریزی را بنویس؛ دست‌کم ۱۰٬۰۰۰ تومان.");
        }
        if (amount > MAX_AMOUNT) {
            throw new IllegalArgumentException("این مبلغ بیش از حد مجاز است؛ اگر واقعاً این مبلغ را واریز کرده‌ای، به پشتیبانی پیام بده.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("عکس یا فایل رسید واریز را انتخاب کن.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("حجم رسید باید کمتر از ۵ مگابایت باشد.");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT).split(";")[0].trim();
        String extension = TYPES.get(type);
        if (extension == null) {
            throw new IllegalArgumentException("رسید باید عکس (JPG، PNG، WEBP) یا PDF باشد.");
        }
        if (receiptRepository.countByUserIdAndStatus(user.getId(), TopUpReceiptStatus.PENDING) >= MAX_PENDING) {
            throw new IllegalArgumentException("چند رسید در انتظار بررسی داری؛ بعد از بررسی آن‌ها رسید تازه بفرست.");
        }

        String relative = "receipts/" + user.getId() + "/" + UUID.randomUUID() + extension;
        Path target = uploadRoot.resolve(relative).normalize();
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target.toFile());
        } catch (IOException exception) {
            throw new IllegalArgumentException("ذخیرهٔ رسید ممکن نشد. دوباره تلاش کن.");
        }

        TopUpReceipt receipt = new TopUpReceipt();
        receipt.setUserId(user.getId());
        receipt.setAmount(amount);
        receipt.setTrackingCode(clean(trackingCode, 40));
        receipt.setStoragePath(relative);
        receipt.setContentType(type.equals("image/jpg") ? "image/jpeg" : type);
        return view(receiptRepository.save(receipt), null);
    }

    @Transactional(readOnly = true)
    public List<ReceiptView> mine() {
        User user = currentUserService.getCurrentUser();
        return receiptRepository.findTop30ByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(receipt -> view(receipt, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReceiptFile myFile(Long id) {
        User user = currentUserService.getCurrentUser();
        TopUpReceipt receipt = require(id);
        if (!Objects.equals(receipt.getUserId(), user.getId())) {
            throw new IllegalArgumentException("رسید پیدا نشد.");
        }
        return file(receipt);
    }

    // ------------------------------------------------------------------ admins

    @Transactional(readOnly = true)
    public List<ReceiptView> forAdmin(String status) {
        List<TopUpReceipt> receipts;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            receipts = receiptRepository.findTop200ByOrderByCreatedAtDesc();
        } else {
            receipts = receiptRepository.findAllByStatusOrderByCreatedAtAsc(
                    TopUpReceiptStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)));
        }
        return receipts.stream()
                .map(receipt -> view(receipt, userRepository.findById(receipt.getUserId()).orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReceiptFile adminFile(Long id) {
        return file(require(id));
    }

    /** Charges the wallet with the amount (or what the admin corrected it to), once. */
    @Transactional
    public ReceiptView approve(Long id, Long amount, String note) {
        User admin = currentUserService.getCurrentUser();
        TopUpReceipt receipt = receiptRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("رسید پیدا نشد."));
        if (receipt.getStatus() != TopUpReceiptStatus.PENDING) {
            throw new IllegalArgumentException("این رسید قبلاً بررسی شده است.");
        }
        long charge = amount == null ? receipt.getAmount() : amount;
        if (charge < 1 || charge > MAX_AMOUNT) {
            throw new IllegalArgumentException("مبلغ تأیید معتبر نیست.");
        }
        User owner = userRepository.findById(receipt.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("صاحب این رسید پیدا نشد."));
        walletService.credit(owner, charge, WalletTransactionType.CARD_TOP_UP,
                "شارژ کارت به کارت", "receipt:" + receipt.getId());
        receipt.setStatus(TopUpReceiptStatus.APPROVED);
        receipt.setApprovedAmount(charge);
        receipt.setAdminNote(clean(note, 300));
        receipt.setDecidedAt(Instant.now());
        receipt.setDecidedBy(admin.getId());
        return view(receiptRepository.save(receipt), owner);
    }

    @Transactional
    public ReceiptView reject(Long id, String note) {
        User admin = currentUserService.getCurrentUser();
        String reason = clean(note, 300);
        if (reason == null) {
            throw new IllegalArgumentException("دلیل رد را بنویس؛ به کاربر نشان داده می‌شود.");
        }
        TopUpReceipt receipt = receiptRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("رسید پیدا نشد."));
        if (receipt.getStatus() != TopUpReceiptStatus.PENDING) {
            throw new IllegalArgumentException("این رسید قبلاً بررسی شده است.");
        }
        receipt.setStatus(TopUpReceiptStatus.REJECTED);
        receipt.setAdminNote(reason);
        receipt.setDecidedAt(Instant.now());
        receipt.setDecidedBy(admin.getId());
        return view(receiptRepository.save(receipt), userRepository.findById(receipt.getUserId()).orElse(null));
    }

    public long pendingCount() {
        return receiptRepository.countByStatus(TopUpReceiptStatus.PENDING);
    }

    // ------------------------------------------------------------------ helpers

    private TopUpReceipt require(Long id) {
        return receiptRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("رسید پیدا نشد."));
    }

    private ReceiptFile file(TopUpReceipt receipt) {
        Path path = uploadRoot.resolve(receipt.getStoragePath()).normalize();
        if (!path.startsWith(uploadRoot) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("فایل رسید پیدا نشد.");
        }
        return new ReceiptFile(new FileSystemResource(path), receipt.getContentType());
    }

    private static ReceiptView view(TopUpReceipt receipt, User owner) {
        return new ReceiptView(
                receipt.getId(),
                receipt.getAmount(),
                receipt.getApprovedAmount(),
                receipt.getTrackingCode(),
                receipt.getStatus().name(),
                receipt.getAdminNote(),
                millis(receipt.getCreatedAt()),
                receipt.getDecidedAt() == null ? null : receipt.getDecidedAt().toEpochMilli(),
                "application/pdf".equals(receipt.getContentType()),
                owner == null ? null : owner.getId(),
                owner == null ? null : owner.visibleName(),
                owner == null ? null : owner.getPhoneNumber());
    }

    private static Long millis(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static String clean(String text, int max) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
