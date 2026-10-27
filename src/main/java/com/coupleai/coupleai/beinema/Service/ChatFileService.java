package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.DTO.messages.AttachmentResponse;
import com.coupleai.coupleai.beinema.DTO.metis.MetisAttachment;
import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.MessageAttachment;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.ChatRoomStatus;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.MessageAttachmentRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.Security.CurrentUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Files shared in a counseling room.
 *
 * Java here only stores, protects and delivers the bytes and asks a neutral
 * reader for a plain-text reading of the content. What the counselor says about
 * a file is decided by the brain in the Metis workflow.
 */
@Service
public class ChatFileService {

    private static final Logger log = LoggerFactory.getLogger(ChatFileService.class);

    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    public static final int MAX_FILES_PER_MESSAGE = 4;
    static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final long ROOM_QUOTA_BYTES = 100L * 1024 * 1024;
    private static final long SUMMARY_WAIT_MILLIS = 30_000L;

    private static final String READER_PROMPT = """
            You are a neutral document reader for a counseling session.
            Read the attached file and write, in Persian, a faithful description of what it contains in at most 120 words.
            For an image: describe what is visible (scene, objects, any text in it). Do not identify people by their appearance.
            For a document: say what kind of document it is and give its main points.
            Do not interpret feelings, do not give advice and do not diagnose.
            If you cannot read it, say so in one sentence.
            Output only the description.
            """;

    private final MessageAttachmentRepository attachmentRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final MetisService metisService;
    private final AgentAccessService agentAccessService;
    private final com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository chatRoomAgentRepository;

    private final Path uploadRoot;
    private final String readerBotId;
    private final ExecutorService readers = Executors.newFixedThreadPool(2);

    public ChatFileService(
            MessageAttachmentRepository attachmentRepository,
            ChatRoomRepository chatRoomRepository,
            ChatRoomParticipantRepository participantRepository,
            CurrentUserService currentUserService,
            UserRepository userRepository,
            MetisService metisService,
            AgentAccessService agentAccessService,
            com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository chatRoomAgentRepository,
            @Value("${beinema.upload.dir:uploads}") String uploadDir,
            @Value("${metis.insight-bot-id}") String readerBotId
    ) {
        this.attachmentRepository = attachmentRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.participantRepository = participantRepository;
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
        this.metisService = metisService;
        this.agentAccessService = agentAccessService;
        this.chatRoomAgentRepository = chatRoomAgentRepository;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        this.readerBotId = readerBotId;
    }

    /** What a file is, decided from its bytes and never from the name or the header alone. */
    record Detected(String contentType, String extension, String kind) {
    }

    public record LoadedFile(Resource resource, MessageAttachment attachment) {
    }

    // ------------------------------------------------------------------ upload

