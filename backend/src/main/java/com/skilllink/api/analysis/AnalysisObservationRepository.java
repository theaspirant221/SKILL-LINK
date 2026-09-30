package com.skilllink.api.analysis;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class AnalysisObservationRepository {
    private final JdbcTemplate jdbc;

    public AnalysisObservationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ObservationRow(
        UUID id,
        UUID analysisRunId,
        UUID snapshotId,
        String observationType,
        String category,
        String factKey,
        String factValue,
        String language,
        String framework,
        String sourcePath,
        Integer startLine,
        Integer endLine,
        String symbol,
        String sourceHash,
        String detector,
        String detectorVersion,
        String confidence,
        String origin,
        Instant createdAt
    ) {}

    private ObservationRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ObservationRow(
            rs.getObject("id", UUID.class),
            rs.getObject("analysis_run_id", UUID.class),
            rs.getObject("snapshot_id", UUID.class),
            rs.getString("observation_type"),
            rs.getString("category"),
            rs.getString("fact_key"),
            rs.getString("fact_value"),
            rs.getString("language"),
            rs.getString("framework"),
            rs.getString("source_path"),
            rs.getObject("start_line", Integer.class),
            rs.getObject("end_line", Integer.class),
            rs.getString("symbol"),
            rs.getString("source_hash"),
            rs.getString("detector"),
            rs.getString("detector_version"),
            rs.getString("confidence"),
            rs.getString("origin"),
            rs.getObject("created_at", java.time.OffsetDateTime.class) == null ? null : rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant()
        );
    }

    public List<ObservationRow> listByRun(UUID runId) {
        return jdbc.query(
            "SELECT * FROM analysis_observation WHERE analysis_run_id = ? ORDER BY category, observation_type, source_path, symbol",
            this::map, runId);
    }

    public List<ObservationRow> listBySnapshot(UUID snapshotId) {
        return jdbc.query(
            "SELECT * FROM analysis_observation WHERE snapshot_id = ? ORDER BY category, observation_type",
            this::map, snapshotId);
    }

    public void deleteByRun(UUID runId) {
        jdbc.update("DELETE FROM analysis_observation WHERE analysis_run_id = ?", runId);
    }

    public void insertBatch(UUID runId, List<ObservationRow> observations) {
        if (observations.isEmpty()) return;
        jdbc.batchUpdate(
            """
            INSERT INTO analysis_observation(
                analysis_run_id, snapshot_id, observation_type, category, fact_key, fact_value,
                language, framework, source_path, start_line, end_line, symbol, source_hash,
                detector, detector_version, confidence, origin
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (analysis_run_id, observation_type, source_path, symbol, fact_key, fact_value) DO NOTHING
            """,
            observations,
            100,
            (ps, obs) -> {
                ps.setObject(1, obs.analysisRunId());
                ps.setObject(2, obs.snapshotId());
                ps.setString(3, obs.observationType());
                ps.setString(4, obs.category());
                ps.setString(5, obs.factKey());
                ps.setString(6, obs.factValue());
                ps.setString(7, obs.language());
                ps.setString(8, obs.framework());
                ps.setString(9, obs.sourcePath());
                if (obs.startLine() != null) ps.setInt(10, obs.startLine()); else ps.setNull(10, java.sql.Types.INTEGER);
                if (obs.endLine() != null) ps.setInt(11, obs.endLine()); else ps.setNull(11, java.sql.Types.INTEGER);
                ps.setString(12, obs.symbol());
                ps.setString(13, obs.sourceHash());
                ps.setString(14, obs.detector());
                ps.setString(15, obs.detectorVersion());
                ps.setString(16, obs.confidence());
                ps.setString(17, obs.origin());
            }
        );
    }

    public int countByRun(UUID runId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM analysis_observation WHERE analysis_run_id = ?", Integer.class, runId);
        return count == null ? 0 : count;
    }
}
