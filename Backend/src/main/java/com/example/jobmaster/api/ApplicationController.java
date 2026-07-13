package com.example.jobmaster.api;

import com.example.jobmaster.dto.ApplicationView;
import com.example.jobmaster.dto.CreateApplicationRequest;
import com.example.jobmaster.dto.UpdateApplicationRequest;
import com.example.jobmaster.service.ApplicationService;
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

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    public ApplicationView create(@Valid @RequestBody CreateApplicationRequest request) {
        return applicationService.create(request);
    }

    @GetMapping
    public List<ApplicationView> list() {
        return applicationService.list();
    }

    @GetMapping("/{id}")
    public ApplicationView get(@PathVariable Long id) {
        return applicationService.get(id);
    }

    @PatchMapping("/{id}")
    public ApplicationView update(@PathVariable Long id, @RequestBody UpdateApplicationRequest request) {
        return applicationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        applicationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
