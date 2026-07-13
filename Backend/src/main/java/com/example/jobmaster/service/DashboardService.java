package com.example.jobmaster.service;

import com.example.jobmaster.domain.Application;
import com.example.jobmaster.domain.ApplicationStatus;
import com.example.jobmaster.domain.Generation;
import com.example.jobmaster.domain.GenerationKind;
import com.example.jobmaster.dto.DashboardReport;
import com.example.jobmaster.dto.ExperienceDto;
import com.example.jobmaster.dto.ProfileDto;
import com.example.jobmaster.dto.ProjectDto;
import com.example.jobmaster.dto.TailoredCv;
import com.example.jobmaster.repository.ApplicationRepository;
import com.example.jobmaster.repository.GenerationRepository;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DashboardService {

    private static final int TOP_SKILLS = 12;
    private static final int MAX_APPS_IN_PROMPT = 40;

    private static final String SUMMARY_SYSTEM_PROMPT = """
            You are a sharp, honest career coach. You receive a snapshot of the \
            candidate's recent job applications (roles, companies, statuses, ATS \
            scores, recurring missing keywords) plus the strongest skills in their \
            profile. Analyze the data and reply in plain Markdown with exactly \
            these sections:

            ## What you are applying to
            The pattern in the roles: stack, seniority, industries, locations. \
            Two or three sentences.

            ## How it is going
            Read the funnel honestly: applications vs interviews vs rejections vs \
            offers. If the numbers are too fresh or too few to judge, say so \
            plainly instead of inventing trends. Two or three sentences.

            ## Recurring gaps
            The skills and keywords that keep showing up as missing. Separate \
            quick wins (terms the candidate can honestly add to their profile or \
            CV wording) from real skill gaps that need learning time.

            ## Do this next
            Four to six concrete, specific actions. Each one line, actionable \
            this week, no platitudes.

            Rules: under 350 words in total. Never use em dashes (the long dash \
            character); use commas, colons, or parentheses instead. No flattery, \
            no filler. Base every claim on the data given, and say when the data \
            is too thin to conclude anything.
            """;

    private final ApplicationRepository applicationRepository;
    private final GenerationRepository generationRepository;
    private final ProfileService profileService;
    private final AiClientService aiClientService;
    private final AiProviderInfo aiProviderInfo;
    private final CostService costService;
    private final ObjectMapper objectMapper;
    private final LocalDate startDate;

    public DashboardService(ApplicationRepository applicationRepository,
                            GenerationRepository generationRepository,
                            ProfileService profileService,
                            AiClientService aiClientService,
                            AiProviderInfo aiProviderInfo,
                            CostService costService,
                            ObjectMapper objectMapper,
                            @Value("${jobmaster.dashboard.start-date}") String startDate) {
        this.applicationRepository = applicationRepository;
        this.generationRepository = generationRepository;
        this.profileService = profileService;
        this.aiClientService = aiClientService;
        this.aiProviderInfo = aiProviderInfo;
        this.costService = costService;
        this.objectMapper = objectMapper;
        this.startDate = LocalDate.parse(startDate);
    }

    @Transactional(readOnly = true)
    public DashboardReport report() {
        List<Application> apps = trackedApplications();
        return new DashboardReport(startDate, stats(apps), topProfileSkills(),
                missingSkills(), lastSummary());
    }

    /**
     * One AI call analyzing the tracked applications; stored as a Generation of
     * kind ANALYSIS (application = null) so its cost shows up in Usage. Only
     * ever runs when the user clicks the button — never on page load.
     */
    @Transactional
    public DashboardReport.AiSummary generateSummary() {
        List<Application> apps = trackedApplications();
        if (apps.isEmpty()) {
            throw new NotFoundException("No applications tracked since " + startDate
                    + " yet. Apply to some jobs first, then run the analysis.");
        }

        String input = buildSummaryInput(apps);
        ModelResult result = callModel(input);

        Generation generation = new Generation();
        generation.setKind(GenerationKind.ANALYSIS);
        generation.setApplication(null);
        generation.setJobTitle("Application pattern analysis");
        generation.setJobDescription(input);
        generation.setResult(result.text());
        generation.setResultMarkdown(result.text());
        generation.setProvider(aiProviderInfo.provider());
        generation.setModel(aiProviderInfo.model());
        generation.setPromptTokens(result.promptTokens());
        generation.setCompletionTokens(result.completionTokens());
        generation.setCostUsd(costService.cost(aiProviderInfo.model(),
                result.promptTokens(), result.completionTokens()));
        generation.setCreatedAt(Instant.now());
        generation = generationRepository.save(generation);

        return toSummary(generation);
    }

    private List<Application> trackedApplications() {
        return applicationRepository.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(since());
    }

    private Instant since() {
        return startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private DashboardReport.Stats stats(List<Application> apps) {
        long applied = apps.stream().filter(a -> a.getAppliedAt() != null).count();
        long interviewing = count(apps, ApplicationStatus.INTERVIEWING);
        long offers = count(apps, ApplicationStatus.OFFER);
        long rejectedNoInterview = apps.stream()
                .filter(a -> a.getStatus() == ApplicationStatus.REJECTED && a.getInterviewedAt() == null)
                .count();
        long rejectedAfterInterview = apps.stream()
                .filter(a -> a.getStatus() == ApplicationStatus.REJECTED && a.getInterviewedAt() != null)
                .count();
        return new DashboardReport.Stats(apps.size(), applied, interviewing, offers,
                rejectedNoInterview, rejectedAfterInterview);
    }

    private long count(List<Application> apps, ApplicationStatus status) {
        return apps.stream().filter(a -> a.getStatus() == status).count();
    }

    /**
     * Skill prominence = 1 for being listed in the profile + one point per
     * mention across headline, summary, experience bullets, and projects.
     */
    private List<DashboardReport.SkillCount> topProfileSkills() {
        ProfileDto profile = profileService.find().orElse(null);
        if (profile == null || profile.skills() == null) {
            return List.of();
        }
        String corpus = profileCorpus(profile).toLowerCase(Locale.ROOT);
        Map<String, Long> counts = new LinkedHashMap<>();
        profile.skills().values().stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(s -> s != null && !s.isBlank())
                .forEach(skill -> counts.merge(skill.trim(), 1 + mentions(corpus, skill.trim()), Long::sum));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_SKILLS)
                .map(e -> new DashboardReport.SkillCount(e.getKey(), e.getValue()))
                .toList();
    }

    private String profileCorpus(ProfileDto profile) {
        StringBuilder sb = new StringBuilder();
        append(sb, profile.headline());
        append(sb, profile.summary());
        if (profile.experiences() != null) {
            for (ExperienceDto exp : profile.experiences()) {
                if (exp.bullets() != null) {
                    exp.bullets().forEach(b -> append(sb, b));
                }
                if (exp.technologies() != null) {
                    exp.technologies().forEach(t -> append(sb, t));
                }
            }
        }
        if (profile.projects() != null) {
            for (ProjectDto project : profile.projects()) {
                append(sb, project.description());
                if (project.technologies() != null) {
                    project.technologies().forEach(t -> append(sb, t));
                }
                if (project.highlights() != null) {
                    project.highlights().forEach(h -> append(sb, h));
                }
            }
        }
        return sb.toString();
    }

    private void append(StringBuilder sb, String value) {
        if (value != null) {
            sb.append(value).append('\n');
        }
    }

    /** Occurrences bounded by non-alphanumerics, so "Java" doesn't match "JavaScript". */
    private long mentions(String lowerCorpus, String skill) {
        Pattern pattern = Pattern.compile(
                "(?<![a-z0-9])" + Pattern.quote(skill.toLowerCase(Locale.ROOT)) + "(?![a-z0-9])");
        Matcher matcher = pattern.matcher(lowerCorpus);
        long count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /** Aggregates keywordsMissing across CVs generated since the start date. */
    private List<DashboardReport.SkillCount> missingSkills() {
        List<Generation> cvs = generationRepository
                .findByKindAndCreatedAtGreaterThanEqual(GenerationKind.CV, since());
        Map<String, String> displayForm = new LinkedHashMap<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Generation cv : cvs) {
            for (String keyword : missingKeywords(cv)) {
                String key = keyword.toLowerCase(Locale.ROOT);
                displayForm.putIfAbsent(key, keyword);
                counts.merge(key, 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_SKILLS)
                .map(e -> new DashboardReport.SkillCount(displayForm.get(e.getKey()), e.getValue()))
                .toList();
    }

    private List<String> missingKeywords(Generation cv) {
        TailoredCv parsed = parseCvQuietly(cv.getResult());
        if (parsed == null || parsed.keywordsMissing() == null) {
            return List.of();
        }
        return parsed.keywordsMissing().stream()
                .filter(k -> k != null && !k.isBlank())
                .map(String::trim)
                .toList();
    }

    private TailoredCv parseCvQuietly(String json) {
        try {
            return objectMapper.readValue(json, TailoredCv.class);
        } catch (JacksonException e) {
            return null;
        }
    }

    private DashboardReport.AiSummary lastSummary() {
        return generationRepository.findFirstByKindOrderByCreatedAtDesc(GenerationKind.ANALYSIS)
                .map(this::toSummary)
                .orElse(null);
    }

    private DashboardReport.AiSummary toSummary(Generation g) {
        return new DashboardReport.AiSummary(g.getId(), g.getResult(), g.getModel(),
                g.getCostUsd(), g.getCreatedAt());
    }

    private String buildSummaryInput(List<Application> apps) {
        StringBuilder sb = new StringBuilder();
        sb.append("APPLICATIONS TRACKED SINCE ").append(startDate).append(":\n");
        apps.stream().limit(MAX_APPS_IN_PROMPT).forEach(app -> {
            sb.append("- ").append(app.getJobTitle());
            if (app.getCompany() != null) {
                sb.append(" at ").append(app.getCompany());
            }
            if (app.getLocation() != null) {
                sb.append(" (").append(app.getLocation()).append(')');
            }
            sb.append(" | status: ").append(app.getStatus());
            if (app.getStatus() == ApplicationStatus.REJECTED) {
                sb.append(app.getInterviewedAt() == null ? " (no interview)" : " (after interview)");
            }
            TailoredCv cv = latestCv(app);
            if (cv != null) {
                if (cv.atsScore() != null) {
                    sb.append(" | ATS score: ").append(cv.atsScore());
                }
                if (cv.keywordsMissing() != null && !cv.keywordsMissing().isEmpty()) {
                    sb.append(" | missing: ").append(String.join(", ",
                            cv.keywordsMissing().stream().limit(6).toList()));
                }
            }
            sb.append('\n');
        });

        List<DashboardReport.SkillCount> strengths = topProfileSkills();
        if (!strengths.isEmpty()) {
            sb.append("\nSTRONGEST PROFILE SKILLS (by prominence): ");
            sb.append(String.join(", ", strengths.stream().map(DashboardReport.SkillCount::name).toList()));
            sb.append('\n');
        }
        List<DashboardReport.SkillCount> gaps = missingSkills();
        if (!gaps.isEmpty()) {
            sb.append("\nKEYWORDS MOST OFTEN MISSING ACROSS TAILORED CVS (keyword x times): ");
            sb.append(String.join(", ", gaps.stream()
                    .map(s -> s.name() + " x" + s.count()).toList()));
            sb.append('\n');
        }
        return sb.toString();
    }

    private TailoredCv latestCv(Application app) {
        return app.getGenerations().stream()
                .filter(g -> g.getKind() == GenerationKind.CV)
                .max(Comparator.comparing(Generation::getCreatedAt))
                .map(g -> parseCvQuietly(g.getResult()))
                .orElse(null);
    }

    private record ModelResult(String text, Integer promptTokens, Integer completionTokens) {
    }

    private ModelResult callModel(String input) {
        try {
            ChatResponse response = aiClientService.client().prompt()
                    .system(SUMMARY_SYSTEM_PROMPT)
                    .user(input)
                    .call()
                    .chatResponse();
            String text = response.getResult().getOutput().getText();
            Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
            return new ModelResult(text,
                    usage == null ? null : usage.getPromptTokens(),
                    usage == null ? null : usage.getCompletionTokens());
        } catch (RuntimeException e) {
            throw new AiCallException("AI provider call failed while analyzing applications: "
                    + e.getMessage(), e);
        }
    }
}
