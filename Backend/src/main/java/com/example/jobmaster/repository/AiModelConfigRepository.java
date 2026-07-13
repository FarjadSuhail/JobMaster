package com.example.jobmaster.repository;

import com.example.jobmaster.domain.AiModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiModelConfigRepository extends JpaRepository<AiModelConfig, Long> {

    Optional<AiModelConfig> findFirstByActiveTrue();

    List<AiModelConfig> findAllByOrderByCreatedAtAsc();
}
