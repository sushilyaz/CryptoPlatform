package com.suhoi.adapters.bitget;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Локальный ObjectMapper (адаптерная библиотека, без Spring). */
final class BitgetJson {
    static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private BitgetJson() {}
}

