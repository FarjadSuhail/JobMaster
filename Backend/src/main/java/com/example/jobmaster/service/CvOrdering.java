package com.example.jobmaster.service;

import com.example.jobmaster.dto.EducationDto;
import com.example.jobmaster.dto.ExperienceDto;
import com.example.jobmaster.dto.TailoredCv;

import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Deterministic safety net behind the prompt: work experience and education
 * must be reverse-chronological (ongoing first, then newest end date) no
 * matter how the model ordered them. Dates are free-text ("Aug 2024", "2023"),
 * so sorting only happens when every entry has at least one parseable date —
 * otherwise the model's order is kept rather than scrambled.
 */
final class CvOrdering {

    private static final DateTimeFormatter MONTH_YEAR =
            DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter FULL_MONTH_YEAR =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    /** Sort key for ongoing entries (blank end date): before everything else. */
    private static final YearMonth ONGOING = YearMonth.of(9999, 12);

    private CvOrdering() {
    }

    static TailoredCv reverseChronological(TailoredCv cv) {
        return new TailoredCv(
                cv.headline(),
                cv.summary(),
                cv.skills(),
                sortByRecency(cv.experiences(), ExperienceDto::startDate, ExperienceDto::endDate),
                cv.projects(),
                sortByRecency(cv.education(), EducationDto::startDate, EducationDto::endDate),
                cv.certifications(),
                cv.keywordsMatched(),
                cv.keywordsAdded(),
                cv.keywordsMissing(),
                cv.atsScore(),
                cv.tailoringNotes());
    }

    private static <T> List<T> sortByRecency(List<T> items,
                                             Function<T, String> startDate,
                                             Function<T, String> endDate) {
        if (items == null || items.size() < 2) {
            return items;
        }
        record Keyed<T>(T item, YearMonth end, YearMonth start) {
        }
        List<Keyed<T>> keyed = new ArrayList<>();
        for (T item : items) {
            YearMonth start = parse(startDate.apply(item));
            YearMonth end = isBlank(endDate.apply(item)) ? ONGOING : parse(endDate.apply(item));
            if (start == null && end == null) {
                return items; // a date we can't read — don't risk scrambling
            }
            keyed.add(new Keyed<>(item, end != null ? end : start, start != null ? start : end));
        }
        return keyed.stream()
                .sorted(Comparator.comparing((Keyed<T> k) -> k.end).reversed()
                        .thenComparing(Comparator.comparing((Keyed<T> k) -> k.start).reversed()))
                .map(Keyed::item)
                .toList();
    }

    /** Accepts "Aug 2024", "August 2024", or a bare year like "2023". */
    private static YearMonth parse(String value) {
        if (isBlank(value)) {
            return null;
        }
        String text = value.trim();
        try {
            return YearMonth.parse(text, MONTH_YEAR);
        } catch (Exception ignored) {
            // fall through
        }
        try {
            return YearMonth.parse(text, FULL_MONTH_YEAR);
        } catch (Exception ignored) {
            // fall through
        }
        try {
            return Year.parse(text).atMonth(6);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank()
                || value.trim().equalsIgnoreCase("present");
    }
}
