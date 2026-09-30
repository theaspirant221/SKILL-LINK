package com.skilllink.api.github;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * GitHub App webhook processing for access lifecycle events. Every delivery is recorded by its
 * GitHub delivery id so duplicates are never processed twice, and installation/repository access
 * changes stop future synchronization without deleting historical evidence.
 */
@Service
public class GithubWebhookService {
    private final GithubProperties properties;
    private final GithubInstallationRepository installations;
    private final GithubConnectionRepository connections;
    private final GithubAppClient appClient;
    private final JdbcTemplate jdbc;

    public GithubWebhookService(GithubProperties properties, GithubInstallationRepository installations, GithubConnectionRepository connections, GithubAppClient appClient, JdbcTemplate jdbc) {
        this.properties = properties; this.installations = installations; this.connections = connections; this.appClient = appClient; this.jdbc = jdbc;
    }

    public static class WebhookException extends RuntimeException {
        private final int status; private final String code;
        public WebhookException(int status, String code, String message) { super(message); this.status = status; this.code = code; }
        public int status() { return status; } public String code() { return code; }
    }

    /** Returns true when the event caused a state change, false when it was ignored or duplicated. */
    @Transactional
    public boolean process(String event, String deliveryId, JsonNode payload) {
        if (event == null || event.isBlank() || deliveryId == null || deliveryId.isBlank()) throw new WebhookException(400, "WEBHOOK_HEADERS_MISSING", "Webhook event and delivery identifiers are required.");
        if (payload == null || payload.isNull() || !payload.isObject()) throw new WebhookException(400, "WEBHOOK_PAYLOAD_INVALID", "Webhook payload is malformed.");
        String action = payload.path("action").asText(null);
        int inserted = jdbc.update("INSERT INTO github_webhook_event(delivery_id, event, action) VALUES (?, ?, ?) ON CONFLICT (delivery_id) DO NOTHING", deliveryId, event, action);
        if (inserted == 0) return false; // duplicate delivery; already processed
        if ("installation".equals(event)) return handleInstallation(payload);
        if ("installation_repositories".equals(event)) return handleInstallationRepositories(payload);
        return false; // unrelated event types are recorded but ignored
    }

    private boolean handleInstallation(JsonNode payload) {
        String action = payload.path("action").asText("");
        long installationId = payload.path("installation").path("id").asLong(0);
        if (installationId == 0) throw new WebhookException(400, "WEBHOOK_PAYLOAD_INVALID", "Installation event is missing the installation id.");
        GithubInstallationRepository.InstallationRow row = installations.findByInstallationId(installationId).orElse(null);
        switch (action) {
            case "deleted", "suspend" -> {
                if (row != null) {
                    installations.updateStatus(installationId, "deleted".equals(action) ? GithubInstallationRepository.STATUS_REMOVED : GithubInstallationRepository.STATUS_SUSPENDED);
                    connections.updateStatus(row.candidateId(), GithubConnectionRepository.STATUS_INSTALLATION_REMOVED);
                }
                appClient.evictInstallationToken(installationId);
                return row != null;
            }
            case "created", "unsuspend", "new_permissions_accepted" -> {
                if (row != null) {
                    installations.updateStatus(installationId, GithubInstallationRepository.STATUS_ACTIVE);
                    connections.updateStatus(row.candidateId(), GithubConnectionRepository.STATUS_ACTIVE);
                }
                return row != null; // unknown installations are linked through the install callback state
            }
            default -> { return false; }
        }
    }

    private boolean handleInstallationRepositories(JsonNode payload) {
        String action = payload.path("action").asText("");
        long installationId = payload.path("installation").path("id").asLong(0);
        if (installationId == 0) throw new WebhookException(400, "WEBHOOK_PAYLOAD_INVALID", "Installation repositories event is missing the installation id.");
        GithubInstallationRepository.InstallationRow row = installations.findByInstallationId(installationId).orElse(null);
        if (row == null) return false;
        List<String> fullNames = new ArrayList<>();
        String field = "removed".equals(action) ? "repositories_removed" : "added".equals(action) ? "repositories_added" : null;
        if (field != null) for (JsonNode repository : payload.path(field)) fullNames.add(repository.path("full_name").asText(null));
        if ("removed".equals(action)) {
            installations.markRepositoriesRevoked(fullNames);
            return true;
        }
        if ("added".equals(action)) {
            installations.markRepositoriesAvailable(fullNames);
            return true;
        }
        return false;
    }
}
