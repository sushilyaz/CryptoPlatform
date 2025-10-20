package com.suhoi.adapters.bitget;

import java.util.regex.Pattern;

/** Утилита нормализации символов (MVP: только *USDT). */
final class BitgetSymbols {
    private static final Pattern USDT = Pattern.compile("USDT$", Pattern.CASE_INSENSITIVE);
    private BitgetSymbols() {}
    static String extractBaseOrThrow(String nativeSymbolUpper) {
        String base = USDT.matcher(nativeSymbolUpper).replaceAll("");
        if (base.equals(nativeSymbolUpper))
            throw new IllegalArgumentException("Only *USDT symbols supported in MVP: " + nativeSymbolUpper);
        return base;
    }
}

