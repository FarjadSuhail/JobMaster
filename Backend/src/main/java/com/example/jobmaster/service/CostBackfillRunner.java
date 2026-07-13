package com.example.jobmaster.service;

import com.example.jobmaster.domain.Generation;
import com.example.jobmaster.repository.GenerationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Fills in costUsd for generations that were created before their model had a
 * configured price (cost is normally snapshotted at generation time). Runs on
 * every start but only touches rows that have tokens and no cost, so it's
 * idempotent and becomes a no-op once history is priced.
 */
@Component
public class CostBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CostBackfillRunner.class);

    private final GenerationRepository repository;
    private final CostService costService;

    public CostBackfillRunner(GenerationRepository repository, CostService costService) {
        this.repository = repository;
        this.costService = costService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int updated = 0;
        for (Generation g : repository.findAll()) {
            if (g.getCostUsd() != null || g.getPromptTokens() == null || g.getCompletionTokens() == null) {
                continue;
            }
            BigDecimal cost = costService.cost(g.getModel(), g.getPromptTokens(), g.getCompletionTokens());
            if (cost != null) {
                g.setCostUsd(cost);
                repository.save(g);
                updated++;
            }
        }
        if (updated > 0) {
            log.info("Backfilled cost for {} generation(s) whose model price was previously unknown", updated);
        }
    }
}
