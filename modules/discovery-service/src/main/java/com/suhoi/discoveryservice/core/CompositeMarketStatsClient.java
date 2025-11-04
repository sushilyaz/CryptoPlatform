package com.suhoi.discoveryservice.core;

import com.suhoi.discovery.MarketRef;
import com.suhoi.discovery.MarketStats;
import com.suhoi.discovery.MarketStatsClient;
import com.suhoi.discovery.VenueMarketStatsClient;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public final class CompositeMarketStatsClient implements MarketStatsClient, AutoCloseable {
    private final Map<String, VenueMarketStatsClient> delegates;

    public CompositeMarketStatsClient(Collection<VenueMarketStatsClient> clients) {
        this.delegates = clients.stream()
                .collect(Collectors.toUnmodifiableMap(c -> c.venue().toUpperCase(Locale.ROOT), c -> c));
    }

    @Override
    public Map<MarketRef, MarketStats> fetch24hStats(Collection<MarketRef> markets) {
        Map<MarketRef, MarketStats> out = new HashMap<>();
        Map<String, List<MarketRef>> byVenue = markets.stream()
                .collect(Collectors.groupingBy(m -> m.venue().toUpperCase(Locale.ROOT)));

        byVenue.forEach((venue, group) -> {
            VenueMarketStatsClient c = delegates.get(venue);
            if (c == null) {
                log.warn("STATS: no delegate for venue={}, skip {} markets", venue, group.size());
                return;
            }
            try {
                Map<MarketRef, MarketStats> part = c.fetch(group);
                out.putAll(part);
                log.debug("STATS: venue={} fetched={} requested={}", venue, part.size(), group.size());
            } catch (Throwable t) {
                log.warn("STATS: venue={} fetch failed: {}", venue, t.toString());
            }
        });

        return out;
    }

    @Override public void close() {
        delegates.values().forEach(v -> { try { v.close(); } catch (Exception ignore) {} });
    }
}
