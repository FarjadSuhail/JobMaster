package com.example.jobmaster.watcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Daily run at 11:00 Europe/Berlin, plus a catch-up on startup so a laptop
 * that was asleep at 11:00 still gets checked when the stack comes back up.
 */
@Component
public class WatcherScheduler {

    private static final Logger log = LoggerFactory.getLogger(WatcherScheduler.class);

    private final WatcherService watcherService;
    private final boolean enabled;

    public WatcherScheduler(WatcherService watcherService,
                            @Value("${jobmaster.watcher.enabled:true}") boolean enabled) {
        this.watcherService = watcherService;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${jobmaster.watcher.cron:0 0 11 * * *}",
            zone = "${jobmaster.watcher.zone:Europe/Berlin}")
    public void daily() {
        if (!enabled) {
            return;
        }
        log.info("Watcher: scheduled daily run starting");
        watcherService.runAll();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        if (!enabled) {
            return;
        }
        boolean due = watcherService.lastSuccessfulRunAt()
                .map(last -> last.isBefore(Instant.now().minus(20, ChronoUnit.HOURS)))
                .orElse(true);
        if (!due) {
            return;
        }
        Thread thread = new Thread(() -> {
            log.info("Watcher: catch-up run starting (last successful run >20h ago or never)");
            watcherService.runAll();
        }, "watcher-catchup");
        thread.setDaemon(true);
        thread.start();
    }
}
