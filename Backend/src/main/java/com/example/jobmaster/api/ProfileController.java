package com.example.jobmaster.api;

import com.example.jobmaster.dto.ProfileDto;
import com.example.jobmaster.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<?> get() {
        return profileService.find()
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                        "message", "No profile configured yet. PUT /api/profile with your CV data first.")));
    }

    @PutMapping
    public ProfileDto put(@Valid @RequestBody ProfileDto profile) {
        return profileService.save(profile);
    }
}
