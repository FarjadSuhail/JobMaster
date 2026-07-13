package com.example.jobmaster.dto;

import com.example.jobmaster.domain.ApplicationStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ApplicationView(
        Long id,
        ApplicationStatus status,
        String jobTitle,
        String company,
        String location,
        String jobDescription,
        String jobUrl,
        String salary,
        String contactPerson,
        String notes,
        LocalDate appliedAt,
        Instant createdAt,
        Instant updatedAt,
        Instant statusChangedAt,
        List<GenerationSummary> generations
) {
}
