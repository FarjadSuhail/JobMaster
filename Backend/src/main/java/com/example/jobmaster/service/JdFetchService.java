package com.example.jobmaster.service;

import com.example.jobmaster.dto.JdFetchResponse;
import com.example.jobmaster.watcher.WatcherService;
import com.example.jobmaster.watcher.adapter.ArbeitsagenturAdapter;
import com.example.jobmaster.watcher.adapter.AshbyAdapter;
import com.example.jobmaster.watcher.adapter.BeeSiteAdapter;
import com.example.jobmaster.watcher.adapter.HtmlText;
import com.example.jobmaster.watcher.adapter.WorkdayAdapter;
import com.example.jobmaster.watcher.domain.AdapterType;
import com.example.jobmaster.watcher.domain.WatchedCompany;
import com.example.jobmaster.watcher.dto.SaveWatchedCompanyRequest;
import com.example.jobmaster.watcher.dto.WatchedCompanyDto;
import com.example.jobmaster.watcher.repository.WatchedCompanyRepository;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches a job description from any posting URL, best effort, in layers:
 *   1. Known ATS URL → its official API (cleanest text).
 *   2. JSON-LD JobPosting embedded in the page (most career sites add it
 *      for Google Jobs indexing).
 *   3. Readable-text extraction from the page HTML (user should review).
 * Sites behind bot walls (e.g. Revolut, LinkedIn) fail with a clear message
 * telling the user to paste the description manually.
 */
@Service
public class JdFetchService {

    private static final Logger log = LoggerFactory.getLogger(JdFetchService.class);

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 15_000;
    private static final int MIN_JD_CHARS = 400;
    private static final int MAX_JD_CHARS = 60_000;

    private static final Pattern SMARTRECRUITERS_URL =
            Pattern.compile("(?:jobs|careers)\\.smartrecruiters\\.com/([^/]+)/(\\d+)");
    private static final Pattern GREENHOUSE_URL =
            Pattern.compile("(?:job-)?boards(?:\\.eu)?\\.greenhouse\\.io/([^/]+)/jobs/(\\d+)");
    private static final Pattern PERSONIO_URL =
            Pattern.compile("([a-z0-9-]+\\.jobs\\.personio\\.(?:de|com))/job/\\d+");
    /** e.g. cerence.wd5.myworkdayjobs.com/(en-US/)?Cerence/job/Ulm/Title_R0005959 */
    private static final Pattern WORKDAY_URL = Pattern.compile(
            "https?://([a-z0-9-]+)\\.(wd\\d+)\\.myworkdayjobs\\.com/(?:[a-z]{2}-[A-Z]{2}/)?([^/?#]+)(/job/[^?#]+)");
    private static final Pattern ASHBY_URL = Pattern.compile(
            "jobs\\.ashbyhq\\.com/([^/?#]+)/([0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12})");
    /** Deutsche Bank careers put the job id in the URL fragment: #/professional/job/71753 */
    private static final Pattern DB_BEESITE_URL =
            Pattern.compile("careers\\.db\\.com/.*?/job/(\\d+)");
    private static final Pattern ARBEITSAGENTUR_URL =
            Pattern.compile("arbeitsagentur\\.de/jobsuche/jobdetail/([A-Za-z0-9_-]+)");
    private static final String DB_API_HOST = "api-deutschebank.beesite.de";
    private static final String DB_JOB_URL_TEMPLATE =
            "https://careers.db.com/professionals/search-roles/#/professional/job/{id}";

    /** Title filters for auto-added companies, shared with bulk discovery. */
    private static final String DEFAULT_INCLUDE_KEYWORDS =
            com.example.jobmaster.watcher.DiscoveryService.DEFAULT_INCLUDE_KEYWORDS;

    /** After one HTML→text pass, output that still looks like markup was double-escaped. */
    private static final Pattern LOOKS_LIKE_HTML =
            Pattern.compile("<\\s*(p|div|h[1-6]|ul|ol|li|br|span|strong|b|em)[\\s/>]",
                    Pattern.CASE_INSENSITIVE);

