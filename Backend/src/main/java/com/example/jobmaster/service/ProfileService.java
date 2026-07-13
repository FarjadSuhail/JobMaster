package com.example.jobmaster.service;

import com.example.jobmaster.domain.ProfileDocument;
import com.example.jobmaster.dto.ProfileDto;
import com.example.jobmaster.repository.ProfileDocumentRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class ProfileService {

    private final ProfileDocumentRepository repository;
    private final ObjectMapper objectMapper;

    public ProfileService(ProfileDocumentRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Optional<ProfileDto> find() {
        return repository.findById(ProfileDocument.SINGLETON_ID).map(doc -> fromJson(doc.getJson()));
    }

    @Transactional(readOnly = true)
    public ProfileDto getRequired() {
        return find().orElseThrow(ProfileNotConfiguredException::new);
    }

    /** Raw JSON as stored — what gets embedded into AI prompts. */
    @Transactional(readOnly = true)
    public String getRequiredJson() {
        return repository.findById(ProfileDocument.SINGLETON_ID)
                .map(ProfileDocument::getJson)
                .orElseThrow(ProfileNotConfiguredException::new);
    }

    @Transactional
    public ProfileDto save(ProfileDto profile) {
        ProfileDocument doc = repository.findById(ProfileDocument.SINGLETON_ID)
                .orElseGet(ProfileDocument::new);
        doc.setJson(toJson(profile));
        doc.setUpdatedAt(Instant.now());
        repository.save(doc);
        return profile;
    }

    private ProfileDto fromJson(String json) {
        try {
            return objectMapper.readValue(json, ProfileDto.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Stored profile JSON is corrupt", e);
        }
    }

    private String toJson(ProfileDto profile) {
        try {
            return objectMapper.writeValueAsString(profile);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not serialize profile", e);
        }
    }
}
