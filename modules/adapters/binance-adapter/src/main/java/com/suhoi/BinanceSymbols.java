package com.suhoi;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Утилиты нормализации символов Binance (MVP: пары BASE/USDT).
 */
final class BinanceSymbols {
    private static final Pattern USDT_SUFFIX = Pattern.compile("USDT$", Pattern.CASE_INSENSITIVE);

    private BinanceSymbols() { }

    /**
     * Нормализует спотовый/перп символ к нижнему регистру для WS и возвращает как есть для REST.
     */
    static String toWsSymbol(String nativeSymbolUpper) {
        // WS у Binance требует lowercase символы для stream name :contentReference[oaicite:7]{index=7}
        return nativeSymbolUpper.toLowerCase(Locale.ROOT) + "@bookTicker";
    }

    /**
     * Возвращает BASE, если символ оканчивается на USDT; иначе бросает IllegalArgumentException.
     */
    static String extractBaseOrThrow(String nativeSymbolUpper) {
        var base = USDT_SUFFIX.matcher(nativeSymbolUpper).replaceAll("");
        if (base.equals(nativeSymbolUpper)) {
            throw new IllegalArgumentException("Only *USDT symbols supported in MVP: " + nativeSymbolUpper);
        }
        return base;
    }
}

