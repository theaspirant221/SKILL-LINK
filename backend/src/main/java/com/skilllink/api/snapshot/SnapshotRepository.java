package com.skilllink.api.snapshot;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SnapshotRepository {
    private final JdbcTemplate jdbc;

    public SnapshotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record SnapshotRow(
        UUID id,
        UUID candidateId,
        UUID projectId,
        UUID repositoryId,
        String githubRepositoryId,
        String ownerLogin,
        String repositoryName,
        String fullName,
        String commitSha,
        String branchName,
        String commitAuthor,
        String commitMessage,
        Instant commitTimestamp,
        Instant snapshotCreatedAt,
        String analysisVersion,
        String filePolicyVersion,
        String status,
        int fileCount,
        int includedFileCount,
        int excludedFileCount,
        long totalBytes,
        String integrityHash,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt,
        String treeSha,
        String sourceHash
    ) {}

    private SnapshotRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new SnapshotRow(
            rs.getObject("id", UUID.class),
            rs.getObject("candidate_id", UUID.class),
            rs.getObject("project_id", UUID.class),
            rs.getObject("repository_id", UUID.class),
            rs.getString("github_repository_id"),
            rs.getString("owner_login"),
            rs.getString("repository_name"),
            rs.getString("full_name"),
            rs.getString("commit_sha"),
            rs.getString("branch_name"),
            rs.getString("commit_author"),
            rs.getString("commit_message"),
            rs.getObject("commit_timestamp", java.time.OffsetDateTime.class) == null ? null : rs.getObject("commit_timestamp", java.time.OffsetDateTime.class).toInstant(),
            rs.getObject("snapshot_created_at", java.time.OffsetDateTime.class) == null ? null : rs.getObject("snapshot_created_at", java.time.OffsetDateTime.class).toInstant(),
            rs.getString("analysis_version"),
            rs.getString("file_policy_version"),
            rs.getString("status"),
            rs.getInt("file_count"),
            rs.getInt("included_file_count"),
            rs.getInt("excluded_file_count"),
            rs.getLong("total_bytes"),
            rs.getString("integrity_hash"),
            rs.getString("error_code"),
            rs.getString("error_message"),
            rs.getObject("created_at", java.time.OffsetDateTime.class) == null ? null : rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
            rs.getObject("updated_at", java.time.OffsetDateTime.class) == null ? null : rs.getObject("updated_at", java.time.OffsetDateTime.class).toInstant(),
            rs.getString("tree_sha"),
            rs.getString("source_hash")
        );
    }

    public Optional<SnapshotRow> findById(UUID candidateId, UUID projectId, UUID snapshotId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM repository_snapshot WHERE id = ? AND candidate_id = ? AND project_id = ?",
                this::map, snapshotId, candidateId, projectId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<SnapshotRow> findByIdAnyCandidate(UUID snapshotId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM repository_snapshot WHERE id = ?",
                this::map, snapshotId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public List<SnapshotRow> listByProject(UUID candidateId, UUID projectId) {
        return jdbc.query(
            "SELECT * FROM repository_snapshot WHERE candidate_id = ? AND project_id = ? ORDER BY created_at DESC",
            this::map, candidateId, projectId);
    }

    public Optional<SnapshotRow> findReadyByRepoCommitAndPolicy(UUID repositoryId, String commitSha, String policyVersion) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM repository_snapshot WHERE repository_id = ? AND commit_sha = ? AND file_policy_version = ? AND status = 'READY' ORDER BY created_at DESC LIMIT 1",
                this::map, repositoryId, commitSha, policyVersion));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public UUID create(
        UUID candidateId,
        UUID projectId,
        UUID repositoryId,
        String githubRepositoryId,
        String ownerLogin,
        String repositoryName,
        String fullName,
        String commitSha,
        String branchName,
        String treeSha,
        String filePolicyVersion,
        String analysisVersion
    ) {
        return jdbc.queryForObject(
            """
            INSERT INTO repository_snapshot(
                candidate_id, project_id, repository_id, github_repository_id,
                owner_login, repository_name, full_name,
                commit_sha, branch_name, branch, tree_sha,
                file_policy_version, analysis_version,
                status, source_hash, source_observed_at, snapshot_created_at,
                file_count, included_file_count, excluded_file_count, total_bytes
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'CREATED', ?, now(), now(), 0, 0, 0, 0)
            RETURNING id
            """,
            UUID.class,
            candidateId, projectId, repositoryId, githubRepositoryId,
            ownerLogin, repositoryName, fullName,
            commitSha, branchName, branchName, treeSha,
            filePolicyVersion, analysisVersion,
            "pending:" + commitSha
        );
    }

    public void updateCommitMetadata(UUID snapshotId, String commitAuthor, String commitMessage, Instant commitTimestamp) {
        jdbc.update(
            "UPDATE repository_snapshot SET commit_author = ?, commit_message = ?, commit_timestamp = ?, updated_at = now() WHERE id = ?",
            commitAuthor, commitMessage, commitTimestamp == null ? null : Timestamp.from(commitTimestamp), snapshotId
        );
    }

    public void updateStatus(UUID snapshotId, String status) {
        jdbc.update("UPDATE repository_snapshot SET status = ?, updated_at = now() WHERE id = ?", status, snapshotId);
    }

    public void markReady(UUID snapshotId, int fileCount, int included, int excluded, long totalBytes, String integrityHash, String sourceHash) {
        jdbc.update(
            """
            UPDATE repository_snapshot SET
                status = 'READY',
                file_count = ?,
                included_file_count = ?,
                excluded_file_count = ?,
                total_bytes = ?,
                content_size_bytes = ?,
                selected_file_count = ?,
                file_count = ?,
                integrity_hash = ?,
                source_hash = ?,
                snapshot_created_at = now(),
                updated_at = now()
            WHERE id = ?
            """,
            fileCount, included, excluded, totalBytes, totalBytes, included, fileCount, integrityHash, sourceHash, snapshotId
        );
    }

    public void markFailed(UUID snapshotId, String errorCode, String errorMessage) {
        jdbc.update(
            "UPDATE repository_snapshot SET status = 'FAILED', error_code = ?, error_message = ?, updated_at = now() WHERE id = ?",
            errorCode, errorMessage, snapshotId
        );
    }

    public void updateProgress(UUID snapshotId, String status, int fileCount, long totalBytes) {
        jdbc.update(
            "UPDATE repository_snapshot SET status = ?, file_count = ?, total_bytes = ?, updated_at = now() WHERE id = ?",
            status, fileCount, totalBytes, snapshotId
        );
    }
}
