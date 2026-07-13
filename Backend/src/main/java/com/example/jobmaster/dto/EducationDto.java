package com.example.jobmaster.dto;

public record EducationDto(
        String institution,
        String degree,
        String field,
        String startDate,
        String endDate,
        String details
) {
}
