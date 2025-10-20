package com.suhoi.adapters.gate;

import com.suhoi.api.adapter.VenueListing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Простые live-тесты discovery: проверяем, что Gate отдаёт пары/контракты USDT.
 * В CI таких тестов обычно помечают как @Tag("integration") и/или выносят в отдельный профиль.
 */
class GateDiscoveryClientTest {

    @Test
    void spot_usdt_pairs_not_empty_and_contains_btc() {
        var d = new GateDiscoveryClient();
        List<VenueListing> list = d.listSpotUsdt();
        assertNotNull(list);
        assertTrue(list.size() > 0);
        assertTrue(list.stream().anyMatch(v -> v.nativeSymbol.equalsIgnoreCase("BTC_USDT")));
    }

    @Test
    void perp_usdt_contracts_not_empty_and_contains_btc() {
        var d = new GateDiscoveryClient();
        List<VenueListing> list = d.listPerpUsdt();
        assertNotNull(list);
        assertTrue(list.size() > 0);
        assertTrue(list.stream().anyMatch(v -> v.nativeSymbol.equalsIgnoreCase("BTC_USDT")));
    }
}

