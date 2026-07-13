package com.example.jobmaster.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A saved AI model configuration selectable from the UI. Exactly one row is
 * active at a time; the active one decides which provider/model/key/params
 * every generation uses — no restart needed when switching.
 */
@Entity
@Table(name = "ai_model_config")
@Getter
@Setter
@NoArgsConstructor
public class AiModelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiProvider provider;

    @Column(nullable = false, length = 128)
    private String model;

    @Column(nullable = false, length = 512)
    private String apiKey;

    /** Sampling randomness; null = provider default. */
    private Double temperature;

    /** Nucleus sampling; null = provider default. */
    private Double topP;

    /** Output length cap; null = provider default (Anthropic then uses 8192). */
    private Integer maxTokens;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
