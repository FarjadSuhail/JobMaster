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
 * Workday recruiting (CXS JSON API, no auth):
 *   list:   POST https://{host}/wday/cxs/{tenant}/{site}/jobs
 *   detail: GET  https://{host}/wday/cxs/{tenant}/{site}{externalPath}
 * Config: {"host": "cerence.wd5.myworkdayjobs.com", "tenant": "cerence", "site": "Cerence"}
 * The API silently returns an empty list for limit > 20, so page size is fixed.
 */
@Component
public class WorkdayAdapter implements JobPortalAdapter {

    static final String BROWSER_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final int PAGE_SIZE = 20;
    private static final int MAX_POSTINGS = 3000;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public WorkdayAdapter(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public AdapterType type() {
        return AdapterType.WORKDAY;
    }

    @Override
    public List<FetchedJob> fetch(WatchedCompany company) {
        Config config = config(company);
        List<FetchedJob> jobs = new ArrayList<>();
        int offset = 0;
        long total;
        do {
            JsonNode root = objectMapper.readTree(listPage(config, offset));
            total = root.path("total").asLong(0);
            for (JsonNode posting : root.path("jobPostings")) {
                String path = posting.path("externalPath").asString(null);
                if (path == null || path.isBlank()) {
                    continue;
                }
                jobs.add(new FetchedJob(
                        path,
                        posting.path("title").asString("").trim(),
                        blankToNull(posting.path("locationsText").asString(null)),
                        null,
                        "https://" + config.host() + "/" + config.site() + path,
                        // list API only has "Posted N Days Ago" text; no reliable date
                        null));
            }
            offset += PAGE_SIZE;
        } while (offset < total && offset < MAX_POSTINGS);
        return jobs;
    }

    @Override
    public String fetchDescription(WatchedCompany company, String externalId, String url) {
        Config config = config(company);
        JsonNode info = detail(config.host(), config.tenant(), config.site(), externalId)
                .path("jobPostingInfo");
        return HtmlText.toPlainText(info.path("jobDescription").asString(null));
    }

    String listPage(Config config, int offset) {
        return restClient.post()
                .uri("https://{host}/wday/cxs/{tenant}/{site}/jobs",
                        config.host(), config.tenant(), config.site())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", BROWSER_UA)
                .body("{\"limit\":" + PAGE_SIZE + ",\"offset\":" + offset
                        + ",\"searchText\":\"\",\"appliedFacets\":{}}")
                .retrieve()
                .body(String.class);
    }

    /** Also used by the URL→JD fetch, which has no WatchedCompany yet. */
    public JsonNode detail(String host, String tenant, String site, String externalPath) {
        String body = restClient.get()
                .uri("https://" + host + "/wday/cxs/" + tenant + "/" + site + externalPath)
                .header("Accept", "application/json")
                .header("User-Agent", BROWSER_UA)
                .retrieve()
                .body(String.class);
        return objectMapper.readTree(body);
    }

    record Config(String host, String tenant, String site) {
    }

    Config config(WatchedCompany company) {
        JsonNode node = objectMapper.readTree(company.getConfigJson());
        String host = node.path("host").asString("");
        String tenant = node.path("tenant").asString("");
        String site = node.path("site").asString("");
        if (host.isBlank() || tenant.isBlank() || site.isBlank()) {
            throw new IllegalArgumentException(
                    "Workday config needs {\"host\", \"tenant\", \"site\"}");
        }
        return new Config(host, tenant, site);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
