package com.suhoi.adapters.dexscreener;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Локальный ObjectMapper (адаптер библиотека, не Spring). */
final class DexscreenerJson {
    static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private DexscreenerJson() {}
}

