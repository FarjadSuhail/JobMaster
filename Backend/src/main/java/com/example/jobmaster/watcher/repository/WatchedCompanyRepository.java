package com.example.jobmaster.watcher.repository;

import com.example.jobmaster.watcher.domain.WatchedCompany;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WatchedCompanyRepository extends JpaRepository<WatchedCompany, Long> {

    List<WatchedCompany> findAllByOrderByNameAsc();

    List<WatchedCompany> findAllByEnabledTrue();
}
