package com.example.jobmaster.watcher;

import com.example.jobmaster.watcher.domain.JobPosting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pushes "new jobs found" messages to a Telegram chat via the Bot API.
 * Disabled (silent no-op) until both TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID
 * are set — see .env.example for the 3-minute BotFather setup. Sending is
 * always best-effort: a Telegram outage must never break a watcher run.
 */
@Component
public class TelegramNotifier {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);
    private static final int MAX_JOBS_PER_MESSAGE = 10;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String botToken;
    private final String chatId;

    public TelegramNotifier(RestClient.Builder restClientBuilder,
                            ObjectMapper objectMapper,
                            @Value("${jobmaster.telegram.bot-token:}") String botToken,
                            @Value("${jobmaster.telegram.chat-id:}") String chatId) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.botToken = botToken == null ? "" : botToken.trim();
        this.chatId = chatId == null ? "" : chatId.trim();
    }

    public boolean isConfigured() {
        return !botToken.isEmpty() && !chatId.isEmpty();
    }

    /** Best-effort push after a watcher run found new postings. Never throws. */
    public void notifyNewJobs(String companyName, List<JobPosting> newPostings) {
        if (!isConfigured() || newPostings.isEmpty()) {
            return;
        }
        try {
            StringBuilder text = new StringBuilder();
            text.append("🆕 <b>").append(newPostings.size())
                    .append(newPostings.size() == 1 ? " new job" : " new jobs")
                    .append("</b> at <b>").append(escape(companyName)).append("</b>\n");
            newPostings.stream().limit(MAX_JOBS_PER_MESSAGE).forEach(p -> {
                text.append("• ");
                if (p.getUrl() != null && !p.getUrl().isBlank()) {
                    text.append("<a href=\"").append(escape(p.getUrl())).append("\">")
                            .append(escape(p.getTitle())).append("</a>");
                } else {
                    text.append(escape(p.getTitle()));
                }
                if (p.getLocation() != null && !p.getLocation().isBlank()) {
                    text.append(" (").append(escape(p.getLocation())).append(')');
                }
                text.append('\n');
            });
            if (newPostings.size() > MAX_JOBS_PER_MESSAGE) {
                text.append("… and ").append(newPostings.size() - MAX_JOBS_PER_MESSAGE)
                        .append(" more — see the Jobs tab.");
            }
            send(text.toString());
        } catch (Exception e) {
            log.warn("Telegram notification failed (run is unaffected): {}", e.getMessage());
        }
    }

    /** For the connection-test endpoint; throws with a readable message on failure. */
    public void sendTest() {
        if (!isConfigured()) {
            throw new IllegalArgumentException("Telegram is not configured — set "
                    + "TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID in .env and restart "
                    + "(see .env.example for the BotFather steps).");
        }
        send("✅ JobMaster is connected. New job postings will arrive here after "
                + "every watcher check.");
    }

    private void send(String html) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", chatId);
        payload.put("text", html);
        payload.put("parse_mode", "HTML");
        payload.put("disable_web_page_preview", true);
        String response = restClient.post()
                .uri("https://api.telegram.org/bot{token}/sendMessage", botToken)
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(payload))
                .retrieve()
                .body(String.class);
        if (response == null || !objectMapper.readTree(response).path("ok").asBoolean(false)) {
            throw new IllegalStateException("Telegram API rejected the message: " + response);
        }
    }

    private String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
