package com.skilllink.api.github;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Checkpoint C webhook security: unsigned or wrongly signed deliveries are rejected, valid
 * signatures are processed exactly once, and installation/repository access changes stop future
 * access without deleting historical evidence.
 */
class GithubWebhookIntegrationTest extends AbstractGithubIntegrationTest {

    private static String installationEvent(String action, long installationId) {
        return "{\"action\":\"" + action + "\",\"installation\":{\"id\":" + installationId + "}}";
    }

    @Test
    void missingSignatureIsRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-GitHub-Event", "installation");
        headers.set("X-GitHub-Delivery", UUID.randomUUID().toString());
        org.springframework.http.ResponseEntity<JsonNode> response = rest.exchange("/api/v1/github/webhooks", HttpMethod.POST, new HttpEntity<>("{}", headers), JsonNode.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("WEBHOOK_SIGNATURE_MISSING", response.getBody().path("code").asText());
    }

    @Test
    void invalidSignatureIsRejected() {
        org.springframework.http.ResponseEntity<JsonNode> response = postWebhook("installation", UUID.randomUUID().toString(), "{\"action\":\"deleted\"}", "wrong-secret");
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("WEBHOOK_SIGNATURE_INVALID", response.getBody().path("code").asText());
    }

    @Test
    void validInstallationDeletedSignatureMarksInstallationRemoved() {
        String token = registerCandidate();
        long installationId = connectCandidate(token);
        assertEquals(GithubDtos.STATUS_CONNECTED, status(token).status());

        String delivery = UUID.randomUUID().toString();
        org.springframework.http.ResponseEntity<JsonNode> response = postWebhook("installation", delivery,
            installationEvent("deleted", installationId), webhookSecret);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("processed", response.getBody().path("status").asText());

        assertEquals(GithubDtos.STATUS_INSTALLATION_REMOVED, status(token).status());
        org.springframework.http.ResponseEntity<JsonNode> repositories = rest.exchange("/api/v1/github/repositories", HttpMethod.GET, bearer(token), JsonNode.class);
        assertEquals(HttpStatus.BAD_REQUEST, repositories.getStatusCode());
        assertEquals("GITHUB_INSTALLATION_REMOVED", repositories.getBody().path("code").asText());
    }

    @Test
    void duplicateDeliveryIsProcessedOnlyOnce() {
        String token = registerCandidate();
        long installationId = connectCandidate(token);
        String delivery = UUID.randomUUID().toString();
        String body = installationEvent("deleted", installationId);

        org.springframework.http.ResponseEntity<JsonNode> first = postWebhook("installation", delivery, body, webhookSecret);
        assertEquals(HttpStatus.OK, first.getStatusCode());
        org.springframework.http.ResponseEntity<JsonNode> second = postWebhook("installation", delivery, body, webhookSecret);
        assertEquals(HttpStatus.OK, second.getStatusCode());
        assertEquals("ignored", second.getBody().path("status").asText(), "duplicate deliveries must not be processed twice");

        // The installation was removed by the first delivery; the duplicate cannot resurrect it.
        assertEquals(GithubDtos.STATUS_INSTALLATION_REMOVED, status(token).status());
    }

    @Test
    void malformedPayloadWithValidSignatureIsRejected() {
        org.springframework.http.ResponseEntity<JsonNode> response = postWebhook("installation", UUID.randomUUID().toString(), "{not-valid-json", webhookSecret);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("WEBHOOK_PAYLOAD_INVALID", response.getBody().path("code").asText());
    }

    @Test
    void unsupportedEventIsRecordedButIgnored() {
        org.springframework.http.ResponseEntity<JsonNode> response = postWebhook("star", UUID.randomUUID().toString(), "{\"action\":\"created\"}", webhookSecret);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ignored", response.getBody().path("status").asText());
    }

    @Test
    void repositoryRemovedEventStopsAccessWithoutDeletingHistory() {
        String token = registerCandidate();
        long installationId = connectCandidate(token);
        when(appClient.installationRepositories(installationId)).thenReturn(List.of(
            new GithubAppClient.InstallationRepository("7001", "foodbridge", "acme-org/foodbridge", "acme-org", true, "main", "Java", "2026-09-01T10:00:00Z", 4200, "Fixture repo")));
        org.springframework.http.ResponseEntity<JsonNode> selected = rest.exchange("/api/v1/github/repositories/7001/select", HttpMethod.POST, bearer(token), JsonNode.class);
        assertEquals(HttpStatus.OK, selected.getStatusCode());

        org.springframework.http.ResponseEntity<JsonNode> response = postWebhook("installation_repositories", UUID.randomUUID().toString(),
            "{\"action\":\"removed\",\"installation\":{\"id\":" + installationId + "},\"repositories_removed\":[{\"full_name\":\"acme-org/foodbridge\"}]}", webhookSecret);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        String accessStatus = jdbc.queryForObject("SELECT connection_status FROM repository WHERE external_id = '7001'", String.class);
        assertEquals("REVOKED", accessStatus, "future synchronization must stop for removed repositories");
        Integer projectCount = jdbc.queryForObject("SELECT count(*) FROM project", Integer.class);
        assertTrue(projectCount >= 1, "historical project references must survive access removal");
    }

    @Test
    void installationSuspendAndUnsuspendAreTracked() {
        String token = registerCandidate();
        long installationId = connectCandidate(token);

        org.springframework.http.ResponseEntity<JsonNode> suspended = postWebhook("installation", UUID.randomUUID().toString(),
            installationEvent("suspend", installationId), webhookSecret);
        assertEquals(HttpStatus.OK, suspended.getStatusCode());
        assertEquals(GithubDtos.STATUS_INSTALLATION_REMOVED, status(token).status());

        org.springframework.http.ResponseEntity<JsonNode> unsuspended = postWebhook("installation", UUID.randomUUID().toString(),
            installationEvent("unsuspend", installationId), webhookSecret);
        assertEquals(HttpStatus.OK, unsuspended.getStatusCode());
        assertEquals(GithubDtos.STATUS_CONNECTED, status(token).status());
    }
}
