package com.suhoi.discoveryservice.config;


import com.suhoi.adapters.binance.BinanceDiscoveryClient;
import com.suhoi.adapters.bybit.BybitDiscoveryClient;
import com.suhoi.adapters.bitget.BitgetDiscoveryClient;
import com.suhoi.adapters.gate.GateDiscoveryClient;
import com.suhoi.adapters.mexc.MexcDiscoveryClient;
import com.suhoi.adapters.dexscreener.DexscreenerDiscoveryClient;
import com.suhoi.api.adapter.DiscoveryClient;
import com.suhoi.api.adapter.ExchangeAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AdaptersFactory {

    @Bean public DiscoveryClient binanceDiscoveryClient() { return new BinanceDiscoveryClient(); }
    @Bean public DiscoveryClient bybitDiscoveryClient()   { return new BybitDiscoveryClient(); }
    @Bean public DiscoveryClient bitgetDiscoveryClient()  { return new BitgetDiscoveryClient(); }
    @Bean public DiscoveryClient gateDiscoveryClient()    { return new GateDiscoveryClient(); }
    @Bean public DiscoveryClient mexcDiscoveryClient()    { return new MexcDiscoveryClient(); }
    @Bean public DiscoveryClient dexsDiscoveryClient()    { return new DexscreenerDiscoveryClient(); }

    @Bean
    public List<ExchangeAdapter> exchangeAdapters(
            DiscoveryClient binanceDiscoveryClient,
            DiscoveryClient bybitDiscoveryClient,
            DiscoveryClient bitgetDiscoveryClient,
            DiscoveryClient gateDiscoveryClient,
            DiscoveryClient mexcDiscoveryClient,
            DiscoveryClient dexsDiscoveryClient
    ) {
        return List.of(
                new DiscoveryOnlyExchangeAdapter("BINANCE", binanceDiscoveryClient),
                new DiscoveryOnlyExchangeAdapter("BYBIT", bybitDiscoveryClient),
                new DiscoveryOnlyExchangeAdapter("BITGET", bitgetDiscoveryClient),
                new DiscoveryOnlyExchangeAdapter("GATE",   gateDiscoveryClient),
                new DiscoveryOnlyExchangeAdapter("MEXC",   mexcDiscoveryClient),
                new DiscoveryOnlyExchangeAdapter("DEXSCREENER", dexsDiscoveryClient)
        );
    }
}


