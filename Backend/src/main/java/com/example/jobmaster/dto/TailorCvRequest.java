package com.example.jobmaster.dto;

import jakarta.validation.constraints.NotBlank;

public record TailorCvRequest(
        @NotBlank(message = "jobDescription is required") String jobDescription,
        /** Attach to this application; when null, a new one is created automatically. */
        Long applicationId,
        String jobTitle,
        String company,
        /** Overrides the profile location on this CV, e.g. "Berlin, Germany". */
        String location,
        String extraInstructions
) {
}
