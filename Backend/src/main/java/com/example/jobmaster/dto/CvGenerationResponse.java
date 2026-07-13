package com.example.jobmaster.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CvGenerationResponse(
        Long id,
        Long applicationId,
        TailoredCv cv,
        String markdown,
        String provider,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        BigDecimal costUsd,
        Instant createdAt
) {
}
