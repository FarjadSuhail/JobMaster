package com.example.jobmaster.watcher;

import com.example.jobmaster.dto.ApplicationView;
import com.example.jobmaster.dto.CreateApplicationRequest;
import com.example.jobmaster.service.ApplicationService;
import com.example.jobmaster.service.NotFoundException;
import com.example.jobmaster.watcher.adapter.FetchedJob;
import com.example.jobmaster.watcher.adapter.JobPortalAdapter;
import com.example.jobmaster.watcher.domain.AdapterType;
import com.example.jobmaster.watcher.domain.JobPosting;
import com.example.jobmaster.watcher.domain.JobPostingStatus;
import com.example.jobmaster.watcher.domain.WatchRun;
import com.example.jobmaster.watcher.domain.WatchedCompany;
import com.example.jobmaster.watcher.dto.JobPostingDto;
import com.example.jobmaster.watcher.dto.SaveWatchedCompanyRequest;
import com.example.jobmaster.watcher.dto.UpdateWatchedCompanyRequest;
import com.example.jobmaster.watcher.dto.WatchRunDto;
import com.example.jobmaster.watcher.dto.WatchedCompanyDto;
import com.example.jobmaster.watcher.repository.JobPostingRepository;
import com.example.jobmaster.watcher.repository.WatchRunRepository;
import com.example.jobmaster.watcher.repository.WatchedCompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class WatcherService {

    private static final Logger log = LoggerFactory.getLogger(WatcherService.class);

    private final WatchedCompanyRepository companyRepository;
    private final JobPostingRepository postingRepository;
    private final WatchRunRepository runRepository;
    private final ApplicationService applicationService;
    private final Map<AdapterType, JobPortalAdapter> adapters = new EnumMap<>(AdapterType.class);
    private final ObjectMapper objectMapper;
    private final TelegramNotifier telegramNotifier;

    public WatcherService(WatchedCompanyRepository companyRepository,
                          JobPostingRepository postingRepository,
                          WatchRunRepository runRepository,
                          ApplicationService applicationService,
                          List<JobPortalAdapter> adapterBeans,
                          ObjectMapper objectMapper,
                          TelegramNotifier telegramNotifier) {
        this.companyRepository = companyRepository;
        this.postingRepository = postingRepository;
        this.runRepository = runRepository;
        this.applicationService = applicationService;
        adapterBeans.forEach(a -> adapters.put(a.type(), a));
        this.objectMapper = objectMapper;
        this.telegramNotifier = telegramNotifier;
    }

    // ------------------------------------------------------------ companies

    public List<WatchedCompanyDto> listCompanies() {
        return companyRepository.findAllByOrderByNameAsc().stream().map(this::toDto).toList();
    }

    public WatchedCompanyDto createCompany(SaveWatchedCompanyRequest request) {
        validateConfig(request.configJson());
        WatchedCompany company = new WatchedCompany();
        company.setName(request.name());
        company.setAdapterType(request.adapterType());
        company.setConfigJson(request.configJson());
        company.setIncludeKeywords(request.includeKeywords());
        company.setExcludeKeywords(request.excludeKeywords());
        company.setLocations(request.locations());
        company.setEnabled(request.enabled() == null || request.enabled());
        company.setCreatedAt(Instant.now());
        return toDto(companyRepository.save(company));
    }

    public WatchedCompanyDto updateCompany(Long id, UpdateWatchedCompanyRequest request) {
        WatchedCompany company = getCompany(id);
        if (request.name() != null) {
            company.setName(request.name());
        }
        if (request.adapterType() != null) {
            company.setAdapterType(request.adapterType());
        }
        if (request.configJson() != null) {
            validateConfig(request.configJson());
            company.setConfigJson(request.configJson());
        }
        if (request.includeKeywords() != null) {
            company.setIncludeKeywords(request.includeKeywords());
        }
        if (request.excludeKeywords() != null) {
            company.setExcludeKeywords(request.excludeKeywords());
        }
        if (request.locations() != null) {
            company.setLocations(request.locations());
        }
        if (request.enabled() != null) {
            company.setEnabled(request.enabled());
        }
        return toDto(companyRepository.save(company));
    }

    public void deleteCompany(Long id) {
        companyRepository.delete(getCompany(id));
    }

    // ----------------------------------------------------------------- runs

    /** Sequentially checks all enabled companies; one failure never blocks the rest. */
    public List<WatchRunDto> runAll() {
        return companyRepository.findAllByEnabledTrue().stream()
                .map(this::executeRun)
                .map(this::toDto)
                .toList();
    }

    public WatchRunDto runCompany(Long id) {
        return toDto(executeRun(getCompany(id)));
    }

    public List<WatchRunDto> recentRuns() {
        return runRepository.findTop50ByOrderByStartedAtDesc().stream().map(this::toDto).toList();
    }

    public Optional<Instant> lastSuccessfulRunAt() {
        return runRepository.findTopByStatusOrderByStartedAtDesc("OK").map(WatchRun::getStartedAt);
    }

    private WatchRun executeRun(WatchedCompany company) {
        WatchRun run = new WatchRun();
        run.setCompanyId(company.getId());
        run.setCompanyName(company.getName());
        run.setStartedAt(Instant.now());
        try {
            JobPortalAdapter adapter = adapters.get(company.getAdapterType());
            if (adapter == null) {
                throw new IllegalStateException("No adapter for " + company.getAdapterType());
            }
            List<FetchedJob> kept = adapter.fetch(company).stream()
                    .filter(job -> matchesFilters(company, job))
                    .toList();

            // A company's very first fetch is a baseline: import as SEEN so day
            // one doesn't flood the feed with hundreds of "new" jobs.
            boolean baseline = postingRepository.countByCompany(company) == 0;
            Instant now = Instant.now();
            Set<String> currentIds = new HashSet<>();
            long newCount = 0;
            List<JobPosting> newPostings = new ArrayList<>();

            for (FetchedJob job : kept) {
                if (!currentIds.add(job.externalId())) {
                    continue;
                }
                Optional<JobPosting> existing =
                        postingRepository.findByCompanyAndExternalId(company, job.externalId());
                if (existing.isPresent()) {
                    JobPosting posting = existing.get();
                    posting.setLastSeenAt(now);
                    posting.setClosed(false);
                    postingRepository.save(posting);
                } else {
                    JobPosting posting = new JobPosting();
                    posting.setCompany(company);
                    posting.setExternalId(job.externalId());
                    posting.setTitle(job.title() == null || job.title().isBlank()
                            ? "(untitled)" : job.title());
                    posting.setLocation(job.location());
                    posting.setDepartment(job.department());
                    posting.setUrl(job.url());
                    posting.setPostedAt(job.postedAt());
                    posting.setFirstSeenAt(now);
                    posting.setLastSeenAt(now);
                    posting.setStatus(baseline ? JobPostingStatus.SEEN : JobPostingStatus.NEW);
                    postingRepository.save(posting);
                    if (!baseline) {
                        newCount++;
                        newPostings.add(posting);
                    }
                }
            }

            // postings that disappeared from the portal
            for (JobPosting posting : postingRepository.findAllByCompanyAndClosedFalse(company)) {
                if (!currentIds.contains(posting.getExternalId())) {
                    posting.setClosed(true);
                    postingRepository.save(posting);
                }
            }

            run.setJobsFound(kept.size());
            run.setJobsNew(newCount);
            run.setStatus("OK");
            company.setLastRunStatus("OK");
            company.setLastError(null);
            log.info("Watcher: {} → {} postings after filters, {} new{}",
                    company.getName(), kept.size(), newCount, baseline ? " (baseline)" : "");
            telegramNotifier.notifyNewJobs(company.getName(), newPostings);
        } catch (Exception e) {
            run.setStatus("ERROR");
            run.setError(truncate(e.toString()));
            company.setLastRunStatus("ERROR");
            company.setLastError(truncate(e.toString()));
            log.warn("Watcher: {} failed: {}", company.getName(), e.toString());
        }
        company.setLastRunAt(run.getStartedAt());
        companyRepository.save(company);
        return runRepository.save(run);
    }

    // ------------------------------------------------------------- postings

    public List<JobPostingDto> listJobs(JobPostingStatus status) {
        List<JobPosting> postings = status == null
                ? postingRepository.findAllByOrderByFirstSeenAtDesc()
                : postingRepository.findAllByStatusOrderByFirstSeenAtDesc(status);
        return postings.stream().map(this::toDto).toList();
    }

    public JobPostingDto dismiss(Long id) {
        JobPosting posting = getPosting(id);
        posting.setStatus(JobPostingStatus.DISMISSED);
        return toDto(postingRepository.save(posting));
    }

    public long markAllSeen() {
        List<JobPosting> newOnes = postingRepository.findAllByStatus(JobPostingStatus.NEW);
        newOnes.forEach(p -> p.setStatus(JobPostingStatus.SEEN));
        postingRepository.saveAll(newOnes);
        return newOnes.size();
    }

    /** Turns a discovered posting into a SAVED application on the board, with the JD fetched. */
    public ApplicationView convert(Long id) {
        JobPosting posting = getPosting(id);
        String jobDescription = fetchDescriptionQuietly(posting);
        String notes = "Found by job watcher on " + LocalDate.now()
                + (posting.getDepartment() == null ? "" : ". Department: " + posting.getDepartment())
                + (jobDescription == null
                        ? ". Job description could not be fetched automatically, paste it from the posting."
                        : "");
        ApplicationView application = applicationService.create(new CreateApplicationRequest(
                posting.getTitle(),
                posting.getCompany().getName(),
                posting.getLocation(),
                jobDescription,
                posting.getUrl(),
                null,
                null,
                notes));
        posting.setStatus(JobPostingStatus.CONVERTED);
        posting.setApplicationId(application.id());
        postingRepository.save(posting);
        return application;
    }

    /** JD fetch is best-effort: a failure must never block applying. */
    private String fetchDescriptionQuietly(JobPosting posting) {
        try {
            JobPortalAdapter adapter = adapters.get(posting.getCompany().getAdapterType());
            return adapter == null ? null
                    : adapter.fetchDescription(posting.getCompany(), posting.getExternalId(), posting.getUrl());
        } catch (Exception e) {
            log.warn("Watcher: JD fetch failed for posting {} ({}): {}",
                    posting.getId(), posting.getTitle(), e.toString());
            return null;
        }
    }

    // -------------------------------------------------------------- helpers

    private boolean matchesFilters(WatchedCompany company, FetchedJob job) {
        String title = job.title() == null ? "" : job.title().toLowerCase(Locale.ROOT);
        if (hasTerms(company.getIncludeKeywords())
                && !containsAny(title, company.getIncludeKeywords())) {
            return false;
        }
        if (hasTerms(company.getExcludeKeywords())
                && containsAny(title, company.getExcludeKeywords())) {
            return false;
        }
        if (hasTerms(company.getLocations()) && job.location() != null
                && !containsAny(job.location().toLowerCase(Locale.ROOT), company.getLocations())) {
            return false;
        }
        return true;
    }

    private boolean hasTerms(String terms) {
        return terms != null && !terms.isBlank();
    }

    private boolean containsAny(String text, String pipeSeparatedTerms) {
        for (String term : pipeSeparatedTerms.split("\\|")) {
            if (!term.isBlank() && text.contains(term.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void validateConfig(String configJson) {
        try {
            objectMapper.readTree(configJson);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("configJson is not valid JSON");
        }
    }

    private WatchedCompany getCompany(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No watched company with id " + id));
    }

    private JobPosting getPosting(Long id) {
        return postingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No job posting with id " + id));
    }

    private WatchedCompanyDto toDto(WatchedCompany c) {
        return new WatchedCompanyDto(c.getId(), c.getName(), c.getAdapterType(), c.getConfigJson(),
                c.getIncludeKeywords(), c.getExcludeKeywords(), c.getLocations(), c.isEnabled(),
                c.getLastRunAt(), c.getLastRunStatus(), c.getLastError(),
                postingRepository.countByCompanyAndStatus(c, JobPostingStatus.NEW));
    }

    private JobPostingDto toDto(JobPosting p) {
        return new JobPostingDto(p.getId(), p.getCompany().getId(), p.getCompany().getName(),
                p.getTitle(), p.getLocation(), p.getDepartment(), p.getUrl(), p.getPostedAt(),
                p.getFirstSeenAt(), p.getStatus(), p.getApplicationId(), p.isClosed());
    }

    private WatchRunDto toDto(WatchRun r) {
        return new WatchRunDto(r.getId(), r.getCompanyId(), r.getCompanyName(), r.getStartedAt(),
                r.getJobsFound(), r.getJobsNew(), r.getStatus(), r.getError());
    }

    private String truncate(String value) {
        return value != null && value.length() > 3900 ? value.substring(0, 3900) : value;
    }
}
