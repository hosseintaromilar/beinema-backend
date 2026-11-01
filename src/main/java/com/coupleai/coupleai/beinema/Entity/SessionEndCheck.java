package com.coupleai.coupleai.beinema.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The companion asked whether to end the conversation here; each person answers on the card. */
@Entity
@Table(name = "session_end_checks", indexes = @Index(name = "idx_end_check_room", columnList = "chat_room_id"))
@Getter
@Setter
@NoArgsConstructor
public class SessionEndCheck extends BaseEntity {

    public static final String OPEN = "OPEN";
    public static final String ENDED = "ENDED";
    public static final String CONTINUED = "CONTINUED";

    @Column(name = "public_id", length = 36, nullable = false, unique = true)
    private String publicId;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "session_number", nullable = false)
    private int sessionNumber;

    @Column(length = 10, nullable = false)
    private String status;

    /** Comma separated ids of those who said yes. */
    @Column(name = "yes_users", length = 100)
    private String yesUsers;
}
