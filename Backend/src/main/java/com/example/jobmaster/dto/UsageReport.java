package com.example.jobmaster.dto;

import java.math.BigDecimal;
import java.util.List;

public record UsageReport(
        long totalGenerations,
        long totalPromptTokens,
        long totalCompletionTokens,
        BigDecimal totalCostUsd,
        List<MonthUsage> months
) {

    /** One calendar month, newest first. Month format: "2026-07". */
    public record MonthUsage(
            String month,
            long generations,
            long promptTokens,
            long completionTokens,
            BigDecimal costUsd,
            List<ModelUsage> byModel
    ) {
    }

    public record ModelUsage(
            String provider,
            String model,
            long generations,
            long promptTokens,
            long completionTokens,
            BigDecimal costUsd
    ) {
    }
}
