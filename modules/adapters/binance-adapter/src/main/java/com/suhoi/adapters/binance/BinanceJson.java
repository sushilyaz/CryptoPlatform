package com.suhoi.adapters.binance;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Локальный ObjectMapper (адаптер библиотека, не Spring). */
final class BinanceJson {
    static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private BinanceJson() {}
}

