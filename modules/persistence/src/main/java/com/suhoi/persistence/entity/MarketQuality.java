package com.suhoi.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "market_quality")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketQuality {

    @Id
    @Column(name = "market_id", nullable = false)
    private Long marketId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "market_id")
    private Market market;

    @Column(name = "vol24h_usd", precision = 18, scale = 2)
    private BigDecimal vol24hUsd;

    @Column(name = "depth50_usd", precision = 18, scale = 2)
    private BigDecimal depth50Usd;

    @Column(name = "quality_score", precision = 9, scale = 4)
    private BigDecimal qualityScore;

    @Column(name = "last_heartbeat_ts")
    private Instant lastHeartbeatTs;
}
