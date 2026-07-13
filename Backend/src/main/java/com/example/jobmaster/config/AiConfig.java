package com.example.jobmaster.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The single point where the app touches AI. ChatClient is provider-agnostic:
 * which vendor sits behind it is decided purely by configuration
 * (spring.ai.model.chat + the matching starter), never by application code.
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
