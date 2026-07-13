package com.example.jobmaster.watcher.adapter;

import com.example.jobmaster.watcher.domain.AdapterType;
import com.example.jobmaster.watcher.domain.WatchedCompany;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * SmartRecruiters — official public API, no auth needed:
 * https://api.smartrecruiters.com/v1/companies/{companyId}/postings
 * Config: {"companyId": "DeliveryHero"}
 */
@Component
public class SmartRecruitersAdapter implements JobPortalAdapter {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_POSTINGS = 3000;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public SmartRecruitersAdapter(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public AdapterType type() {
        return AdapterType.SMARTRECRUITERS;
    }

    @Override
    public String fetchDescription(WatchedCompany company, String externalId, String url) {
        String companyId = companyId(company);
        String body = restClient.get()
                .uri("https://api.smartrecruiters.com/v1/companies/{id}/postings/{postingId}",
                        companyId, externalId)
                .retrieve()
                .body(String.class);
        JsonNode sections = objectMapper.readTree(body).path("jobAd").path("sections");
        StringBuilder text = new StringBuilder();
        for (String section : new String[]{"companyDescription", "jobDescription",
                "qualifications", "additionalInformation"}) {
            String sectionText = HtmlText.toPlainText(sections.path(section).path("text").asString(null));
            if (sectionText != null) {
                String title = sections.path(section).path("title").asString(null);
                if (title != null && !title.isBlank()) {
                    text.append(title).append('\n');
                }
                text.append(sectionText).append("\n\n");
            }
        }
        return text.isEmpty() ? null : text.toString().trim();
    }

    @Override
    public List<FetchedJob> fetch(WatchedCompany company) {
        String companyId = companyId(company);

        List<FetchedJob> jobs = new ArrayList<>();
        int offset = 0;
        long totalFound;
        do {
            String body = restClient.get()
                    .uri("https://api.smartrecruiters.com/v1/companies/{id}/postings?limit={limit}&offset={offset}",
                            companyId, PAGE_SIZE, offset)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            totalFound = root.path("totalFound").asLong();
            for (JsonNode posting : root.path("content")) {
                jobs.add(toJob(companyId, posting));
            }
            offset += PAGE_SIZE;
        } while (offset < totalFound && offset < MAX_POSTINGS);
        return jobs;
    }

    private FetchedJob toJob(String companyId, JsonNode posting) {
        String id = posting.path("id").asString();
        JsonNode location = posting.path("location");
        String city = location.path("city").asString(null);
        boolean remote = location.path("remote").asBoolean(false);
        String locationText = joinNonBlank(city, countryName(location.path("country").asString(null)))
                + (remote ? " (remote)" : "");

        Instant postedAt = null;
        String released = posting.path("releasedDate").asString(null);
        if (released != null) {
            try {
                postedAt = Instant.parse(released);
            } catch (Exception ignored) {
                // source date format changed; not critical
            }
        }

        return new FetchedJob(
                id,
                posting.path("name").asString("").trim(),
                locationText.isBlank() ? null : locationText,
                posting.path("department").path("label").asString(null),
                "https://jobs.smartrecruiters.com/" + companyId + "/" + id,
                postedAt);
    }

    private String companyId(WatchedCompany company) {
        String companyId = objectMapper.readTree(company.getConfigJson()).path("companyId").asString();
        if (companyId == null || companyId.isBlank()) {
            throw new IllegalArgumentException("SmartRecruiters config needs {\"companyId\": ...}");
        }
        return companyId;
    }

    /** "de" → "Germany", so location filters can use country names across all adapters. */
    private String countryName(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String name = java.util.Locale.of("", code.toUpperCase()).getDisplayCountry(java.util.Locale.ENGLISH);
        return name == null || name.isBlank() ? code.toUpperCase() : name;
    }

    private String joinNonBlank(String a, String b) {
        if (a == null || a.isBlank()) {
            return b == null ? "" : b;
        }
        return b == null || b.isBlank() ? a : a + ", " + b;
    }
}
