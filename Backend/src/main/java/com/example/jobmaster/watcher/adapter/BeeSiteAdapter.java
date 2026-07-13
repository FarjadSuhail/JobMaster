package com.example.jobmaster.watcher.adapter;

import com.example.jobmaster.watcher.domain.AdapterType;
import com.example.jobmaster.watcher.domain.WatchedCompany;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Milch & Zucker BeeSite (used by Deutsche Bank among others) — public
 * HR-XML-style search API:
 *   list:   POST https://{apiHost}/search/  (FirstItem is 1-based)
 *   detail: GET  https://{apiHost}/jobhtml/{id}.json  → {"html": "..."}
 * Config: {"apiHost": "api-deutschebank.beesite.de",
 *          "jobUrlTemplate": "https://careers.db.com/professionals/search-roles/#/professional/job/{id}"}
 * Note: the API returns German country names ("Deutschland") regardless of
 * LanguageCode, so location filters should use city names for BeeSite portals.
 */
@Component
public class BeeSiteAdapter implements JobPortalAdapter {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_POSTINGS = 3000;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public BeeSiteAdapter(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public AdapterType type() {
        return AdapterType.BEESITE;
    }

    @Override
    public List<FetchedJob> fetch(WatchedCompany company) {
        Config config = config(company);
        List<FetchedJob> jobs = new ArrayList<>();
        int firstItem = 1;
        long total;
        do {
            JsonNode result = objectMapper.readTree(searchPage(config, firstItem))
                    .path("SearchResult");
            total = result.path("SearchResultCountAll").asLong(0);
            for (JsonNode item : result.path("SearchResultItems")) {
                JsonNode job = item.path("MatchedObjectDescriptor");
                String id = job.path("PositionID").asString(null);
                if (id == null || id.isBlank()) {
                    continue;
                }
                jobs.add(new FetchedJob(
                        id,
                        job.path("PositionTitle").asString("").trim(),
                        location(job),
                        null,
                        config.jobUrlTemplate().replace("{id}", id),
                        postedAt(job)));
            }
            firstItem += PAGE_SIZE;
        } while (firstItem <= total && firstItem <= MAX_POSTINGS);
        return jobs;
    }

    @Override
    public String fetchDescription(WatchedCompany company, String externalId, String url) {
        return HtmlText.toPlainText(jobHtmlRaw(config(company).apiHost(), externalId));
    }

    /** Raw job-ad HTML; also used by the URL→JD fetch (which parses the title out). */
    public String jobHtmlRaw(String apiHost, String positionId) {
        String body = restClient.get()
                .uri("https://{host}/jobhtml/{id}.json", apiHost, positionId)
                .header("Accept", "application/json")
                .header("User-Agent", WorkdayAdapter.BROWSER_UA)
                .retrieve()
                .body(String.class);
        return objectMapper.readTree(body).path("html").asString(null);
    }

    String searchPage(Config config, int firstItem) {
        String request = """
                {"LanguageCode":"en","SearchParameters":{"FirstItem":%d,"CountItem":%d,\
                "MatchedObjectDescriptor":["PositionID","PositionTitle","PositionURI",\
                "PositionLocation.CityName","PositionLocation.CountryName",\
                "PublicationStartDate"],\
                "Sort":[{"Criterion":"PublicationStartDate","Direction":"DESC"}]},\
                "SearchCriteria":[null]}""".formatted(firstItem, PAGE_SIZE);
        return restClient.post()
                .uri("https://{host}/search/", config.apiHost())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", WorkdayAdapter.BROWSER_UA)
                .body(request)
                .retrieve()
                .body(String.class);
    }

    record Config(String apiHost, String jobUrlTemplate) {
    }

    Config config(WatchedCompany company) {
        JsonNode node = objectMapper.readTree(company.getConfigJson());
        String apiHost = node.path("apiHost").asString("");
        String template = node.path("jobUrlTemplate").asString("");
        if (apiHost.isBlank() || template.isBlank()) {
            throw new IllegalArgumentException(
                    "BeeSite config needs {\"apiHost\", \"jobUrlTemplate\"} "
                            + "(template contains {id})");
        }
        return new Config(apiHost, template);
    }

    private String location(JsonNode job) {
        JsonNode location = job.path("PositionLocation");
        if (location.isArray()) {
            location = location.isEmpty() ? location : location.get(0);
        }
        String city = location.path("CityName").asString(null);
        String country = location.path("CountryName").asString(null);
        if (city == null || city.isBlank()) {
            return country == null || country.isBlank() ? null : country;
        }
        return country == null || country.isBlank() ? city : city + ", " + country;
    }

    private Instant postedAt(JsonNode job) {
        String date = job.path("PublicationStartDate").asString(null);
        if (date == null) {
            return null;
        }
        try {
            return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (Exception e) {
            return null;
        }
    }
}
