package com.suhoi.persistence.entity;

import com.suhoi.market.MarketKind;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "markets",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_markets_asset_venue_kind",
                columnNames = {"asset", "venue", "kind"}
        )
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Market {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "market_id", nullable = false)
    private Long marketId;

    @Column(name = "asset", nullable = false)
    private String asset;

    @Column(name = "venue", nullable = false)
    private String venue;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private MarketKind kind;

    @Column(name = "native_symbol", nullable = false)
    private String nativeSymbol;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "min_qty", precision = 38, scale = 18)
    private BigDecimal minQty;

    @Column(name = "min_notional", precision = 38, scale = 18)
    private BigDecimal minNotional;

    // readonly связи для удобства навигации/джойнов
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset", referencedColumnName = "asset", insertable = false, updatable = false)
    private Instrument instrument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue", referencedColumnName = "venue", insertable = false, updatable = false)
    private Venue venueRef;
}
