package com.suhoi.persistence.entity;

import com.suhoi.market.MarketKind;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_minute")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceMinute {

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Id implements Serializable {
        @Column(name = "asset", nullable = false)
        private String asset;

        @Column(name = "venue", nullable = false)
        private String venue;

        @Enumerated(EnumType.STRING)
        @Column(name = "kind", nullable = false)
        private MarketKind kind;

        @Column(name = "ts_minute", nullable = false)
        private Instant tsMinute;
    }

    @EmbeddedId
    private Id id;

    @Column(name = "mid", nullable = false, precision = 38, scale = 18)
    private BigDecimal mid;
}
