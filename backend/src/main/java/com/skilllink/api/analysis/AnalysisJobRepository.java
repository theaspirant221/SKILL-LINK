package com.skilllink.api.analysis;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AnalysisJobRepository {
    private final JdbcTemplate jdbc;
    public AnalysisJobRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String SELECT = "SELECT id, candidate_id, project_id, repository_id, repository_snapshot_id, state, progress, stage, attempt, error_code, error_message, created_at, started_at, completed_at FROM analysis_job ";

    public UUID create(UUID candidateId, UUID projectId, UUID repositoryId, String requestId) {
        return jdbc.queryForObject("INSERT INTO analysis_job(candidate_id, project_id, repository_id, request_id) VALUES (?, ?, ?, ?) RETURNING id", UUID.class, candidateId, projectId, repositoryId, requestId);
    }

    public Optional<JobRecord> find(UUID candidateId, UUID projectId, UUID jobId) { return query(SELECT + "WHERE id = ? AND candidate_id = ? AND project_id = ?", jobId, candidateId, projectId); }
    public Optional<JobRecord> findById(UUID jobId) { return query(SELECT + "WHERE id = ?", jobId); }

    private Optional<JobRecord> query(String sql, Object... args) {
        try { return Optional.ofNullable(jdbc.queryForObject(sql, (rs, rowNum) -> map(rs), args)); }
        catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
    }

    private JobRecord map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new JobRecord(rs.getObject("id", UUID.class), rs.getObject("candidate_id", UUID.class), rs.getObject("project_id", UUID.class), rs.getObject("repository_id", UUID.class), rs.getObject("repository_snapshot_id", UUID.class), rs.getString("state"), rs.getInt("progress"), rs.getString("stage"), rs.getInt("attempt"), rs.getString("error_code"), rs.getString("error_message"), toInstant(rs.getObject("created_at", java.time.OffsetDateTime.class)), toInstant(rs.getObject("started_at", java.time.OffsetDateTime.class)), toInstant(rs.getObject("completed_at", java.time.OffsetDateTime.class)));
    }

    public void updateState(UUID jobId, String state, int progress, String stage) { jdbc.update("UPDATE analysis_job SET state = ?, progress = ?, stage = ?, started_at = coalesce(started_at, now()) WHERE id = ?", state, progress, stage, jobId); }
    public void attachSnapshot(UUID jobId, UUID snapshotId) { jdbc.update("UPDATE analysis_job SET repository_snapshot_id = ? WHERE id = ?", snapshotId, jobId); }
    public void complete(UUID jobId) { jdbc.update("UPDATE analysis_job SET state = 'COMPLETED', progress = 100, stage = 'Completed', completed_at = now(), error_code = NULL, error_message = NULL WHERE id = ?", jobId); }
    public void fail(UUID jobId, String errorCode, String message) { jdbc.update("UPDATE analysis_job SET state = 'FAILED', stage = 'Failed', error_code = ?, error_message = ?, completed_at = now() WHERE id = ?", errorCode, message, jobId); }
    public void retry(UUID jobId) { jdbc.update("UPDATE analysis_job SET state = 'QUEUED', progress = 0, stage = 'Queued', attempt = attempt + 1, error_code = NULL, error_message = NULL, started_at = NULL, completed_at = NULL WHERE id = ?", jobId); }
    public record JobRecord(UUID id, UUID candidateId, UUID projectId, UUID repositoryId, UUID snapshotId, String state, int progress, String stage, int attempt, String errorCode, String errorMessage, Instant createdAt, Instant startedAt, Instant completedAt) {}
    private static Instant toInstant(java.time.OffsetDateTime value) { return value == null ? null : value.toInstant(); }
}
