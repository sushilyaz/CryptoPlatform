package com.suhoi.discoveryservice.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "discovery")
public class DiscoveryProperties {
    private long refreshMs = 600_000;
    private boolean deleteStale = true;

    private MinSet min = new MinSet();
    private QualityWeights quality = new QualityWeights();

    @Data public static class MinSet {
        private Spot spot = new Spot();
        private Perp perp = new Perp();
        private Dex dex = new Dex();

        @Data public static class Spot {
            @Min(0) private long vol24hUsd = 200_000;
            @Min(0) private long depth50Usd = 1_000;
        }
        @Data public static class Perp {
            @Min(0) private long vol24hUsd = 5_000_000;
            @Min(0) private long depth50Usd = 2_000;
        }
        @Data public static class Dex {
            @Min(0) private long tvlUsd = 100_000;
            @Min(0) private long vol24hUsd = 20_000;
            @Min(0) private int  ageHours = 24;
        }
    }

    @Data public static class QualityWeights {
        private double depthWeight = 0.5;
        private double volWeight = 0.5;
    }
}

