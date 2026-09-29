package com.skilllink.api.examination;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ExaminationExceptionHandler {
    @ExceptionHandler(ExaminationService.ExaminationNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound() { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body("EXAMINATION_NOT_FOUND", "That project defense is not available.")); }
    @ExceptionHandler(ExaminationService.ExaminationInputException.class)
    ResponseEntity<Map<String, Object>> input(ExaminationService.ExaminationInputException ex) { return ResponseEntity.badRequest().body(body(ex.code(), ex.getMessage())); }
    private Map<String, Object> body(String code, String message) { return Map.of("code", code, "message", message, "requestId", UUID.randomUUID().toString(), "timestamp", Instant.now().toString(), "details", Map.of()); }
}
