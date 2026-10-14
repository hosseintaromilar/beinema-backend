package com.coupleai.coupleai.beinema.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A file shared in a counseling room. The bytes live in a private folder and are
 * only served to members of the room. {@code summary} is a neutral machine
 * reading of the content that the counselor brain receives as text.
 */
@Entity
@Table(
        name = "message_attachments",
        indexes = {
                @Index(name = "idx_attachment_room", columnList = "chat_room_id"),
                @Index(name = "idx_attachment_message", columnList = "message_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageAttachment extends BaseEntity {

    public static final String PENDING = "PENDING";
    public static final String READY = "READY";
    public static final String FAILED = "FAILED";

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    /** Null until the message that carries the file is sent. */
    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    /** IMAGE or FILE. */
    @Column(nullable = false, length = 16)
    private String kind;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Column(length = 64)
    private String sha256;

    @Column(name = "summary_status", nullable = false, length = 16)
    @Builder.Default
    private String summaryStatus = PENDING;

    @Column(columnDefinition = "TEXT")
    private String summary;
}
