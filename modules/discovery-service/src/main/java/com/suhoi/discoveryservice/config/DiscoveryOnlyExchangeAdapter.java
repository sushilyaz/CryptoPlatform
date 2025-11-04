package com.suhoi.discoveryservice.config;

import com.suhoi.api.adapter.DiscoveryClient;
import com.suhoi.api.adapter.ExchangeAdapter;
import com.suhoi.api.adapter.StreamClient;

public final class DiscoveryOnlyExchangeAdapter implements ExchangeAdapter {
    private final String venue;
    private final DiscoveryClient discovery;

    public DiscoveryOnlyExchangeAdapter(String venue, DiscoveryClient discovery) {
        this.venue = venue;
        this.discovery = discovery;
    }

    @Override
    public String venue() {
        return venue;
    }

    @Override
    public DiscoveryClient discovery() {
        return discovery;
    }

    @Override
    public StreamClient spotStream() {
        throw new UnsupportedOperationException("not used in discovery-service");
    }

    @Override
    public StreamClient perpStream() {
        throw new UnsupportedOperationException("not used in discovery-service");
    }
}

