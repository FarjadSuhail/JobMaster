package com.example.jobmaster.api;

import com.example.jobmaster.dto.ApplicationView;
import com.example.jobmaster.watcher.WatcherService;
import com.example.jobmaster.watcher.domain.JobPostingStatus;
import com.example.jobmaster.watcher.dto.JobPostingDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class JobsController {

    private final WatcherService watcherService;

    public JobsController(WatcherService watcherService) {
        this.watcherService = watcherService;
    }

    /** status=NEW (default) | SEEN | DISMISSED | CONVERTED | ALL */
    @GetMapping
    public List<JobPostingDto> list(@RequestParam(defaultValue = "NEW") String status) {
        JobPostingStatus filter = "ALL".equalsIgnoreCase(status)
                ? null
                : JobPostingStatus.valueOf(status.toUpperCase());
        return watcherService.listJobs(filter);
    }

    @PostMapping("/{id}/dismiss")
    public JobPostingDto dismiss(@PathVariable Long id) {
        return watcherService.dismiss(id);
    }

    /** Creates a SAVED board application from this posting. */
    @PostMapping("/{id}/convert")
    public ApplicationView convert(@PathVariable Long id) {
        return watcherService.convert(id);
    }

    @PostMapping("/mark-seen")
    public Map<String, Long> markAllSeen() {
        return Map.of("updated", watcherService.markAllSeen());
    }
}
