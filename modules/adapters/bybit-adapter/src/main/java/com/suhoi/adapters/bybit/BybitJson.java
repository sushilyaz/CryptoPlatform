package com.suhoi.adapters.bybit;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Локальный ObjectMapper (библиотека адаптера, не Spring). */
final class BybitJson {
    static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private BybitJson() {}
}

