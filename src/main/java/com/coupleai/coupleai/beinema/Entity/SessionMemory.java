package com.coupleai.coupleai.beinema.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "session_memories")
@Getter
@Setter
@NoArgsConstructor
public class SessionMemory extends BaseEntity {

    @Column(nullable = false, unique = true)
    private Long chatRoomId;

    @Column(columnDefinition = "TEXT")
    private String snapshotJson;
}
