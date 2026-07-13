package com.example.jobmaster.api;

import com.example.jobmaster.dto.DashboardReport;
import com.example.jobmaster.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** Stats + skill lists + last stored AI summary. Never triggers an AI call. */
    @GetMapping
    public DashboardReport report() {
        return dashboardService.report();
    }

    /** Runs ONE paid AI call and stores the result; only invoked by the button. */
    @PostMapping("/summary")
    public DashboardReport.AiSummary generateSummary() {
        return dashboardService.generateSummary();
    }
}
