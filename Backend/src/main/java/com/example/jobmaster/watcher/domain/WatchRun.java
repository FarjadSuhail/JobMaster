package com.example.jobmaster.watcher.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Log of one fetch of one company's portal. companyName is denormalized so the
 * log survives company deletion.
 */
@Entity
@Table(name = "watch_run")
@Getter
@Setter
@NoArgsConstructor
public class WatchRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long companyId;

    @Column(nullable = false)
    private String companyName;

    @Column(nullable = false)
    private Instant startedAt;

    private long jobsFound;

    private long jobsNew;

    /** OK or ERROR. */
    @Column(nullable = false, length = 16)
    private String status;

    @Column(length = 4000)
    private String error;
}
