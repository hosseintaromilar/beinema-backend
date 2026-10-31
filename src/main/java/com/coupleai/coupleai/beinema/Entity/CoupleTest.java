package com.coupleai.coupleai.beinema.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A test of a companion's kit in one room. Each person answers separately; what the
 * other answered stays hidden until both have finished.
 */
@Entity
@Table(name = "couple_tests", indexes = @Index(name = "idx_couple_test_room", columnList = "chat_room_id"))
@Getter
@Setter
@NoArgsConstructor
public class CoupleTest extends BaseEntity {

    @Column(name = "public_id", length = 36, nullable = false, unique = true)
    private String publicId;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "test_key", length = 20, nullable = false)
    private String testKey;

    /** JSON object: user id to {item id: chosen option}. */
    @Column(name = "answers_json", columnDefinition = "TEXT")
    private String answersJson;

    @Column(nullable = false)
    private boolean completed;
}
