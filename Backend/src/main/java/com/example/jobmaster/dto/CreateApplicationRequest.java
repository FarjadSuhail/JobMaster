package com.example.jobmaster.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateApplicationRequest(
        @NotBlank(message = "jobTitle is required") String jobTitle,
        String company,
        String location,
        String jobDescription,
        String jobUrl,
        String salary,
        String contactPerson,
        String notes
) {
}
