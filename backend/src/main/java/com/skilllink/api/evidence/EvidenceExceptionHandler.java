package com.skilllink.api.evidence;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EvidenceExceptionHandler {
    @ExceptionHandler(EvidenceService.EvidenceNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound() { return ResponseEntity.status(404).body(Map.of("code", "EVIDENCE_NOT_FOUND", "message", "The evidence is not available to this account.", "requestId", UUID.randomUUID().toString(), "details", Map.of())); }
    @ExceptionHandler(EvidenceService.EvidenceInputException.class)
    ResponseEntity<Map<String, Object>> invalid(EvidenceService.EvidenceInputException ex) { return ResponseEntity.badRequest().body(Map.of("code", ex.code(), "message", ex.getMessage(), "requestId", UUID.randomUUID().toString(), "details", Map.of())); }
}
