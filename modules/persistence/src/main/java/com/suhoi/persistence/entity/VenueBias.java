package com.suhoi.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "venue_bias")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VenueBias {

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Id implements Serializable {
        @Column(name = "asset", nullable = false)
        private String asset;

        @Column(name = "market_id", nullable = false)
        private Long marketId;

        @Column(name = "ts_minute", nullable = false)
        private Instant tsMinute;
    }

    @EmbeddedId
    private Id id;

    @Column(name = "bias", nullable = false, precision = 38, scale = 18)
    private BigDecimal bias;

    // Удобная связка, если нужно вытянуть Market
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id", referencedColumnName = "market_id",
            insertable = false, updatable = false)
    private Market market;
}
