package com.suhoi.events;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Унифицированный тик по всем площадкам.
 */
public record Tick(
        Instant ts,
        String asset,       // BASE (e.g., BTC)
        String venue,       // BINANCE, BYBIT, ...
        String kind,        // SPOT | PERP | FUTURES | DEX
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal mid,
        BigDecimal depthUsd50,
        Instant heartbeatTs,
        String marketId,    // Канонический market_id из БД (если есть)
        String nativeSymbol // Нативный символ биржи (e.g., BTCUSDT)
) {}
