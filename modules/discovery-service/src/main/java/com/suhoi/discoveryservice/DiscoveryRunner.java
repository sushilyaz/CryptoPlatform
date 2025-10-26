// modules/discovery-service/src/main/java/com/suhoi/discoveryservice/DiscoveryRunner.java
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
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryRunner {

    private final List<ExchangeAdapter> adapters;     // бины из AdaptersConfig
    private final MarketStatsClient statsClient;      // композитный клиент 24h-объёмов/TVL
    private final DiscoveryThresholds thresholds;     // пороги анти-шума + overrides по venue

    private final VenueRepository venueRepo;
    private final InstrumentRepository instrumentRepo;
    private final MarketRepository marketRepo;

    // Можно перечислить активы для которых хотим детальный INFO-лог (иначе только DEBUG)
    // Пример конфигурации — discovery.log.sampleAssets: BTC,ETH,SOL
    private final Set<String> sampleAssets = new HashSet<>();

    @Scheduled(initialDelay = 5_000, fixedDelayString = "${discovery.refreshMs:300000}")
    @Transactional
    public void refresh() {
        long t0 = System.currentTimeMillis();
        log.info("DISCOVERY: start refresh");

        // 1) собрать листинги SPOT + PERP по всем адаптерам
        List<VenueListing> listings = new ArrayList<>();
        Map<String, int[]> countersByAdapter = new LinkedHashMap<>(); // venue -> [spot, perp, futures, dex]

        for (ExchangeAdapter adapter : adapters) {
            String venue = safeUpper(adapter.venue());
            int spotCnt = 0, perpCnt = 0, futCnt = 0, dexCnt = 0;

            try {
                DiscoveryClient d = adapter.discovery();

                List<VenueListing> spot = safeList(d.listSpotUsdt());
                spotCnt = spot.size();
                listings.addAll(spot);

                List<VenueListing> perp = safeList(d.listPerpUsdt());
                perpCnt = perp.size();
                listings.addAll(perp);

                // если где-то будет DEX discovery — можно добавить сюда и увеличить dexCnt
            } catch (Throwable t) {
                log.warn("DISCOVERY: adapter {} discovery failed: {}", venue, t.toString());
            }

            countersByAdapter.put(venue, new int[]{spotCnt, perpCnt, futCnt, dexCnt});
        }

        if (listings.isEmpty()) {
            log.warn("DISCOVERY: no listings collected; aborting refresh");
            return;
        }

        // краткая сводка по адаптерам
        countersByAdapter.forEach((venue, c) ->
                log.info("DISCOVERY: venue={} listings => spot={}, perp={}, futures={}, dex={}",
                        venue, c[0], c[1], c[2], c[3])
        );

        // 2) составить refs и получить 24h-статы
        List<MarketRef> refs = listings.stream()
                .map(v -> new MarketRef(v.base, v.venue, v.kind, v.nativeSymbol))
                .toList();

        Map<MarketRef, MarketStats> statByRef = Collections.emptyMap();
        try {
            statByRef = statsClient.fetch24hStats(refs);
            log.info("DISCOVERY: fetched stats for {} markets (requested={})", statByRef.size(), refs.size());
        } catch (Throwable t) {
            log.warn("DISCOVERY: statsClient.fetch24hStats failed: {}", t.toString());
        }

        // 3) применить пороги с учётом venue-override'ов
        Set<MarketRef> passed = new HashSet<>();
        Map<String, Boolean> hasSpotOrDexByAsset = new HashMap<>();

        for (VenueListing v : listings) {
            MarketRef ref = new MarketRef(v.base, v.venue, v.kind, v.nativeSymbol);
            MarketStats st = statByRef.get(ref);

            if (st == null) {
                log.debug("FILTER: asset={} venue={} kind={} symbol={} -> drop(no_stats)",
                        v.base, v.venue, v.kind, v.nativeSymbol);
                continue;
            }
            if (st.vol24hUsd() == null) {
                log.debug("FILTER: asset={} venue={} kind={} symbol={} -> drop(no_vol24h)",
                        v.base, v.venue, v.kind, v.nativeSymbol);
                continue;
            }

            String kind = upper(v.kind);
            boolean ok = switch (kind) {
                case "SPOT" -> ge(st.vol24hUsd(), thresholds.effectiveSpotVol(v.venue));
                case "PERP", "FUTURES" -> ge(st.vol24hUsd(), thresholds.effectivePerpVol(v.venue));
                case "DEX" -> {
                    BigDecimal volThr = thresholds.effectiveDexVol(v.venue);
                    BigDecimal tvlThr = thresholds.effectiveDexTvl(v.venue);
                    boolean res = ge(st.vol24hUsd(), volThr)
                            && st.liquidityUsd() != null
                            && ge(st.liquidityUsd(), tvlThr);
                    yield res;
                }
                default -> false;
            };

            if (ok) {
                passed.add(ref);
                if ("SPOT".equals(kind) || "DEX".equals(kind)) {
                    hasSpotOrDexByAsset.put(v.base, true);
                }
                log.debug("FILTER: asset={} venue={} kind={} symbol={} -> PASS (vol24h={}, liq={})",
                        v.base, v.venue, v.kind, v.nativeSymbol, st.vol24hUsd(), st.liquidityUsd());
            } else {
                log.debug("FILTER: asset={} venue={} kind={} symbol={} -> DROP (vol24h={}, liq={}) thr[spot={},perp={},dexVol={},dexTvl={}]",
                        v.base, v.venue, v.kind, v.nativeSymbol, st.vol24hUsd(), st.liquidityUsd(),
                        thresholds.effectiveSpotVol(v.venue), thresholds.effectivePerpVol(v.venue),
                        thresholds.effectiveDexVol(v.venue), thresholds.effectiveDexTvl(v.venue));
            }
        }

        // 4) убрать деривативы без «якоря» (SPOT/DEX) по активу
        int beforeAnchor = passed.size();
        passed.removeIf(m ->
                (eqi(m.kind(), "PERP") || eqi(m.kind(), "FUTURES"))
                        && !Boolean.TRUE.equals(hasSpotOrDexByAsset.get(m.asset()))
        );
        int removedByAnchor = beforeAnchor - passed.size();
        if (removedByAnchor > 0) {
            log.info("DISCOVERY: removed {} derivative markets without anchor (spot/dex)", removedByAnchor);
        }

        // 4.1) доп. шаг — если по (asset, venue, kind) несколько записей, оставить лучшую по vol (DEX: vol, затем TVL)
        Map<String, MarketRef> bestByAvk = new HashMap<>();
        for (MarketRef m : passed) {
            String k = (upper(m.asset()) + "|" + upper(m.venue()) + "|" + upper(m.kind()));
            MarketRef cur = bestByAvk.get(k);
            MarketStats st = statByRef.get(m);
            if (cur == null) {
                bestByAvk.put(k, m);
            } else {
                MarketStats stCur = statByRef.get(cur);
                int cmp = st.vol24hUsd().compareTo(stCur.vol24hUsd());
                if (cmp > 0 || (cmp == 0 && "DEX".equalsIgnoreCase(m.kind())
                        && st.liquidityUsd() != null && stCur.liquidityUsd() != null
                        && st.liquidityUsd().compareTo(stCur.liquidityUsd()) > 0)) {
                    bestByAvk.put(k, m);
                }
            }
        }
        Set<MarketRef> finalEnabled = new HashSet<>(bestByAvk.values());
        log.info("DISCOVERY: final enabled markets = {} (after best-of and anchor rules)", finalEnabled.size());

        // 4.2) сводки по активам (легко увидеть «для BTC спот на X, деривативы на Y, dex Z»)
        summarizeByAsset(listings, finalEnabled);

        // 5) сохранить в БД только прошедшие рынки
        PersistStats ps = persist(listings, finalEnabled);
        log.info("DISCOVERY: persist summary -> venues[created={}] instruments[created={}] markets[upserted={}]",
                ps.venuesCreated, ps.instrumentsCreated, ps.marketsUpserted);

        long dt = System.currentTimeMillis() - t0;
        log.info("DISCOVERY: refresh done in {} ms", dt);
    }

    // --------- persist ---------

    private PersistStats persist(List<VenueListing> listings, Set<MarketRef> enabledRefs) {
        int venuesCreated = 0;
        int instrumentsCreated = 0;
        int marketsUpserted = 0;

        // a) venues
        Set<String> venues = listings.stream().map(v -> v.venue).collect(Collectors.toSet());
        for (String code : venues) {
            boolean exists = venueRepo.existsById(code);
            if (!exists) {
                var venue = new Venue();
                venue.setVenue(code);
                venue.setName(code);
                venue.setEnabled(true);
                venueRepo.save(venue);
                venuesCreated++;
                log.debug("PERSIST: venue created {}", code);
            }
        }

        // b) instruments
        Set<String> assets = listings.stream().map(v -> v.base).collect(Collectors.toSet());
        for (String asset : assets) {
            boolean exists = instrumentRepo.existsById(asset);
            if (!exists) {
                var instr = new Instrument();
                instr.setAsset(asset);
                instr.setBaseSymbol(asset);
                instr.setQuoteSymbol("USDT");
                instr.setScale(Math.max(0, guessScale(listings, asset)));
                instrumentRepo.save(instr);
                instrumentsCreated++;
                log.debug("PERSIST: instrument created {}", asset);
            }
        }

        // c) markets — только прошедшие
        for (VenueListing v : listings) {
            MarketRef ref = new MarketRef(v.base, v.venue, v.kind, v.nativeSymbol);
            if (!enabledRefs.contains(ref)) continue;

            var existing = marketRepo.findByAssetAndVenueAndKind(
                    v.base, v.venue, MarketKind.valueOf(upper(v.kind))
            );

            Market m = existing.orElseGet(() -> {
                var nm = new Market();
                nm.setAsset(v.base);
                nm.setVenue(v.venue);
                nm.setKind(MarketKind.valueOf(upper(v.kind)));
                return nm;
            });

            m.setNativeSymbol(v.nativeSymbol);
            m.setStatus(v.status);

            marketRepo.save(m);
            marketsUpserted++;
            log.debug("PERSIST: market upsert asset={} venue={} kind={} symbol={}",
                    v.base, v.venue, v.kind, v.nativeSymbol);
        }

        return new PersistStats(venuesCreated, instrumentsCreated, marketsUpserted);
    }

    private record PersistStats(int venuesCreated, int instrumentsCreated, int marketsUpserted) {}

    // --------- утилиты/сводки/логика ---------

    private void summarizeByAsset(List<VenueListing> listings, Set<MarketRef> enabled) {
        // чтобы понимать «что получилось» не заглядывая в БД
        Map<String, List<VenueListing>> byAsset = listings.stream()
                .collect(Collectors.groupingBy(v -> upper(v.base)));

        for (var e : byAsset.entrySet()) {
            String asset = e.getKey();

            // наборы до фильтров
            Set<String> spotVenues = e.getValue().stream()
                    .filter(v -> "SPOT".equalsIgnoreCase(v.kind))
                    .map(v -> upper(v.venue)).collect(Collectors.toCollection(TreeSet::new));
            Set<String> perpVenues = e.getValue().stream()
                    .filter(v -> "PERP".equalsIgnoreCase(v.kind) || "FUTURES".equalsIgnoreCase(v.kind))
                    .map(v -> upper(v.venue)).collect(Collectors.toCollection(TreeSet::new));
            Set<String> dexVenues = e.getValue().stream()
                    .filter(v -> "DEX".equalsIgnoreCase(v.kind))
                    .map(v -> upper(v.venue)).collect(Collectors.toCollection(TreeSet::new));

            // наборы «после»
            Set<String> spotEnabled = new TreeSet<>();
            Set<String> perpEnabled = new TreeSet<>();
            Set<String> dexEnabled = new TreeSet<>();

            for (MarketRef m : enabled) {
                if (!upper(m.asset()).equals(asset)) continue;
                switch (upper(m.kind())) {
                    case "SPOT" -> spotEnabled.add(upper(m.venue()));
                    case "PERP", "FUTURES" -> perpEnabled.add(upper(m.venue()));
                    case "DEX" -> dexEnabled.add(upper(m.venue()));
                }
            }

            boolean sample = sampleAssets.contains(asset);
            if (sample) {
                // подробный INFO для интересующих активов
                log.info("SUMMARY[{}]: SPOT all={} enabled={} | PERP all={} enabled={} | DEX all={} enabled={}",
                        asset, spotVenues, spotEnabled, perpVenues, perpEnabled, dexVenues, dexEnabled);
            } else {
                // сжатый INFO и подробности в DEBUG
                log.info("SUMMARY[{}]: SPOT={}→{}, PERP={}→{}, DEX={}→{}",
                        asset, spotVenues.size(), spotEnabled.size(),
                        perpVenues.size(), perpEnabled.size(),
                        dexVenues.size(), dexEnabled.size());
                log.debug("SUMMARY_DETAILS[{}]: SPOT all={} enabled={}", asset, spotVenues, spotEnabled);
                log.debug("SUMMARY_DETAILS[{}]: PERP all={} enabled={}", asset, perpVenues, perpEnabled);
                log.debug("SUMMARY_DETAILS[{}]: DEX  all={} enabled={}", asset, dexVenues, dexEnabled);
            }
        }
    }

    private static boolean ge(BigDecimal x, BigDecimal y) { return x.compareTo(y) >= 0; }
    private static boolean eqi(String a, String b) { return a != null && a.equalsIgnoreCase(b); }
    private static String upper(String s) { return s == null ? null : s.toUpperCase(Locale.ROOT); }
    private static String safeUpper(String s) { return s == null ? "UNKNOWN" : s.toUpperCase(Locale.ROOT); }
    private static <T> List<T> safeList(List<T> l) { return l == null ? List.of() : l; }

    private static int guessScale(List<VenueListing> listings, String asset) {
        return listings.stream()
                .filter(v -> asset.equalsIgnoreCase(v.base))
                .map(v -> v.priceScale)
                .filter(ps -> ps > 0 && ps < 18)
                .min(Integer::compareTo)
                .orElse(8);
    }
}
