package com.example.jobmaster.watcher.adapter;

import com.example.jobmaster.watcher.domain.AdapterType;
import com.example.jobmaster.watcher.domain.WatchedCompany;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Greenhouse — official public job board API, no auth needed:
 * https://boards-api.greenhouse.io/v1/boards/{boardToken}/jobs
 * Config: {"boardToken": "n26"}
 */
@Component
public class GreenhouseAdapter implements JobPortalAdapter {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GreenhouseAdapter(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public AdapterType type() {
        return AdapterType.GREENHOUSE;
    }

    @Override
    public String fetchDescription(WatchedCompany company, String externalId, String url) {
        String boardToken = boardToken(company);
        String body = restClient.get()
                .uri("https://boards-api.greenhouse.io/v1/boards/{token}/jobs/{id}", boardToken, externalId)
                .retrieve()
                .body(String.class);
        // Greenhouse returns the description as HTML-escaped HTML
        String content = objectMapper.readTree(body).path("content").asString(null);
        return HtmlText.toPlainText(org.jsoup.parser.Parser.unescapeEntities(
                content == null ? "" : content, false));
    }

    @Override
    public List<FetchedJob> fetch(WatchedCompany company) {
        String boardToken = boardToken(company);
        String body = restClient.get()
                .uri("https://boards-api.greenhouse.io/v1/boards/{token}/jobs", boardToken)
                .retrieve()
                .body(String.class);

        List<FetchedJob> jobs = new ArrayList<>();
        for (JsonNode job : objectMapper.readTree(body).path("jobs")) {
            long id = job.path("id").asLong();
            if (id == 0) {
                continue;
            }
            jobs.add(new FetchedJob(
                    String.valueOf(id),
                    job.path("title").asString("").trim(),
                    job.path("location").path("name").asString(null),
                    null,
                    job.path("absolute_url").asString(null),
                    null));
        }
        return jobs;
    }

    private String boardToken(WatchedCompany company) {
        String boardToken = objectMapper.readTree(company.getConfigJson()).path("boardToken").asString();
        if (boardToken == null || boardToken.isBlank()) {
            throw new IllegalArgumentException("Greenhouse config needs {\"boardToken\": ...}");
        }
        return boardToken;
    }
}
