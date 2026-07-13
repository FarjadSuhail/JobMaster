package com.example.jobmaster.domain;

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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * One job application — the central object of the tracker. Generated CVs and
 * cover letters hang off it, so you can always see which documents went to
 * which job and where the application stands.
 */
@Entity
@Table(name = "application")
@Getter
@Setter
@NoArgsConstructor
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApplicationStatus status = ApplicationStatus.SAVED;

    @Column(nullable = false)
    private String jobTitle;

    private String company;

    /** Location shown on documents for this job (overrides the profile location). */
    private String location;

    @Column(length = 100_000)
    private String jobDescription;

    private String jobUrl;

    private String salary;

    private String contactPerson;

    @Column(length = 20_000)
    private String notes;

    /** When the status last changed — drives the board's "most recently moved" ordering.
     *  Unlike updatedAt, editing other fields (notes, salary, ...) does not touch this. */
    private Instant statusChangedAt;

    /** Set automatically when the status first moves to APPLIED (editable). */
    private LocalDate appliedAt;

    /** Set automatically when the status first moves to INTERVIEWING — lets the
     *  dashboard tell "rejected without interview" from "rejected after interview". */
    private LocalDate interviewedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Documents generated for this application; deleted together with it. */
    @OneToMany(mappedBy = "application", cascade = CascadeType.REMOVE)
    private List<Generation> generations = new ArrayList<>();
}
