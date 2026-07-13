package com.example.jobmaster.watcher.adapter;

import com.example.jobmaster.watcher.domain.AdapterType;
import com.example.jobmaster.watcher.domain.WatchedCompany;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SuccessFactors career sites (e.g. jobs.sap.com) — no official API; parses
 * the server-rendered job list pages. The most change-sensitive adapter:
 * failures surface in the run log instead of breaking anything else.
 * Config: {"baseUrl": "https://jobs.sap.com", "listPath": "/go/SAP-Jobs-in-Germany/850601/", "maxPages": 10}
 */
@Component
public class SuccessFactorsAdapter implements JobPortalAdapter {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Safari/537.36";
    private static final int PAGE_SIZE = 25;
    private static final Pattern JOB_ID = Pattern.compile("/(\\d+)/?$");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private final ObjectMapper objectMapper;

    public SuccessFactorsAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AdapterType type() {
        return AdapterType.SUCCESSFACTORS;
    }

    @Override
    public String fetchDescription(WatchedCompany company, String externalId, String url) throws Exception {
        if (url == null || url.isBlank()) {
            return null;
        }
        Document doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(20_000).get();
        // SuccessFactors job pages mark the body up with itemprop="description";
        // the other selectors cover older layout variants.
        for (String selector : new String[]{"[itemprop=description]", ".jobdescription",
                "div.job", "div.joqReqDescription"}) {
            Element element = doc.selectFirst(selector);
            if (element != null) {
                String text = HtmlText.toPlainText(element.html());
                if (text != null && text.length() > 100) {
                    return text;
                }
            }
        }
        return null;
    }

    @Override
    public List<FetchedJob> fetch(WatchedCompany company) throws Exception {
        JsonNode config = objectMapper.readTree(company.getConfigJson());
        String baseUrl = config.path("baseUrl").asString();
        String listPath = config.path("listPath").asString();
        int maxPages = config.path("maxPages").asInt(10);
        if (baseUrl == null || baseUrl.isBlank() || listPath == null || listPath.isBlank()) {
            throw new IllegalArgumentException(
                    "SuccessFactors config needs {\"baseUrl\": ..., \"listPath\": ...}");
        }

        List<FetchedJob> jobs = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (int page = 0; page < maxPages; page++) {
            int offset = page * PAGE_SIZE;
            String url = baseUrl + listPath + (offset == 0 ? "" : offset + "/");
            Document doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(20_000).get();

            int newOnPage = 0;
            for (FetchedJob job : parsePage(doc, baseUrl)) {
                if (seenIds.add(job.externalId())) {
                    jobs.add(job);
                    newOnPage++;
                }
            }
            // pagination exhausted (repeated or empty page)
            if (newOnPage == 0) {
                break;
            }
            Thread.sleep(700); // politeness between pages
        }
        return jobs;
    }

    private List<FetchedJob> parsePage(Document doc, String baseUrl) {
        List<FetchedJob> jobs = new ArrayList<>();

        // Classic SuccessFactors list markup: one <tr class="data-row"> per job
        for (Element row : doc.select("tr.data-row")) {
            Element link = row.selectFirst("a.jobTitle-link");
            if (link == null) {
                continue;
            }
            String externalId = extractId(link.attr("href"));
            if (externalId == null) {
                continue;
            }
            jobs.add(new FetchedJob(
                    externalId,
                    link.text().trim(),
                    textOf(row.selectFirst("span.jobLocation")),
                    textOf(row.selectFirst("span.jobDepartment")),
                    baseUrl + link.attr("href"),
                    parseDate(textOf(row.selectFirst("span.jobDate")))));
        }
        if (!jobs.isEmpty()) {
            return jobs;
        }

        // Fallback for layout variants: any jobTitle-link anchors, deduped by id
        Set<String> ids = new HashSet<>();
        for (Element link : doc.select("a.jobTitle-link")) {
            String externalId = extractId(link.attr("href"));
            if (externalId == null || !ids.add(externalId)) {
                continue;
            }
            jobs.add(new FetchedJob(externalId, link.text().trim(), null, null,
                    baseUrl + link.attr("href"), null));
        }
        return jobs;
    }

    private String extractId(String href) {
        Matcher matcher = JOB_ID.matcher(href == null ? "" : href.trim());
        return matcher.find() ? matcher.group(1) : null;
    }

    private String textOf(Element element) {
        if (element == null) {
            return null;
        }
        String text = element.text().trim();
        return text.isBlank() ? null : text;
    }

    private Instant parseDate(String text) {
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text, DATE).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (Exception e) {
            return null;
        }
    }
}
