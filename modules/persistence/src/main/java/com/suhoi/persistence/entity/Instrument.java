package com.suhoi.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "instruments")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Instrument {

    @Id
    @Column(name = "asset", nullable = false)
    private String asset;

    @Column(name = "base_symbol", nullable = false)
    private String baseSymbol;

    @Builder.Default
    @Column(name = "quote_symbol", nullable = false)
    private String quoteSymbol = "USDT";

    @Column(name = "scale", nullable = false)
    private int scale;
}
