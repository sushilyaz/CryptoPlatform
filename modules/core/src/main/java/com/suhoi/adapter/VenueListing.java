package com.suhoi.adapter;

import java.util.Objects;

/**
 * Описание листинга площадки для одного рынка.
 * Инварианты:
 * - quote в MVP всегда USDT;
 * - status должен соответствовать TRADING-эквиваленту площадки.
 */
public final class VenueListing {
    public final String venue;        // напр., BINANCE
    public final String kind;         // SPOT | PERP | FUTURES | DEX
    public final String nativeSymbol; // напр., BTCUSDT
    public final String base;         // напр., BTC
    public final String quote;        // USDT (в MVP)
    public final int priceScale;      // кол-во знаков цены по tickSize
    public final int qtyScale;        // кол-во знаков количества по stepSize
    public final String status;       // TRADING / иное

    public VenueListing(String venue, String kind, String nativeSymbol,
                        String base, String quote, int priceScale, int qtyScale, String status) {
        this.venue = Objects.requireNonNull(venue);
        this.kind = Objects.requireNonNull(kind);
        this.nativeSymbol = Objects.requireNonNull(nativeSymbol);
        this.base = Objects.requireNonNull(base);
        this.quote = Objects.requireNonNull(quote);
        this.priceScale = priceScale;
        this.qtyScale = qtyScale;
        this.status = Objects.requireNonNull(status);
    }
}
