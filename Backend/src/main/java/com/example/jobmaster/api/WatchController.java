package com.example.jobmaster.api;

import com.example.jobmaster.watcher.DiscoveryService;
import com.example.jobmaster.watcher.TelegramNotifier;
import com.example.jobmaster.watcher.WatcherService;
import com.example.jobmaster.watcher.dto.DiscoveryHit;
import com.example.jobmaster.watcher.dto.SaveWatchedCompanyRequest;
import com.example.jobmaster.watcher.dto.UpdateWatchedCompanyRequest;
import com.example.jobmaster.watcher.dto.WatchRunDto;
import com.example.jobmaster.watcher.dto.WatchedCompanyDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/watch")
public class WatchController {

    private final WatcherService watcherService;
    private final DiscoveryService discoveryService;
    private final TelegramNotifier telegramNotifier;

    public WatchController(WatcherService watcherService, DiscoveryService discoveryService,
                           TelegramNotifier telegramNotifier) {
        this.watcherService = watcherService;
        this.discoveryService = discoveryService;
        this.telegramNotifier = telegramNotifier;
    }

    /** Sends a "connected" message so the user can verify their bot setup. */
    @PostMapping("/telegram-test")
    public Map<String, String> telegramTest() {
        telegramNotifier.sendTest();
        return Map.of("message", "Test message sent — check your Telegram.");
    }

    public record DiscoverRequest(List<String> names) {
    }

    public record AddDiscoveredRequest(List<DiscoveryHit> items) {
    }

    /** Probes candidate companies against public board APIs; ~20-40s for the built-in list. */
    @PostMapping("/discover")
    public List<DiscoveryHit> discover(@RequestBody(required = false) DiscoverRequest request) {
        return discoveryService.discover(request == null ? null : request.names());
    }

    @PostMapping("/discover/add")
    public List<WatchedCompanyDto> addDiscovered(@RequestBody AddDiscoveredRequest request) {
        return discoveryService.addDiscovered(request.items() == null ? List.of() : request.items());
    }

    @GetMapping("/companies")
    public List<WatchedCompanyDto> companies() {
        return watcherService.listCompanies();
    }

    @PostMapping("/companies")
    public WatchedCompanyDto create(@Valid @RequestBody SaveWatchedCompanyRequest request) {
        return watcherService.createCompany(request);
    }

    @PatchMapping("/companies/{id}")
    public WatchedCompanyDto update(@PathVariable Long id,
                                    @RequestBody UpdateWatchedCompanyRequest request) {
        return watcherService.updateCompany(id, request);
    }

    @DeleteMapping("/companies/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        watcherService.deleteCompany(id);
        return ResponseEntity.noContent().build();
    }

    /** Check all enabled companies now. */
    @PostMapping("/run")
    public List<WatchRunDto> runAll() {
        return watcherService.runAll();
    }

    /** Check one company now (works even when disabled — used for testing configs). */
    @PostMapping("/companies/{id}/run")
    public WatchRunDto runOne(@PathVariable Long id) {
        return watcherService.runCompany(id);
    }

    @GetMapping("/runs")
    public List<WatchRunDto> runs() {
        return watcherService.recentRuns();
    }
}
