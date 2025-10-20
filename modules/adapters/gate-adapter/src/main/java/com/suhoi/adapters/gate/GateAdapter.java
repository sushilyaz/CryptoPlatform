package com.suhoi.adapters.gate;

import com.suhoi.adapter.DiscoveryClient;
import com.suhoi.adapter.ExchangeAdapter;
import com.suhoi.adapter.StreamClient;

/**
 * Композит для Gate: discovery + два потоковых клиента (SPOT и PERP).
 * Использование:
 * var adapter = new GateAdapter();
 * var sub = adapter.spotStream().subscribeBookTicker(List.of("BTC_USDT"), handler);
 * ...
 * sub.close(); adapter.close();
 */
public final class GateAdapter implements ExchangeAdapter, AutoCloseable {
    private final GateDiscoveryClient discovery = new GateDiscoveryClient();
    private final GateSpotStreamClient spot = new GateSpotStreamClient();
    private final GatePerpStreamClient perp = new GatePerpStreamClient();

    @Override
    public String venue() {
        return "GATE";
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
        spot.close();
        perp.close();
    }
}
