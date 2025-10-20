package com.suhoi.adapters.mexc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Локальный ObjectMapper для адаптера MEXC. */
final class MexcJson {
    static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private MexcJson() {}
}
