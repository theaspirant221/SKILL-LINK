package com.skilllink.api.snapshot;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class SnapshotExceptionHandler {

    @ExceptionHandler(SnapshotService.SnapshotException.class)
    public ResponseEntity<Map<String, Object>> handleSnapshotException(SnapshotService.SnapshotException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.httpStatus() >= 400 && ex.httpStatus() < 600 ? ex.httpStatus() : 400);
        return ResponseEntity.status(status).body(Map.of(
            "code", ex.code(),
            "message", ex.getMessage(),
            "requestId", UUID.randomUUID().toString().replaceAll("[^A-Za-z0-9_-]", ""),
            "timestamp", Instant.now().toString(),
            "details", Map.of()
        ));
    }
}
