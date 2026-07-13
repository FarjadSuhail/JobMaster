package com.example.jobmaster.dto;

import com.example.jobmaster.domain.ApplicationStatus;

import java.time.LocalDate;

/** Partial update — null fields are left unchanged. */
public record UpdateApplicationRequest(
        ApplicationStatus status,
        String jobTitle,
        String company,
        String location,
        String jobDescription,
        String jobUrl,
        String salary,
        String contactPerson,
        String notes,
        LocalDate appliedAt
) {
}
