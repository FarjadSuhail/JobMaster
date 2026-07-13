package com.example.jobmaster.repository;

import com.example.jobmaster.domain.Application;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findAllByOrderByUpdatedAtDesc();

    List<Application> findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(Instant since);
}
