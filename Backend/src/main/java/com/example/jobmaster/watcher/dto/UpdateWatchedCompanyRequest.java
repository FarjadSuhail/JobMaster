package com.example.jobmaster.watcher.dto;

import com.example.jobmaster.watcher.domain.AdapterType;

/** Partial update — null fields are left unchanged. */
public record UpdateWatchedCompanyRequest(
        String name,
        AdapterType adapterType,
        String configJson,
        String includeKeywords,
        String excludeKeywords,
        String locations,
        Boolean enabled
) {
}
