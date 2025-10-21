package com.suhoi.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "fair_minute")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FairMinute {

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Id implements Serializable {
        @Column(name = "asset", nullable = false)
        private String asset;

        @Column(name = "ts_minute", nullable = false)
        private Instant tsMinute;
    }

    @EmbeddedId
    private Id id;

    @Column(name = "fair", nullable = false, precision = 38, scale = 18)
    private BigDecimal fair;
}
