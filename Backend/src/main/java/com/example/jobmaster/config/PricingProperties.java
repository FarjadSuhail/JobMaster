package com.example.jobmaster.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-model token prices in USD per 1M tokens, keyed by model name.
 * Defaults live in application.properties (jobmaster.pricing.models.*) and can
 * be adjusted there when vendors change their prices.
 */
@Component
@ConfigurationProperties(prefix = "jobmaster.pricing")
@Getter
@Setter
public class PricingProperties {

    private Map<String, ModelPrice> models = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class ModelPrice {
        private double inputPerMtok;
        private double outputPerMtok;
    }
}
