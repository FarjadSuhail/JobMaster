package com.example.jobmaster.dto;

import java.util.List;

public record ExperienceDto(
        String company,
        String title,
        /** Sub-line under the title, e.g. "Energy Informatik team". */
        String team,
        String location,
        String startDate,
        String endDate,
        List<String> bullets,
        List<String> technologies
) {
}
