package com.skilllink.api.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skilllink.api.analysis.SecretRedactor;
import com.skilllink.api.github.GithubAppClient;
import com.skilllink.api.github.GithubClient;
import com.skilllink.api.github.GithubConnectionRepository;
import com.skilllink.api.github.GithubDtos;
import com.skilllink.api.github.GithubInstallationRepository;
import com.skilllink.api.github.GithubOAuthService;
import com.skilllink.api.github.GithubRepositoryJdbcRepository;
import com.skilllink.api.project.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SnapshotServiceTest {
    @Mock ProjectService projects;
    @Mock GithubRepositoryJdbcRepository githubRepositories;
    @Mock GithubOAuthService githubOAuth;
    @Mock GithubClient github;
    @Mock GithubAppClient appClient;
    @Mock GithubInstallationRepository installations;
    @Mock GithubConnectionRepository connections;
    @Mock SnapshotRepository snapshots;
    @Mock SnapshotFileRepository snapshotFiles;
    @Mock JdbcTemplate jdbc;

    private SnapshotService service;
    private final ObjectMapper mapper = new ObjectMapper();
    private final SnapshotFilePolicy policy = new SnapshotFilePolicy(500_000, 20_000_000, 1000, 10000, 20, "v1");
    private final SecretRedactor redactor = new SecretRedactor();

    @BeforeEach
    void setUp() {
        service = new SnapshotService(projects, githubRepositories, githubOAuth, github, appClient, installations, connections, snapshots, snapshotFiles, policy, redactor, jdbc);
    }

    @Test
    void createsSnapshotWithResolvedCommitSha() throws Exception {
        UUID candidateId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID repositoryId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        var repository = new GithubRepositoryJdbcRepository.RepositoryRow(repositoryId, projectId, "acme/foodbridge", "main", "acme", "Java", "PRIVATE");

        when(projects.repository(candidateId, projectId)).thenReturn(repository);
        when(githubOAuth.repositories(candidateId)).thenReturn(List.of(
            new GithubDtos.RepositorySummary("7001", "foodbridge", "acme/foodbridge", "acme", true, "PRIVATE", "main", "Java", Instant.parse("2026-09-01T10:00:00Z"), 4200, "Fixture repo", "acme-org")
        ));
        when(githubOAuth.accessToken(candidateId)).thenReturn("token");
        when(snapshots.findReadyByRepoCommitAndPolicy(eq(repositoryId), anyString(), eq("v1"))).thenReturn(java.util.Optional.empty());

        // Mock commit resolution
        when(github.commit(eq("acme/foodbridge"), eq("main"), eq("token"))).thenReturn(mapper.readTree("""
            {
              "sha": "abc123def456abc123def456abc123def456abcd",
              "commit": {
                "author": {"name": "Alice", "date": "2026-09-29T10:00:00Z"},
                "message": "Initial commit",
                "tree": {"sha": "tree-sha-123"}
              }
            }
            """));

        when(snapshots.create(any(UUID.class), any(UUID.class), any(UUID.class), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(snapshotId);
        when(snapshots.findById(eq(candidateId), eq(projectId), eq(snapshotId))).thenReturn(java.util.Optional.of(
            new SnapshotRepository.SnapshotRow(
                snapshotId, candidateId, projectId, repositoryId,
                "7001", "acme", "foodbridge", "acme/foodbridge",
                "abc123def456abc123def456abc123def456abcd", "main",
                "Alice", "Initial commit", Instant.parse("2026-09-29T10:00:00Z"),
                Instant.now(), "v1", "v1", "READY",
                2, 2, 0, 1000L, "integrity-hash", null, null,
                Instant.now(), Instant.now(), "tree-sha-123", "source-hash"
            )
        ));

        // Mock tree
        when(github.tree(eq("acme/foodbridge"), eq("tree-sha-123"), eq("token"))).thenReturn(mapper.readTree("""
            {
              "truncated": false,
              "tree": [
                {"path": "src/App.java", "type": "blob", "sha": "blob1", "size": 500},
                {"path": "README.md", "type": "blob", "sha": "blob2", "size": 100}
              ]
            }
            """));

        when(github.blob(eq("acme/foodbridge"), eq("blob1"), eq("token"))).thenReturn(java.util.Base64.getEncoder().encodeToString("public class App {}".getBytes()));
        when(github.blob(eq("acme/foodbridge"), eq("blob2"), eq("token"))).thenReturn(java.util.Base64.getEncoder().encodeToString("# README".getBytes()));

        SnapshotDtos.SnapshotResponse response = service.createSnapshot(candidateId, projectId, "main");

        assertNotNull(response);
        assertEquals("abc123def456abc123def456abc123def456abcd", response.commitSha());
        assertEquals("abc123d", response.shortSha());
        assertEquals("main", response.branchName());
        assertEquals("READY", response.status());
        assertEquals("acme/foodbridge", response.fullName());

        verify(snapshots).create(any(UUID.class), any(UUID.class), any(UUID.class), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(snapshotFiles).insertBatch(eq(snapshotId), anyList());
    }

    @Test
    void reusesExistingReadySnapshotForIdempotency() throws Exception {
        UUID candidateId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID repositoryId = UUID.randomUUID();
        var repository = new GithubRepositoryJdbcRepository.RepositoryRow(repositoryId, projectId, "acme/foodbridge", "main", "acme", "Java", "PRIVATE");

        when(projects.repository(candidateId, projectId)).thenReturn(repository);
        when(githubOAuth.repositories(candidateId)).thenReturn(List.of(
            new GithubDtos.RepositorySummary("7001", "foodbridge", "acme/foodbridge", "acme", true, "PRIVATE", "main", "Java", Instant.parse("2026-09-01T10:00:00Z"), 4200, "Fixture repo", "acme-org")
        ));
        when(githubOAuth.accessToken(candidateId)).thenReturn("token");
        when(github.commit(anyString(), anyString(), anyString())).thenReturn(mapper.readTree("""
            {"sha": "same-sha-123", "commit": {"author": {"name": "Bob", "date": "2026-09-29T10:00:00Z"}, "message": "msg", "tree": {"sha": "tree-sha"}}}
            """));

        UUID existingId = UUID.randomUUID();
        var existingRow = new SnapshotRepository.SnapshotRow(
            existingId, candidateId, projectId, repositoryId,
            "7001", "acme", "foodbridge", "acme/foodbridge",
            "same-sha-123", "main",
            "Bob", "msg", Instant.now(),
            Instant.now(), "v1", "v1", "READY",
            1, 1, 0, 100L, "hash", null, null,
            Instant.now(), Instant.now(), "tree-sha", "source"
        );
        when(snapshots.findReadyByRepoCommitAndPolicy(repositoryId, "same-sha-123", "v1")).thenReturn(java.util.Optional.of(existingRow));

        SnapshotDtos.SnapshotResponse response = service.createSnapshot(candidateId, projectId, "main");

        assertEquals(existingId, response.snapshotId());
        assertEquals("same-sha-123", response.commitSha());
        verify(snapshots, never()).create(any(UUID.class), any(UUID.class), any(UUID.class), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void rejectsUnauthorizedRepository() {
        UUID candidateId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID repositoryId = UUID.randomUUID();
        var repository = new GithubRepositoryJdbcRepository.RepositoryRow(repositoryId, projectId, "acme/private-repo", "main", "acme", "Java", "PRIVATE");

        when(projects.repository(candidateId, projectId)).thenReturn(repository);
        when(githubOAuth.repositories(candidateId)).thenReturn(List.of()); // empty, not authorized
        when(githubOAuth.status(candidateId)).thenReturn(new GithubDtos.ConnectionStatus("CONNECTED", true, "user", 123L, "", Instant.now(), Instant.now(), null));

        assertThrows(SnapshotService.SnapshotException.class, () -> service.createSnapshot(candidateId, projectId, "main"));
    }
}
