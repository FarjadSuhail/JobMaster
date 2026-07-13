package com.example.jobmaster.api;

import com.example.jobmaster.dto.AiModelConfigDto;
import com.example.jobmaster.dto.SaveAiModelRequest;
import com.example.jobmaster.service.AiModelService;
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
@RequestMapping("/api/ai/models")
public class AiModelController {

    private final AiModelService aiModelService;

    public AiModelController(AiModelService aiModelService) {
        this.aiModelService = aiModelService;
    }

    @GetMapping
    public List<AiModelConfigDto> list() {
        return aiModelService.list();
    }

    @PostMapping
    public AiModelConfigDto create(@Valid @RequestBody SaveAiModelRequest request) {
        return aiModelService.create(request);
    }

    @PatchMapping("/{id}")
    public AiModelConfigDto update(@PathVariable Long id, @RequestBody SaveAiModelRequest request) {
        return aiModelService.update(id, request);
    }

    @PostMapping("/{id}/activate")
    public AiModelConfigDto activate(@PathVariable Long id) {
        return aiModelService.activate(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        aiModelService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
