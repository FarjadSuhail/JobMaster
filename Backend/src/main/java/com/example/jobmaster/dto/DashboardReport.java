package com.example.jobmaster.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the Dashboard tab shows. Tracking starts at {@code since}
 * (configurable via jobmaster.dashboard.start-date) — applications created
 * before that date are ignored so old data doesn't skew the funnel.
 */
public record DashboardReport(
        LocalDate since,
        Stats stats,
        List<SkillCount> topProfileSkills,
        List<SkillCount> missingSkills,
        AiSummary lastSummary
) {

    public record Stats(
            long tracked,
            long applied,
            long interviewing,
            long offers,
            long rejectedNoInterview,
            long rejectedAfterInterview
    ) {
    }

    /** A skill with how often it shows up (profile mentions, or missing-keyword hits). */
    public record SkillCount(String name, long count) {
    }

    /** The most recent stored AI analysis; regenerated only on explicit request. */
    public record AiSummary(
            Long generationId,
            String text,
            String model,
            BigDecimal costUsd,
            Instant createdAt
    ) {
    }
}