    public AttachmentResponse upload(Long chatRoomId, MultipartFile file) {
        User user = currentUserService.getCurrentUser();
        ChatRoom room = requireMemberRoom(chatRoomId, user);

        if (room.getStatus() != ChatRoomStatus.ACTIVE) {
            throw new IllegalArgumentException("این جلسه دیگر فعال نیست.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("فایلی انتخاب نشده است.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("حجم فایل باید کمتر از ۱۰ مگابایت باشد.");
        }
        if (attachmentRepository.totalBytesInRoom(chatRoomId) + file.getSize() > ROOM_QUOTA_BYTES) {
            throw new IllegalArgumentException("حجم فایل‌های این گفتگو به سقف مجاز رسیده است.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("خواندن فایل ممکن نشد.");
        }
        if (DocxText.isOldWord(bytes)) {
            throw new IllegalArgumentException(
                    "فایل Word قدیمی (.doc) خوانده نمی‌شود؛ آن را به PDF یا docx تبدیل کن و دوباره بفرست."
            );
        }
        Detected detected = detect(bytes)
                .orElseThrow(() -> new IllegalArgumentException(
                        "فقط عکس (JPG، PNG، WEBP)، PDF، فایل Word (docx) و فایل متنی پذیرفته می‌شود."
                ));

        String storedName = UUID.randomUUID() + detected.extension();
        Path directory = uploadRoot.resolve("rooms").resolve(String.valueOf(chatRoomId));
        Path target = directory.resolve(storedName);
        try {
            Files.createDirectories(directory);
            Files.write(target, bytes);
        } catch (IOException exception) {
            throw new IllegalArgumentException("ذخیرهٔ فایل ناموفق بود.");
        }

        MessageAttachment saved = attachmentRepository.save(
                MessageAttachment.builder()
                        .chatRoomId(chatRoomId)
                        .uploaderId(user.getId())
                        .originalName(cleanName(file.getOriginalFilename(), detected.extension()))
                        .contentType(detected.contentType())
                        .sizeBytes((long) bytes.length)
                        .kind(detected.kind())
                        .storagePath("rooms/" + chatRoomId + "/" + storedName)
                        .sha256(sha256(bytes))
                        .build()
        );

        Long attachmentId = saved.getId();
        Long uploaderId = user.getId();
        readers.submit(() -> summarize(attachmentId, uploaderId));
        return AttachmentResponse.from(saved);
    }

    // ---------------------------------------------------------------- download

    public LoadedFile load(Long chatRoomId, Long attachmentId) {
        User user = currentUserService.getCurrentUser();
        requireMemberRoom(chatRoomId, user);
        MessageAttachment attachment = attachmentRepository.findByIdAndChatRoomId(attachmentId, chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("فایل پیدا نشد."));
        Path path = resolve(attachment.getStoragePath());
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("فایل پیدا نشد.");
        }
        return new LoadedFile(new FileSystemResource(path), attachment);
    }

    // ------------------------------------------------------- link to a message

    /**
     * Attaches uploaded files to a message that is being sent. A file can only be
     * used by the person who uploaded it, in the room it was uploaded to, once.
     */
    public List<MessageAttachment> prepare(Long chatRoomId, Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > MAX_FILES_PER_MESSAGE) {
            throw new IllegalArgumentException("در هر پیام حداکثر ۴ فایل می‌شود فرستاد.");
        }
        List<MessageAttachment> found = attachmentRepository.findAllByIdIn(ids);
        List<MessageAttachment> claimed = new ArrayList<>();
        for (MessageAttachment attachment : found) {
            boolean usable = chatRoomId.equals(attachment.getChatRoomId())
                    && userId.equals(attachment.getUploaderId())
                    && attachment.getMessageId() == null;
            if (!usable) {
                throw new IllegalArgumentException("یکی از فایل‌ها قابل استفاده نیست.");
            }
            claimed.add(attachment);
        }
        return claimed;
    }

    /** Links validated files to the message that was just saved. */
    public void link(List<MessageAttachment> attachments, Long messageId) {
        for (MessageAttachment attachment : attachments) {
            attachment.setMessageId(messageId);
            attachmentRepository.save(attachment);
        }
    }

    public List<AttachmentResponse> responsesForMessage(Long messageId) {
        return attachmentRepository.findAllByMessageId(messageId).stream()
                .map(AttachmentResponse::from)
                .toList();
    }

    // ----------------------------------------------------- text for the brain

    /**
     * The block appended to the message the brain receives. It carries the
     * neutral reading of each file and whether the person also wrote a message.
     */
    public String describeForBrain(List<MessageAttachment> attachments, boolean withMessage) {
        if (attachments == null || attachments.isEmpty()) {
            return "";
        }
        awaitSummaries(attachments);
        StringBuilder block = new StringBuilder();
        for (MessageAttachment attachment : attachments) {
            MessageAttachment fresh = attachmentRepository.findById(attachment.getId()).orElse(attachment);
            String summary = fresh.getSummary();
            if (summary == null || summary.isBlank()) {
                summary = "(could not be read)";
            }
            block.append("\n[ATTACHMENT | ")
                    .append(fresh.getKind()).append(" | ")
                    .append(fresh.getOriginalName()).append(" | WITH_MESSAGE=").append(withMessage)
                    .append("]\nREADER_SUMMARY: ")
                    .append(summary.replace("\n", " "));
        }
        return block.toString();
    }

    // -------------------------------------------------------------- cleanup

