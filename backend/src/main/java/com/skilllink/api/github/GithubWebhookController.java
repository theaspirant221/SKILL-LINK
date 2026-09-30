package com.skilllink.api.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * Public GitHub webhook receiver. Unsigned or wrongly signed deliveries are always rejected;
 * the signature is verified with a constant-time HMAC-SHA256 comparison over the raw body.
 */
@RestController
@RequestMapping("/api/v1/github")
public class GithubWebhookController {
    private final GithubWebhookService service;
    private final GithubProperties properties;
    private final ObjectMapper objectMapper;

    public GithubWebhookController(GithubWebhookService service, GithubProperties properties, ObjectMapper objectMapper) {
        this.service = service; this.properties = properties; this.objectMapper = objectMapper;
    }

    @PostMapping("/webhooks")
    public ResponseEntity<Map<String, Object>> receive(HttpServletRequest request,
                                                       @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
                                                       @RequestHeader(value = "X-GitHub-Event", required = false) String event,
                                                       @RequestHeader(value = "X-GitHub-Delivery", required = false) String delivery) {
        if (!properties.webhookConfigured()) return error(HttpStatus.UNAUTHORIZED, "WEBHOOK_NOT_CONFIGURED", "Webhook processing is not configured on this environment.");
        byte[] rawBody;
        try {
            rawBody = request.getInputStream().readAllBytes();
        } catch (IOException ex) {
            return error(HttpStatus.BAD_REQUEST, "WEBHOOK_PAYLOAD_INVALID", "Webhook body could not be read.");
        }
        if (signature == null || signature.isBlank()) return error(HttpStatus.UNAUTHORIZED, "WEBHOOK_SIGNATURE_MISSING", "Webhook signature is missing.");
        if (!GithubWebhookSignatures.isValid(rawBody, signature, properties.webhookSecret())) return error(HttpStatus.UNAUTHORIZED, "WEBHOOK_SIGNATURE_INVALID", "Webhook signature is invalid.");
        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (IOException ex) {
            return error(HttpStatus.BAD_REQUEST, "WEBHOOK_PAYLOAD_INVALID", "Webhook payload is malformed.");
        }
        try {
            boolean processed = service.process(event, delivery, payload);
            return ResponseEntity.ok(Map.of("status", processed ? "processed" : "ignored"));
        } catch (GithubWebhookService.WebhookException ex) {
            return error(HttpStatus.valueOf(ex.status()), ex.code(), ex.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
    }
}
