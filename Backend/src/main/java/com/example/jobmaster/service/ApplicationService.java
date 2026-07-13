package com.example.jobmaster.service;

import com.example.jobmaster.domain.Application;
import com.example.jobmaster.domain.ApplicationStatus;
import com.example.jobmaster.domain.Generation;
import com.example.jobmaster.dto.ApplicationView;
import com.example.jobmaster.dto.CreateApplicationRequest;
import com.example.jobmaster.dto.GenerationSummary;
import com.example.jobmaster.dto.UpdateApplicationRequest;
import com.example.jobmaster.repository.ApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class ApplicationService {

    private final ApplicationRepository repository;

    public ApplicationService(ApplicationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ApplicationView create(CreateApplicationRequest request) {
        Application app = new Application();
        app.setJobTitle(request.jobTitle());
        app.setCompany(request.company());
        app.setLocation(request.location());
        app.setJobDescription(request.jobDescription());
        app.setJobUrl(request.jobUrl());
        app.setSalary(request.salary());
        app.setContactPerson(request.contactPerson());
        app.setNotes(request.notes());
        Instant now = Instant.now();
        app.setCreatedAt(now);
        app.setUpdatedAt(now);
        app.setStatusChangedAt(now);
        return toView(repository.save(app));
    }

    @Transactional(readOnly = true)
    public List<ApplicationView> list() {
        return repository.findAllByOrderByUpdatedAtDesc().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationView get(Long id) {
        return toView(getEntity(id));
    }

    @Transactional
    public ApplicationView update(Long id, UpdateApplicationRequest request) {
        Application app = getEntity(id);
        if (request.status() != null && request.status() != app.getStatus()) {
            app.setStatus(request.status());
            app.setStatusChangedAt(Instant.now());
            if (request.status() == ApplicationStatus.APPLIED && app.getAppliedAt() == null) {
                app.setAppliedAt(LocalDate.now());
            }
            if (request.status() == ApplicationStatus.INTERVIEWING && app.getInterviewedAt() == null) {
                app.setInterviewedAt(LocalDate.now());
            }
        }
        if (request.jobTitle() != null) {
            app.setJobTitle(request.jobTitle());
        }
        if (request.company() != null) {
            app.setCompany(request.company());
        }
        if (request.location() != null) {
            app.setLocation(request.location());
        }
        if (request.jobDescription() != null) {
            app.setJobDescription(request.jobDescription());
        }
        if (request.jobUrl() != null) {
            app.setJobUrl(request.jobUrl());
        }
        if (request.salary() != null) {
            app.setSalary(request.salary());
        }
        if (request.contactPerson() != null) {
            app.setContactPerson(request.contactPerson());
        }
        if (request.notes() != null) {
            app.setNotes(request.notes());
        }
        if (request.appliedAt() != null) {
            app.setAppliedAt(request.appliedAt());
        }
        app.setUpdatedAt(Instant.now());
        return toView(repository.save(app));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(getEntity(id));
    }

    /**
     * Used by the generation services: attach to an existing application, or
     * create a fresh one (status SAVED) from the generation request's job data.
     */
    @Transactional
    public Application resolveOrCreate(Long applicationId, String jobTitle, String company,
                                       String location, String jobDescription) {
        if (applicationId != null) {
            Application app = getEntity(applicationId);
            if (isBlank(app.getJobDescription()) && !isBlank(jobDescription)) {
                app.setJobDescription(jobDescription);
            }
            app.setUpdatedAt(Instant.now());
            return repository.save(app);
        }
        Application app = new Application();
        app.setJobTitle(isBlank(jobTitle) ? "Untitled role" : jobTitle);
        app.setCompany(company);
        app.setLocation(location);
        app.setJobDescription(jobDescription);
        Instant now = Instant.now();
        app.setCreatedAt(now);
        app.setUpdatedAt(now);
        app.setStatusChangedAt(now);
        return repository.save(app);
    }

    @Transactional
    public void touch(Application app) {
        app.setUpdatedAt(Instant.now());
        repository.save(app);
    }

    private Application getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("No application found with id " + id));
    }

    private ApplicationView toView(Application app) {
        List<GenerationSummary> generations = app.getGenerations().stream()
                .sorted(Comparator.comparing(Generation::getCreatedAt).reversed())
                .map(g -> new GenerationSummary(g.getId(), g.getKind(), app.getId(),
                        g.getJobTitle(), g.getCompany(), g.getProvider(), g.getModel(),
                        g.getCostUsd(), g.getCreatedAt()))
                .toList();
        return new ApplicationView(app.getId(), app.getStatus(), app.getJobTitle(), app.getCompany(),
                app.getLocation(), app.getJobDescription(), app.getJobUrl(), app.getSalary(),
                app.getContactPerson(), app.getNotes(), app.getAppliedAt(),
                app.getCreatedAt(), app.getUpdatedAt(), app.getStatusChangedAt(), generations);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
