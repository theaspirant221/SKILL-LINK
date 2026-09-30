package com.skilllink.api.analysis;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class AnalysisFileErrorRepository {
    private final JdbcTemplate jdbc;

    public AnalysisFileErrorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record FileErrorRow(
        UUID id,
        UUID analysisRunId,
        UUID snapshotId,
        String sourcePath,
        String errorCode,
        String errorMessage,
        String detector,
        Instant createdAt
    ) {}

    private FileErrorRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new FileErrorRow(
            rs.getObject("id", UUID.class),
            rs.getObject("analysis_run_id", UUID.class),
            rs.getObject("snapshot_id", UUID.class),
            rs.getString("source_path"),
            rs.getString("error_code"),
            rs.getString("error_message"),
            rs.getString("detector"),
            rs.getObject("created_at", java.time.OffsetDateTime.class) == null ? null : rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant()
        );
    }

    public List<FileErrorRow> listByRun(UUID runId) {
        return jdbc.query(
            "SELECT * FROM analysis_file_error WHERE analysis_run_id = ? ORDER BY source_path",
            this::map, runId);
    }

    public void deleteByRun(UUID runId) {
        jdbc.update("DELETE FROM analysis_file_error WHERE analysis_run_id = ?", runId);
    }

    public void insertBatch(UUID runId, List<FileErrorRow> errors) {
        if (errors.isEmpty()) return;
        jdbc.batchUpdate(
            """
            INSERT INTO analysis_file_error(analysis_run_id, snapshot_id, source_path, error_code, error_message, detector)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (analysis_run_id, source_path, error_code) DO NOTHING
            """,
            errors,
            100,
            (ps, err) -> {
                try {
                    ps.setObject(1, err.analysisRunId());
                    ps.setObject(2, err.snapshotId());
                    ps.setString(3, err.sourcePath());
                    ps.setString(4, err.errorCode());
                    ps.setString(5, err.errorMessage());
                    ps.setString(6, err.detector());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        );
    }
}
