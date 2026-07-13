package com.example.jobmaster.dto;

import java.util.List;
import java.util.Map;

/**
 * Structured output the AI must produce when tailoring the CV to a job
 * description. Spring AI's BeanOutputConverter derives the JSON schema for
 * the prompt from this record.
 */
public record TailoredCv(
        String headline,
        String summary,
        Map<String, List<String>> skills,
        List<ExperienceDto> experiences,
        List<ProjectDto> projects,
        List<EducationDto> education,
        List<String> certifications,
        List<String> keywordsMatched,
        List<String> keywordsAdded,
        /** Important JD requirements the profile honestly cannot cover. */
        List<String> keywordsMissing,
        /** The model's ATS keyword-match estimate for this CV vs. this JD, 0-100. */
        Integer atsScore,
        String tailoringNotes
) {
}
