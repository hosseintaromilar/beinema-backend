package com.coupleai.coupleai.beinema.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A deletion that happened: a message or a whole room, who asked and what was done. Only ids and
 * outcomes, never any words. Metis keeps a removed conversation up to three months (its privacy
 * policy), so its step says the removal was requested.
 */
@Entity
@Table(name = "deletion_log")
@Getter
@Setter
@NoArgsConstructor
public class DeletionLog extends BaseEntity {

    public static final String MESSAGE = "MESSAGE";
    public static final String ROOM = "ROOM";

    @Column(length = 10, nullable = false)
    private String scope;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    /** DONE for our database and files. */
    @Column(name = "db_status", length = 10, nullable = false)
    private String dbStatus = "DONE";

    /** NONE when there was nothing at Metis, REQUESTED when its removal was sent. */
    @Column(name = "metis_status", length = 10, nullable = false)
    private String metisStatus = "NONE";
}
