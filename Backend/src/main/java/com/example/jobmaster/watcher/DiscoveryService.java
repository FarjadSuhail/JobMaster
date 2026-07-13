package com.example.jobmaster.watcher;

import com.example.jobmaster.watcher.domain.AdapterType;
import com.example.jobmaster.watcher.domain.WatchedCompany;
import com.example.jobmaster.watcher.dto.DiscoveryHit;
import com.example.jobmaster.watcher.dto.SaveWatchedCompanyRequest;
import com.example.jobmaster.watcher.dto.WatchedCompanyDto;
import com.example.jobmaster.watcher.repository.WatchedCompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Finds watchable career boards: probes candidate company names against the
 * public Greenhouse / SmartRecruiters / Ashby APIs (their identifiers are
 * usually a predictable slug of the company name) and reports verified boards
 * with live job counts, ready for one-click adding.
 */
@Service
public class DiscoveryService {

    /** Standard title filters for bulk/auto-added companies. */
    public static final String DEFAULT_INCLUDE_KEYWORDS =
            "software|developer|engineer|backend|full stack|fullstack|full-stack|java|cloud|devops";

    private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);
    private static final int PROBE_THREADS = 12;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final WatchedCompanyRepository companyRepository;
    private final WatcherService watcherService;

    public DiscoveryService(ObjectMapper objectMapper,
                            WatchedCompanyRepository companyRepository,
                            WatcherService watcherService) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.objectMapper = objectMapper;
        this.companyRepository = companyRepository;
        this.watcherService = watcherService;
    }

    public List<DiscoveryHit> discover(List<String> customNames) {
        List<String> names = customNames == null || customNames.isEmpty()
                ? builtinNames()
                : customNames.stream().map(String::trim).filter(n -> !n.isEmpty()).toList();

        Map<String, String> watched = watchedIdentifiers();
        List<DiscoveryHit> hits = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(PROBE_THREADS);
        try {
            List<Future<DiscoveryHit>> futures = names.stream()
                    .map(name -> pool.submit(() -> probe(name, watched)))
                    .toList();
            for (Future<DiscoveryHit> future : futures) {
                try {
                    DiscoveryHit hit = future.get();
                    if (hit != null) {
                        hits.add(hit);
                    }
                } catch (Exception e) {
                    log.debug("Discovery probe failed: {}", e.getMessage());
                }
            }
        } finally {
            pool.shutdown();
        }
        hits.sort(Comparator.comparingLong(DiscoveryHit::jobCount).reversed());
        return hits;
    }

    /** Adds the given verified boards with the standard tech filters; baselines run in background. */
    public List<WatchedCompanyDto> addDiscovered(List<DiscoveryHit> items) {
        Map<String, String> watched = watchedIdentifiers();
        List<WatchedCompanyDto> created = new ArrayList<>();
        for (DiscoveryHit item : items) {
            if (watched.containsKey(item.adapterType() + "|" + item.identifier().toLowerCase(Locale.ROOT))) {
                continue;
            }
            WatchedCompanyDto dto = watcherService.createCompany(new SaveWatchedCompanyRequest(
                    item.name(), item.adapterType(), item.configJson(),
                    DEFAULT_INCLUDE_KEYWORDS, null, null, true));
            created.add(dto);
            baselineInBackground(dto.id(), dto.name());
        }
        return created;
    }

    // ---------- probing ----------

    private DiscoveryHit probe(String name, Map<String, String> watched) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        String srId = name.replaceAll("[^A-Za-z0-9]", "");
        if (slug.isBlank()) {
            return null;
        }

        Long count = probeGreenhouse(slug);
        if (count != null) {
            return hit(name, AdapterType.GREENHOUSE, slug,
                    "{\"boardToken\": \"" + slug + "\"}", count, watched);
        }
        count = probeSmartRecruiters(srId);
        if (count != null) {
            return hit(name, AdapterType.SMARTRECRUITERS, srId,
                    "{\"companyId\": \"" + srId + "\"}", count, watched);
        }
        count = probeAshby(slug);
        if (count != null) {
            return hit(name, AdapterType.ASHBY, slug,
                    "{\"jobBoardName\": \"" + slug + "\"}", count, watched);
        }
        return null;
    }

    private DiscoveryHit hit(String name, AdapterType type, String identifier,
                             String configJson, long jobCount, Map<String, String> watched) {
        String watchedAs = watched.get(type + "|" + identifier.toLowerCase(Locale.ROOT));
        return new DiscoveryHit(name, type, identifier, configJson, jobCount,
                watchedAs != null, watchedAs);
    }

    private Long probeGreenhouse(String token) {
        try {
            String body = restClient.get()
                    .uri("https://boards-api.greenhouse.io/v1/boards/{t}/jobs", token)
                    .retrieve().body(String.class);
            long count = objectMapper.readTree(body).path("jobs").size();
            return count > 0 ? count : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Long probeSmartRecruiters(String companyId) {
        try {
            String body = restClient.get()
                    .uri("https://api.smartrecruiters.com/v1/companies/{id}/postings?limit=1", companyId)
                    .retrieve().body(String.class);
            long count = objectMapper.readTree(body).path("totalFound").asLong(0);
            return count > 0 ? count : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Long probeAshby(String boardName) {
        try {
            String body = restClient.get()
                    .uri("https://api.ashbyhq.com/posting-api/job-board/{n}", boardName)
                    .retrieve().body(String.class);
            long count = objectMapper.readTree(body).path("jobs").size();
            return count > 0 ? count : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- helpers ----------

    private List<String> builtinNames() {
        try {
            JsonNode array = objectMapper.readTree(
                    new ClassPathResource("discovery-companies.json").getContentAsByteArray());
            List<String> names = new ArrayList<>();
            array.forEach(n -> names.add(n.asString()));
            return names;
        } catch (Exception e) {
            throw new IllegalStateException("Bundled discovery-companies.json is unreadable", e);
        }
    }

    /** "TYPE|identifier(lowercase)" → watched company name, for dedupe/labeling. */
    private Map<String, String> watchedIdentifiers() {
        Map<String, String> result = new ConcurrentHashMap<>();
        for (WatchedCompany company : companyRepository.findAll()) {
            try {
                JsonNode config = objectMapper.readTree(company.getConfigJson());
                String identifier = switch (company.getAdapterType()) {
                    case GREENHOUSE -> config.path("boardToken").asString("");
                    case SMARTRECRUITERS -> config.path("companyId").asString("");
                    case ASHBY -> config.path("jobBoardName").asString("");
                    default -> "";
                };
                if (!identifier.isBlank()) {
                    result.put(company.getAdapterType() + "|" + identifier.toLowerCase(Locale.ROOT),
                            company.getName());
                }
            } catch (Exception ignored) {
                // unreadable config — skip
            }
        }
        return result;
    }

    private void baselineInBackground(Long companyId, String name) {
        Thread thread = new Thread(() -> {
            try {
                watcherService.runCompany(companyId);
            } catch (Exception e) {
                log.warn("Baseline import for discovered {} failed: {}", name, e.getMessage());
            }
        }, "discovery-baseline");
        thread.setDaemon(true);
        thread.start();
    }
}
