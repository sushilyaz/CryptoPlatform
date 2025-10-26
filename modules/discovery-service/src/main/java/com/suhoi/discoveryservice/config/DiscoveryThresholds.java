// modules/discovery-service/src/main/java/com/suhoi/discoveryservice/config/DiscoveryThresholds.java
package com.suhoi.discoveryservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Пороговые значения discovery. Есть "дефолты" и переопределения по venue. <br>
 * Если для конкретной биржи задан override — берётся он, иначе дефолт. <br>
 */
@Data
@ConfigurationProperties(prefix = "discovery.thresholds")
public class DiscoveryThresholds {

    /** Дефолтный минимальный 24h объём (USD) для SPOT. */
    private BigDecimal minSpotVol24hUsd = new BigDecimal("20000"); // 20k

    /** Дефолтный минимальный 24h объём (USD) для PERP/FUTURES. */
    private BigDecimal minPerpVol24hUsd = new BigDecimal("500000"); // 500k

    /** Дефолтный минимальный 24h объём (USD) для DEX. */
    private BigDecimal minDexVol24hUsd = new BigDecimal("2000"); // 2k

    /** Дефолтный минимальный TVL (USD) для DEX. */
    private BigDecimal minDexTvlUsd = new BigDecimal("10000"); // 10k

    /**
     * Переопределения по venue (ключ — BINANCE/BYBIT/… в любом регистре):
     * discovery.thresholds.overrides.spot.BINANCE=...
     * discovery.thresholds.overrides.perp.BYBIT=...
     * discovery.thresholds.overrides.dexVol.DEXSCREENER=...
     * discovery.thresholds.overrides.dexTvl.DEXSCREENER=...
     */
    private Overrides overrides = new Overrides();

    @Data
    public static class Overrides {
        private Map<String, BigDecimal> spot = new HashMap<>();
        private Map<String, BigDecimal> perp = new HashMap<>();
        private Map<String, BigDecimal> dexVol = new HashMap<>();
        private Map<String, BigDecimal> dexTvl = new HashMap<>();
    }

    public BigDecimal effectiveSpotVol(String venue) {
        return get(overrides.spot, venue, minSpotVol24hUsd);
    }

    public BigDecimal effectivePerpVol(String venue) {
        return get(overrides.perp, venue, minPerpVol24hUsd);
    }

    public BigDecimal effectiveDexVol(String venue) {
        return get(overrides.dexVol, venue, minDexVol24hUsd);
    }

    public BigDecimal effectiveDexTvl(String venue) {
        return get(overrides.dexTvl, venue, minDexTvlUsd);
    }

    private static BigDecimal get(Map<String, BigDecimal> map, String venue, BigDecimal def) {
        if (venue == null) return def;
        return map.getOrDefault(venue.toUpperCase(Locale.ROOT), def);
    }
}
