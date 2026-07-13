package com.example.jobmaster.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * The user's master profile — the single source of truth the AI works from.
 * Skills are grouped by category, e.g. {"Languages": ["Java", "Python"]}.
 */
public record ProfileDto(
        @NotBlank String fullName,
        String email,
        String phone,
        String location,
        Map<String, String> links,
        String headline,
        String summary,
        Map<String, List<String>> skills,
        List<ExperienceDto> experiences,
        List<EducationDto> education,
        List<ProjectDto> projects,
        List<String> certifications,
        List<String> languages
) {

    /** Copy with the location replaced — used for per-application overrides. */
    public ProfileDto withLocation(String newLocation) {
        if (newLocation == null || newLocation.isBlank()) {
            return this;
        }
        return new ProfileDto(fullName, email, phone, newLocation, links, headline, summary,
                skills, experiences, education, projects, certifications, languages);
    }
}
