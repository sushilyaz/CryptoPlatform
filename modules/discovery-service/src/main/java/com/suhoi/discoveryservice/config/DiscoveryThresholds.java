package com.suhoi.discoveryservice.config;


import org.springframework.boot.context.properties.ConfigurationProperties;
import java.math.BigDecimal;

/** Анти-шум пороги по 24h объёмам (USD). */
@ConfigurationProperties(prefix = "discovery.thresholds")
public record DiscoveryThresholds(
        BigDecimal minSpotVol24hUsd,
        BigDecimal minPerpVol24hUsd,
        BigDecimal minDexVol24hUsd,
        BigDecimal minDexTvlUsd
) {
    public DiscoveryThresholds {
        // дефолты на случай, если не задали в ENV
        if (minSpotVol24hUsd == null) minSpotVol24hUsd = new BigDecimal("200000"); // 200k
        if (minPerpVol24hUsd == null) minPerpVol24hUsd = new BigDecimal("5000000"); // 5M
        if (minDexVol24hUsd == null)  minDexVol24hUsd  = new BigDecimal("20000"); // 20k
        if (minDexTvlUsd == null)     minDexTvlUsd     = new BigDecimal("100000"); // 100k
    }
}

