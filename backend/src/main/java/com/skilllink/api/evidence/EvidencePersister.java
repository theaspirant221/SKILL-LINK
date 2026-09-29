package com.skilllink.api.evidence;

import com.skilllink.api.analysis.AnalysisModels;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class EvidencePersister {
    private final JdbcTemplate jdbc;
    public EvidencePersister(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void persist(UUID candidateId, UUID projectId, AnalysisModels.FetchedSnapshot snapshot, AnalysisModels.AnalysisOutput output) {
        for (AnalysisModels.Signal signal : output.signals()) {
            UUID skillId = findSkill(signal.skillKey());
            if (skillId == null) continue; // An unsupported detector output cannot create uncontrolled taxonomy data.
            UUID sourceId = findOrCreateSource(snapshot, signal);
            if (jdbc.queryForObject("SELECT count(*) FROM evidence WHERE evidence_source_id = ? AND skill_id = ? AND observation = ?", Long.class, sourceId, skillId, signal.observation()) == 0) {
                jdbc.update("""
                    INSERT INTO evidence(candidate_id, project_id, skill_id, evidence_source_id, observation, evidence_strength, verification_method, status, independent_signal)
                    VALUES (?, ?, ?, ?, ?, ?, 'DETERMINISTIC_REPOSITORY_ANALYSIS', 'OBSERVED', ?)
                    """, candidateId, projectId, skillId, sourceId, signal.observation(), signal.strength(), signal.independentSignal());
            }
            jdbc.update("""
                INSERT INTO candidate_skill(candidate_id, skill_id, status, freshness_state, latest_evidence_at)
                VALUES (?, ?, 'EVIDENCE_FOUND', 'CURRENT', now())
                ON CONFLICT (candidate_id, skill_id) DO UPDATE SET latest_evidence_at = now(),
                  status = CASE WHEN candidate_skill.status IN ('VERIFIED', 'DISPUTED') THEN candidate_skill.status ELSE 'EVIDENCE_FOUND' END,
                  freshness_state = CASE WHEN candidate_skill.status IN ('VERIFIED', 'DISPUTED') THEN candidate_skill.freshness_state ELSE 'CURRENT' END,
                  updated_at = now()
                """, candidateId, skillId);
        }
    }

    private UUID findSkill(String key) { try { return jdbc.queryForObject("SELECT id FROM skill WHERE key = ? AND status = 'ACTIVE'", UUID.class, key); } catch (org.springframework.dao.EmptyResultDataAccessException ex) { return null; } }
    private UUID findOrCreateSource(AnalysisModels.FetchedSnapshot snapshot, AnalysisModels.Signal signal) {
        try { return jdbc.queryForObject("SELECT id FROM evidence_source WHERE snapshot_id = ? AND source_type = ? AND source_reference = ? AND source_location = ? AND coalesce(source_hash, '') = coalesce(?, '')", UUID.class, snapshot.snapshotId(), signal.sourceType(), snapshot.commitSha(), signal.sourceLocation(), signal.blobSha()); }
        catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            String visibility = "PRIVATE"; // source visibility is promoted only by an explicit sharing action.
            return jdbc.queryForObject("""
                INSERT INTO evidence_source(snapshot_id, source_type, source_reference, source_location, source_hash, visibility, observed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, UUID.class, snapshot.snapshotId(), signal.sourceType(), snapshot.commitSha(), signal.sourceLocation(), signal.blobSha(), visibility, java.sql.Timestamp.from(Instant.now()));
        }
    }
}
