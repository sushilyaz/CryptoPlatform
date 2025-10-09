package com.suhoi.math;

import java.math.BigDecimal;
import java.util.List;

/**
 * Примитивный winsorize по заданным квантилям.
 * Для MVP достаточно; в проде вероятно будет скользящее окно.
 */
public final class Winsorizer {
    private Winsorizer() {}

    public static void winsorize(List<BigDecimal> values, double lowerQ, double upperQ) {
        if (values == null || values.isEmpty()) return;
        values.sort(null);
        int n = values.size();
        int li = (int)Math.floor(lowerQ * (n - 1));
        int ui = (int)Math.ceil(upperQ * (n - 1));
        BigDecimal low = values.get(li);
        BigDecimal up  = values.get(ui);
        for (int i = 0; i < n; i++) {
            BigDecimal v = values.get(i);
            if (v.compareTo(low) < 0) values.set(i, low);
            else if (v.compareTo(up) > 0) values.set(i, up);
        }
    }
}
