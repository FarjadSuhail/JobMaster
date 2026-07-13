package com.example.jobmaster.dto;

import com.example.jobmaster.domain.AiProvider;

import java.time.Instant;

public record AiModelConfigDto(
        Long id,
        AiProvider provider,
        String model,
        /** Never the real key — masked to the last 4 characters. */
        String apiKeyMasked,
        Double temperature,
        Double topP,
        Integer maxTokens,
        boolean active,
        Instant createdAt
) {
}