    public void deleteRoomFiles(Long chatRoomId) {
        Path directory = uploadRoot.resolve("rooms").resolve(String.valueOf(chatRoomId));
        attachmentRepository.deleteByChatRoomId(chatRoomId);
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var stream = Files.walk(directory)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // leftover files are harmless
                }
            });
        } catch (IOException ignored) {
            // leftover files are harmless
        }
    }

    // ----------------------------------------------------------------- internals

    private void summarize(Long attachmentId, Long uploaderId) {
        MessageAttachment attachment = attachmentRepository.findById(attachmentId).orElse(null);
        if (attachment == null) {
            return;
        }
        try {
            Path path = resolve(attachment.getStoragePath());
            byte[] bytes = Files.readAllBytes(path);
            String summary;
            if ("text/plain".equals(attachment.getContentType())) {
                String text = new String(bytes, StandardCharsets.UTF_8).strip();
                summary = text.length() > 1500 ? text.substring(0, 1500) + "..." : text;
            } else if ("application/pdf".equals(attachment.getContentType())
                    && PdfText.extract(bytes).isPresent()) {
                // a PDF with real text: its own words; a scan goes to the model below
                String text = PdfText.extract(bytes).orElse("");
                summary = text.length() > 1500 ? text.substring(0, 1500) + "..." : text;
            } else if (DOCX.equals(attachment.getContentType())) {
                // a Word file: its own words, like a text file
                String text = DocxText.extract(bytes).orElse("");
                summary = text.isBlank() ? "(an empty Word document)"
                        : text.length() > 1500 ? text.substring(0, 1500) + "..." : text;
            } else {
                User uploader = userRepository.findById(uploaderId)
                        .orElseThrow(() -> new IllegalStateException("uploader missing"));
                String url = metisService.uploadFile(bytes, attachment.getOriginalName(), attachment.getContentType());
                var session = metisService.createConversation(readerBotId, uploader);
                summary = metisService.sendMessageWithAttachments(
                        session.getId(),
                        READER_PROMPT,
                        List.of(MetisAttachment.builder()
                                .content(url)
                                .contentType("IMAGE".equals(attachment.getKind()) ? "IMAGE" : "FILE")
                                .build())
                );
            }
            attachment.setSummary(summary == null ? "" : summary.strip());
            attachment.setSummaryStatus(MessageAttachment.READY);
        } catch (Exception exception) {
            log.warn("Could not read attachment {}", attachmentId, exception);
            attachment.setSummaryStatus(MessageAttachment.FAILED);
        }
        attachmentRepository.save(attachment);
    }

    private void awaitSummaries(List<MessageAttachment> attachments) {
        long deadline = System.currentTimeMillis() + SUMMARY_WAIT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            boolean pending = attachments.stream().anyMatch(item ->
                    attachmentRepository.findById(item.getId())
                            .map(fresh -> MessageAttachment.PENDING.equals(fresh.getSummaryStatus()))
                            .orElse(false));
            if (!pending) {
                return;
            }
            try {
                Thread.sleep(700);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private ChatRoom requireMemberRoom(Long chatRoomId, User user) {
        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("گفتگو پیدا نشد."));
        if (!participantRepository.existsByChatRoomAndUser(room, user)) {
            throw new IllegalArgumentException("شما عضو این گفتگو نیستید.");
        }
        Long agentId = chatRoomAgentRepository.findActiveAgentIds(chatRoomId).stream().findFirst().orElse(null);
        agentAccessService.requireValidAccess(chatRoomId, agentId);
        return room;
    }

    private Path resolve(String storagePath) {
        if (storagePath == null) {
            return null;
        }
        Path path = uploadRoot.resolve(storagePath).normalize();
        return path.startsWith(uploadRoot) ? path : null;
    }

    private static String cleanName(String original, String extension) {
        String name = original == null ? "" : original;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}<>:\"|?*]", "").strip();
        if (name.isEmpty()) {
            name = "file" + extension;
        }
        return name.length() > 120 ? name.substring(name.length() - 120) : name;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            return null;
        }
    }

    static Optional<Detected> detect(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return Optional.empty();
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return Optional.of(new Detected("image/jpeg", ".jpg", "IMAGE"));
        }
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return Optional.of(new Detected("image/png", ".png", "IMAGE"));
        }
        if (bytes.length > 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return Optional.of(new Detected("image/webp", ".webp", "IMAGE"));
        }
        if (bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F') {
            return Optional.of(new Detected("application/pdf", ".pdf", "FILE"));
        }
        if (DocxText.isZip(bytes)) {
            // a zip is accepted only when it is a Word document
            return DocxText.extract(bytes).isPresent()
                    ? Optional.of(new Detected(DOCX, ".docx", "FILE"))
                    : Optional.empty();
        }
        // plain text: no NUL bytes and valid UTF-8 in the first 4 KB
        int sample = Math.min(bytes.length, 4096);
        for (int i = 0; i < sample; i++) {
            if (bytes[i] == 0) {
                return Optional.empty();
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes, 0, sample));
        } catch (java.nio.charset.CharacterCodingException exception) {
            // a multi-byte character cut at the sample boundary is fine
            if (sample == bytes.length) {
                return Optional.empty();
            }
        }
        return Optional.of(new Detected("text/plain", ".txt", "FILE"));
    }

    public static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
