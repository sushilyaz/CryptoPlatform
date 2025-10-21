package com.suhoi.discoveryservice.core;

import com.suhoi.discoveryservice.config.DiscoveryProperties;
import com.suhoi.market.MarketKind;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class QualityScorer {

    private final DiscoveryProperties props;

    public BigDecimal score(MarketKind kind,
                            BigDecimal vol24hUsd,
                            BigDecimal depth50Usd,
                            BigDecimal dexTvlUsd,
                            Integer dexAgeHours) {

        double vol = vol24hUsd == null ? 0d : vol24hUsd.doubleValue();
        double depth = depth50Usd == null ? 0d : depth50Usd.doubleValue();
        double tvl = dexTvlUsd == null ? 0d : dexTvlUsd.doubleValue();
        int age = Optional.ofNullable(dexAgeHours).orElse(0);

        double volRatio, depthRatio;

        switch (kind) {
            case SPOT -> {
                volRatio = safeRatio(vol, props.getMin().getSpot().getVol24hUsd(), 5.0);
                depthRatio = safeRatio(depth, props.getMin().getSpot().getDepth50Usd(), 3.0);
            }
            case PERP, FUTURES -> {
                volRatio = safeRatio(vol, props.getMin().getPerp().getVol24hUsd(), 5.0);
                depthRatio = safeRatio(depth, props.getMin().getPerp().getDepth50Usd(), 3.0);
            }
            case DEX -> {
                // На DEX часто нет depth, оценим суррогатно по TVL/age
                volRatio = safeRatio(vol, props.getMin().getDex().getVol24hUsd(), 5.0);
                double tvlRatio = safeRatio(tvl, props.getMin().getDex().getTvlUsd(), 3.0);
                double ageRatio = Math.min(1.0, (double) age / props.getMin().getDex().getAgeHours());
                depthRatio = 0.7 * tvlRatio + 0.3 * ageRatio; // эвристика
            }
            default -> {
                volRatio = 0; depthRatio = 0;
            }
        }

        double wD = props.getQuality().getDepthWeight();
        double wV = props.getQuality().getVolWeight();
        double score = Math.max(0.0, Math.min(1.0, wD * depthRatio + wV * volRatio));
        return BigDecimal.valueOf(score);
    }

    private static double safeRatio(double value, double minRequired, double capMultiplier) {
        if (minRequired <= 0) return 1.0;
        return Math.max(0.0, Math.min(capMultiplier, value / minRequired)) / capMultiplier;
    }
}

