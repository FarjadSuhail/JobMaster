package com.example.jobmaster.service;

import com.example.jobmaster.dto.EducationDto;
import com.example.jobmaster.dto.ExperienceDto;
import com.example.jobmaster.dto.ProfileDto;
import com.example.jobmaster.dto.ProjectDto;
import com.example.jobmaster.dto.TailoredCv;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.function.DoubleFunction;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Renders generations as one-page PDFs. The CV layout replicates the user's
 * Word template: Calibri-metric font (Carlito), centered name header, blue
 * section headings (#1c4587) with a #1155cc rule, three-column experience
 * headers, and underlined keywords inside bullets.
 */
@Service
public class PdfService {

    /**
     * AI output length varies, but one page is a hard requirement — so render
     * at full size and, on overflow, retry at progressively smaller typography
     * until it fits. 0.82 is the floor: below that the CV reads cramped, and
     * content that still overflows there is a prompt problem, not a layout one.
     */
    private static final double[] FIT_SCALES = {1.0, 0.94, 0.88, 0.82};

    public byte[] cvPdf(ProfileDto profile, TailoredCv cv) {
        return fitToOnePage(scale -> cvHtml(profile, cv, scale));
    }

    public byte[] coverLetterPdf(ProfileDto profile, String letter, String jobTitle, String company) {
        return fitToOnePage(scale -> coverLetterHtml(profile, letter, jobTitle, company, scale));
    }

    private byte[] fitToOnePage(DoubleFunction<String> htmlAtScale) {
        byte[] pdf = null;
        for (double scale : FIT_SCALES) {
            pdf = toPdf(htmlAtScale.apply(scale));
            if (pageCount(pdf) <= 1) {
                return pdf;
            }
        }
        return pdf;
    }

    private int pageCount(byte[] pdf) {
        try (PDDocument document = PDDocument.load(pdf)) {
            return document.getNumberOfPages();
        } catch (Exception e) {
            return 1; // unreadable output will fail later anyway; don't loop on it
        }
    }

    // ------------------------------------------------------------------ CV

    private String cvHtml(ProfileDto profile, TailoredCv cv, double scale) {
        List<String> keywords = new ArrayList<>();
        if (cv.keywordsMatched() != null) {
            keywords.addAll(cv.keywordsMatched());
        }
        if (cv.keywordsAdded() != null) {
            keywords.addAll(cv.keywordsAdded());
        }

        StringBuilder html = new StringBuilder();
        html.append("""
                <!DOCTYPE html>
                <html><head><style>
                @page { size: A4; margin: 0.35in 0.45in; }
                body { font-family: 'Carlito', sans-serif; font-size: @BODYpt; line-height: 1.18; color: #000; margin: 0; }
                h1 { font-size: @H1pt; text-align: center; margin: 0; }
                p.contact { text-align: center; margin: 2pt 0 @CGAPpt 0; }
                a { color: #1155cc; }
                h2 { font-size: @H2pt; color: #1c4587; border-bottom: 1pt solid #1155cc;
                     margin: @H2TOPpt 0 @H2BOTpt 0; padding-bottom: 1.5pt; }
                p.summary { margin: 2pt 0 0 0; }
                table { width: 100%; border-collapse: collapse; }
                td { padding: 0; vertical-align: top; }
                table.exphead { margin-top: @EXPTOPpt; }
                td.l { width: 33%; text-align: left; }
                td.c { width: 34%; text-align: center; }
                td.r { width: 33%; text-align: right; }
                tr.head td { font-weight: bold; font-size: @HEADpt; }
                ul { margin: @ULTOPpt 0 @ULBOTpt 0; padding-left: 20pt; }
                li { margin: 0 0 @LIpt 0; padding-left: 2pt; }
                td.bullet { width: 14pt; text-align: center; }
                td.dates { width: 14%; text-align: right; font-weight: bold; }
                table.edu td { padding-bottom: @EDUPADpt; }
                span.cat { font-weight: bold; display: inline-block; width: 1.6in; }
                </style></head><body>
                """
                .replace("@BODY", pt(11.2 * scale))
                .replace("@H1", pt(21 * scale))
                .replace("@H2TOP", pt(7 * scale))
                .replace("@H2BOT", pt(3 * scale))
                .replace("@H2", pt(13 * scale))
                .replace("@CGAP", pt(4 * scale))
                .replace("@EXPTOP", pt(5 * scale))
                .replace("@HEAD", pt(11.6 * scale))
                .replace("@ULTOP", pt(3 * scale))
                .replace("@ULBOT", pt(4 * scale))
                .replace("@LI", pt(2 * scale))
                .replace("@EDUPAD", pt(3 * scale)));

        html.append("<h1>").append(esc(profile.fullName())).append("</h1>\n");
        html.append("<p class=\"contact\">").append(contactLine(profile)).append("</p>\n");

        if (notBlank(cv.summary())) {
            html.append("<h2>Summary</h2>\n<p class=\"summary\">")
                    .append(underlineKeywords(esc(cv.summary().trim()), keywords)).append("</p>\n");
        }

        if (cv.experiences() != null && !cv.experiences().isEmpty()) {
            html.append("<h2>Work Experience</h2>\n");
            for (ExperienceDto exp : cv.experiences()) {
                html.append("<table class=\"exphead\"><tr class=\"head\">")
                        .append("<td class=\"l\">").append(esc(exp.title())).append("</td>")
                        .append("<td class=\"c\">").append(esc(exp.company())).append("</td>")
                        .append("<td class=\"r\">").append(esc(dateRange(exp.startDate(), exp.endDate()))).append("</td>")
                        .append("</tr>");
                if (notBlank(exp.team()) || notBlank(exp.location())) {
                    html.append("<tr>")
                            .append("<td class=\"l\">").append(esc(nullSafe(exp.team()))).append("</td>")
                            .append("<td class=\"c\">").append(esc(nullSafe(exp.location()))).append("</td>")
                            .append("<td class=\"r\"></td></tr>");
                }
                html.append("</table>\n");
                if (exp.bullets() != null && !exp.bullets().isEmpty()) {
                    html.append("<ul>");
                    for (String bullet : exp.bullets()) {
                        html.append("<li>").append(underlineKeywords(esc(bullet), keywords)).append("</li>");
                    }
                    html.append("</ul>\n");
                }
            }
        }

        if (cv.education() != null && !cv.education().isEmpty()) {
            html.append("<h2>Education</h2>\n<table class=\"edu\">");
            for (EducationDto edu : cv.education()) {
                html.append("<tr><td class=\"bullet\">&#8226;</td><td><b>")
                        .append(esc(joinNonBlank(" in ", edu.degree(), edu.field()))).append(",</b> ")
                        .append(esc(nullSafe(edu.institution()))).append(".");
                if (notBlank(edu.details())) {
                    html.append(' ').append(esc(edu.details()));
                }
                html.append("</td><td class=\"dates\">")
                        .append(esc(dateRange(edu.startDate(), edu.endDate())))
                        .append("</td></tr>");
            }
            html.append("</table>\n");
        }

        if (cv.skills() != null && !cv.skills().isEmpty()) {
            html.append("<h2>Technical Skills</h2>\n<ul>");
            for (Map.Entry<String, List<String>> group : cv.skills().entrySet()) {
                html.append("<li><span class=\"cat\">").append(esc(group.getKey())).append(":</span> ")
                        .append(esc(String.join(", ", group.getValue()))).append("</li>");
            }
            html.append("</ul>\n");
        }

        if (cv.projects() != null && !cv.projects().isEmpty()) {
            html.append("<h2>Projects and Volunteer Work</h2>\n<ul>");
            for (ProjectDto project : cv.projects()) {
                html.append("<li><b>").append(esc(project.name())).append(":</b> ")
                        .append(esc(nullSafe(project.description())));
                if (project.highlights() != null && !project.highlights().isEmpty()) {
                    html.append(' ').append(esc(String.join(" ", project.highlights())));
                }
                html.append("</li>");
            }
            html.append("</ul>\n");
        }

        if (cv.certifications() != null && !cv.certifications().isEmpty()) {
            html.append("<h2>Certifications</h2>\n<ul>");
            cv.certifications().forEach(cert -> html.append("<li>").append(esc(cert)).append("</li>"));
            html.append("</ul>\n");
        }

        return html.append("</body></html>").toString();
    }

    // ---------------------------------------------------------- Cover letter

    private String coverLetterHtml(ProfileDto profile, String letter, String jobTitle,
                                   String company, double scale) {
        StringBuilder html = new StringBuilder();
        html.append("""
                <!DOCTYPE html>
                <html><head><style>
                @page { size: A4; margin: 0.8in 0.9in; }
                body { font-family: 'Carlito', sans-serif; font-size: @BODYpt; line-height: 1.35; color: #000; margin: 0; }
                h1 { font-size: @H1pt; text-align: center; margin: 0; }
                p.contact { text-align: center; margin: 1pt 0 0 0; border-bottom: 1pt solid #1155cc; padding-bottom: 8pt; }
                a { color: #1155cc; }
                p.meta { margin: @METApt 0 0 0; }
                p.date { text-align: right; margin: 4pt 0 @METApt 0; }
                p.body { margin: 0 0 @PBOTpt 0; }
                </style></head><body>
                """
                .replace("@BODY", pt(11 * scale))
                .replace("@H1", pt(16 * scale))
                .replace("@META", pt(14 * scale))
                .replace("@PBOT", pt(9 * scale)));

        html.append("<h1>").append(esc(profile.fullName())).append("</h1>\n");
        html.append("<p class=\"contact\">").append(contactLine(profile)).append("</p>\n");

        String re = joinNonBlank(" — ", jobTitle, company);
        if (!re.isBlank()) {
            html.append("<p class=\"meta\"><b>Application for: ").append(esc(re)).append("</b></p>\n");
        }
        html.append("<p class=\"date\">")
                .append(LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)))
                .append("</p>\n");

        for (String paragraph : letter.strip().split("\\n\\s*\\n")) {
            html.append("<p class=\"body\">")
                    .append(esc(paragraph).replace("\n", "<br/>"))
                    .append("</p>\n");
        }

        return html.append("</body></html>").toString();
    }

    // -------------------------------------------------------------- Helpers

    private byte[] toPdf(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            useFont(builder, "Carlito-Regular", 400, BaseRendererBuilder.FontStyle.NORMAL);
            useFont(builder, "Carlito-Bold", 700, BaseRendererBuilder.FontStyle.NORMAL);
            useFont(builder, "Carlito-Italic", 400, BaseRendererBuilder.FontStyle.ITALIC);
            useFont(builder, "Carlito-BoldItalic", 700, BaseRendererBuilder.FontStyle.ITALIC);
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF rendering failed: " + e.getMessage(), e);
        }
    }

    private void useFont(PdfRendererBuilder builder, String file, int weight, BaseRendererBuilder.FontStyle style) {
        builder.useFont(() -> PdfService.class.getResourceAsStream("/fonts/" + file + ".ttf"),
                "Carlito", weight, style, true);
    }

    private String contactLine(ProfileDto profile) {
        List<String> parts = new ArrayList<>();
        if (notBlank(profile.phone())) {
            parts.add(esc(profile.phone()));
        }
        if (notBlank(profile.email())) {
            parts.add("<a href=\"mailto:" + esc(profile.email()) + "\">" + esc(profile.email()) + "</a>");
        }
        if (profile.links() != null) {
            profile.links().forEach((label, url) ->
                    parts.add("<a href=\"" + esc(url) + "\">" + esc(displayUrl(url)) + "</a>"));
        }
        if (notBlank(profile.location())) {
            parts.add(esc(profile.location()));
        }
        return String.join(" | ", parts);
    }

    /**
     * Underlines job-description keywords inside an already-escaped bullet,
     * mirroring how the original CV underlines key terms. Single alternation
     * pass so matches never nest.
     */
    private String underlineKeywords(String escapedText, List<String> keywords) {
        List<String> patterns = keywords.stream()
                .filter(k -> k != null && !k.isBlank())
                .sorted((a, b) -> b.length() - a.length())
                .map(k -> Pattern.quote(esc(k)))
                .collect(Collectors.toList());
        if (patterns.isEmpty()) {
            return escapedText;
        }
        Pattern pattern = Pattern.compile("(?i)\\b(" + String.join("|", patterns) + ")\\b");
        Matcher matcher = pattern.matcher(escapedText);
        return matcher.replaceAll("<u>$1</u>");
    }

    /** CSS points value, e.g. pt(11.2 * 0.94) → "10.53". */
    private String pt(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private String displayUrl(String url) {
        return url.replaceFirst("^https?://(www\\.)?", "");
    }

    private String dateRange(String start, String end) {
        return nullSafe(start) + "–" + (notBlank(end) ? end : "Present");
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

    private String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
