package com.example.jobmaster.dto;

import com.example.jobmaster.domain.GenerationKind;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GenerationDetail(
        Long id,
        GenerationKind kind,
        Long applicationId,
        String jobTitle,
        String company,
        /** Per-application location override used on this document, if any. */
        String location,
        String jobDescription,
        /** Populated for CV generations. */
        TailoredCv cv,
        /** Rendered Markdown for CV generations. */
        String markdown,
        /** Populated for cover letter generations. */
        String coverLetter,
        String provider,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        BigDecimal costUsd,
        Instant createdAt
) {
}
