package com.example.jobmaster.service;

import com.example.jobmaster.domain.AiModelConfig;
import com.example.jobmaster.repository.AiModelConfigRepository;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;

/**
 * Hands out the ChatClient every generation uses. When an active AiModelConfig
 * exists, the client applies that config's model, API key, and sampling
 * parameters as default options — switchable from the UI without a restart.
 * The API key ride-along in the options is Spring AI 2.0's runtime-auth
 * mechanism, so the boot-time ChatModel can serve any key/model of its
 * provider. Under the "mock" profile the auto-configured mock client is
 * always used.
 */
@Service
public class AiClientService {

    private final AiModelConfigRepository repository;
    private final ObjectProvider<ChatClient> fallbackClient;
    private final ObjectProvider<OpenAiChatModel> openAiModel;
    private final ObjectProvider<AnthropicChatModel> anthropicModel;
    private final boolean mockProfile;

    private volatile ChatClient cached;
    private volatile Long cachedConfigId;
    private volatile Instant cachedConfigUpdatedAt;

    public AiClientService(AiModelConfigRepository repository,
                           ObjectProvider<ChatClient> fallbackClient,
                           ObjectProvider<OpenAiChatModel> openAiModel,
                           ObjectProvider<AnthropicChatModel> anthropicModel,
                           Environment environment) {
        this.repository = repository;
        this.fallbackClient = fallbackClient;
        this.openAiModel = openAiModel;
        this.anthropicModel = anthropicModel;
        this.mockProfile = Arrays.asList(environment.getActiveProfiles()).contains("mock");
    }

    public ChatClient client() {
        if (mockProfile) {
            return requireFallback();
        }
        AiModelConfig active = repository.findFirstByActiveTrue().orElse(null);
        if (active == null) {
            return requireFallback();
        }
        ChatClient client = cached;
        if (client == null || !active.getId().equals(cachedConfigId)
                || !active.getUpdatedAt().equals(cachedConfigUpdatedAt)) {
            synchronized (this) {
                if (cached == null || !active.getId().equals(cachedConfigId)
                        || !active.getUpdatedAt().equals(cachedConfigUpdatedAt)) {
                    cached = build(active);
                    cachedConfigId = active.getId();
                    cachedConfigUpdatedAt = active.getUpdatedAt();
                }
                client = cached;
            }
        }
        return client;
    }

    /** Called whenever the active config changes so the next call rebuilds. */
    public void invalidate() {
        synchronized (this) {
            cached = null;
            cachedConfigId = null;
            cachedConfigUpdatedAt = null;
        }
    }

    private ChatClient build(AiModelConfig config) {
        return switch (config.getProvider()) {
            case OPENAI -> {
                OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                        .model(config.getModel())
                        .apiKey(config.getApiKey());
                if (config.getTemperature() != null) {
                    options.temperature(config.getTemperature());
                }
                if (config.getTopP() != null) {
                    options.topP(config.getTopP());
                }
                if (config.getMaxTokens() != null) {
                    options.maxCompletionTokens(config.getMaxTokens());
                }
                OpenAiChatModel model = openAiModel.getIfAvailable(
                        () -> OpenAiChatModel.builder().options(options.build()).build());
                yield ChatClient.builder(model).defaultOptions(options).build();
            }
            case ANTHROPIC -> {
                AnthropicChatOptions.Builder options = AnthropicChatOptions.builder()
                        .model(config.getModel())
                        .apiKey(config.getApiKey())
                        // Anthropic requires an output cap; 8192 is a sane default
                        .maxTokens(config.getMaxTokens() != null ? config.getMaxTokens() : 8192);
                if (config.getTemperature() != null) {
                    options.temperature(config.getTemperature());
                }
                if (config.getTopP() != null) {
                    options.topP(config.getTopP());
                }
                AnthropicChatModel model = anthropicModel.getIfAvailable(
                        () -> AnthropicChatModel.builder().options(options.build()).build());
                yield ChatClient.builder(model).defaultOptions(options).build();
            }
        };
    }

    private ChatClient requireFallback() {
        ChatClient client = fallbackClient.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException(
                    "No AI configured: add a model in Settings or set provider properties.");
        }
        return client;
    }
}
