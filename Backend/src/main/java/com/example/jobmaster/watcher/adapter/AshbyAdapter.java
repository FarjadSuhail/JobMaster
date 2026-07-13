package com.example.jobmaster.watcher.adapter;

import com.example.jobmaster.watcher.domain.AdapterType;
import com.example.jobmaster.watcher.domain.WatchedCompany;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Ashby — official public posting API, no auth:
 *   https://api.ashbyhq.com/posting-api/job-board/{jobBoardName}
 * One response carries every job including its description.
 * Config: {"jobBoardName": "galvany"}
 */
@Component
public class AshbyAdapter implements JobPortalAdapter {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AshbyAdapter(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public AdapterType type() {
        return AdapterType.ASHBY;
    }

    @Override
    public List<FetchedJob> fetch(WatchedCompany company) {
        List<FetchedJob> jobs = new ArrayList<>();
        for (JsonNode job : board(boardName(company)).path("jobs")) {
            if (!job.path("isListed").asBoolean(true)) {
                continue;
            }
            jobs.add(new FetchedJob(
                    job.path("id").asString(),
                    job.path("title").asString("").trim(),
                    location(job),
                    blankToNull(job.path("department").asString(null)),
                    job.path("jobUrl").asString(null),
                    publishedAt(job)));
        }
        return jobs;
    }

    @Override
    public String fetchDescription(WatchedCompany company, String externalId, String url) {
        for (JsonNode job : board(boardName(company)).path("jobs")) {
            if (externalId.equals(job.path("id").asString())) {
                String plain = job.path("descriptionPlain").asString(null);
                if (plain != null && !plain.isBlank()) {
                    return plain.trim();
                }
                return HtmlText.toPlainText(job.path("descriptionHtml").asString(null));
            }
        }
        return null;
    }

    /** Also used by the URL→JD fetch, which has no WatchedCompany yet. */
    public JsonNode board(String jobBoardName) {
        String body = restClient.get()
                .uri("https://api.ashbyhq.com/posting-api/job-board/{name}", jobBoardName)
                .header("Accept", "application/json")
                .header("User-Agent", WorkdayAdapter.BROWSER_UA)
                .retrieve()
                .body(String.class);
        return objectMapper.readTree(body);
    }

    String boardName(WatchedCompany company) {
        String name = objectMapper.readTree(company.getConfigJson()).path("jobBoardName").asString("");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Ashby config needs {\"jobBoardName\": ...}");
        }
        return name;
    }

    private String location(JsonNode job) {
        String location = blankToNull(job.path("location").asString(null));
        if (job.path("isRemote").asBoolean(false)) {
            return location == null ? "Remote" : location + " (remote)";
        }
        return location;
    }

    private Instant publishedAt(JsonNode job) {
        String published = job.path("publishedAt").asString(null);
        if (published == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(published).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
