package com.example.jobmaster.service;

import com.example.jobmaster.config.PricingProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Map;

@Service
public class CostService {

    private static final BigDecimal MTOK = BigDecimal.valueOf(1_000_000);

    private final PricingProperties pricing;

    public CostService(PricingProperties pricing) {
        this.pricing = pricing;
    }

    /** USD cost for one generation; null when tokens or the model's price are unknown. */
    public BigDecimal cost(String model, Integer promptTokens, Integer completionTokens) {
        PricingProperties.ModelPrice price = resolvePrice(model);
        if (price == null || promptTokens == null || completionTokens == null) {
            return null;
        }
        BigDecimal input = BigDecimal.valueOf(promptTokens)
                .multiply(BigDecimal.valueOf(price.getInputPerMtok()));
        BigDecimal output = BigDecimal.valueOf(completionTokens)
                .multiply(BigDecimal.valueOf(price.getOutputPerMtok()));
        return input.add(output).divide(MTOK, 6, RoundingMode.HALF_UP);
    }

    /**
     * Exact match first, then longest configured prefix — so a dated id like
     * "gpt-5.4-2026-03-05" picks up the "gpt-5.4" price, and "gpt-5.4-nano-..."
     * prefers "gpt-5.4-nano" over "gpt-5.4".
     */
    private PricingProperties.ModelPrice resolvePrice(String model) {
        if (model == null) {
            return null;
        }
        PricingProperties.ModelPrice exact = pricing.getModels().get(model);
        if (exact != null) {
            return exact;
        }
        return pricing.getModels().entrySet().stream()
                .filter(e -> model.startsWith(e.getKey()))
                .max(Comparator.comparingInt(e -> e.getKey().length()))
                .map(Map.Entry::getValue)
                .orElse(null);
    }
}
