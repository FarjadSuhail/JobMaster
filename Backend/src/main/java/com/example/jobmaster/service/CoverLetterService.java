package com.example.jobmaster.service;

import com.example.jobmaster.domain.Application;
import com.example.jobmaster.domain.Generation;
import com.example.jobmaster.domain.GenerationKind;
import com.example.jobmaster.dto.CoverLetterRequest;
import com.example.jobmaster.dto.CoverLetterResponse;
import com.example.jobmaster.dto.ProfileDto;
import com.example.jobmaster.repository.GenerationRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

@Service
public class CoverLetterService {

    private static final String SYSTEM_PROMPT = """
            You are an elite career coach and professional writer who has screened \
            thousands of cover letters as a hiring manager. You write letters that \
            get interviews: specific, human, and built around what the COMPANY \
            needs rather than around the candidate. You receive the candidate's \
            master profile (JSON), optionally a CV already tailored to this job, \
            the target job description, and sometimes a contact person.

            STEP 1 - COMPANY RESEARCH (internal; never output this step):
            Build a picture of the company before writing a word:
            a) Mine the JD: what the company builds, who its customers are, its \
               market and scale, the team's tech stack, the adjectives it uses \
               about itself ("customer-obsessed", "regulated", "early-stage"), and \
               the problems this role clearly exists to solve. A JD is the company \
               telling you what keeps it busy; read it that way.
            b) Add your own knowledge when the company is well known: products, \
               mission, market position, engineering reputation. STRICT GUARD: use \
               only stable, widely known facts you are highly confident about. \
               Never invent news, funding, launches, metrics, or team names. If \
               unsure you have the right company, rely on the JD alone.
            c) Choose the register: a fintech startup letter and a global bank \
               letter must not sound the same. Match the company's formality and \
               energy without parodying it.

            STEP 2 - ARGUMENT DESIGN (internal):
            a) Identify the role's top 2-3 needs from the JD: the requirements that \
               repeat, carry "must", or define the seniority.
            b) Pick the ONE or TWO strongest proof stories from the profile/CV: \
               concrete work with real outcomes and numbers. Depth beats coverage; \
               two vivid, quantified stories outrank six name-dropped skills.
            c) Find the genuine overlap: why does THIS candidate at THIS company \
               make sense? Tech-stack overlap, domain experience, product affinity, \
               mission alignment the profile actually supports. That overlap is the \
               spine of the letter.
            d) Plan what the CV cannot say: motivation, context (career switch, \
               relocation, why this domain), how the candidate thinks and works.

            STEP 3 - WRITE THE LETTER:
            Shape:
            - 250-380 words, three to four paragraphs. Salutation, body, sign-off \
              with the candidate's name from the profile.
            - When a contact person is provided, address them by name ("Dear Ms. \
              Weber,"). Otherwise "Dear [Company] Hiring Team,". Never "To Whom It \
              May Concern".

            Paragraph 1, the hook (2-3 sentences):
            - Open with something only this candidate could write to only this \
              company: a specific reaction to what they build, a real point of \
              contact with their product, domain or tech, or the sharpest one-line \
              version of the candidate's value for this exact role.
            - Hard test: if the opening sentence would fit any other company after \
              swapping the name, rewrite it.
            - BANNED openers: "I am writing to express my interest", "I am excited \
              to apply", "As a passionate ...".

            Body paragraphs, proof through story:
            - Map the strongest evidence to the company's top needs as a short \
              story: the situation, what the candidate did, the outcome with the \
              real numbers from the profile. Never invent or inflate.
            - Weave the STEP 1 research into the argument: connect the candidate's \
              work to what the company is building or the challenges the JD names. \
              One precise company-specific connection beats five compliments.
            - Do NOT summarize the resume. If a sentence merely restates a CV \
              bullet, cut it or turn it into a story with cause and effect.
            - Address at most one honest gap or transition briefly and confidently \
              if it strengthens the case; never apologize.

            Final paragraph, the close:
            - One forward-looking sentence about what the candidate would want to \
              tackle in the role, grounded in the JD, then a direct, warm close \
              with availability to talk. No groveling, no "I hope", no thanking \
              for time and consideration as the substance of the paragraph.

            VOICE AND STYLE (this separates world-class from generic):
            - Write like a sharp, warm human. Vary sentence length; let one long \
              sentence carry an idea, then land a short one. Read each sentence \
              and ask: would a person say this out loud?
            - NEVER use em dashes (the long dash character). Use commas, colons, \
              semicolons, or parentheses instead.
            - BANNED words and phrases (AI tells and cliches): "proven track \
              record", "detail-oriented", "results-driven", "leverage", "synergy", \
              "passionate about", "thrilled", "dynamic environment", "fast-paced", \
              "team player", "hit the ground running", "wear many hats", "delve", \
              "spearheaded", "seamlessly", and stacked three-part rhetorical lists.
            - Concrete beats abstract: name the technology, the system, the number. \
              One real detail outranks three adjectives.
            - Confidence without arrogance: state what was done and what would be \
              done, not how amazing the candidate is.

            TRUTHFULNESS (non-negotiable):
            - Every claim about the candidate must come from the profile or the \
              tailored CV. Never invent employers, projects, metrics, tools, dates \
              or degrees.
            - Never imply the candidate uses or has worked with the company's \
              product unless the profile shows it; genuine interest can be \
              expressed without pretending usage.
            - Company facts follow the STEP 1 guard: JD-derived or rock-solid \
              general knowledge only.

            TONE PARAMETER: the request may name a tone (professional, \
            enthusiastic, concise, formal). Apply it on top of the STEP 1c \
            register; "concise" means 220-280 words.

            OUTPUT: only the letter text: salutation, body paragraphs, sign-off, \
            candidate's name. No JSON, no markdown, no subject line, no commentary.
            """;

