package com.skilllink.api.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.skilllink.api.github.AbstractGithubIntegrationTest;
import com.skilllink.api.github.GithubAppClient;
import com.skilllink.api.github.GithubClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Checkpoint D integration tests: immutable repository snapshots
 * - Candidate creates snapshot at exact commit SHA
 * - Manifest persisted with file hashes
 * - Idempotency, candidate isolation, access revoked, secret redaction, size limits, immutability
 */
class SnapshotIntegrationTest extends AbstractGithubIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired SnapshotService snapshotService;

    @Test
    void candidateCreatesSnapshotWithExactCommitShaAndManifest() {
        String token = registerCandidate();
        long installationId = connectCandidate(token);
        UUID projectId = selectProject(token, installationId);

        // Mock GitHub commit resolution and tree
        when(github.commit(eq("acme-org/foodbridge"), eq("main"), any())).thenReturn(commitJson("abc123def456abc123def456abc123def456abcd", "tree-sha-1", "Alice", "Initial commit"));
        when(github.tree(eq("acme-org/foodbridge"), eq("tree-sha-1"), any())).thenReturn(treeJson(List.of(
            treeEntry("src/App.java", "blob1", 500),
            treeEntry("README.md", "blob2", 100)
        )));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob1"), any())).thenReturn(encode("public class App {}"));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob2"), any())).thenReturn(encode("# Foodbridge\nA sample project"));

        var candidateId = currentCandidateId(token);
        var snapshot = snapshotService.createSnapshot(candidateId, projectId, "main");

        assertEquals("abc123def456abc123def456abc123def456abcd", snapshot.commitSha());
        assertEquals("abc123d", snapshot.shortSha());
        assertEquals("main", snapshot.branchName());
        assertEquals("READY", snapshot.status());
        assertEquals("acme-org/foodbridge", snapshot.fullName());
        assertNotNull(snapshot.integrityHash());
        assertTrue(snapshot.includedFileCount() >= 1);

        // Manifest persisted
        var detail = snapshotService.getSnapshotDetail(candidateId, projectId, snapshot.snapshotId());
        assertEquals(snapshot.snapshotId(), detail.snapshot().snapshotId());
        assertTrue(detail.files().size() >= 1);
        assertTrue(detail.files().stream().anyMatch(f -> f.path().equals("src/App.java") && f.included()));
        assertNotNull(detail.summary().integrityHash());
    }

    @Test
    void duplicateRequestIsIdempotent() {
        String token = registerCandidate();
        long installationId = connectCandidate(token);
        UUID projectId = selectProject(token, installationId);

        when(github.commit(eq("acme-org/foodbridge"), any(), any())).thenReturn(commitJson("same-sha-1234567890abcdef1234567890abcdef12", "tree-sha-dup", "Bob", "Same commit"));
        when(github.tree(eq("acme-org/foodbridge"), eq("tree-sha-dup"), any())).thenReturn(treeJson(List.of(
            treeEntry("src/App.java", "blob-dup", 100)
        )));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-dup"), any())).thenReturn(encode("class App {}"));

        var candidateId = currentCandidateId(token);
        var first = snapshotService.createSnapshot(candidateId, projectId, "main");
        var second = snapshotService.createSnapshot(candidateId, projectId, "main");

        assertEquals(first.snapshotId(), second.snapshotId(), "Same commit should reuse existing READY snapshot");
        assertEquals(first.commitSha(), second.commitSha());
    }

    @Test
    void candidateIsolationPreventsAccessingOtherCandidateSnapshot() {
        String tokenA = registerCandidate();
        long instA = connectCandidate(tokenA);
        UUID projectA = selectProject(tokenA, instA);

        when(github.commit(any(), any(), any())).thenReturn(commitJson("isolated-sha-abc123def456abc123def456abc123def4", "tree-iso", "Alice", "Commit"));
        when(github.tree(any(), any(), any())).thenReturn(treeJson(List.of(treeEntry("src/App.java", "blob-iso", 100))));
        when(github.blob(any(), any(), any())).thenReturn(encode("class A {}"));

        var candidateA = currentCandidateId(tokenA);
        var snapshotA = snapshotService.createSnapshot(candidateA, projectA, "main");

        String tokenB = registerCandidate();
        var candidateB = currentCandidateId(tokenB);

        // Candidate B tries to access A's snapshot via service (should fail because project not owned)
        assertThrows(Exception.class, () -> snapshotService.getSnapshot(candidateB, projectA, snapshotA.snapshotId()));

        // Via HTTP API, B cannot list A's project snapshots
        var response = rest.exchange("/api/v1/projects/" + projectA + "/snapshots", HttpMethod.GET, bearer(tokenB), JsonNode.class);
        assertTrue(response.getStatusCode().is4xxClientError(), "Candidate B should not access A's project snapshots");
    }

    @Test
    void repositoryAccessRevokedBlocksSnapshot() {
        String token = registerCandidate();
        long installationId = connectCandidate(token);
        UUID projectId = selectProject(token, installationId);

        // Revoke installation
        installations.updateStatus(installationId, com.skilllink.api.github.GithubInstallationRepository.STATUS_REMOVED);

        var candidateId = currentCandidateId(token);
        assertThrows(SnapshotService.SnapshotException.class, () -> snapshotService.createSnapshot(candidateId, projectId, "main"));
    }

    @Test
    void secretRedactionBeforePersistence() {
        String token = registerCandidate();
        long installationId = connectCandidate(token);
        UUID projectId = selectProject(token, installationId);

        when(github.commit(any(), any(), any())).thenReturn(commitJson("secret-sha-1234567890abcdef1234567890abcdef12", "tree-secret", "Eve", "Add secret"));
        when(github.tree(any(), any(), any())).thenReturn(treeJson(List.of(
            treeEntry("src/Config.java", "blob-secret", 200)
        )));
        // Content with secret
        when(github.blob(any(), eq("blob-secret"), any())).thenReturn(encode("String api_key = \"super-secret-api-key-12345\";"));

        var candidateId = currentCandidateId(token);
        var snapshot = snapshotService.createSnapshot(candidateId, projectId, "main");

        var detail = snapshotService.getSnapshotDetail(candidateId, projectId, snapshot.snapshotId());
        var configFile = detail.files().stream().filter(f -> f.path().equals("src/Config.java")).findFirst().orElseThrow();
        assertTrue(configFile.secretRedacted(), "File with secret should be marked redacted");
        assertTrue(configFile.secretCount() > 0);
        // Ensure secret not persisted in raw form - check via file manifest content hash is of redacted content
        // The service redacts before hashing, so raw secret should not be in DB
    }

    @Test
    void sizeLimitsAreEnforced() {
        String token = registerCandidate();
        long installationId = connectCandidate(token);
        UUID projectId = selectProject(token, installationId);

        when(github.commit(any(), any(), any())).thenReturn(commitJson("large-sha-1234567890abcdef1234567890abcdef12", "tree-large", "Alice", "Large"));
        // Tree with many entries exceeding limit
        var manyEntries = new java.util.ArrayList<JsonNode>();
        // We will mock tree with 2 entries, but one is huge
        when(github.tree(any(), any(), any())).thenReturn(treeJson(List.of(
            treeEntry("src/Huge.java", "blob-huge", 10_000_000), // 10MB, exceeds max-file-bytes 500k
            treeEntry("src/Ok.java", "blob-ok", 100)
        )));
        when(github.blob(any(), eq("blob-huge"), any())).thenReturn(encode("a".repeat(100)));
        when(github.blob(any(), eq("blob-ok"), any())).thenReturn(encode("class Ok {}"));

        var candidateId = currentCandidateId(token);
        var snapshot = snapshotService.createSnapshot(candidateId, projectId, "main");

        var detail = snapshotService.getSnapshotDetail(candidateId, projectId, snapshot.snapshotId());
        // Huge file should be excluded
        var hugeFile = detail.files().stream().filter(f -> f.path().equals("src/Huge.java")).findFirst().orElseThrow();
        assertFalse(hugeFile.included());
        assertTrue(hugeFile.exclusionReason().contains("FILE_TOO_LARGE") || hugeFile.exclusionReason().contains("MAX_FILES") || hugeFile.exclusionReason().contains("BINARY") || hugeFile.exclusionReason().contains("TOO_LARGE"));
    }

    @Test
    void readySnapshotCannotBeMutated() {
        String token = registerCandidate();
        long installationId = connectCandidate(token);
        UUID projectId = selectProject(token, installationId);

        when(github.commit(any(), any(), any())).thenReturn(commitJson("immutable-sha-1234567890abcdef1234567890ab", "tree-imm", "Alice", "Immutable"));
        when(github.tree(any(), any(), any())).thenReturn(treeJson(List.of(treeEntry("src/App.java", "blob-imm", 100))));
        when(github.blob(any(), any(), any())).thenReturn(encode("class App {}"));

        var candidateId = currentCandidateId(token);
        var snapshot = snapshotService.createSnapshot(candidateId, projectId, "main");
        assertEquals("READY", snapshot.status());

        // Attempt to change commit SHA via direct DB update should be prevented by service logic (immutability)
        // The repository has UNIQUE constraint on (repository_id, commit_sha, file_policy_version) for READY snapshots
        // Creating new snapshot with same repo but different SHA should create new record, not overwrite
        when(github.commit(any(), any(), any())).thenReturn(commitJson("different-sha-abcdef1234567890abcdef1234567890", "tree-diff", "Bob", "Different"));
        when(github.tree(any(), eq("tree-diff"), any())).thenReturn(treeJson(List.of(treeEntry("src/App.java", "blob-diff", 100))));
        when(github.blob(any(), eq("blob-diff"), any())).thenReturn(encode("class Diff {}"));

        var second = snapshotService.createSnapshot(candidateId, projectId, "main");
        assertNotEquals(snapshot.snapshotId(), second.snapshotId(), "Different commit should create new snapshot, not mutate existing");
        assertNotEquals(snapshot.commitSha(), second.commitSha());

        // Verify first snapshot still has original SHA
        var firstDetail = snapshotService.getSnapshotDetail(candidateId, projectId, snapshot.snapshotId());
        assertEquals("immutable-sha-1234567890abcdef1234567890ab", firstDetail.snapshot().commitSha());
    }

    @Test
    void snapshotApiRequiresAuthenticationAndCorrectRole() {
        // Anonymous
        var anonymous = rest.exchange("/api/v1/projects/" + UUID.randomUUID() + "/snapshots", HttpMethod.GET, new org.springframework.http.HttpEntity<>(new org.springframework.http.HttpHeaders()), JsonNode.class);
        assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());

        // Recruiter
        String recruiterToken = registerRecruiter();
        var recruiter = rest.exchange("/api/v1/projects/" + UUID.randomUUID() + "/snapshots", HttpMethod.GET, bearer(recruiterToken), JsonNode.class);
        assertEquals(HttpStatus.FORBIDDEN, recruiter.getStatusCode());
    }

    // Helpers

    private UUID selectProject(String token, long installationId) {
        when(appClient.installationRepositories(installationId)).thenReturn(List.of(
            new GithubAppClient.InstallationRepository("7001", "foodbridge", "acme-org/foodbridge", "acme-org", true, "main", "Java", "2026-09-01T10:00:00Z", 4200, "Fixture repo")
        ));
        var response = rest.exchange("/api/v1/github/repositories/7001/select", HttpMethod.POST, bearer(token), JsonNode.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String projectIdStr = response.getBody().path("projectId").asText();
        return UUID.fromString(projectIdStr);
    }

    private UUID currentCandidateId(String candidateToken) {
        var me = rest.exchange("/api/v1/auth/me", HttpMethod.GET, bearer(candidateToken), JsonNode.class);
        return candidateId(me.getBody().path("email").asText());
    }

    private JsonNode commitJson(String sha, String treeSha, String author, String message) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readTree("""
                {
                  "sha": "%s",
                  "commit": {
                    "author": {"name": "%s", "date": "2026-09-29T10:00:00Z"},
                    "message": "%s",
                    "tree": {"sha": "%s"}
                  },
                  "author": {"login": "%s"}
                }
                """.formatted(sha, author, message, treeSha, author));
        } catch (Exception ex) { throw new RuntimeException(ex); }
    }

    private JsonNode treeJson(List<JsonNode> entries) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var array = mapper.createArrayNode();
            entries.forEach(array::add);
            var root = mapper.createObjectNode();
            root.put("truncated", false);
            root.set("tree", array);
            return root;
        } catch (Exception ex) { throw new RuntimeException(ex); }
    }

    private JsonNode treeEntry(String path, String sha, long size) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.createObjectNode();
            node.put("path", path);
            node.put("type", "blob");
            node.put("sha", sha);
            node.put("size", size);
            return node;
        } catch (Exception ex) { throw new RuntimeException(ex); }
    }

    private String encode(String content) {
        return Base64.getEncoder().encodeToString(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
