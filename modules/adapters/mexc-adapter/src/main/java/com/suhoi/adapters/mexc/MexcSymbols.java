package com.suhoi.adapters.mexc;

import java.util.Locale;
import java.util.regex.Pattern;

/** Утилиты нормализации нативных символов MEXC. */
final class MexcSymbols {
    private static final Pattern USDT_SPOT = Pattern.compile("USDT$", Pattern.CASE_INSENSITIVE);

    private MexcSymbols() {}

    /** Для SPOT: "BTCUSDT" → asset "BTC" (только USDT пары в MVP). */
    static String extractSpotBaseOrThrow(String spotNativeUpper) {
        var base = USDT_SPOT.matcher(spotNativeUpper).replaceAll("");
        if (base.equals(spotNativeUpper)) {
            throw new IllegalArgumentException("Only *USDT spot symbols supported: " + spotNativeUpper);
        }
        return base;
    }

    /** Для PERP: "BTC_USDT" → asset "BTC". */
    static String extractPerpBaseOrThrow(String perpNative) {
        int idx = perpNative.indexOf('_');
        if (idx <= 0 || idx == perpNative.length() - 1) {
            throw new IllegalArgumentException("Unexpected perp symbol (expected BASE_USDT): " + perpNative);
        }
        String quote = perpNative.substring(idx + 1).toUpperCase(Locale.ROOT);
        if (!"USDT".equals(quote)) {
            throw new IllegalArgumentException("Only *USDT perps supported in MVP: " + perpNative);
        }
        return perpNative.substring(0, idx).toUpperCase(Locale.ROOT);
    }
}
