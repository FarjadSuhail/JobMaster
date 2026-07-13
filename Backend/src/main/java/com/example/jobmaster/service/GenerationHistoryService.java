package com.example.jobmaster.service;

import com.example.jobmaster.domain.Generation;
import com.example.jobmaster.domain.GenerationKind;
import com.example.jobmaster.dto.GenerationDetail;
import com.example.jobmaster.dto.GenerationSummary;
import com.example.jobmaster.dto.PdfFile;
import com.example.jobmaster.dto.ProfileDto;
import com.example.jobmaster.dto.TailoredCv;
import com.example.jobmaster.repository.GenerationRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GenerationHistoryService {

    private final GenerationRepository repository;
    private final ObjectMapper objectMapper;
    private final ProfileService profileService;
    private final PdfService pdfService;

    public GenerationHistoryService(GenerationRepository repository, ObjectMapper objectMapper,
                                    ProfileService profileService, PdfService pdfService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.profileService = profileService;
        this.pdfService = pdfService;
    }

    @Transactional(readOnly = true)
    public List<GenerationSummary> list() {
        // Dashboard analyses live in the same table (so Usage counts their cost)
        // but aren't documents — keep them out of the history list.
        return repository.findByKindNotOrderByCreatedAtDesc(GenerationKind.ANALYSIS).stream()
                .map(g -> new GenerationSummary(g.getId(), g.getKind(), applicationId(g),
                        g.getJobTitle(), g.getCompany(), g.getProvider(), g.getModel(),
                        g.getCostUsd(), g.getCreatedAt()))
                .toList();
    }

    private Long applicationId(Generation g) {
        return g.getApplication() == null ? null : g.getApplication().getId();
    }

    @Transactional(readOnly = true)
    public GenerationDetail get(Long id) {
        Generation g = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("No generation found with id " + id));

        boolean isCv = g.getKind() == GenerationKind.CV;
        return new GenerationDetail(
                g.getId(),
                g.getKind(),
                applicationId(g),
                g.getJobTitle(),
                g.getCompany(),
                g.getLocation(),
                g.getJobDescription(),
                isCv ? parseCv(g.getResult()) : null,
                isCv ? g.getResultMarkdown() : null,
                isCv ? null : g.getResult(),
                g.getProvider(),
                g.getModel(),
                g.getPromptTokens(),
                g.getCompletionTokens(),
                g.getCostUsd(),
                g.getCreatedAt());
    }

    /** Renders a stored generation (CV or cover letter) as a downloadable PDF. */
    @Transactional(readOnly = true)
    public PdfFile pdf(Long id) {
        Generation g = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("No generation found with id " + id));
        if (g.getKind() == GenerationKind.ANALYSIS) {
            throw new NotFoundException("Generation " + id + " is a dashboard analysis, not a document");
        }
        // Use the location that was chosen for this application, not today's profile value
        ProfileDto profile = profileService.getRequired().withLocation(g.getLocation());

        byte[] bytes;
        String kindLabel;
        if (g.getKind() == GenerationKind.CV) {
            bytes = pdfService.cvPdf(profile, parseCv(g.getResult()));
            kindLabel = "CV";
        } else {
            bytes = pdfService.coverLetterPdf(profile, g.getResult(), g.getJobTitle(), g.getCompany());
            kindLabel = "Cover_Letter";
        }
        return new PdfFile(filename(profile.fullName(), kindLabel, g.getCompany()), bytes);
    }

    private String filename(String fullName, String kindLabel, String company) {
        String name = sanitize(fullName) + "_" + kindLabel;
        if (company != null && !company.isBlank()) {
            name += "_" + sanitize(company);
        }
        return name + ".pdf";
    }

    private String sanitize(String value) {
        return value.trim().replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private TailoredCv parseCv(String json) {
        try {
            return objectMapper.readValue(json, TailoredCv.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Stored CV JSON is corrupt", e);
        }
    }
}
