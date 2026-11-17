package com.coupleai.coupleai.beinema.market;

import com.coupleai.coupleai.beinema.Entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A market report as it was on the day it was made: the prices, a year of each market, the analysis
 * and the reference rates. Made by Pouria in a conversation (chatRoomId) or by a person from the
 * dashboard (userId), and kept so it can be opened again later in «گزارش‌های مالی».
 */
@Entity
@Table(name = "market_reports", indexes = {
        @Index(name = "idx_market_report_room", columnList = "chat_room_id"),
        @Index(name = "idx_market_report_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
public class MarketReport extends BaseEntity {

    @Column(name = "public_id", length = 36, nullable = false, unique = true)
    private String publicId;

    @Column(name = "chat_room_id")
    private Long chatRoomId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "data_json", columnDefinition = "MEDIUMTEXT", nullable = false)
    private String dataJson;
}
