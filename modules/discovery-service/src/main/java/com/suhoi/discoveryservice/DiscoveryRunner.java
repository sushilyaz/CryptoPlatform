// modules/discovery-service/src/main/java/com/suhoi/discovery/DiscoveryRunner.java
package com.suhoi.discoveryservice;

import com.suhoi.api.adapter.DiscoveryClient;
import com.suhoi.api.adapter.ExchangeAdapter;
import com.suhoi.api.adapter.VenueListing;
import com.suhoi.discovery.MarketRef;
import com.suhoi.discovery.MarketStats;
import com.suhoi.discovery.MarketStatsClient;
import com.suhoi.discoveryservice.config.DiscoveryThresholds;
import com.suhoi.market.MarketKind;
import com.suhoi.persistence.entity.Instrument;
import com.suhoi.persistence.entity.Market;
import com.suhoi.persistence.entity.Venue;
import com.suhoi.persistence.repo.InstrumentRepository;
import com.suhoi.persistence.repo.MarketRepository;
import com.suhoi.persistence.repo.VenueRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Периодическая процедура discovery:
 *
 * <ol>
 *   <li>Собрать листинги SPOT/PERP у всех {@link ExchangeAdapter}</li>
 *   <li>Запросить 24h-объёмы/TVL через {@link MarketStatsClient}</li>
 *   <li>Отфильтровать рынки по порогам {@link DiscoveryThresholds}</li>
 *   <li>Гарантировать «якорь» для PERP: у актива должен быть SPOT или валидный DEX</li>
 *   <li>Сохранить валидные {@code Venue}/{@code Instrument}/{@code Market} в БД</li>
 * </ol>
 *
 * NB: мы сохраняем ТОЛЬКО прошедшие рынки (нет «enabled» в Market — так и задумано).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryRunner {

    private final List<ExchangeAdapter> adapters; // бины из AdaptersConfig
    private final MarketStatsClient statsClient;  // композитный клиент 24h-объёмов
    private final DiscoveryThresholds thresholds; // пороги анти-шума

    private final VenueRepository venueRepo;
    private final InstrumentRepository instrumentRepo;
    private final MarketRepository marketRepo;

    /**
     * Авто-обновление каждые N минут (по умолчанию 5 мин).
     * Можно дополнительно сделать REST-эндпойнт, который дергает этот метод вручную.
     */
    @Scheduled(initialDelay = 5_000, fixedDelayString = "${discovery.refreshMs:300000}")
    @Transactional
    public void refresh() {
        long tStart = System.currentTimeMillis();
        log.info("Discovery refresh: start");

        // 1) собрать листинги SPOT + PERP
        List<VenueListing> listings = new ArrayList<>();
        for (ExchangeAdapter a : adapters) {
            DiscoveryClient d = a.discovery();
            safeAddAll(listings, d.listSpotUsdt());
            safeAddAll(listings, d.listPerpUsdt());
        }
        if (listings.isEmpty()) {
            log.warn("Discovery refresh: no listings from adapters");
            return;
        }

        // 2) подготовить MarketRef → стянуть 24h-метрики
        List<MarketRef> refs = listings.stream()
                .map(v -> new MarketRef(v.base, v.venue, v.kind, v.nativeSymbol))
                .toList();

        Map<MarketRef, MarketStats> statByRef = statsClient.fetch24hStats(refs);

        // 3) применить пороги
        Set<MarketRef> passed = new HashSet<>();
        Map<String, Boolean> hasSpotOrDexByAsset = new HashMap<>();

        for (VenueListing v : listings) {
            MarketRef mref = new MarketRef(v.base, v.venue, v.kind, v.nativeSymbol);
            MarketStats st = statByRef.get(mref);
            if (st == null || st.vol24hUsd() == null) continue;

            String kind = v.kind.toUpperCase(Locale.ROOT);
            switch (kind) {
                case "SPOT" -> {
                    if (ge(st.vol24hUsd(), thresholds.minSpotVol24hUsd())) {
                        passed.add(mref);
                        hasSpotOrDexByAsset.put(v.base, true);
                    }
                }
                case "PERP", "FUTURES" -> {
                    if (ge(st.vol24hUsd(), thresholds.minPerpVol24hUsd())) {
                        passed.add(mref);
                    }
                }
                case "DEX" -> {
                    if (ge(st.vol24hUsd(), thresholds.minDexVol24hUsd())
                            && st.liquidityUsd() != null
                            && ge(st.liquidityUsd(), thresholds.minDexTvlUsd())) {
                        passed.add(mref);
                        hasSpotOrDexByAsset.put(v.base, true);
                    }
                }
                default -> { /* ignore */ }
            }
        }

        // 4) удерживаем только PERP/FUTURES, у которых есть «якорь» SPOT или валидный DEX по этому asset
        passed.removeIf(m ->
                (eqi(m.kind(), "PERP") || eqi(m.kind(), "FUTURES"))
                        && !Boolean.TRUE.equals(hasSpotOrDexByAsset.get(m.asset()))
        );

        Map<String, MarketRef> bestByAvk = new HashMap<>();
        for (MarketRef m : passed) {
            String key = (m.asset() + "|" + m.venue() + "|" + m.kind()).toUpperCase(Locale.ROOT);
            MarketStats st = statByRef.get(m);
            if (st == null) continue;

            MarketRef curBest = bestByAvk.get(key);
            if (curBest == null) {
                bestByAvk.put(key, m);
                continue;
            }
            MarketStats stBest = statByRef.get(curBest);
            // сравниваем vol; если равны и это DEX — сравним liquidity
            int cmp = st.vol24hUsd().compareTo(stBest.vol24hUsd());
            if (cmp > 0 || (cmp == 0 && m.kind().equalsIgnoreCase("DEX")
                    && st.liquidityUsd() != null && stBest.liquidityUsd() != null
                    && st.liquidityUsd().compareTo(stBest.liquidityUsd()) > 0)) {
                bestByAvk.put(key, m);
            }
        }
        Set<MarketRef> finalEnabled = new HashSet<>(bestByAvk.values());
        // 5) сохранить в БД (venues, instruments, markets)
        persist(listings, finalEnabled);

        long dt = System.currentTimeMillis() - tStart;
        log.info("Discovery refresh: done in {} ms (listings={}, passed={})", dt, listings.size(), passed.size());
    }

    // -------------------- persistence под твои сущности --------------------

    private void persist(List<VenueListing> listings, Set<MarketRef> enabledRefs) {
        // a) venues
        Set<String> venues = listings.stream().map(v -> v.venue).collect(Collectors.toSet());
        for (String code : venues) {
            venueRepo.findById(code).orElseGet(() -> {
                var venue = new Venue();
                venue.setVenue(code);     // ID = venue
                venue.setName(code);      // простое читаемое имя (можешь заменить на красивое)
                venue.setEnabled(true);
                return venueRepo.save(venue);
            });
        }

        // b) instruments
        Set<String> assets = listings.stream().map(v -> v.base).collect(Collectors.toSet());
        for (String asset : assets) {
            instrumentRepo.findById(asset).orElseGet(() -> {
                var instr = new Instrument();
                instr.setAsset(asset);          // ID
                instr.setBaseSymbol(asset);
                instr.setQuoteSymbol("USDT");
                instr.setScale(Math.max(0, guessScale(listings, asset))); // грубая оценка
                return instrumentRepo.save(instr);
            });
        }

        // c) markets — сохраняем ТОЛЬКО прошедшие
        Map<String, List<VenueListing>> byKey = listings.stream()
                .collect(Collectors.groupingBy(v -> key(v.base, v.venue, v.kind)));

        for (VenueListing v : listings) {
            MarketRef ref = new MarketRef(v.base, v.venue, v.kind, v.nativeSymbol);
            if (!enabledRefs.contains(ref)) continue; // НЕ хранить «пыль»

            var existing = marketRepo.findByAssetAndVenueAndKind(
                    v.base, v.venue, MarketKind.valueOf(v.kind.toUpperCase(Locale.ROOT))
            );

            Market m = existing.orElseGet(() -> {
                var nm = new Market();
                nm.setAsset(v.base);
                nm.setVenue(v.venue);
                nm.setKind(MarketKind.valueOf(v.kind.toUpperCase(Locale.ROOT)));
                return nm;
            });

            // обновляем витрину полей (что у нас есть из листинга)
            m.setNativeSymbol(v.nativeSymbol);
            m.setStatus(v.status);
            // minQty/minNotional в VenueListing нет — оставляем как есть/NULL

            marketRepo.save(m);
        }
    }

    // -------------------- утилиты --------------------

    private static boolean ge(BigDecimal x, BigDecimal y) { return x.compareTo(y) >= 0; }
    private static boolean eqi(String a, String b) { return a != null && a.equalsIgnoreCase(b); }
    private static void safeAddAll(List<VenueListing> dst, List<VenueListing> src) { if (src != null) dst.addAll(src); }
    private static String key(String asset, String venue, String kind) { return asset + "|" + venue + "|" + kind.toUpperCase(Locale.ROOT); }

    /**
     * Очень грубая оценка scale по priceScale листингов этого актива (если есть),
     * иначе дефолт 8. Этот scale влияет только на визуализацию/округления.
     */
    private static int guessScale(List<VenueListing> listings, String asset) {
        return listings.stream()
                .filter(v -> asset.equals(v.base))
                .map(v -> v.priceScale)
                .filter(ps -> ps > 0 && ps < 18)
                .min(Integer::compareTo)
                .orElse(8);
    }
}
