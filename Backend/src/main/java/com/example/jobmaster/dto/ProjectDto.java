package com.example.jobmaster.dto;

import java.util.List;

public record ProjectDto(
        String name,
        String description,
        String url,
        List<String> technologies,
        List<String> highlights
) {
}
