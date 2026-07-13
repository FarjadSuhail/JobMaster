package com.example.jobmaster.api;

import com.example.jobmaster.service.AiCallException;
import com.example.jobmaster.service.JdFetchException;
import com.example.jobmaster.service.NotFoundException;
import com.example.jobmaster.service.ProfileNotConfiguredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ProfileNotConfiguredException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> profileNotConfigured(ProfileNotConfiguredException e) {
        return Map.of("message", e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> notFound(NotFoundException e) {
        return Map.of("message", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> validationFailed(MethodArgumentNotValidException e) {
        String details = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return Map.of("message", "Validation failed: " + details);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException e) {
        return Map.of("message", e.getMessage() == null ? "Invalid request" : e.getMessage());
    }

    @ExceptionHandler(JdFetchException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Map<String, String> jdFetchFailed(JdFetchException e) {
        return Map.of("message", e.getMessage());
    }

    @ExceptionHandler(AiCallException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> aiCallFailed(AiCallException e) {
        log.error("AI provider call failed", e);
        return Map.of("message", e.getMessage());
    }
}