    private final AiClientService aiClientService;
    private final ProfileService profileService;
    private final GenerationRepository generationRepository;
    private final ApplicationService applicationService;
    private final AiProviderInfo aiProviderInfo;
    private final CostService costService;
    private final ObjectMapper objectMapper;

    public CoverLetterService(AiClientService aiClientService,
                              ProfileService profileService,
                              GenerationRepository generationRepository,
                              ApplicationService applicationService,
                              AiProviderInfo aiProviderInfo,
                              CostService costService,
                              ObjectMapper objectMapper) {
        this.aiClientService = aiClientService;
        this.profileService = profileService;
        this.generationRepository = generationRepository;
        this.applicationService = applicationService;
        this.aiProviderInfo = aiProviderInfo;
        this.costService = costService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CoverLetterResponse generate(CoverLetterRequest request) {
        ProfileDto profile = profileService.getRequired().withLocation(request.location());
        String profileJson = objectMapper.writeValueAsString(profile);

        Generation cvGeneration = findCvGeneration(request.cvGenerationId());
        String tailoredCvMarkdown = cvGeneration == null ? null : cvGeneration.getResultMarkdown();
        Application application = resolveApplication(request, cvGeneration);

        ModelResult result = callModel(buildUserPrompt(profileJson, tailoredCvMarkdown, request,
                application.getContactPerson()));

        Generation generation = new Generation();
        generation.setKind(GenerationKind.COVER_LETTER);
        generation.setApplication(application);
        generation.setJobTitle(request.jobTitle());
        generation.setCompany(request.company());
        generation.setLocation(blankToNull(request.location()));
        generation.setJobDescription(request.jobDescription());
        generation.setResult(result.letter());
        generation.setProvider(aiProviderInfo.provider());
        generation.setModel(aiProviderInfo.model());
        generation.setPromptTokens(result.promptTokens());
        generation.setCompletionTokens(result.completionTokens());
        generation.setCostUsd(costService.cost(aiProviderInfo.model(),
                result.promptTokens(), result.completionTokens()));
        generation.setCreatedAt(Instant.now());
        generation = generationRepository.save(generation);

        return new CoverLetterResponse(generation.getId(), application.getId(), result.letter(),
                generation.getProvider(), generation.getModel(),
                generation.getPromptTokens(), generation.getCompletionTokens(), generation.getCostUsd(),
                generation.getCreatedAt());
    }

    private record ModelResult(String letter, Integer promptTokens, Integer completionTokens) {
    }

    /** Explicit applicationId wins; otherwise reuse the CV's application; otherwise create one. */
    private Application resolveApplication(CoverLetterRequest request, Generation cvGeneration) {
        if (request.applicationId() == null && cvGeneration != null && cvGeneration.getApplication() != null) {
            Application application = cvGeneration.getApplication();
            applicationService.touch(application);
            return application;
        }
        return applicationService.resolveOrCreate(request.applicationId(),
                request.jobTitle(), request.company(), request.location(), request.jobDescription());
    }

    private Generation findCvGeneration(Long cvGenerationId) {
        if (cvGenerationId == null) {
            return null;
        }
        return generationRepository.findById(cvGenerationId)
                .filter(g -> g.getKind() == GenerationKind.CV)
                .orElseThrow(() -> new NotFoundException(
                        "No CV generation found with id " + cvGenerationId));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private ModelResult callModel(String userPrompt) {
        try {
            ChatResponse response = aiClientService.client().prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .chatResponse();
            String letter = response.getResult().getOutput().getText();
            Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
            return new ModelResult(letter,
                    usage == null ? null : usage.getPromptTokens(),
                    usage == null ? null : usage.getCompletionTokens());
        } catch (RuntimeException e) {
            throw new AiCallException("AI provider call failed while writing cover letter: " + e.getMessage(), e);
        }
    }

    private String buildUserPrompt(String profileJson, String tailoredCvMarkdown,
                                   CoverLetterRequest request, String contactPerson) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Write a cover letter for the job below.\n\n");
        prompt.append("CANDIDATE MASTER PROFILE (JSON):\n").append(profileJson).append("\n\n");
        if (tailoredCvMarkdown != null) {
            prompt.append("CV ALREADY TAILORED TO THIS JOB:\n").append(tailoredCvMarkdown).append("\n\n");
        }
        prompt.append("TARGET JOB DESCRIPTION:\n");
        if (request.jobTitle() != null || request.company() != null) {
            prompt.append("Role: ").append(request.jobTitle() == null ? "" : request.jobTitle());
            if (request.company() != null) {
                prompt.append(" at ").append(request.company());
            }
            prompt.append('\n');
        }
        prompt.append(request.jobDescription()).append('\n');
        if (contactPerson != null && !contactPerson.isBlank()) {
            prompt.append("\nCONTACT PERSON (address the letter to them): ")
                    .append(contactPerson).append('\n');
        }
        prompt.append("\nTone: ").append(request.tone() == null || request.tone().isBlank()
                ? "professional" : request.tone()).append('\n');
        if (request.extraInstructions() != null && !request.extraInstructions().isBlank()) {
            prompt.append("\nADDITIONAL INSTRUCTIONS FROM THE CANDIDATE:\n")
                    .append(request.extraInstructions()).append('\n');
        }
        return prompt.toString();
    }
}
