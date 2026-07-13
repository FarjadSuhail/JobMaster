package com.example.jobmaster.watcher.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A company career portal checked daily for new postings. */
@Entity
@Table(name = "watched_company")
@Getter
@Setter
@NoArgsConstructor
public class WatchedCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AdapterType adapterType;

    /** Adapter-specific settings, e.g. {"companyId":"DeliveryHero"}. */
    @Column(nullable = false, length = 4000)
    private String configJson;

    /** Pipe-separated terms; if set, a posting title must contain at least one. */
    private String includeKeywords;

    /** Pipe-separated terms; a posting title must contain none. */
    private String excludeKeywords;

    /** Pipe-separated terms; if set, the posting location must contain one. */
    private String locations;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant lastRunAt;

    private String lastRunStatus;

    @Column(length = 4000)
    private String lastError;

    @OneToMany(mappedBy = "company", cascade = CascadeType.REMOVE)
    private List<JobPosting> postings = new ArrayList<>();
}
