package com.skilllink.api.analysis;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AnalysisRunRepository {
    private final JdbcTemplate jdbc;

    public AnalysisRunRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record RunRow(
        UUID id,
        UUID snapshotId,
        UUID candidateId,
        UUID projectId,
        UUID repositoryId,
        String analyzerVersion,
        String status,
        Instant startedAt,
        Instant completedAt,
        String failureCode,
        String failureMessage,
        int observationCount,
        Instant createdAt,
        Instant updatedAt
    ) {}

    private RunRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new RunRow(
            rs.getObject("id", UUID.class),
            rs.getObject("snapshot_id", UUID.class),
            rs.getObject("candidate_id", UUID.class),
            rs.getObject("project_id", UUID.class),
            rs.getObject("repository_id", UUID.class),
            rs.getString("analyzer_version"),
            rs.getString("status"),
            rs.getObject("started_at", java.time.OffsetDateTime.class) == null ? null : rs.getObject("started_at", java.time.OffsetDateTime.class).toInstant(),
            rs.getObject("completed_at", java.time.OffsetDateTime.class) == null ? null : rs.getObject("completed_at", java.time.OffsetDateTime.class).toInstant(),
            rs.getString("failure_code"),
            rs.getString("failure_message"),
            rs.getInt("observation_count"),
            rs.getObject("created_at", java.time.OffsetDateTime.class) == null ? null : rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
            rs.getObject("updated_at", java.time.OffsetDateTime.class) == null ? null : rs.getObject("updated_at", java.time.OffsetDateTime.class).toInstant()
        );
    }

    public UUID create(UUID snapshotId, UUID candidateId, UUID projectId, UUID repositoryId, String analyzerVersion) {
        return jdbc.queryForObject(
            """
            INSERT INTO analysis_run(snapshot_id, candidate_id, project_id, repository_id, analyzer_version, status)
            VALUES (?, ?, ?, ?, ?, 'QUEUED')
            RETURNING id
            """,
            UUID.class,
            snapshotId, candidateId, projectId, repositoryId, analyzerVersion
        );
    }

    public Optional<RunRow> findById(UUID candidateId, UUID projectId, UUID runId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM analysis_run WHERE id = ? AND candidate_id = ? AND project_id = ?",
                this::map, runId, candidateId, projectId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<RunRow> findByIdAnyCandidate(UUID runId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM analysis_run WHERE id = ?",
                this::map, runId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public List<RunRow> listBySnapshot(UUID candidateId, UUID projectId, UUID snapshotId) {
        return jdbc.query(
            "SELECT * FROM analysis_run WHERE candidate_id = ? AND project_id = ? AND snapshot_id = ? ORDER BY created_at DESC",
            this::map, candidateId, projectId, snapshotId);
    }

    public List<RunRow> listByProject(UUID candidateId, UUID projectId) {
        return jdbc.query(
            "SELECT * FROM analysis_run WHERE candidate_id = ? AND project_id = ? ORDER BY created_at DESC",
            this::map, candidateId, projectId);
    }

    public void updateStatus(UUID runId, String status) {
        jdbc.update("UPDATE analysis_run SET status = ? WHERE id = ?", status, runId);
    }

    public void markRunning(UUID runId) {
        jdbc.update("UPDATE analysis_run SET status = 'RUNNING', started_at = now() WHERE id = ?", runId);
    }

    public void markComplete(UUID runId, int observationCount) {
        jdbc.update(
            "UPDATE analysis_run SET status = 'COMPLETE', completed_at = now(), observation_count = ? WHERE id = ?",
            observationCount, runId);
    }

    public void markFailed(UUID runId, String failureCode, String failureMessage) {
        jdbc.update(
            "UPDATE analysis_run SET status = 'FAILED', completed_at = now(), failure_code = ?, failure_message = ? WHERE id = ?",
            failureCode, failureMessage, runId);
    }
}
