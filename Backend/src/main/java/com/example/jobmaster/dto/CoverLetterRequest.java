package com.example.jobmaster.dto;

import jakarta.validation.constraints.NotBlank;

public record CoverLetterRequest(
        @NotBlank(message = "jobDescription is required") String jobDescription,
        /** Attach to this application; falls back to the CV's application, then a new one. */
        Long applicationId,
        String jobTitle,
        String company,
        /** Overrides the profile location on this letter, e.g. "Berlin, Germany". */
        String location,
        /** e.g. "professional", "enthusiastic", "concise". Defaults to professional. */
        String tone,
        /** Optional id of a previous CV generation to base the letter on. */
        Long cvGenerationId,
        String extraInstructions
) {
}
