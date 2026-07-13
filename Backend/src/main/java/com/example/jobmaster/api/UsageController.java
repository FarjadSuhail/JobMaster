package com.example.jobmaster.api;

import com.example.jobmaster.dto.UsageReport;
import com.example.jobmaster.service.UsageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UsageController {

    private final UsageService usageService;

    public UsageController(UsageService usageService) {
        this.usageService = usageService;
    }

    @GetMapping("/api/usage")
    public UsageReport usage() {
        return usageService.report();
    }
}
