package com.example.jobmaster.config;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Fake ChatModel for the "mock" Spring profile so the whole API can be
 * exercised without an API key or spending credits. For CV requests it echoes
 * the profile embedded in the prompt back as the "tailored" CV, which makes
 * PDF/layout testing realistic. Also doubles as proof of the provider
 * abstraction: the rest of the app doesn't know it's fake.
 */
@Configuration
@Profile("mock")
public class MockAiConfig {

    private static final String PROFILE_MARKER = "CANDIDATE MASTER PROFILE (JSON):\n";
    private static final String JOB_MARKER = "\n\nTARGET JOB DESCRIPTION:";

    private static final String MOCK_COVER_LETTER = """
            Dear Hiring Team,

            This is a canned cover letter from the mock AI provider, kept to a few \
            paragraphs so the PDF layout can be checked without spending credits.

            Run the app without the "mock" profile and with an API key configured \
            to get real output tailored to the job description you submitted.

            Sincerely,
            JobMaster Mock
            """;

    @Bean
    public ChatModel mockChatModel(ObjectMapper objectMapper) {
        return prompt -> {
            String text = promptText(prompt);
            String reply = text.toLowerCase().contains("cover letter")
                    ? MOCK_COVER_LETTER
                    : echoProfileAsCv(text, objectMapper);
            // Fake but plausible token counts so the Usage tab can be exercised
            ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                    .usage(new DefaultUsage(text.length() / 4, reply.length() / 4))
                    .build();
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))), metadata);
        };
    }

    /** Builds a TailoredCv-shaped JSON reply from the profile inside the prompt. */
    private String echoProfileAsCv(String promptText, ObjectMapper mapper) {
        int start = promptText.indexOf(PROFILE_MARKER);
        int end = promptText.indexOf(JOB_MARKER);
        if (start < 0 || end <= start) {
            return fallbackCvJson();
        }
        try {
            JsonNode profile = mapper.readTree(
                    promptText.substring(start + PROFILE_MARKER.length(), end));
            ObjectNode cv = mapper.createObjectNode();
            cv.set("headline", profile.path("headline"));
            cv.set("summary", profile.path("summary"));
            cv.set("skills", profile.path("skills"));
            cv.set("experiences", profile.path("experiences"));
            cv.set("projects", profile.path("projects"));
            cv.set("education", profile.path("education"));
            cv.set("certifications", profile.path("certifications"));
            cv.putArray("keywordsMatched")
                    .add("Java").add("Spring Boot").add("REST").add("Docker").add("PostgreSQL")
                    .add("microservices");
            cv.putArray("keywordsAdded");
            cv.putArray("keywordsMissing").add("Kubernetes production experience");
            cv.put("atsScore", 91);
            cv.put("tailoringNotes", "Mock provider: profile echoed back unchanged.");
            return mapper.writeValueAsString(cv);
        } catch (RuntimeException e) {
            return fallbackCvJson();
        }
    }

    private String fallbackCvJson() {
        return """
                {
                  "headline": "Mock Headline",
                  "summary": "Mock summary.",
                  "skills": {"Languages": ["Java"]},
                  "experiences": [],
                  "projects": [],
                  "education": [],
                  "certifications": [],
                  "keywordsMatched": [],
                  "keywordsAdded": [],
                  "tailoringNotes": "Mock fallback: no profile found in prompt."
                }
                """;
    }

    private static String promptText(Prompt prompt) {
        StringBuilder sb = new StringBuilder();
        prompt.getInstructions().forEach(message -> sb.append(message.getText()).append('\n'));
        return sb.toString();
    }
}
