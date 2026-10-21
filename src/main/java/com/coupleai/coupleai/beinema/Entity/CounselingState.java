package com.coupleai.coupleai.beinema.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The structured memory of one counseling room, across all its sessions: goal,
 * focus per partner, thread, plans, safety and the last moves (see
 * counseling.CounselingSnapshot). It is a view of the conversation, never a
 * replacement for the messages. The version guards against concurrent updates.
 */
@Entity
@Table(name = "counseling_states")
@Getter
@Setter
@NoArgsConstructor
public class CounselingState extends BaseEntity {

    @Column(name = "chat_room_id", nullable = false, unique = true)
    private Long chatRoomId;

    @Version
    private Long version;

    @Column(name = "state_json", columnDefinition = "MEDIUMTEXT")
    private String stateJson;

    /** The newest partner message the director has already decided about. */
    @Column(name = "last_processed_message_id")
    private Long lastProcessedMessageId;

    @Column(name = "session_number")
    private Integer sessionNumber;
}
