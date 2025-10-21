package com.suhoi.adapters.dexscreener;

import com.suhoi.api.adapter.DiscoveryClient;
import com.suhoi.api.adapter.ExchangeAdapter;
import com.suhoi.api.adapter.StreamClient;

/**
 * Композит DexScreener: Discovery + PollStream (DEX).
 */
public final class DexscreenerAdapter implements ExchangeAdapter, AutoCloseable {

    private final DexscreenerDiscoveryClient discovery = new DexscreenerDiscoveryClient();
    private final DexscreenerPollStreamClient stream   = new DexscreenerPollStreamClient();

    @Override public String venue() { return "DEXSCREENER"; }
    @Override public DiscoveryClient discovery() { return discovery; }
    @Override public StreamClient spotStream() { return stream; }
    @Override public StreamClient perpStream() { return stream; } // нет перпов, для совместимости вернем тот же поллер

    @Override public void close() {
        stream.close();
    }
}
