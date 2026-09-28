package com.skilllink.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED", "The request contains invalid fields.", requestId(request), Map.of("fields", exception.getBindingResult().getFieldErrors().stream().map(error -> error.getField()).toList())));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> fallback(Exception exception, HttpServletRequest request) {
        // Do not return stack traces or provider responses to clients. Log with requestId in production.
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError("INTERNAL_ERROR", "The request could not be completed.", requestId(request), Map.of()));
    }

    private String requestId(HttpServletRequest request) {
        String header = request.getHeader("X-Request-Id");
        return header == null || header.isBlank() ? UUID.randomUUID().toString() : header;
    }

    public record ApiError(String code, String message, String requestId, Map<String, Object> details) {
        public String timestamp() { return Instant.now().toString(); }
    }
}
