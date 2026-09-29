package com.skilllink.api.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class AuditService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    public AuditService(JdbcTemplate jdbc, ObjectMapper objectMapper) { this.jdbc = jdbc; this.objectMapper = objectMapper; }

    public void record(UUID actorUserId, String action, String resourceType, UUID resourceId, String requestId, Map<String, ?> metadata) {
        try {
            jdbc.update("INSERT INTO audit_log(actor_user_id, action, resource_type, resource_id, request_id, metadata) VALUES (?, ?, ?, ?, ?, ?::jsonb)", actorUserId, action, resourceType, resourceId, requestId, objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata));
        } catch (Exception ignored) {
            // Audit writes must never turn a successful user operation into a duplicate or provider-facing error.
        }
    }
}
