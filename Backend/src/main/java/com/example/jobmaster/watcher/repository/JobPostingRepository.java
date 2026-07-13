package com.example.jobmaster.watcher.repository;

import com.example.jobmaster.watcher.domain.JobPosting;
import com.example.jobmaster.watcher.domain.JobPostingStatus;
import com.example.jobmaster.watcher.domain.WatchedCompany;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    Optional<JobPosting> findByCompanyAndExternalId(WatchedCompany company, String externalId);

    long countByCompany(WatchedCompany company);

    List<JobPosting> findAllByStatusOrderByFirstSeenAtDesc(JobPostingStatus status);

    List<JobPosting> findAllByOrderByFirstSeenAtDesc();

    List<JobPosting> findAllByCompanyAndClosedFalse(WatchedCompany company);

    List<JobPosting> findAllByStatus(JobPostingStatus status);

    long countByStatus(JobPostingStatus status);

    long countByCompanyAndStatus(WatchedCompany company, JobPostingStatus status);
}
