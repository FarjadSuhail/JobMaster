package com.example.jobmaster.watcher.dto;

import java.time.Instant;

public record WatchRunDto(
        Long id,
        Long companyId,
        String companyName,
        Instant startedAt,
        long jobsFound,
        long jobsNew,
        String status,
        String error
) {
}
