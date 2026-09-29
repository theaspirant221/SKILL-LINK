package com.skilllink.api.project;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProjectExceptionHandler {
    @ExceptionHandler(ProjectService.ProjectNotFoundException.class)
    ResponseEntity<Map<String, Object>> missing() { return ResponseEntity.status(404).body(Map.of("code", "PROJECT_NOT_FOUND", "message", "The project is not available to this account.", "requestId", UUID.randomUUID().toString(), "details", Map.of())); }
}
