package com.suhoi.discovery;

import java.util.Objects;

/**
 * Идентификатор рынка (для резолва 24h-метрик).<br>
 * asset — каноническая база (BTC/ETH/...),<br>
 * venue — BINANCE/BYBIT/...,<br>
 * kind  — SPOT | PERP | FUTURES | DEX,<br>
 * nativeSymbol — нативный символ площадки (BTCUSDT, BTC_USDT, <chain>:<pairAddress> для DEX).<br>
 */
public record MarketRef(String asset, String venue, String kind, String nativeSymbol) {
    public MarketRef {
        Objects.requireNonNull(asset); Objects.requireNonNull(venue);
        Objects.requireNonNull(kind);  Objects.requireNonNull(nativeSymbol);
    }
}

