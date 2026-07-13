package com.example.jobmaster.watcher.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One job posting discovered on a watched portal. Diffing key: (company, externalId). */
@Entity
@Table(name = "job_posting",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "externalId"}))
@Getter
@Setter
@NoArgsConstructor
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id")
    private WatchedCompany company;

    @Column(nullable = false)
    private String externalId;

    @Column(nullable = false, length = 512)
    private String title;

    private String location;

    private String department;

    @Column(length = 1000)
    private String url;

    /** Posting date reported by the source, when available. */
    private Instant postedAt;

    @Column(nullable = false)
    private Instant firstSeenAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobPostingStatus status = JobPostingStatus.NEW;

    /** Set when converted into a board application. */
    private Long applicationId;

    /** True when the posting no longer appears on the portal. */
    @Column(nullable = false)
    private boolean closed = false;
}
