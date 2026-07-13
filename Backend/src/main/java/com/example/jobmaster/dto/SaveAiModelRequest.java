package com.example.jobmaster.dto;

import com.example.jobmaster.domain.AiProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Used for both create and update (PATCH: null fields unchanged). On create,
 * a blank apiKey reuses the key of an existing config of the same provider.
 * maxTokens 0 clears the cap back to the provider default.
 */
public record SaveAiModelRequest(
        @NotNull(message = "provider is required") AiProvider provider,
        @NotBlank(message = "model is required") String model,
        String apiKey,
        Double temperature,
        Double topP,
        Integer maxTokens
) {
}
