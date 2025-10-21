package com.suhoi.adapters.dexscreener;

import java.util.Locale;

/**
 * Утилиты по символам/идентификаторам DexScreener.
 * nativeSymbol формата: {@code <chainId>:<pairAddress>} — например: {@code solana:JUP...CN}
 */
final class DexscreenerSymbols {
    private DexscreenerSymbols() {}

    static String nativeOf(String chainId, String pairAddress) {
        return chainId + ":" + pairAddress;
    }

    /** Из nativeSymbol вытаскивает chainId. */
    static String chainFromNative(String nativeSymbol) {
        int idx = nativeSymbol.indexOf(':');
        if (idx <= 0) throw new IllegalArgumentException("Invalid nativeSymbol: " + nativeSymbol);
        return nativeSymbol.substring(0, idx);
    }

    /** Из nativeSymbol вытаскивает pairAddress. */
    static String pairFromNative(String nativeSymbol) {
        int idx = nativeSymbol.indexOf(':');
        if (idx <= 0 || idx >= nativeSymbol.length() - 1)
            throw new IllegalArgumentException("Invalid nativeSymbol: " + nativeSymbol);
        return nativeSymbol.substring(idx + 1);
    }

    /** В DEX-мире asset = baseToken.symbol (верхний регистр). */
    static String assetFromBaseSymbol(String baseSymbol) {
        return baseSymbol == null ? "" : baseSymbol.toUpperCase(Locale.ROOT);
    }

    /** Кол-во значащих десятичных в строковом числе (для эвристики priceScale). */
    static int decimalsOf(String decimalStr) {
        if (decimalStr == null) return 0;
        int idx = decimalStr.indexOf('.');
        if (idx < 0) return 0;
        int trailing = 0;
        for (int i = idx + 1; i < decimalStr.length(); i++) {
            char c = decimalStr.charAt(i);
            if (c == '0') trailing++;
            else { trailing = decimalStr.length() - i; break; }
        }
        return trailing;
    }
}
