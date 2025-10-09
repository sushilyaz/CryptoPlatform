package com.suhoi.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Секундный снапшот fair-цены по инструменту.
 */
public record FairSnap(
        Instant ts,
        String asset,
        BigDecimal fair,
        List<Source> sources // вклад рынков в расчет fair
) {
    public record Source(String marketId, BigDecimal mid, BigDecimal weight) {
    }
}
