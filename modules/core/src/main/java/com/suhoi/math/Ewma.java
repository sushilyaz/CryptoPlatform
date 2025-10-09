package com.suhoi.math;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Простой EWMA-аккумулятор для оценки bias.
 */
public final class Ewma {
    private final BigDecimal alpha; // (0,1]
    private BigDecimal value;
    private final MathContext mc;

    public Ewma(BigDecimal alpha, MathContext mc) {
        if (alpha.compareTo(BigDecimal.ZERO) <= 0 || alpha.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("alpha must be in (0,1]");
        }
        this.alpha = alpha;
        this.mc = mc;
    }

    public void reset(BigDecimal initial) {
        this.value = initial;
    }

    public BigDecimal update(BigDecimal x) {
        if (value == null) {
            value = x;
            return value;
        }
        // v = alpha * x + (1 - alpha) * v
        BigDecimal oneMinus = BigDecimal.ONE.subtract(alpha, mc);
        value = alpha.multiply(x, mc).add(oneMinus.multiply(value, mc), mc);
        return value;
    }

    public BigDecimal value() { return value; }
}
