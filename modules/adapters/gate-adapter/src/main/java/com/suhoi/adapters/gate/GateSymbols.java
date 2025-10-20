package com.suhoi.adapters.gate;


import java.util.regex.Pattern;

/**
 * Утилиты нормализации символов Gate (формат {@code BASE_USDT}).
 */
final class GateSymbols {
    private static final Pattern USDT_SUFFIX = Pattern.compile("_USDT$", Pattern.CASE_INSENSITIVE);

    private GateSymbols() {}

    /**
     * Возвращает BASE из нативного символа формата {@code BASE_USDT}.
     * Бросает {@link IllegalArgumentException}, если суффикс {@code _USDT} отсутствует.
     */
    static String extractBaseOrThrow(String nativeSymbolUpper) {
        String base = USDT_SUFFIX.matcher(nativeSymbolUpper).replaceAll("");
        if (base.equals(nativeSymbolUpper)) {
            throw new IllegalArgumentException("Only *_USDT symbols supported in MVP: " + nativeSymbolUpper);
        }
        return base;
    }

    /** Кол-во значимых десятичных знаков у строкового шага цены/кол-ва вида "0.0010". */
    static int decimalsOf(String decimalStr) {
        if (decimalStr == null) return 0;
        int dot = decimalStr.indexOf('.');
        if (dot < 0) return 0;
        int trailing = 0;
        for (int i = dot + 1; i < decimalStr.length(); i++) {
            char ch = decimalStr.charAt(i);
            if (ch == '0') trailing++;
            else { trailing = decimalStr.length() - i; break; }
        }
        return trailing;
    }
}

