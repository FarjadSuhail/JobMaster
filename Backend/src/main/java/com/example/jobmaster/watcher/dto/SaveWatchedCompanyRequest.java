package com.example.jobmaster.watcher.dto;

import com.example.jobmaster.watcher.domain.AdapterType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Used for create; for update, null fields mean "leave unchanged". */
public record SaveWatchedCompanyRequest(
        @NotBlank(message = "name is required") String name,
        @NotNull(message = "adapterType is required") AdapterType adapterType,
        @NotBlank(message = "configJson is required") String configJson,
        String includeKeywords,
        String excludeKeywords,
        String locations,
        Boolean enabled
) {
}
