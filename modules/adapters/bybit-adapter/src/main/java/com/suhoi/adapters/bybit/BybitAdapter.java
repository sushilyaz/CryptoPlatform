package com.suhoi.adapters.bybit;

import com.suhoi.api.adapter.DiscoveryClient;
import com.suhoi.api.adapter.ExchangeAdapter;
import com.suhoi.api.adapter.StreamClient;


/**
 * Композит Bybit v5: discovery + два потоковых клиента
 *  - SPOT: orderbook L1 (best bid/ask) → mid
 *  - PERP (linear): tickers → bid1/ask1 → mid
 */
public final class BybitAdapter implements ExchangeAdapter, AutoCloseable {
    private final BybitDiscoveryClient discovery = new BybitDiscoveryClient();
    private final BybitSpotOrderbookL1StreamClient spot = new BybitSpotOrderbookL1StreamClient();
    private final BybitPerpTickersStreamClient perp = new BybitPerpTickersStreamClient();

    @Override public String venue() { return "BYBIT"; }
    @Override public DiscoveryClient discovery() { return discovery; }
    @Override public StreamClient spotStream() { return spot; }
    @Override public StreamClient perpStream() { return perp; }

    @Override public void close() {
        try { spot.close(); } catch (Exception ignore) {}
        try { perp.close(); } catch (Exception ignore) {}
    }
}

