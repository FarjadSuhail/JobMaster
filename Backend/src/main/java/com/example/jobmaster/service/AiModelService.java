package com.example.jobmaster.service;

import com.example.jobmaster.domain.AiModelConfig;
import com.example.jobmaster.dto.AiModelConfigDto;
import com.example.jobmaster.dto.SaveAiModelRequest;
import com.example.jobmaster.repository.AiModelConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class AiModelService {

    private final AiModelConfigRepository repository;
    private final AiClientService aiClientService;

    public AiModelService(AiModelConfigRepository repository, AiClientService aiClientService) {
        this.repository = repository;
        this.aiClientService = aiClientService;
    }

    @Transactional(readOnly = true)
    public List<AiModelConfigDto> list() {
        return repository.findAllByOrderByCreatedAtAsc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Optional<AiModelConfig> active() {
        return repository.findFirstByActiveTrue();
    }

    @Transactional
    public AiModelConfigDto create(SaveAiModelRequest request) {
        AiModelConfig config = new AiModelConfig();
        config.setProvider(request.provider());
        config.setModel(request.model().trim());
        config.setApiKey(resolveKey(request));
        config.setTemperature(request.temperature());
        config.setTopP(request.topP());
        config.setMaxTokens(normalizeMaxTokens(request.maxTokens()));
        boolean first = repository.count() == 0;
        config.setActive(first);
        Instant now = Instant.now();
        config.setCreatedAt(now);
        config.setUpdatedAt(now);
        AiModelConfig saved = repository.save(config);
        if (first) {
            aiClientService.invalidate();
        }
        return toDto(saved);
    }

    @Transactional
    public AiModelConfigDto update(Long id, SaveAiModelRequest request) {
        AiModelConfig config = get(id);
        if (request.provider() != null) {
            config.setProvider(request.provider());
        }
        if (request.model() != null && !request.model().isBlank()) {
            config.setModel(request.model().trim());
        }
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            config.setApiKey(request.apiKey().trim());
        }
        config.setTemperature(request.temperature());
        config.setTopP(request.topP());
        config.setMaxTokens(normalizeMaxTokens(request.maxTokens()));
        config.setUpdatedAt(Instant.now());
        AiModelConfig saved = repository.save(config);
        if (saved.isActive()) {
            aiClientService.invalidate();
        }
        return toDto(saved);
    }

    @Transactional
    public AiModelConfigDto activate(Long id) {
        AiModelConfig target = get(id);
        for (AiModelConfig config : repository.findAll()) {
            if (config.isActive() && !config.getId().equals(id)) {
                config.setActive(false);
                repository.save(config);
            }
        }
        target.setActive(true);
        target.setUpdatedAt(Instant.now());
        AiModelConfig saved = repository.save(target);
        aiClientService.invalidate();
        return toDto(saved);
    }

    @Transactional
    public void delete(Long id) {
        AiModelConfig config = get(id);
        if (config.isActive()) {
            throw new IllegalArgumentException(
                    "Cannot delete the active model — activate another one first.");
        }
        repository.delete(config);
    }

    /** Blank key on create = reuse the key of an existing config of the same provider. */
    private String resolveKey(SaveAiModelRequest request) {
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            return request.apiKey().trim();
        }
        return repository.findAllByOrderByCreatedAtAsc().stream()
                .filter(c -> c.getProvider() == request.provider())
                .map(AiModelConfig::getApiKey)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No stored key for " + request.provider()
                                + " yet — the API key is required for the first "
                                + request.provider() + " model."));
    }

    private Integer normalizeMaxTokens(Integer maxTokens) {
        return maxTokens == null || maxTokens <= 0 ? null : maxTokens;
    }

    private AiModelConfig get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("No AI model config with id " + id));
    }

    private AiModelConfigDto toDto(AiModelConfig c) {
        return new AiModelConfigDto(c.getId(), c.getProvider(), c.getModel(),
                mask(c.getApiKey()), c.getTemperature(), c.getTopP(), c.getMaxTokens(),
                c.isActive(), c.getCreatedAt());
    }

    private String mask(String key) {
        if (key == null || key.length() < 8) {
            return "••••";
        }
        return "••••" + key.substring(key.length() - 4);
    }
}
