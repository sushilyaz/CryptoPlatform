package com.suhoi.adapters.bybit;

import java.util.regex.Pattern;

/** Утилиты по символам Bybit (MVP: BASEUSDT). */
final class BybitSymbols {
    private static final Pattern USDT = Pattern.compile("USDT$", Pattern.CASE_INSENSITIVE);
    private BybitSymbols() {}
    static String extractBaseOrThrow(String nativeSymbolUpper) {
        String base = USDT.matcher(nativeSymbolUpper).replaceAll("");
        if (base.equals(nativeSymbolUpper))
            throw new IllegalArgumentException("Only *USDT symbols supported in MVP: " + nativeSymbolUpper);
        return base;
    }
}

