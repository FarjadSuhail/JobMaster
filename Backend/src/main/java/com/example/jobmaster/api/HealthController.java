package com.example.jobmaster.api;

import com.example.jobmaster.service.AiProviderInfo;
import com.example.jobmaster.service.ProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final AiProviderInfo aiProviderInfo;
    private final ProfileService profileService;

    public HealthController(AiProviderInfo aiProviderInfo, ProfileService profileService) {
        this.aiProviderInfo = aiProviderInfo;
        this.profileService = profileService;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "aiProvider", aiProviderInfo.provider(),
                "aiModel", aiProviderInfo.model(),
                "profileConfigured", profileService.find().isPresent());
    }
}
