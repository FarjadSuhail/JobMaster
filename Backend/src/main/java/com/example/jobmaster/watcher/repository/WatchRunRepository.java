package com.example.jobmaster.watcher.repository;

import com.example.jobmaster.watcher.domain.WatchRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchRunRepository extends JpaRepository<WatchRun, Long> {

    List<WatchRun> findTop50ByOrderByStartedAtDesc();

    Optional<WatchRun> findTopByStatusOrderByStartedAtDesc(String status);
}
