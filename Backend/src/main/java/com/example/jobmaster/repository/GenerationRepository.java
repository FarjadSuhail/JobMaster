package com.example.jobmaster.repository;

import com.example.jobmaster.domain.Generation;
import com.example.jobmaster.domain.GenerationKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GenerationRepository extends JpaRepository<Generation, Long> {

    List<Generation> findAllByOrderByCreatedAtDesc();

    List<Generation> findByKindNotOrderByCreatedAtDesc(GenerationKind kind);

    List<Generation> findByKindAndCreatedAtGreaterThanEqual(GenerationKind kind, Instant since);

    Optional<Generation> findFirstByKindOrderByCreatedAtDesc(GenerationKind kind);
}
