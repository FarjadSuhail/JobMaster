package com.example.jobmaster.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * The user's master profile (experience, education, skills...) stored as a
 * single JSON document. It is always read, edited, and fed to the AI as a
 * whole, so a document column beats normalized tables here.
 */
@Entity
@Table(name = "profile_document")
@Getter
@Setter
@NoArgsConstructor
public class ProfileDocument {

    /** Single-user app for now: exactly one row, with this fixed id. */
    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(nullable = false, length = 100_000)
    private String json;

    @Column(nullable = false)
    private Instant updatedAt;
}
