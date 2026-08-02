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
                      "software|developer|engineer|backend|full stack|fullstack|full-stack|angular|react|vue|node|node.js|python|nest|nest.js|cloud|aws|gcp|azure|docker|kubernetes|devops";

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
        seed("Celonis", AdapterType.GREENHOUSE,
                "{\"boardToken\": \"celonis\"}",
                TECH_KEYWORDS, null, null);
        seed("HelloFresh", AdapterType.GREENHOUSE,
                "{\"boardToken\": \"hellofresh\"}",
                TECH_KEYWORDS, null, null);
        seed("SumUp", AdapterType.GREENHOUSE,
                "{\"boardToken\": \"sumup\"}",
                TECH_KEYWORDS, null, null);
        seed("N26", AdapterType.GREENHOUSE,
                "{\"boardToken\": \"n26\"}",
                TECH_KEYWORDS, null, null);
        seed("GetYourGuide", AdapterType.GREENHOUSE,
                "{\"boardToken\": \"getyourguide\"}",
                TECH_KEYWORDS, null, null);
        seed("Parloa", AdapterType.GREENHOUSE,
                "{\"boardToken\": \"parloa\"}",
                TECH_KEYWORDS, null, null);
        seed("Staffbase", AdapterType.GREENHOUSE,
                "{\"boardToken\": \"staffbase\"}",
                TECH_KEYWORDS, null, null);
        seed("Contentful", AdapterType.GREENHOUSE,
                "{\"boardToken\": \"contentful\"}",
                TECH_KEYWORDS, null, null);
        seed("Trade Republic", AdapterType.GREENHOUSE,
                "{\"boardToken\": \"traderepublic\"}",
                TECH_KEYWORDS, null, null);
        seed("Thoughtworks", AdapterType.GREENHOUSE,
                "{\"boardToken\": \"thoughtworks\"}",
                TECH_KEYWORDS, null, null);
        seed("Hive", AdapterType.GREENHOUSE,
                "{\"boardToken\": \"hive\"}",
                TECH_KEYWORDS, null, null);
        seed("AUTO1", AdapterType.SMARTRECRUITERS,
                "{\"companyId\": \"AUTO1\"}",
                TECH_KEYWORDS, null, null);
        seed("Miro", AdapterType.ASHBY,
                "{\"jobBoardName\": \"miro\"}",
                TECH_KEYWORDS, null, null);
        seed("Enpal", AdapterType.ASHBY,
                "{\"jobBoardName\": \"enpal\"}",
                TECH_KEYWORDS, null, null);
        seed("Rasa", AdapterType.ASHBY,
                "{\"jobBoardName\": \"rasa\"}",
                TECH_KEYWORDS, null, null);
        seed("Babbel", AdapterType.ASHBY,
                "{\"jobBoardName\": \"babbel\"}",
                TECH_KEYWORDS, null, null);
        seed("Aleph Alpha", AdapterType.ASHBY,
                "{\"jobBoardName\": \"alephalpha\"}",
                TECH_KEYWORDS, null, null);
        seed("Zalando", AdapterType.GREENHOUSE,
                "{\"boardToken\": \"zalando\"}",
                TECH_KEYWORDS, null, null);
        seed("Spryker", AdapterType.GREENHOUSE,
                "{\"boardToken\": \"spryker\"}",
                TECH_KEYWORDS, null, null);
        seed("Mambu", AdapterType.GREENHOUSE,
                "{\"boardToken\": \"mambu\"}",
                TECH_KEYWORDS, null, null);
        seed("Schwarz Digits", AdapterType.GREENHOUSE,
                "{\"boardToken\": \"schwarzdigits\"}",
                TECH_KEYWORDS, null, null);
        seed("SAP Signavio", AdapterType.GREENHOUSE,
                "{\"boardToken\": \"signavio\"}",
                TECH_KEYWORDS, null, null);
        seed("Merantix", AdapterType.GREENHOUSE,
                "{\"boardToken\": \"merantix\"}",
                TECH_KEYWORDS, null, null);
        seed("Merantix Momentum", AdapterType.GREENHOUSE,
                "{\"boardToken\": \"merantixmomentum\"}",
                TECH_KEYWORDS, null, null);
        seed("deepset", AdapterType.GREENHOUSE,
                "{\"boardToken\": \"deepset\"}",
                TECH_KEYWORDS, null, null);
        seed("SVA System Vertrieb Alexander", AdapterType.GREENHOUSE,
                "{\"boardToken\": \"svasystemvertriebalexander\"}",
                TECH_KEYWORDS, null, null);
        seed("Accenture Germany", AdapterType.GREENHOUSE,
                "{\"boardToken\": \"accenture\"}",
                TECH_KEYWORDS, null, null);
        seed("Capgemini Germany", AdapterType.GREENHOUSE,
                "{\"boardToken\": \"capgemini\"}",
                TECH_KEYWORDS, null, null);
        seed("Personio", AdapterType.PERSONIO,
                "{\"host\": \"personio.jobs.personio.de\"}",
                TECH_KEYWORDS, null, null);
        log.info("Watcher: seeded initial watched companies (SAP, Delivery Hero, EGYM, Celonis, HelloFresh, SumUp, N26, GetYourGuide, Parloa, Staffbase, Contentful, Trade Republic, Thoughtworks, Hive, AUTO1, Miro, Enpal, Rasa, Babbel, Aleph Alpha, Zalando, Spryker, Mambu, Schwarz Digits, SAP Signavio, Merantix, Merantix Momentum, deepset, SVA System Vertrieb Alexander, Accenture Germany, Capgemini Germany, Personio)");
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
