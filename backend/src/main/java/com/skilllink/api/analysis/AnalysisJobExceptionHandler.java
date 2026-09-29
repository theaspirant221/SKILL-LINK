package com.skilllink.api.analysis;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AnalysisJobExceptionHandler {
    @ExceptionHandler(AnalysisJobService.AnalysisJobNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound() { return ResponseEntity.status(404).body(Map.of("code", "ANALYSIS_JOB_NOT_FOUND", "message", "The analysis job is not available to this account.", "requestId", UUID.randomUUID().toString(), "details", Map.of())); }
    @ExceptionHandler(AnalysisJobService.AnalysisJobStateException.class)
    ResponseEntity<Map<String, Object>> invalidState(AnalysisJobService.AnalysisJobStateException ex) { return ResponseEntity.badRequest().body(Map.of("code", "ANALYSIS_JOB_INVALID_STATE", "message", ex.getMessage(), "requestId", UUID.randomUUID().toString(), "details", Map.of())); }
}
