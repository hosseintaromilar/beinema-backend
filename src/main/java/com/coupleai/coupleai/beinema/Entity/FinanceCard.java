package com.coupleai.coupleai.beinema.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A card of the financial companion's path in a room: the risk quiz (answers per person), the
 * household's financial picture (one per room, edited by either of them), a market report (the
 * prices at that moment) or a plan of scenarios (computed at that moment, with each one's choice).
 */
@Entity
@Table(name = "finance_cards", indexes = @Index(name = "idx_finance_card_room", columnList = "chat_room_id"))
@Getter
@Setter
@NoArgsConstructor
public class FinanceCard extends BaseEntity {

    public static final String RISK = "RISK";
    public static final String PICTURE = "PICTURE";
    public static final String MARKET = "MARKET";
    public static final String PLAN = "PLAN";

    @Column(name = "public_id", length = 36, nullable = false, unique = true)
    private String publicId;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(length = 10, nullable = false)
    private String kind;

    /** JSON: RISK {userId: {item: option}}; PICTURE {values, hints}; MARKET {quotes}; PLAN {plan, choices}. */
    @Column(name = "data_json", columnDefinition = "MEDIUMTEXT")
    private String dataJson;

    @Column(name = "updated_by")
    private Long updatedBy;
}
