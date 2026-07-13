package com.example.jobmaster.api;

import com.example.jobmaster.dto.CoverLetterRequest;
import com.example.jobmaster.dto.CoverLetterResponse;
import com.example.jobmaster.dto.CvGenerationResponse;
import com.example.jobmaster.dto.GenerationDetail;
import com.example.jobmaster.dto.GenerationSummary;
import com.example.jobmaster.dto.PdfFile;
import com.example.jobmaster.dto.TailorCvRequest;
import com.example.jobmaster.service.CoverLetterService;
import com.example.jobmaster.service.CvTailoringService;
import com.example.jobmaster.service.GenerationHistoryService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class GenerationController {

    private final CvTailoringService cvTailoringService;
    private final CoverLetterService coverLetterService;
    private final GenerationHistoryService historyService;

    public GenerationController(CvTailoringService cvTailoringService,
                                CoverLetterService coverLetterService,
                                GenerationHistoryService historyService) {
        this.cvTailoringService = cvTailoringService;
        this.coverLetterService = coverLetterService;
        this.historyService = historyService;
    }

    /** Tailor the stored CV to a job description. */
    @PostMapping("/cv")
    public CvGenerationResponse tailorCv(@Valid @RequestBody TailorCvRequest request) {
        return cvTailoringService.tailor(request);
    }

    /** Generate a cover letter for a job description. */
    @PostMapping("/cover-letter")
    public CoverLetterResponse coverLetter(@Valid @RequestBody CoverLetterRequest request) {
        return coverLetterService.generate(request);
    }

    @GetMapping("/generations")
    public List<GenerationSummary> listGenerations() {
        return historyService.list();
    }

    @GetMapping("/generations/{id}")
    public GenerationDetail getGeneration(@PathVariable Long id) {
        return historyService.get(id);
    }

    /** Download a generated CV or cover letter as a one-page PDF. */
    @GetMapping("/generations/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        PdfFile pdf = historyService.pdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .headers(headers -> headers.setContentDisposition(
                        ContentDisposition.attachment().filename(pdf.filename()).build()))
                .body(pdf.bytes());
    }
}
