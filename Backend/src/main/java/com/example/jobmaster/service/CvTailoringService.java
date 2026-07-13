package com.example.jobmaster.service;

import com.example.jobmaster.domain.Application;
import com.example.jobmaster.domain.Generation;
import com.example.jobmaster.domain.GenerationKind;
import com.example.jobmaster.dto.CvGenerationResponse;
import com.example.jobmaster.dto.ProfileDto;
import com.example.jobmaster.dto.TailorCvRequest;
import com.example.jobmaster.dto.TailoredCv;
import com.example.jobmaster.repository.GenerationRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CvTailoringService {

    private static final String SYSTEM_PROMPT = """
            You are an expert CV/resume writer and ATS (applicant tracking system) \
            optimization specialist. You receive a candidate's master profile as JSON \
            and a target job description. Produce a tailored CV structured as the \
            required JSON output.

            STEP 1: KEYWORD AND GAP ANALYSIS (complete this before tailoring content):
            Systematically compare the job description to the master profile:
            a) Extract from the JD: required and preferred skills, tools, frameworks, \
               methodologies, certifications, domain terms, seniority signals, and \
               recurring responsibility phrases.
            b) For each extracted term, classify against the profile:
               - DIRECT MATCH: explicit in profile (skills, experience bullets, \
                 projects, education, certifications).
               - IMPLIED MATCH: not named verbatim but clearly supported by adjacent \
                 work (e.g. profile says "Spring Boot REST APIs" and JD says \
                 "microservices"; profile says "PostgreSQL" and JD says "SQL").
               - PROFILE-ONLY STRENGTH: in the profile but absent from the JD yet \
                 relevant to the role (complementary tech, domain depth, leadership, \
                 scale, compliance exposure). Plan to surface these where they \
                 strengthen candidacy.
               - GAP: in the JD but weak or missing in the profile. Do not invent \
                 coverage. Note in tailoringNotes; only include under keywordsAdded \
                 if the profile shows a credible adjacent foundation and the skill \
                 could be picked up quickly (e.g. knows React, JD asks for Next.js).
            c) Rank JD priorities: which requirements appear most often or are \
               labeled required vs nice-to-have. Tailor ordering and bullet emphasis \
               to match that ranking.

            STEP 2: TAILOR THE CV using that analysis:
            Content strategy:
            - Reorder sections, skills, and project entries so the most JD-relevant \
              items appear first. EXCEPTION: work experience and education entries \
              must ALWAYS stay in reverse-chronological order (most recent first, \
              ongoing roles at the top). Recruiters and ATS parsers treat any other \
              order as an error. Express relevance through bullet selection, bullet \
              order, and emphasis within each role, never by moving jobs out of \
              time order.
            - Rewrite headline and summary to foreground the candidate's fit for THIS \
              role using JD language where honestly supported.
            - Weave JD keywords naturally into summary, skills, and experience bullets. \
              Every keyword must be defensible from the profile. No keyword stuffing.
            - Include profile strengths not named in the JD when they are clearly \
              relevant (e.g. extra languages, adjacent frameworks, domain expertise).
            - Rephrase bullets for ATS clarity while keeping them readable to humans.
            - Trim or deprioritize profile content with little relevance to this JD \
              rather than padding. Keep roughly the same overall length as the source.

            Bullet writing:
            - Format: strong action verb + what was done + outcome or impact.
            - Preserve real numbers, metrics, and scope from the profile. NEVER \
              invent or inflate metrics, team sizes, or percentages.
            - Each bullet should read as something a professional wrote, not a \
              keyword dump. Vary structure across bullets; avoid repeating the same \
              opening verb or parallel template on every line.
            - NEVER use em dashes (the long dash character) in any field. They are \
              a common AI tell. Use commas, periods, colons, or parentheses instead.
            - Use formal, precise language. Avoid buzzwords ("synergy", "leverage", \
              "passionate") unless they appear in the JD itself.

            ATS HARD RULES (automated screeners match literally; these are not optional):
            - MIRROR THE JD'S EXACT WORDING for every skill/term the profile honestly \
              supports: same casing, hyphenation, and phrasing ("RESTful APIs" not \
              "REST interfaces", "CI/CD" not "build pipelines") in at least one place. \
              Close synonyms are for human readers; the verbatim form is for the machine.
            - For each key technology or method, include BOTH the acronym and the \
              spelled-out form at least once across the CV where it reads naturally \
              ("AWS (Amazon Web Services)", "CI/CD (Continuous Integration/Continuous \
              Delivery)") because different ATS systems match different forms.
            - Align the headline with the target job title when the profile honestly \
              supports it (e.g. "Software Engineer - Backend (Java)" for a Backend \
              Engineer posting). Recruiters search by title; title match ranks highest.
            - FRONT-LOAD: the 5 most important JD keywords must each appear in the \
              summary or in the first two bullets of the most relevant role, not \
              only in the skills table.
            - Every honestly-supportable hard requirement from the JD must literally \
              appear somewhere in the CV (skills line or a bullet). A supported \
              keyword that never appears is a free point thrown away.

            STRICT TRUTHFULNESS (violating these makes the output worthless):
            - Never invent employers, job titles, dates, degrees, certifications, \
              tools, projects, or achievements not in the profile.
            - Every skill listed must exist in the profile or be clearly implied by \
              documented experience. Do not add skills the candidate cannot defend \
              in an interview.
            - Do not claim full proficiency in a JD requirement based only on \
              "could learn quickly" unless the profile shows strong adjacent proof.

            OUTPUT METADATA FIELDS (populate accurately after tailoring):
            - keywordsMatched: JD keywords, tools, and phrases the profile directly \
              or clearly supports (include both verbatim JD terms and close synonyms \
              you reflected in the CV text).
            - keywordsAdded: (1) JD terms you incorporated that were only implied, \
              not explicit, in the profile; AND (2) profile strengths you highlighted \
              that were not in the JD but are relevant to the role; AND (3) adjacent \
              skills you included because the profile supports a credible foundation \
              and the candidate could learn the remainder quickly. List each item \
              as a short label (e.g. "Kubernetes", "stakeholder management").
            - keywordsMissing: important JD requirements that do NOT appear in the \
              CV because the profile cannot honestly support them. This list is how \
              the candidate sees what to learn or address; never shrink it by \
              fabricating coverage.
            - atsScore: your honest 0-100 estimate of this CV's ATS keyword-match \
              score against THIS job description: the share of important JD keywords \
              covered verbatim, weighted by importance (required skills and the job \
              title weigh most), with deductions for each unmet must-have.
            - tailoringNotes: 3-5 sentences covering (a) what you emphasized and \
              why, (b) profile strengths beyond the JD that you surfaced, (c) real \
              gaps the candidate should address before or during the process, and \
              (d) any quick-study items worth mentioning honestly in interviews. \
              Write in plain, formal prose with no em dashes.

            ONE-PAGE TARGET: the rendered CV must FILL exactly one A4 page — never \
            overflow to a second page, but also never leave a large empty gap at the \
            bottom. A half-empty page reads as a thin profile. Budget:
            - summary: 2-3 sentences, tailored to this job (it appears at the top of \
              the CV, right under the contact line).
            - 5-6 bullets for the most relevant recent role, 2-4 for the others; \
              at most 12-13 bullets across all roles combined.
            - Each bullet 15-30 words (one to two lines). When space allows, expand \
              the most relevant achievements with concrete detail from the profile \
              rather than padding with filler.
            - Up to 6 skill categories, each fitting on one line (~12 items max).
            - 2-3 projects, one line of description each.

            STEP 3: ATS SELF-CHECK (do this before emitting the final answer):
            Re-read the job description as if you were the ATS. List the 15-25 most \
            important keywords/phrases (required skills, tools, methodologies, and \
            the job title itself). For each one the profile honestly supports, verify \
            it appears VERBATIM somewhere in your tailored CV. If any supported \
            keyword is absent, revise the CV to include it naturally before answering. \
            Then set atsScore honestly. Target 85 or higher; fall short ONLY when the \
            profile genuinely lacks must-have requirements, and in that case make the \
            gap explicit in keywordsMissing and tailoringNotes. A high score achieved \
            by fabrication is worthless; a high score achieved by complete coverage \
            of what is true is the goal.
            """;

    private final AiClientService aiClientService;
    private final ProfileService profileService;
    private final CvMarkdownRenderer markdownRenderer;
    private final GenerationRepository generationRepository;
    private final ApplicationService applicationService;
    private final AiProviderInfo aiProviderInfo;
    private final CostService costService;
    private final ObjectMapper objectMapper;

    public CvTailoringService(AiClientService aiClientService,
                              ProfileService profileService,
                              CvMarkdownRenderer markdownRenderer,
                              GenerationRepository generationRepository,
                              ApplicationService applicationService,
                              AiProviderInfo aiProviderInfo,
                              CostService costService,
                              ObjectMapper objectMapper) {
        this.aiClientService = aiClientService;
        this.profileService = profileService;
        this.markdownRenderer = markdownRenderer;
        this.generationRepository = generationRepository;
        this.applicationService = applicationService;
        this.aiProviderInfo = aiProviderInfo;
        this.costService = costService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CvGenerationResponse tailor(TailorCvRequest request) {
        // Per-application location override: the AI prompt, the markdown, and the
        // PDF all see the same effective location.
        ProfileDto profile = profileService.getRequired().withLocation(request.location());
        String profileJson = objectMapper.writeValueAsString(profile);

        Application application = applicationService.resolveOrCreate(request.applicationId(),
                request.jobTitle(), request.company(), request.location(), request.jobDescription());

        ModelResult result = callModel(buildUserPrompt(profileJson, request));
        // Hard guarantee regardless of model behavior: work history and education
        // stay reverse-chronological (recruiters/ATS treat any other order as an error).
        TailoredCv cv = CvOrdering.reverseChronological(result.cv());
        String markdown = markdownRenderer.render(profile, cv);

        Generation generation = new Generation();
        generation.setKind(GenerationKind.CV);
        generation.setApplication(application);
        generation.setJobTitle(request.jobTitle());
        generation.setCompany(request.company());
        generation.setLocation(blankToNull(request.location()));
        generation.setJobDescription(request.jobDescription());
        generation.setResult(toJson(cv));
        generation.setResultMarkdown(markdown);
        generation.setProvider(aiProviderInfo.provider());
        generation.setModel(aiProviderInfo.model());
        generation.setPromptTokens(result.promptTokens());
        generation.setCompletionTokens(result.completionTokens());
        generation.setCostUsd(costService.cost(aiProviderInfo.model(),
                result.promptTokens(), result.completionTokens()));
        generation.setCreatedAt(Instant.now());
        generation = generationRepository.save(generation);

        return new CvGenerationResponse(generation.getId(), application.getId(), cv, markdown,
                generation.getProvider(), generation.getModel(),
                generation.getPromptTokens(), generation.getCompletionTokens(), generation.getCostUsd(),
                generation.getCreatedAt());
    }

    private record ModelResult(TailoredCv cv, Integer promptTokens, Integer completionTokens) {
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private ModelResult callModel(String userPrompt) {
        try {
            var responseEntity = aiClientService.client().prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .responseEntity(TailoredCv.class);
            Usage usage = responseEntity.response() == null
                    ? null
                    : responseEntity.response().getMetadata().getUsage();
            return new ModelResult(responseEntity.entity(),
                    usage == null ? null : usage.getPromptTokens(),
                    usage == null ? null : usage.getCompletionTokens());
        } catch (RuntimeException e) {
            throw new AiCallException("AI provider call failed while tailoring CV: " + e.getMessage(), e);
        }
    }

    private String buildUserPrompt(String profileJson, TailorCvRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("CANDIDATE MASTER PROFILE (JSON):\n").append(profileJson).append("\n\n");
        prompt.append("TARGET JOB DESCRIPTION:\n");
        if (request.jobTitle() != null || request.company() != null) {
            prompt.append("Role: ").append(nullSafe(request.jobTitle()));
            if (request.company() != null) {
                prompt.append(" at ").append(request.company());
            }
            prompt.append('\n');
        }
        prompt.append(request.jobDescription()).append('\n');
        if (request.extraInstructions() != null && !request.extraInstructions().isBlank()) {
            prompt.append("\nADDITIONAL INSTRUCTIONS FROM THE CANDIDATE:\n")
                    .append(request.extraInstructions()).append('\n');
        }
        return prompt.toString();
    }

    private String toJson(TailoredCv cv) {
        try {
            return objectMapper.writeValueAsString(cv);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not serialize tailored CV", e);
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
