package com.example.jobmaster.dto;

import com.example.jobmaster.domain.GenerationKind;

import java.math.BigDecimal;
import java.time.Instant;

public record GenerationSummary(
        Long id,
        GenerationKind kind,
        Long applicationId,
        String jobTitle,
        String company,
        String provider,
        String model,
        BigDecimal costUsd,
        Instant createdAt
) {
}
