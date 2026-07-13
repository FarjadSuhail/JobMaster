package com.example.jobmaster.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CoverLetterResponse(
        Long id,
        Long applicationId,
        String coverLetter,
        String provider,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        BigDecimal costUsd,
        Instant createdAt
) {
}
