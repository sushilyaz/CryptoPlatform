package com.suhoi.discoveryservice.core;

import com.suhoi.api.adapter.DiscoveryClient;
import com.suhoi.api.adapter.VenueListing;
import com.suhoi.discoveryservice.config.DiscoveryProperties;
import com.suhoi.market.MarketKind;
import com.suhoi.persistence.entity.*;
import com.suhoi.persistence.repo.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscoveryOrchestrator {

    private final List<DiscoveryClient> clients;
    private final DiscoveryProperties props;
    private final QualityScorer scorer;

    private final VenueRepository venueRepo;
    private final InstrumentRepository instrumentRepo;
    private final MarketRepository marketRepo;
    private final MarketQualityRepository mqRepo;

    @Scheduled(fixedDelayString = "${discovery.refreshMs:600000}")
    public void scheduled() {
        runOnce();
    }

    /** Можно дергать руками или через control.reload */
    @Transactional
    public void runOnce() {
        log.info("Discovery: start");

        // 1) собрать листинги со всех адаптеров
        List<VenueListing> listings = new ArrayList<>();
        for (DiscoveryClient c : clients) {
            try {
                listings.addAll(c.listSpotUsdt());
            } catch (Exception e) {
                log.warn("Discovery spot failed for {}: {}", c.getClass().getSimpleName(), e.getMessage());
            }
            try {
                listings.addAll(c.listPerpUsdt());
            } catch (Exception e) {
                log.warn("Discovery perp failed for {}: {}", c.getClass().getSimpleName(), e.getMessage());
            }
        }

        // 2) нормализованный набор “живых” рынков (status == TRADING и т.п.)
        var active = listings.stream()
                .filter(v -> "TRADING".equalsIgnoreCase(v.status()) || "ACTIVE".equalsIgnoreCase(v.status()))
                .collect(Collectors.toList());

        // 3) построить индекс по asset -> виды рынков
        Map<String, EnumSet<MarketKind>> assetKinds = new HashMap<>();
        for (var v : active) {
            var kind = MarketKind.valueOf(v.kind()); // гарантируется адаптером
            assetKinds.computeIfAbsent(v.asset(), k -> EnumSet.noneOf(MarketKind.class)).add(kind);
        }

        // 4) фильтр: каждый PERP должен иметь SPOT или DEX
        List<VenueListing> filtered = active.stream().filter(v -> {
            var kind = MarketKind.valueOf(v.kind());
            if (kind == MarketKind.PERP || kind == MarketKind.FUTURES) {
                var kinds = assetKinds.getOrDefault(v.asset(), EnumSet.noneOf(MarketKind.class));
                return kinds.contains(MarketKind.SPOT) || kinds.contains(MarketKind.DEX);
            }
            return true;
        }).collect(Collectors.toList());

        // 5) анти-шум + quality
        List<Enriched> enriched = filtered.stream()
                .map(Enriched::from)
                .map(e -> e.withQuality(props, scorer))
                .filter(e -> e.passesAntiNoise(props))
                .toList();

        // 6) upsert в БД
        Set<Key> currentKeys = new HashSet<>();
        for (var e : enriched) {
            upsert(e);
            currentKeys.add(Key.of(e.asset, e.venue, e.kind));
        }

        // 7) удалить устаревшие (если включено)
        if (props.isDeleteStale()) {
            var all = marketRepo.findAll();
            for (var m : all) {
                var key = Key.of(m.getAsset(), m.getVenue(), m.getKind());
                if (!currentKeys.contains(key)) {
                    log.info("Deleting stale market: {}", key);
                    mqRepo.findById(m.getMarketId()).ifPresent(mqRepo::delete);
                    marketRepo.delete(m);
                }
            }
        }

        log.info("Discovery: done. active={}, filtered={}, saved={}", active.size(), filtered.size(), enriched.size());
    }

    private record Key(String asset, String venue, MarketKind kind) {
        static Key of(String a, String v, MarketKind k) { return new Key(a, v, k); }
    }

    private void upsert(Enriched e) {
        // venues (обычно сидятся миграцией — оставим на всякий случай)
        venueRepo.findById(e.venue).orElseGet(() ->
                venueRepo.save(Venue.builder().venue(e.venue).name(e.venue).enabled(true).build())
        );

        // instruments
        instrumentRepo.findById(e.asset).orElseGet(() ->
                instrumentRepo.save(Instrument.builder()
                        .asset(e.asset)
                        .baseSymbol(e.base)
                        .quoteSymbol("USDT")
                        .scale(8) // если нужно — подтяни из листинга
                        .build())
        );

        // markets
        var marketOpt = marketRepo.findByAssetAndVenueAndKind(e.asset, e.venue, e.kind);
        Market market = marketOpt.orElseGet(Market::new);
        if (market.getMarketId() == null) {
            market.setAsset(e.asset);
            market.setVenue(e.venue);
            market.setKind(e.kind);
        }
        market.setNativeSymbol(e.nativeSymbol);
        market.setStatus("TRADING");
        market.setMinQty(e.minQty);
        market.setMinNotional(e.minNotional);
        market = marketRepo.save(market);

        // market_quality
        var mq = mqRepo.findById(market.getMarketId()).orElseGet(MarketQuality::new);
        mq.setMarket(market);
        mq.setVol24hUsd(e.vol24hUsd);
        mq.setDepth50Usd(e.depth50Usd);
        mq.setQualityScore(e.qualityScore);
        mq.setLastHeartbeatTs(Instant.now());
        mqRepo.save(mq);
    }

    /** Внутреннее "обогащение" VenueListing метриками и качеством. */
    private record Enriched(
            String venue, String asset, MarketKind kind, String nativeSymbol, String base,
            BigDecimal minQty, BigDecimal minNotional,
            BigDecimal vol24hUsd, BigDecimal depth50Usd,
            BigDecimal dexTvlUsd, Integer dexAgeHours,
            BigDecimal qualityScore
    ) {
        static Enriched from(VenueListing v) {
            return new Enriched(
                    v.venue(),
                    v.asset(),
                    MarketKind.valueOf(v.kind()),
                    v.nativeSymbol(),
                    v.base(),
                    v.minQty(),
                    v.minNotional(),
                    v.vol24hUsd(),     // ожидается от адаптера; если нет — null
                    v.depth50Usd(),    // ожидается от адаптера; если нет — null
                    v.dexTvlUsd(),     // DEX-поля
                    v.dexAgeHours(),
                    null
            );
        }

        Enriched withQuality(DiscoveryProperties p, QualityScorer scorer) {
            var score = scorer.score(kind, vol24hUsd, depth50Usd, dexTvlUsd, dexAgeHours);
            return new Enriched(venue, asset, kind, nativeSymbol, base, minQty, minNotional,
                    vol24hUsd, depth50Usd, dexTvlUsd, dexAgeHours, score);
        }

        boolean passesAntiNoise(DiscoveryProperties p) {
            return switch (kind) {
                case SPOT -> {
                    boolean volOk = num(vol24hUsd) >= p.getMin().getSpot().getVol24hUsd();
                    boolean depthOk = num(depth50Usd) >= p.getMin().getSpot().getDepth50Usd();
                    yield volOk && depthOk;
                }
                case PERP, FUTURES -> {
                    boolean volOk = num(vol24hUsd) >= p.getMin().getPerp().getVol24hUsd();
                    boolean depthOk = num(depth50Usd) >= p.getMin().getPerp().getDepth50Usd();
                    yield volOk && depthOk;
                }
                case DEX -> {
                    boolean tvlOk = num(dexTvlUsd) >= p.getMin().getDex().getTvlUsd();
                    boolean volOk = num(vol24hUsd) >= p.getMin().getDex().getVol24hUsd();
                    boolean ageOk = Optional.ofNullable(dexAgeHours).orElse(0) >= p.getMin().getDex().getAgeHours();
                    yield tvlOk && volOk && ageOk;
                }
            };
        }

        private static long num(BigDecimal v) { return v == null ? 0L : v.longValue(); }
    }
}

