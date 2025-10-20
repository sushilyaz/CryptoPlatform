package com.suhoi.adapters.bitget;

import com.suhoi.api.adapter.DiscoveryClient;
import com.suhoi.api.adapter.ExchangeAdapter;
import com.suhoi.api.adapter.StreamClient;
import lombok.extern.slf4j.Slf4j;

/**
 * Композит Bitget: discovery + два потоковых клиента (SPOT и USDT-FUTURES).
 * <pre>
 *   var adapter = new BitgetAdapter();
 *   var spotSub = adapter.spotStream().subscribeBookTicker(List.of("BTCUSDT"), handler);
 *   var perpSub = adapter.perpStream().subscribeBookTicker(List.of("BTCUSDT"), handler);
 * </pre>
 */
public final class BitgetAdapter implements ExchangeAdapter, AutoCloseable {
    private final BitgetDiscoveryClient discovery = new BitgetDiscoveryClient();
    private final BitgetSpotTickerStreamClient spot = new BitgetSpotTickerStreamClient();
    private final BitgetPerpTickerStreamClient perp = new BitgetPerpTickerStreamClient();

    @Override
    public String venue() {
        return "BITGET";
    }

    @Override
    public DiscoveryClient discovery() {
        return discovery;
    }

    @Override
    public StreamClient spotStream() {
        return spot;
    }

    @Override
    public StreamClient perpStream() {
        return perp;
    }

    @Override
    public void close() {
        try {
            spot.close();
        } catch (Exception ignore) {
        }
        try {
            perp.close();
        } catch (Exception ignore) {
        }
    }
}

