package com.example.jobmaster.api;

import com.example.jobmaster.dto.JdFetchResponse;
import com.example.jobmaster.service.JdFetchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jd-fetch")
public class JdFetchController {

    public record JdFetchRequest(@NotBlank(message = "url is required") String url) {
    }

    private final JdFetchService jdFetchService;

    public JdFetchController(JdFetchService jdFetchService) {
        this.jdFetchService = jdFetchService;
    }

    @PostMapping
    public JdFetchResponse fetch(@Valid @RequestBody JdFetchRequest request) {
        return jdFetchService.fetch(request.url());
    }
}
