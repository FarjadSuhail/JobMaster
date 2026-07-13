package com.example.jobmaster.watcher.adapter;

import com.example.jobmaster.watcher.domain.AdapterType;
import com.example.jobmaster.watcher.domain.WatchedCompany;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Bundesagentur für Arbeit job search — the official federal job board API
 * (documented at jobsuche.api.bund.dev, public key, no registration):
 *   search: GET https://rest.arbeitsagentur.de/jobboerse/jobsuche-service/pc/v4/jobs?was=...&wo=...
 *   detail: GET .../pc/v3/jobdetails/{base64url(refnr)}
 * Unlike the other adapters this is not one company's portal but a keyword
 * search across all of Germany; the employer is folded into the posting title.
 * Config: {"was": "java backend", "wo": "Deutschland", "maxPages": 5}
 */
@Component
public class ArbeitsagenturAdapter implements JobPortalAdapter {

    public static final String API_KEY = "jobboerse-jobsuche";
    private static final String BASE = "https://rest.arbeitsagentur.de/jobboerse/jobsuche-service";
    private static final int PAGE_SIZE = 100;
    private static final int DEFAULT_MAX_PAGES = 5;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ArbeitsagenturAdapter(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public AdapterType type() {
        return AdapterType.ARBEITSAGENTUR;
    }

    @Override
    public List<FetchedJob> fetch(WatchedCompany company) {
        Config config = config(company);
        List<FetchedJob> jobs = new ArrayList<>();
        int page = 1;
        long total;
        do {
            JsonNode root = objectMapper.readTree(searchPage(config, page));
            total = root.path("maxErgebnisse").asLong(0);
            for (JsonNode job : root.path("stellenangebote")) {
                String refnr = job.path("refnr").asString(null);
                if (refnr == null || refnr.isBlank()) {
                    continue;
                }
                jobs.add(new FetchedJob(
                        refnr,
                        title(job),
                        location(job.path("arbeitsort")),
                        job.path("beruf").asString(null),
                        "https://www.arbeitsagentur.de/jobsuche/jobdetail/" + refnr,
                        postedAt(job)));
            }
            page++;
        } while ((long) (page - 1) * PAGE_SIZE < total && page <= config.maxPages());
        return jobs;
    }

    @Override
    public String fetchDescription(WatchedCompany company, String externalId, String url) {
        JsonNode detail = jobDetails(externalId);
        String description = detail.path("stellenangebotsBeschreibung").asString(null);
        return description == null || description.isBlank() ? null : description.trim();
    }

    /** Also used by the URL→JD fetch for pasted arbeitsagentur.de links. */
    public JsonNode jobDetails(String refnr) {
        String encoded = Base64.getUrlEncoder().encodeToString(refnr.getBytes(StandardCharsets.UTF_8));
        String body = restClient.get()
                .uri(BASE + "/pc/v3/jobdetails/" + encoded)
                .header("X-API-Key", API_KEY)
                .header("Accept", "application/json")
                .header("User-Agent", WorkdayAdapter.BROWSER_UA)
                .retrieve()
                .body(String.class);
        return objectMapper.readTree(body);
    }

    private String searchPage(Config config, int page) {
        String uri = BASE + "/pc/v4/jobs?was=" + URLEncoder.encode(config.was(), StandardCharsets.UTF_8)
                + "&wo=" + URLEncoder.encode(config.wo(), StandardCharsets.UTF_8)
                + "&size=" + PAGE_SIZE + "&page=" + page;
        return restClient.get()
                .uri(uri)
                .header("X-API-Key", API_KEY)
                .header("Accept", "application/json")
                .header("User-Agent", WorkdayAdapter.BROWSER_UA)
                .retrieve()
                .body(String.class);
    }

    /** The watched "company" is the job board, so the real employer joins the title. */
    private String title(JsonNode job) {
        String title = job.path("titel").asString("").trim();
        String employer = job.path("arbeitgeber").asString("").trim();
        return employer.isBlank() ? title : title + " · " + employer;
    }

    private String location(JsonNode arbeitsort) {
        String ort = arbeitsort.path("ort").asString(null);
        String region = arbeitsort.path("region").asString(null);
        if (ort == null || ort.isBlank()) {
            return region;
        }
        return region == null || region.isBlank() ? ort : ort + ", " + region;
    }

    private Instant postedAt(JsonNode job) {
        String date = job.path("aktuelleVeroeffentlichungsdatum").asString(null);
        if (date == null) {
            return null;
        }
        try {
            return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    record Config(String was, String wo, int maxPages) {
    }

    Config config(WatchedCompany company) {
        JsonNode node = objectMapper.readTree(company.getConfigJson());
        String was = node.path("was").asString("");
        if (was.isBlank()) {
            throw new IllegalArgumentException(
                    "Arbeitsagentur config needs {\"was\": \"<search keywords>\"} "
                            + "(plus optional \"wo\" and \"maxPages\")");
        }
        String wo = node.path("wo").asString("Deutschland");
        int maxPages = node.path("maxPages").asInt(DEFAULT_MAX_PAGES);
        return new Config(was, wo.isBlank() ? "Deutschland" : wo, Math.max(1, maxPages));
    }
}
