package com.skilllink.api.evidence;

import com.skilllink.api.project.ProjectService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EvidenceService {
    private final JdbcTemplate jdbc;
    private final ProjectService projects;
    public EvidenceService(JdbcTemplate jdbc, ProjectService projects) { this.jdbc = jdbc; this.projects = projects; }

    public List<EvidenceDtos.EvidenceResponse> list(UUID candidateId, UUID projectId) {
        projects.get(candidateId, projectId);
        return jdbc.query("""
            SELECT e.id evidence_id, s.id skill_id, s.name skill_name, p.name project_name, r.full_name, rs.id snapshot_id, rs.commit_sha,
                   es.source_type, es.source_location, e.observation, e.evidence_strength, e.verification_method, e.status, es.visibility,
                   es.observed_at, es.source_hash, e.independent_signal
            FROM evidence e JOIN skill s ON s.id = e.skill_id JOIN project p ON p.id = e.project_id
            JOIN evidence_source es ON es.id = e.evidence_source_id JOIN repository_snapshot rs ON rs.id = es.snapshot_id
            JOIN repository r ON r.id = rs.repository_id
            WHERE e.candidate_id = ? AND e.project_id = ? ORDER BY es.observed_at DESC, e.created_at DESC
            """, (rs, rowNum) -> new EvidenceDtos.EvidenceResponse(rs.getObject("evidence_id", UUID.class), rs.getObject("skill_id", UUID.class), rs.getString("skill_name"), rs.getString("project_name"), rs.getString("full_name"), rs.getObject("snapshot_id", UUID.class), rs.getString("commit_sha"), rs.getString("source_type"), rs.getString("source_location"), rs.getString("observation"), rs.getString("evidence_strength"), rs.getString("verification_method"), rs.getString("status"), rs.getString("visibility"), rs.getObject("observed_at", java.time.OffsetDateTime.class).toInstant(), rs.getString("source_hash"), rs.getString("independent_signal")), candidateId, projectId);
    }

    public List<EvidenceDtos.SkillResponse> skills(UUID candidateId) { return jdbc.query("""
        SELECT cs.skill_id, s.key, s.name, s.category, cs.status, cs.freshness_state, cs.last_verified_at, cs.latest_evidence_at,
               (SELECT count(*) FROM evidence e WHERE e.candidate_id = cs.candidate_id AND e.skill_id = cs.skill_id) evidence_count
        FROM candidate_skill cs JOIN skill s ON s.id = cs.skill_id WHERE cs.candidate_id = ? ORDER BY s.category, s.name
        """, (rs, rowNum) -> new EvidenceDtos.SkillResponse(rs.getObject("skill_id", UUID.class), rs.getString("key"), rs.getString("name"), rs.getString("category"), rs.getString("status"), rs.getString("freshness_state"), instant(rs, "last_verified_at"), instant(rs, "latest_evidence_at"), rs.getInt("evidence_count")), candidateId); }

    @Transactional
    public void dispute(UUID candidateId, UUID evidenceId, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 2000) throw new EvidenceInputException("DISPUTE_REASON_INVALID", "Provide a dispute reason between 1 and 2000 characters.");
        Long count = jdbc.queryForObject("SELECT count(*) FROM evidence WHERE id = ? AND candidate_id = ?", Long.class, evidenceId, candidateId);
        if (count == null || count == 0) throw new EvidenceNotFoundException();
        jdbc.update("INSERT INTO dispute(submitted_by, candidate_id, evidence_id, reason) VALUES (?, ?, ?, ?)", candidateId, candidateId, evidenceId, reason.trim());
        jdbc.update("UPDATE evidence SET status = 'DISPUTED', updated_at = now() WHERE id = ?", evidenceId);
    }

    @Transactional
    public void visibility(UUID candidateId, UUID evidenceId, String visibility) {
        if (!List.of("PRIVATE", "RECRUITER_SHARED", "INSTITUTION_SHARED", "PUBLIC_SUMMARY", "PUBLIC").contains(visibility)) throw new EvidenceInputException("VISIBILITY_INVALID", "That visibility is not supported.");
        int updated = jdbc.update("UPDATE evidence_source es SET visibility = ? FROM evidence e WHERE e.evidence_source_id = es.id AND e.id = ? AND e.candidate_id = ?", visibility, evidenceId, candidateId);
        if (updated == 0) throw new EvidenceNotFoundException();
    }
    private java.time.Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException { java.time.OffsetDateTime value = rs.getObject(column, java.time.OffsetDateTime.class); return value == null ? null : value.toInstant(); }
    public static class EvidenceNotFoundException extends RuntimeException {}
    public static class EvidenceInputException extends RuntimeException { private final String code; public EvidenceInputException(String code, String message) { super(message); this.code = code; } public String code() { return code; } }
}
