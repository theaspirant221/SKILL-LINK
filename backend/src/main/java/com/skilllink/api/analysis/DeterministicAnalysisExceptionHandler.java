package com.skilllink.api.analysis;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class DeterministicAnalysisExceptionHandler {

    @ExceptionHandler(DeterministicAnalysisService.AnalysisException.class)
    public ResponseEntity<Map<String, Object>> handleAnalysisException(DeterministicAnalysisService.AnalysisException ex) {
        HttpStatus status = switch (ex.httpStatus()) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        Map<String, Object> body = Map.of(
            "code", ex.code(),
            "message", ex.getMessage(),
            "requestId", UUID.randomUUID().toString(),
            "timestamp", Instant.now().toString()
        );

        return ResponseEntity.status(status).body(body);
    }
}
