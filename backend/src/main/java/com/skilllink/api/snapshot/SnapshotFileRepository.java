package com.skilllink.api.snapshot;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class SnapshotFileRepository {
    private final JdbcTemplate jdbc;

    public SnapshotFileRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record FileRow(
        UUID id,
        UUID snapshotId,
        String path,
        String language,
        long sizeBytes,
        String contentHash,
        String blobSha,
        boolean included,
        String exclusionReason,
        boolean secretRedacted,
        int secretCount
    ) {}

    private FileRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new FileRow(
            rs.getObject("id", UUID.class),
            rs.getObject("snapshot_id", UUID.class),
            rs.getString("path"),
            rs.getString("language"),
            rs.getLong("size_bytes"),
            rs.getString("content_hash"),
            rs.getString("blob_sha"),
            rs.getBoolean("included"),
            rs.getString("exclusion_reason"),
            rs.getBoolean("secret_redacted"),
            rs.getInt("secret_count")
        );
    }

    public List<FileRow> listBySnapshot(UUID snapshotId) {
        return jdbc.query(
            "SELECT * FROM repository_snapshot_file WHERE snapshot_id = ? ORDER BY path ASC",
            this::map, snapshotId
        );
    }

    public void insertBatch(UUID snapshotId, List<FileRow> files) {
        // Batch insert for performance
        jdbc.batchUpdate(
            """
            INSERT INTO repository_snapshot_file(
                snapshot_id, path, language, size_bytes, content_hash, blob_sha,
                included, exclusion_reason, secret_redacted, secret_count
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (snapshot_id, path) DO UPDATE SET
                language = EXCLUDED.language,
                size_bytes = EXCLUDED.size_bytes,
                content_hash = EXCLUDED.content_hash,
                blob_sha = EXCLUDED.blob_sha,
                included = EXCLUDED.included,
                exclusion_reason = EXCLUDED.exclusion_reason,
                secret_redacted = EXCLUDED.secret_redacted,
                secret_count = EXCLUDED.secret_count
            """,
            files,
            100,
            (ps, file) -> {
                ps.setObject(1, snapshotId);
                ps.setString(2, file.path());
                ps.setString(3, file.language());
                ps.setLong(4, file.sizeBytes());
                ps.setString(5, file.contentHash());
                ps.setString(6, file.blobSha());
                ps.setBoolean(7, file.included());
                ps.setString(8, file.exclusionReason());
                ps.setBoolean(9, file.secretRedacted());
                ps.setInt(10, file.secretCount());
            }
        );
    }

    public void deleteBySnapshot(UUID snapshotId) {
        jdbc.update("DELETE FROM repository_snapshot_file WHERE snapshot_id = ?", snapshotId);
    }

    public int countBySnapshot(UUID snapshotId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM repository_snapshot_file WHERE snapshot_id = ?", Integer.class, snapshotId);
        return count == null ? 0 : count;
    }
}
