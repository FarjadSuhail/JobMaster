package com.example.jobmaster.service;

import com.example.jobmaster.domain.AiModelConfig;
import com.example.jobmaster.domain.AiProvider;
import com.example.jobmaster.repository.AiModelConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * One-time migration of the env/properties AI setup into the database, so the
 * Settings tab reflects the current configuration from day one. Skipped when
 * configs already exist or no API key is set.
 */
@Component
public class AiModelConfigSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AiModelConfigSeeder.class);

    private final AiModelConfigRepository repository;
    private final Environment environment;

    public AiModelConfigSeeder(AiModelConfigRepository repository, Environment environment) {
        this.repository = repository;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        String provider = environment.getProperty("spring.ai.model.chat", "openai");
        String key = environment.getProperty("spring.ai." + provider + ".api-key", "");
        if (key.isBlank() || (!provider.equals("openai") && !provider.equals("anthropic"))) {
            return;
        }
        AiModelConfig config = new AiModelConfig();
        config.setProvider(AiProvider.valueOf(provider.toUpperCase()));
        config.setModel(environment.getProperty("spring.ai." + provider + ".chat.options.model", "gpt-4o-mini"));
        config.setApiKey(key);
        String temperature = environment.getProperty("spring.ai." + provider + ".chat.options.temperature");
        config.setTemperature(temperature == null ? null : Double.parseDouble(temperature));
        config.setActive(true);
        Instant now = Instant.now();
        config.setCreatedAt(now);
        config.setUpdatedAt(now);
        repository.save(config);
        log.info("Seeded AI model config from environment: {} / {}", provider, config.getModel());
    }
}
