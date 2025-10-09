package com.suhoi.events;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Алерт на раскор (OPEN/CLOSE).
 */
public record AlertEvent(
        Instant ts,
        String asset,
        String marketId,
        BigDecimal devPct,
        BigDecimal price,
        BigDecimal fair,
        BigDecimal bias,
        BigDecimal thresholdPct,
        String state // OPEN | CLOSE
) {}
