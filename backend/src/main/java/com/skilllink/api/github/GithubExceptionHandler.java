package com.skilllink.api.github;

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
public class GithubExceptionHandler {
    @ExceptionHandler(GithubOAuthService.GithubConfigurationException.class)
    ResponseEntity<Map<String, Object>> configuration(GithubOAuthService.GithubConfigurationException ex) { return body(HttpStatus.BAD_REQUEST, ex.code(), ex.getMessage()); }
    @ExceptionHandler(GithubClient.GithubException.class)
    ResponseEntity<Map<String, Object>> github(GithubClient.GithubException ex) { return body(ex.status() == 401 ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_GATEWAY, ex.code(), ex.getMessage()); }
    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String code, String message) { return ResponseEntity.status(status).body(Map.of("code", code, "message", message, "requestId", UUID.randomUUID().toString(), "timestamp", Instant.now().toString(), "details", Map.of())); }
}
