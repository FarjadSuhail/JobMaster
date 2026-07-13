package com.example.jobmaster.service;

import com.example.jobmaster.dto.EducationDto;
import com.example.jobmaster.dto.ExperienceDto;
import com.example.jobmaster.dto.TailoredCv;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CvOrderingTest {

    @Test
    void restoresReverseChronologicalOrderWhenModelReordersByRelevance() {
        // the exact bug: model put Acme Corp (ended 2023) above the newer
        // transition (ended 2024) because it was more JD-relevant
        TailoredCv cv = cvWith(List.of(
                experience("Beta Labs", "Aug 2024", null),
                experience("Acme Corp", "Mar 2021", "Oct 2023"),
                experience("Gamma University", "Oct 2023", "Aug 2024")));

        List<String> companies = CvOrdering.reverseChronological(cv).experiences().stream()
                .map(ExperienceDto::company)
                .toList();

        assertEquals(List.of("Beta Labs", "Gamma University", "Acme Corp"), companies);
    }

    @Test
    void sortsEducationWithBareYearDates() {
        TailoredCv cv = cvWithEducation(List.of(
                education("Institute of Business Administration", "2017", "2021"),
                education("Gamma University", "2023", "2026")));

        List<String> institutions = CvOrdering.reverseChronological(cv).education().stream()
                .map(EducationDto::institution)
                .toList();

        assertEquals(List.of("Gamma University", "Institute of Business Administration"), institutions);
    }

    @Test
    void keepsModelOrderWhenAnyDateIsUnreadable() {
        TailoredCv cv = cvWith(List.of(
                experience("B", "somewhere in the 90s", null),
                experience("A", "Aug 2024", null)));

        List<String> companies = CvOrdering.reverseChronological(cv).experiences().stream()
                .map(ExperienceDto::company)
                .toList();

        assertEquals(List.of("B", "A"), companies);
    }

    private ExperienceDto experience(String company, String start, String end) {
        return new ExperienceDto(company, "Engineer", null, null, start, end, List.of(), List.of());
    }

    private EducationDto education(String institution, String start, String end) {
        return new EducationDto(institution, "Degree", "Field", start, end, null);
    }

    private TailoredCv cvWith(List<ExperienceDto> experiences) {
        return new TailoredCv(null, null, null, experiences, null, null, null,
                null, null, null, null, null);
    }

    private TailoredCv cvWithEducation(List<EducationDto> education) {
        return new TailoredCv(null, null, null, null, null, education, null,
                null, null, null, null, null);
    }
}
