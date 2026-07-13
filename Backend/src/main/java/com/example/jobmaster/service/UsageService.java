package com.example.jobmaster.service;

import com.example.jobmaster.domain.Generation;
import com.example.jobmaster.dto.UsageReport;
import com.example.jobmaster.repository.GenerationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class UsageService {

    private static final DateTimeFormatter MONTH =
            DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneId.systemDefault());

    private final GenerationRepository repository;

    public UsageService(GenerationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public UsageReport report() {
        List<Generation> all = repository.findAll();

        Map<String, List<Generation>> byMonth = all.stream()
                .collect(Collectors.groupingBy(g -> MONTH.format(g.getCreatedAt()), TreeMap::new,
                        Collectors.toList()));

        List<UsageReport.MonthUsage> months = byMonth.entrySet().stream()
                .map(e -> monthUsage(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(UsageReport.MonthUsage::month).reversed())
                .toList();

        return new UsageReport(all.size(), sumPrompt(all), sumCompletion(all), sumCost(all), months);
    }

    private UsageReport.MonthUsage monthUsage(String month, List<Generation> generations) {
        Map<String, List<Generation>> byModel = generations.stream()
                .collect(Collectors.groupingBy(g -> g.getProvider() + "/" + g.getModel(),
                        TreeMap::new, Collectors.toList()));

        List<UsageReport.ModelUsage> models = byModel.values().stream()
                .map(list -> new UsageReport.ModelUsage(
                        list.getFirst().getProvider(), list.getFirst().getModel(),
                        list.size(), sumPrompt(list), sumCompletion(list), sumCost(list)))
                .toList();

        return new UsageReport.MonthUsage(month, generations.size(),
                sumPrompt(generations), sumCompletion(generations), sumCost(generations), models);
    }

    private long sumPrompt(List<Generation> generations) {
        return generations.stream()
                .mapToLong(g -> g.getPromptTokens() == null ? 0 : g.getPromptTokens()).sum();
    }

    private long sumCompletion(List<Generation> generations) {
        return generations.stream()
                .mapToLong(g -> g.getCompletionTokens() == null ? 0 : g.getCompletionTokens()).sum();
    }

    private BigDecimal sumCost(List<Generation> generations) {
        return generations.stream()
                .map(Generation::getCostUsd)
                .filter(c -> c != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