    /** Selectors career sites commonly wrap the description in, best first. */
    private static final List<String> JD_SELECTORS = List.of(
            "[itemprop=description]",
            ".job-description", ".jobdescription", ".job__description", "#job-description",
            ".posting-content", ".jobad", ".job-ad", "article", "main");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final WatcherService watcherService;
    private final WatchedCompanyRepository companyRepository;
    private final WorkdayAdapter workdayAdapter;
    private final AshbyAdapter ashbyAdapter;
    private final BeeSiteAdapter beeSiteAdapter;
    private final ArbeitsagenturAdapter arbeitsagenturAdapter;

    public JdFetchService(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
                          WatcherService watcherService,
                          WatchedCompanyRepository companyRepository,
                          WorkdayAdapter workdayAdapter,
                          AshbyAdapter ashbyAdapter,
                          BeeSiteAdapter beeSiteAdapter,
                          ArbeitsagenturAdapter arbeitsagenturAdapter) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.watcherService = watcherService;
        this.companyRepository = companyRepository;
        this.workdayAdapter = workdayAdapter;
        this.ashbyAdapter = ashbyAdapter;
        this.beeSiteAdapter = beeSiteAdapter;
        this.arbeitsagenturAdapter = arbeitsagenturAdapter;
    }

    public JdFetchResponse fetch(String url) {
        URI uri = validate(url);
        JdFetchResponse response = extract(url, uri);
        return withWatchMessage(response, autoWatch(url, response.company()));
    }

    private JdFetchResponse extract(String url, URI uri) {
        // Layer 1: URLs of platforms with public APIs — skip the HTML entirely
        Matcher sr = SMARTRECRUITERS_URL.matcher(url);
        if (sr.find()) {
            return fromSmartRecruiters(sr.group(1), sr.group(2));
        }
        Matcher gh = GREENHOUSE_URL.matcher(url);
        if (gh.find()) {
            return fromGreenhouse(gh.group(1), gh.group(2));
        }
        Matcher wd = WORKDAY_URL.matcher(url);
        if (wd.find()) {
            return fromWorkday(wd.group(1) + "." + wd.group(2) + ".myworkdayjobs.com",
                    wd.group(1), wd.group(3), wd.group(4));
        }
        Matcher ashby = ASHBY_URL.matcher(url);
        if (ashby.find()) {
            return fromAshby(ashby.group(1), ashby.group(2));
        }
        Matcher db = DB_BEESITE_URL.matcher(url);
        if (db.find()) {
            return fromDbBeeSite(db.group(1));
        }
        Matcher aa = ARBEITSAGENTUR_URL.matcher(url);
        if (aa.find()) {
            return fromArbeitsagentur(aa.group(1));
        }

        Document doc = fetchPage(uri);

        // Layer 2: JSON-LD JobPosting (what Google Jobs reads)
        JdFetchResponse fromLd = fromJsonLd(doc);
        if (fromLd != null) {
            return fromLd;
        }

        // Layer 3: readable text out of the page
        JdFetchResponse fromHtml = fromPageText(doc);
        if (fromHtml != null) {
            return fromHtml;
        }

        throw new JdFetchException("Couldn't find a job description on that page. It probably "
                + "loads its content with JavaScript only. Please copy the description "
                + "from your browser and paste it manually.");
    }

    // ---------- auto-watch ----------

    /**
     * When the URL sits on a platform the watcher supports, make sure the
     * company is on the watch list: add it (with the list's usual tech title
     * filters) and kick off its silent baseline import in the background.
     */
    private String autoWatch(String url, String companyName) {
        AdapterType type;
        String identifier;
        String configJson;

        Matcher sr = SMARTRECRUITERS_URL.matcher(url);
        Matcher gh = GREENHOUSE_URL.matcher(url);
        Matcher po = PERSONIO_URL.matcher(url.toLowerCase());
        Matcher wd = WORKDAY_URL.matcher(url);
        Matcher ashby = ASHBY_URL.matcher(url);
        if (sr.find()) {
            type = AdapterType.SMARTRECRUITERS;
            identifier = sr.group(1);
            configJson = "{\"companyId\": \"" + identifier + "\"}";
        } else if (gh.find()) {
            type = AdapterType.GREENHOUSE;
            identifier = gh.group(1);
            configJson = "{\"boardToken\": \"" + identifier + "\"}";
        } else if (po.find()) {
            type = AdapterType.PERSONIO;
            identifier = po.group(1);
            configJson = "{\"host\": \"" + identifier + "\"}";
        } else if (wd.find()) {
            type = AdapterType.WORKDAY;
            String host = wd.group(1) + "." + wd.group(2) + ".myworkdayjobs.com";
            String site = wd.group(3);
            identifier = host + "|" + site;
            configJson = "{\"host\": \"" + host + "\", \"tenant\": \"" + wd.group(1)
                    + "\", \"site\": \"" + site + "\"}";
        } else if (ashby.find()) {
            type = AdapterType.ASHBY;
            identifier = ashby.group(1);
            configJson = "{\"jobBoardName\": \"" + identifier + "\"}";
        } else if (DB_BEESITE_URL.matcher(url).find()) {
            type = AdapterType.BEESITE;
            identifier = DB_API_HOST;
            configJson = "{\"apiHost\": \"" + DB_API_HOST + "\", \"jobUrlTemplate\": \""
                    + DB_JOB_URL_TEMPLATE + "\"}";
        } else if (ARBEITSAGENTUR_URL.matcher(url).find()) {
            // a federal job-board link, not a company portal — nothing to watch here
            return "This posting comes from the Arbeitsagentur job board, which your "
                    + "Arbeitsagentur search entries already cover. To watch the employer "
                    + "directly, paste a link from their own careers portal.";
        } else {
            return "This site isn't on a platform the watcher supports, so the company "
                    + "couldn't be added to the watch list automatically.";
        }

        WatchedCompany existing = findWatched(type, identifier);
        if (existing != null) {
            return existing.getName() + " is already on the watch list.";
        }

        String name = companyName != null ? companyName : identifier;
        try {
            WatchedCompanyDto created = watcherService.createCompany(new SaveWatchedCompanyRequest(
                    name, type, configJson, DEFAULT_INCLUDE_KEYWORDS, null, null, true));
            baselineInBackground(created.id(), name);
            return name + " was added to the watch list — its current postings are being "
                    + "imported silently in the background (only jobs posted later count as "
                    + "new). Tune its filters in the Jobs tab.";
        } catch (Exception e) {
            log.warn("Auto-watch failed for {} ({})", name, identifier, e);
            return "Couldn't add " + name + " to the watch list automatically: " + e.getMessage();
        }
    }

    /** Same platform + same identifier in the config = already watched. */
    private WatchedCompany findWatched(AdapterType type, String identifier) {
        for (WatchedCompany company : companyRepository.findAll()) {
            if (company.getAdapterType() != type) {
                continue;
            }
            try {
                JsonNode config = objectMapper.readTree(company.getConfigJson());
                String existing = switch (type) {
                    case SMARTRECRUITERS -> config.path("companyId").asString("");
                    case GREENHOUSE -> config.path("boardToken").asString("");
                    case PERSONIO -> config.path("host").asString("");
                    case WORKDAY -> config.path("host").asString("") + "|"
                            + config.path("site").asString("");
                    case ASHBY -> config.path("jobBoardName").asString("");
                    case BEESITE -> config.path("apiHost").asString("");
                    default -> "";
                };
                if (existing.equalsIgnoreCase(identifier)) {
                    return company;
                }
            } catch (Exception ignored) {
                // unreadable config — treat as not matching
            }
        }
        return null;
    }

    private void baselineInBackground(Long companyId, String name) {
        Thread thread = new Thread(() -> {
            try {
                watcherService.runCompany(companyId);
            } catch (Exception e) {
                log.warn("Baseline import for auto-watched {} failed: {}", name, e.getMessage());
            }
        }, "jd-autowatch-baseline");
        thread.setDaemon(true);
        thread.start();
    }

    private JdFetchResponse withWatchMessage(JdFetchResponse r, String watchMessage) {
        return new JdFetchResponse(r.jobTitle(), r.company(), r.location(),
                r.jobDescription(), r.source(), watchMessage);
    }

    private URI validate(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new JdFetchException("That doesn't look like a valid URL.");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            throw new JdFetchException("Only http(s) links are supported.");
        }
        String host = uri.getHost() == null ? "" : uri.getHost();
        if (host.contains("linkedin.com")) {
            throw new JdFetchException("LinkedIn blocks automated access. Open the posting and "
                    + "follow its apply link to the company's own careers page, then paste "
                    + "that URL here instead.");
        }
        return uri;
    }

    private Document fetchPage(URI uri) {
        try {
            return Jsoup.connect(uri.toString())
                    .userAgent(BROWSER_UA)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .get();
        } catch (HttpStatusException e) {
            if (e.getStatusCode() == 403 || e.getStatusCode() == 401 || e.getStatusCode() == 429) {
                throw new JdFetchException("This site blocks automated access (HTTP "
                        + e.getStatusCode() + "). Please paste the job description manually.");
            }
            throw new JdFetchException("The page returned HTTP " + e.getStatusCode()
                    + " — check the link is still valid.");
        } catch (Exception e) {
            throw new JdFetchException("Couldn't reach that page: " + e.getMessage());
        }
    }

    // ---------- layer 1: official APIs ----------

    private JdFetchResponse fromSmartRecruiters(String companyId, String postingId) {
        try {
            String body = restClient.get()
                    .uri("https://api.smartrecruiters.com/v1/companies/{id}/postings/{postingId}",
                            companyId, postingId)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode sections = root.path("jobAd").path("sections");
            StringBuilder text = new StringBuilder();
            for (String section : new String[]{"companyDescription", "jobDescription",
                    "qualifications", "additionalInformation"}) {
                String sectionText =
                        HtmlText.toPlainText(sections.path(section).path("text").asString(null));
                if (sectionText != null) {
                    String title = sections.path(section).path("title").asString(null);
                    if (title != null && !title.isBlank()) {
                        text.append(title).append('\n');
                    }
                    text.append(sectionText).append("\n\n");
                }
            }
            if (text.isEmpty()) {
                throw new JdFetchException("SmartRecruiters returned no description for this posting.");
            }
            JsonNode location = root.path("location");
            return response(root.path("name").asString(null),
                    root.path("company").path("name").asString(null),
                    joinNonBlank(location.path("city").asString(null),
                            location.path("country").asString(null)),
                    text.toString().trim(), "SmartRecruiters API");
        } catch (JdFetchException e) {
            throw e;
        } catch (Exception e) {
            throw new JdFetchException("SmartRecruiters lookup failed for this URL: " + e.getMessage(), e);
        }
    }

    private JdFetchResponse fromGreenhouse(String boardToken, String jobId) {
        try {
            String body = restClient.get()
                    .uri("https://boards-api.greenhouse.io/v1/boards/{token}/jobs/{id}",
                            boardToken, jobId)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            // Greenhouse returns the description as HTML-escaped HTML
            String content = root.path("content").asString(null);
            String text = HtmlText.toPlainText(
                    Parser.unescapeEntities(content == null ? "" : content, false));
            if (text == null) {
                throw new JdFetchException("Greenhouse returned no description for this posting.");
            }
            return response(root.path("title").asString(null),
                    root.path("company_name").asString(null),
                    root.path("location").path("name").asString(null),
                    text, "Greenhouse API");
        } catch (JdFetchException e) {
            throw e;
        } catch (Exception e) {
            throw new JdFetchException("Greenhouse lookup failed for this URL: " + e.getMessage(), e);
        }
    }

    private JdFetchResponse fromWorkday(String host, String tenant, String site, String path) {
        try {
            JsonNode root = workdayAdapter.detail(host, tenant, site, path);
            JsonNode info = root.path("jobPostingInfo");
            String description = HtmlText.toPlainText(info.path("jobDescription").asString(null));
            if (description == null) {
                throw new JdFetchException("Workday returned no description for this posting.");
            }
            String company = firstNonBlank(
                    root.path("hiringOrganization").path("name").asString(""), site);
            return response(info.path("title").asString(null), company,
                    info.path("location").asString(null), description, "Workday API");
        } catch (JdFetchException e) {
            throw e;
        } catch (Exception e) {
            throw new JdFetchException("Workday lookup failed for this URL: " + e.getMessage(), e);
        }
    }

    private JdFetchResponse fromAshby(String board, String jobId) {
        try {
            for (JsonNode job : ashbyAdapter.board(board).path("jobs")) {
                if (!jobId.equalsIgnoreCase(job.path("id").asString(""))) {
                    continue;
                }
                String description = job.path("descriptionPlain").asString(null);
                if (description == null || description.isBlank()) {
                    description = HtmlText.toPlainText(job.path("descriptionHtml").asString(null));
                }
                if (description == null) {
                    throw new JdFetchException("Ashby returned no description for this posting.");
                }
                String company = Character.toUpperCase(board.charAt(0)) + board.substring(1);
                return response(job.path("title").asString(null), company,
                        job.path("location").asString(null), description.trim(), "Ashby API");
            }
            throw new JdFetchException("This posting is no longer on the company's Ashby board — "
                    + "it may have been taken down.");
        } catch (JdFetchException e) {
            throw e;
        } catch (Exception e) {
            throw new JdFetchException("Ashby lookup failed for this URL: " + e.getMessage(), e);
        }
    }

    private JdFetchResponse fromDbBeeSite(String positionId) {
        try {
            String html = beeSiteAdapter.jobHtmlRaw(DB_API_HOST, positionId);
            String description = HtmlText.toPlainText(html);
            if (description == null) {
                throw new JdFetchException("Deutsche Bank returned no description for job "
                        + positionId + " — the posting may be gone.");
            }
            Element h1 = org.jsoup.Jsoup.parse(html).selectFirst("h1");
            return response(h1 == null ? null : h1.text(), "Deutsche Bank", null,
                    description, "Deutsche Bank careers API");
        } catch (JdFetchException e) {
            throw e;
        } catch (Exception e) {
            throw new JdFetchException("Deutsche Bank lookup failed for this URL: "
                    + e.getMessage(), e);
        }
    }

    private JdFetchResponse fromArbeitsagentur(String refnr) {
        try {
            JsonNode detail = arbeitsagenturAdapter.jobDetails(refnr);
            String description = detail.path("stellenangebotsBeschreibung").asString(null);
            if (description == null || description.isBlank()) {
                throw new JdFetchException("The Arbeitsagentur has no description for this posting "
                        + "(some only link to the employer's site). Please paste it manually.");
            }
            JsonNode address = detail.path("stellenlokationen");
            if (address.isArray() && !address.isEmpty()) {
                address = address.get(0);
            }
            return response(detail.path("stellenangebotsTitel").asString(null),
                    detail.path("firma").asString(null),
                    address.path("adresse").path("ort").asString(null),
                    description.trim(), "Arbeitsagentur API");
        } catch (JdFetchException e) {
            throw e;
        } catch (Exception e) {
            throw new JdFetchException("Arbeitsagentur lookup failed for this URL: "
                    + e.getMessage(), e);
        }
    }

    // ---------- layer 2: JSON-LD ----------

    private JdFetchResponse fromJsonLd(Document doc) {
        for (Element script : doc.select("script[type=application/ld+json]")) {
            try {
                JsonNode root = objectMapper.readTree(script.data());
                for (JsonNode node : flatten(root)) {
                    if (!isJobPosting(node)) {
                        continue;
                    }
                    String description = htmlToText(node.path("description").asString(null));
                    if (description == null || description.length() < MIN_JD_CHARS) {
                        continue;
                    }
                    return response(node.path("title").asString(null),
                            node.path("hiringOrganization").path("name").asString(null),
                            jsonLdLocation(node),
                            description, "page (structured data)");
                }
            } catch (Exception e) {
                log.debug("Skipping unparseable JSON-LD block: {}", e.getMessage());
            }
        }
        return null;
    }

    /** JSON-LD roots can be a single object, an array, or wrapped in @graph. */
    private List<JsonNode> flatten(JsonNode root) {
        List<JsonNode> nodes = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(nodes::add);
        } else {
            nodes.add(root);
        }
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode node : nodes) {
            result.add(node);
            if (node.has("@graph")) {
                node.path("@graph").forEach(result::add);
            }
        }
        return result;
    }

    private boolean isJobPosting(JsonNode node) {
        JsonNode type = node.path("@type");
        if (type.isArray()) {
            for (JsonNode t : type) {
                if ("JobPosting".equalsIgnoreCase(t.asString(""))) {
                    return true;
                }
            }
            return false;
        }
        return "JobPosting".equalsIgnoreCase(type.asString(""));
    }

    private String jsonLdLocation(JsonNode posting) {
        JsonNode loc = posting.path("jobLocation");
        if (loc.isArray()) {
            loc = loc.isEmpty() ? loc : loc.get(0);
        }
        JsonNode address = loc.path("address");
        return joinNonBlank(address.path("addressLocality").asString(null),
                address.path("addressCountry").asString(null));
    }

    // ---------- layer 3: readable page text ----------

    private JdFetchResponse fromPageText(Document doc) {
        String title = firstNonBlank(
                doc.select("meta[property=og:title]").attr("content"), doc.title());
        String company = doc.select("meta[property=og:site_name]").attr("content");

        for (String selector : JD_SELECTORS) {
            Element el = doc.selectFirst(selector);
            if (el == null) {
                continue;
            }
            String text = HtmlText.toPlainText(el.html());
            if (text != null && text.length() >= MIN_JD_CHARS) {
                return response(title, company, null, text,
                        "page text — review it before generating");
            }
        }

        // last resort: whole page minus obvious chrome
        doc.select("nav, header, footer, script, style, noscript, form").remove();
        String text = HtmlText.toPlainText(doc.body() == null ? null : doc.body().html());
        if (text != null && text.length() >= MIN_JD_CHARS * 2) {
            return response(title, company, null, text,
                    "page text — review it before generating");
        }
        return null;
    }

    // ---------- helpers ----------

    /**
     * HTML → text, with a second pass when the source was HTML-escaped HTML
     * (e.g. Greenhouse-generated JSON-LD): the first parse only decodes the
     * entities, so tags survive as literal text and need one more parse.
     */
    private String htmlToText(String html) {
        String text = HtmlText.toPlainText(html);
        if (text != null && LOOKS_LIKE_HTML.matcher(text).find()) {
            text = HtmlText.toPlainText(text);
        }
        return text;
    }

    private JdFetchResponse response(String title, String company, String location,
                                     String description, String source) {
        if (description.length() > MAX_JD_CHARS) {
            description = description.substring(0, MAX_JD_CHARS);
        }
        return new JdFetchResponse(blankToNull(title), blankToNull(company),
                blankToNull(location), description, source, null);
    }

    private String joinNonBlank(String a, String b) {
        if (a == null || a.isBlank()) {
            return b == null || b.isBlank() ? null : b;
        }
        return b == null || b.isBlank() ? a : a + ", " + b;
    }

    private String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
