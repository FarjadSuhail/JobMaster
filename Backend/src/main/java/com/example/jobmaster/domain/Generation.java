package com.example.jobmaster.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** One AI generation run: a tailored CV or a cover letter for a specific job. */
@Entity
@Table(name = "generation")
@Getter
@Setter
@NoArgsConstructor
public class Generation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GenerationKind kind;

    /** The job application this document belongs to; null for legacy rows. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private Application application;

    private String jobTitle;

    private String company;

    /** Location shown on this document (per-application override); null = profile location. */
    private String location;

    @Column(nullable = false, length = 100_000)
    private String jobDescription;

    /** Structured JSON for CV generations, plain text for cover letters. */
    @Column(nullable = false, length = 100_000)
    private String result;

    /** Rendered Markdown for CV generations; null for cover letters. */
    @Column(length = 100_000)
    private String resultMarkdown;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(nullable = false, length = 128)
    private String model;

    /** Token usage reported by the AI provider; null when unavailable. */
    private Integer promptTokens;

    private Integer completionTokens;

    /** Cost in USD at generation-time prices; null when the model has no configured price. */
    @Column(precision = 12, scale = 6)
    private BigDecimal costUsd;

    @Column(nullable = false)
    private Instant createdAt;
}
