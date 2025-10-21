package com.suhoi.discoveryservice.config;


import com.suhoi.adapters.binance.BinanceDiscoveryClient;
import com.suhoi.adapters.bybit.BybitDiscoveryClient;
import com.suhoi.adapters.bitget.BitgetDiscoveryClient;
import com.suhoi.adapters.gate.GateDiscoveryClient;
import com.suhoi.adapters.mexc.MexcDiscoveryClient;
import com.suhoi.adapters.dexscreener.DexscreenerDiscoveryClient;
import com.suhoi.api.adapter.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdaptersFactory {

    @Bean public DiscoveryClient binanceDiscoveryClient() { return new BinanceDiscoveryClient(); }
    @Bean public DiscoveryClient bybitDiscoveryClient()   { return new BybitDiscoveryClient(); }
    @Bean public DiscoveryClient bitgetDiscoveryClient()  { return new BitgetDiscoveryClient(); }
    @Bean public DiscoveryClient gateDiscoveryClient()    { return new GateDiscoveryClient(); }
    @Bean public DiscoveryClient mexcDiscoveryClient()    { return new MexcDiscoveryClient(); }
    @Bean public DiscoveryClient dexsDiscoveryClient()    { return new DexscreenerDiscoveryClient(); }
}

