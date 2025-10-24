package com.suhoi.discovery;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 24h-метрики рынка в USD (если доступно).<br>
 * Для CEX:<br>
 *  - spot: quoteVolume(USDT) как vol24hUsd;<br>
 *  - perp/futures: turnover24h(USDT) как vol24hUsd.<br>
 * Для DEX:<br>
 *  - volume.h24 → vol24hUsd;<br>
 *  - liquidity.usd (если есть) оставляем как optional.<br>
 */
public record MarketStats(
        BigDecimal vol24hUsd,
        BigDecimal liquidityUsd, // nullable для CEX
        Instant asOf
) {}

