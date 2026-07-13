package com.example.jobmaster.service;

import com.example.jobmaster.dto.EducationDto;
import com.example.jobmaster.dto.ExperienceDto;
import com.example.jobmaster.dto.ProfileDto;
import com.example.jobmaster.dto.ProjectDto;
import com.example.jobmaster.dto.TailoredCv;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders a tailored CV as Markdown — a display/download-friendly format the
 * frontend can show directly or convert to PDF later.
 */
@Component
public class CvMarkdownRenderer {

    public String render(ProfileDto profile, TailoredCv cv) {
        StringBuilder md = new StringBuilder();

        md.append("# ").append(profile.fullName()).append('\n');
        String headline = firstNonBlank(cv.headline(), profile.headline());
        if (headline != null) {
            md.append("**").append(headline).append("**\n");
        }
        md.append('\n').append(contactLine(profile)).append('\n');

        if (notBlank(cv.summary())) {
            md.append("\n## Summary\n\n").append(cv.summary().trim()).append('\n');
        }

        if (cv.skills() != null && !cv.skills().isEmpty()) {
            md.append("\n## Skills\n\n");
            for (Map.Entry<String, List<String>> group : cv.skills().entrySet()) {
                md.append("- **").append(group.getKey()).append(":** ")
                        .append(String.join(", ", group.getValue())).append('\n');
            }
        }

        if (cv.experiences() != null && !cv.experiences().isEmpty()) {
            md.append("\n## Experience\n");
            for (ExperienceDto exp : cv.experiences()) {
                md.append("\n### ").append(exp.title()).append(" — ").append(exp.company()).append('\n');
                md.append('*').append(dateRange(exp.startDate(), exp.endDate()));
                if (notBlank(exp.team())) {
                    md.append(" · ").append(exp.team());
                }
                if (notBlank(exp.location())) {
                    md.append(" · ").append(exp.location());
                }
                md.append("*\n");
                appendBullets(md, exp.bullets());
                if (exp.technologies() != null && !exp.technologies().isEmpty()) {
                    md.append("\n*Technologies: ").append(String.join(", ", exp.technologies())).append("*\n");
                }
            }
        }

        if (cv.projects() != null && !cv.projects().isEmpty()) {
            md.append("\n## Projects\n");
            for (ProjectDto project : cv.projects()) {
                md.append("\n### ").append(project.name()).append('\n');
                if (notBlank(project.description())) {
                    md.append(project.description().trim()).append('\n');
                }
                appendBullets(md, project.highlights());
                if (project.technologies() != null && !project.technologies().isEmpty()) {
                    md.append("\n*Technologies: ").append(String.join(", ", project.technologies())).append("*\n");
                }
                if (notBlank(project.url())) {
                    md.append('<').append(project.url()).append(">\n");
                }
            }
        }

        if (cv.education() != null && !cv.education().isEmpty()) {
            md.append("\n## Education\n");
            for (EducationDto edu : cv.education()) {
                md.append("\n### ").append(joinNonBlank(", ", edu.degree(), edu.field()))
                        .append(" — ").append(edu.institution()).append('\n');
                md.append('*').append(dateRange(edu.startDate(), edu.endDate())).append("*\n");
                if (notBlank(edu.details())) {
                    md.append(edu.details().trim()).append('\n');
                }
            }
        }

        if (cv.certifications() != null && !cv.certifications().isEmpty()) {
            md.append("\n## Certifications\n\n");
            cv.certifications().forEach(cert -> md.append("- ").append(cert).append('\n'));
        }

        return md.toString();
    }

    private String contactLine(ProfileDto profile) {
        List<String> parts = new ArrayList<>();
        if (notBlank(profile.email())) {
            parts.add(profile.email());
        }
        if (notBlank(profile.phone())) {
            parts.add(profile.phone());
        }
        if (notBlank(profile.location())) {
            parts.add(profile.location());
        }
        if (profile.links() != null) {
            profile.links().forEach((label, url) -> parts.add("[" + label + "](" + url + ")"));
        }
        return String.join(" · ", parts);
    }

    private void appendBullets(StringBuilder md, List<String> bullets) {
        if (bullets == null || bullets.isEmpty()) {
            return;
        }
        md.append('\n');
        bullets.forEach(bullet -> md.append("- ").append(bullet).append('\n'));
    }

    private String dateRange(String start, String end) {
        return (start == null ? "" : start) + " – " + (notBlank(end) ? end : "Present");
    }

    private String firstNonBlank(String a, String b) {
        return notBlank(a) ? a : (notBlank(b) ? b : null);
    }

    private String joinNonBlank(String separator, String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (notBlank(value)) {
                parts.add(value);
            }
        }
        return String.join(separator, parts);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
