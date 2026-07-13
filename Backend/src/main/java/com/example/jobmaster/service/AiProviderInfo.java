package com.example.jobmaster.service;

import com.example.jobmaster.domain.AiModelConfig;
import com.example.jobmaster.repository.AiModelConfigRepository;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Resolves which provider/model is currently active, for logging and responses. */
@Component
public class AiProviderInfo {

    private final Environment env;
    private final AiModelConfigRepository configRepository;
    private final boolean mockProfile;

    public AiProviderInfo(Environment env, AiModelConfigRepository configRepository) {
        this.env = env;
        this.configRepository = configRepository;
        this.mockProfile = Arrays.asList(env.getActiveProfiles()).contains("mock");
    }

    public String provider() {
        if (!mockProfile) {
            Optional<AiModelConfig> active = configRepository.findFirstByActiveTrue();
            if (active.isPresent()) {
                return active.get().getProvider().name().toLowerCase(Locale.ROOT);
            }
        }
        String provider = env.getProperty("spring.ai.model.chat", "openai");
        return "none".equals(provider) ? "mock" : provider;
    }

    public String model() {
        if (!mockProfile) {
            Optional<AiModelConfig> active = configRepository.findFirstByActiveTrue();
            if (active.isPresent()) {
                return active.get().getModel();
            }
        }
        String provider = provider();
        if ("mock".equals(provider)) {
            return "mock";
        }
        return env.getProperty("spring.ai." + provider + ".chat.options.model", "unknown");
    }
}
