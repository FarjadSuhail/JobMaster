package com.example.jobmaster.watcher;

import com.example.jobmaster.watcher.domain.AdapterType;
import com.example.jobmaster.watcher.domain.WatchedCompany;
import com.example.jobmaster.watcher.repository.WatchedCompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Seeds the initial watched companies once, on an empty table. Edit them in the UI afterwards. */
@Component
public class WatchedCompanySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WatchedCompanySeeder.class);

    private static final String TECH_KEYWORDS =
            "software|developer|engineer|backend|full stack|fullstack|full-stack|java|cloud|devops";

    private final WatchedCompanyRepository repository;

    public WatchedCompanySeeder(WatchedCompanyRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        seed("SAP", AdapterType.SUCCESSFACTORS,
                "{\"baseUrl\": \"https://jobs.sap.com\", \"listPath\": \"/go/SAP-Jobs-in-Germany/850601/\", \"maxPages\": 10}",
                TECH_KEYWORDS, null, null);
        seed("Delivery Hero", AdapterType.SMARTRECRUITERS,
                "{\"companyId\": \"DeliveryHero\"}",
                TECH_KEYWORDS, null, "germany|berlin|munich|remote");
        seed("EGYM", AdapterType.PERSONIO,
                "{\"host\": \"egym.jobs.personio.de\"}",
                TECH_KEYWORDS, null, null);
        log.info("Watcher: seeded initial watched companies (SAP, Delivery Hero, EGYM)");
    }

    private void seed(String name, AdapterType type, String config,
                      String include, String exclude, String locations) {
        WatchedCompany company = new WatchedCompany();
        company.setName(name);
        company.setAdapterType(type);
        company.setConfigJson(config);
        company.setIncludeKeywords(include);
        company.setExcludeKeywords(exclude);
        company.setLocations(locations);
        company.setEnabled(true);
        company.setCreatedAt(Instant.now());
        repository.save(company);
    }
}
