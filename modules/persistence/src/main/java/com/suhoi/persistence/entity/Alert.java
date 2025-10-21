package com.suhoi.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "alerts")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "ts", nullable = false)
    private Instant ts;

    @Column(name = "asset", nullable = false)
    private String asset;

    @Column(name = "market_id", nullable = false)
    private Long marketId;

    @Column(name = "dev_pct", nullable = false, precision = 12, scale = 6)
    private BigDecimal devPct;

    @Column(name = "fair", nullable = false, precision = 38, scale = 18)
    private BigDecimal fair;

    @Column(name = "price", nullable = false, precision = 38, scale = 18)
    private BigDecimal price;

    @Column(name = "bias", nullable = false, precision = 38, scale = 18)
    private BigDecimal bias;

    @Column(name = "threshold_pct", nullable = false, precision = 12, scale = 6)
    private BigDecimal thresholdPct;

    @Column(name = "state", nullable = false)
    private String state; // OPEN/CLOSE (хранится строкой, CHECK в БД)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id", referencedColumnName = "market_id",
            insertable = false, updatable = false)
    private Market market;
}
