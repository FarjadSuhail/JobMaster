package com.example.jobmaster.watcher.dto;

import com.example.jobmaster.watcher.domain.JobPostingStatus;

import java.time.Instant;

public record JobPostingDto(
        Long id,
        Long companyId,
        String companyName,
        String title,
        String location,
        String department,
        String url,
        Instant postedAt,
        Instant firstSeenAt,
        JobPostingStatus status,
        Long applicationId,
        boolean closed
) {
}
