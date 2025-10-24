package com.suhoi.discoveryservice.config;

import com.suhoi.adapters.binance.BinanceMarketStatsClient;
import com.suhoi.adapters.bitget.BitgetMarketStatsClient;
import com.suhoi.adapters.bybit.BybitMarketStatsClient;
import com.suhoi.adapters.dexscreener.DexscreenerMarketStatsClient;
import com.suhoi.adapters.gate.GateMarketStatsClient;
import com.suhoi.adapters.mexc.MexcMarketStatsClient;
import com.suhoi.discovery.MarketStatsClient;
import com.suhoi.discovery.VenueMarketStatsClient;
import com.suhoi.discoveryservice.core.CompositeMarketStatsClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class StatsClientsConfig {

    @Bean
    public MarketStatsClient marketStatsClient() {
        List<VenueMarketStatsClient> delegates = List.of(
                new BinanceMarketStatsClient(),
                new BybitMarketStatsClient(),
                new BitgetMarketStatsClient(),
                new GateMarketStatsClient(),
                new MexcMarketStatsClient(),
                new DexscreenerMarketStatsClient()
        );
        return new CompositeMarketStatsClient(delegates);
    }
}

