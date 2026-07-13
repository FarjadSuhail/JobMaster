package com.example.jobmaster.watcher.dto;

import com.example.jobmaster.watcher.domain.AdapterType;

import java.time.Instant;

public record WatchedCompanyDto(
        Long id,
        String name,
        AdapterType adapterType,
        String configJson,
        String includeKeywords,
        String excludeKeywords,
        String locations,
        boolean enabled,
        Instant lastRunAt,
        String lastRunStatus,
        String lastError,
        long newPostings
) {
}
