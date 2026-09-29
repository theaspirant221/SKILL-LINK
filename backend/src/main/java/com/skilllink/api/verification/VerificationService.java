package com.skilllink.api.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skilllink.api.audit.AuditService;
import com.skilllink.api.project.ProjectService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VerificationService {
    private final JdbcTemplate jdbc;
    private final ProjectService projects;
    private final AuditService audit;

    public VerificationService(JdbcTemplate jdbc, ProjectService projects, AuditService audit) { this.jdbc = jdbc; this.projects = projects; this.audit = audit; }

    @Transactional
    public VerificationDtos.VerificationResponse evaluate(UUID candidateId, UUID skillId, String requestId) {
        SkillRow skill = findSkill(skillId);
        PolicyRow policy = activePolicy();
        int evidenceCount = jdbc.queryForObject("SELECT count(*) FROM evidence e WHERE e.candidate_id = ? AND e.skill_id = ? AND e.status <> 'DISPUTED'", Integer.class, candidateId, skillId);
        int completedEvidenceCount = jdbc.queryForObject("SELECT count(*) FROM evidence e JOIN evidence_source es ON es.id = e.evidence_source_id JOIN repository_snapshot rs ON rs.id = es.snapshot_id WHERE e.candidate_id = ? AND e.skill_id = ? AND e.status <> 'DISPUTED' AND rs.analysis_status = 'COMPLETED'", Integer.class, candidateId, skillId);
        UUID snapshotId = latestId("SELECT rs.id FROM evidence e JOIN evidence_source es ON es.id = e.evidence_source_id JOIN repository_snapshot rs ON rs.id = es.snapshot_id WHERE e.candidate_id = ? AND e.skill_id = ? AND e.status <> 'DISPUTED' AND rs.analysis_status = 'COMPLETED' ORDER BY rs.created_at DESC LIMIT 1", candidateId, skillId);
        EvidenceFacts evidence = new EvidenceFacts(evidenceCount, completedEvidenceCount, snapshotId);
        int passedExaminations = jdbc.queryForObject("SELECT count(*) FROM examination ex WHERE ex.candidate_id = ? AND ex.result = 'PASSED' AND EXISTS (SELECT 1 FROM evidence e WHERE e.candidate_id = ex.candidate_id AND e.skill_id = ? AND e.project_id = ex.project_id AND e.status <> 'DISPUTED')", Integer.class, candidateId, skillId);
        UUID examinationId = latestId("SELECT ex.id FROM examination ex WHERE ex.candidate_id = ? AND ex.result = 'PASSED' AND EXISTS (SELECT 1 FROM evidence e WHERE e.candidate_id = ex.candidate_id AND e.skill_id = ? AND e.project_id = ex.project_id AND e.status <> 'DISPUTED') ORDER BY ex.completed_at DESC LIMIT 1", candidateId, skillId);
        ExamFacts exam = new ExamFacts(passedExaminations, examinationId);
        int passedChallenges = jdbc.queryForObject("SELECT count(*) FROM challenge_submission cs JOIN practical_challenge pc ON pc.id = cs.challenge_id WHERE cs.candidate_id = ? AND pc.skill_id = ? AND cs.status = 'PASSED'", Integer.class, candidateId, skillId);
        UUID submissionId = latestId("SELECT cs.id FROM challenge_submission cs JOIN practical_challenge pc ON pc.id = cs.challenge_id WHERE cs.candidate_id = ? AND pc.skill_id = ? AND cs.status = 'PASSED' ORDER BY cs.submitted_at DESC LIMIT 1", candidateId, skillId);
        ChallengeFacts challenge = new ChallengeFacts(passedChallenges, submissionId);

        boolean repositoryGate = evidence.completedEvidenceCount() > 0;
        boolean defenseGate = exam.passedCount() > 0;
        boolean practicalGate = challenge.passedCount() > 0;
        String status = repositoryGate && defenseGate && practicalGate ? "VERIFIED" : repositoryGate && (defenseGate || practicalGate) ? "PARTIAL" : "NOT_VERIFIED";
        String explanation = "Policy " + policy.key() + " " + policy.version() + " requires completed repository evidence, a passed project defense, and an explicitly reviewed practical verification. " +
            "Observed: " + evidence.completedEvidenceCount() + " completed evidence item(s), defense=" + (defenseGate ? "passed" : "missing or not passed") + ", practical=" + (practicalGate ? "passed" : "missing or not passed") + ".";
        UUID resultId = jdbc.queryForObject("""
            INSERT INTO verification_result(candidate_id, skill_id, policy_id, status, explanation, repository_snapshot_id, examination_id, challenge_submission_id, model_metadata)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb) RETURNING id
            """, UUID.class, candidateId, skillId, policy.id(), status, explanation, evidence.snapshotId(), exam.examinationId(), challenge.submissionId(), "{\"evaluator\":\"policy_engine\",\"version\":\"" + policy.version() + "\"}");
        jdbc.update("""
            INSERT INTO candidate_skill(candidate_id, skill_id, status, freshness_state, last_verified_at, verification_policy_version, latest_evidence_at)
            VALUES (?, ?, ?, CASE WHEN ? = 'VERIFIED' THEN 'CURRENT' ELSE 'NOT_APPLICABLE' END, CASE WHEN ? = 'VERIFIED' THEN now() ELSE NULL END, ?, (SELECT max(es.observed_at) FROM evidence e JOIN evidence_source es ON es.id = e.evidence_source_id WHERE e.candidate_id = ? AND e.skill_id = ?))
            ON CONFLICT (candidate_id, skill_id) DO UPDATE SET status = CASE WHEN EXCLUDED.status = 'VERIFIED' OR candidate_skill.status <> 'VERIFIED' THEN EXCLUDED.status ELSE candidate_skill.status END,
              freshness_state = CASE WHEN EXCLUDED.status = 'VERIFIED' THEN 'CURRENT' ELSE candidate_skill.freshness_state END,
              last_verified_at = CASE WHEN EXCLUDED.status = 'VERIFIED' THEN now() ELSE candidate_skill.last_verified_at END,
              verification_policy_version = EXCLUDED.verification_policy_version,
              updated_at = now()
            """, candidateId, skillId, status, status, status, policy.version(), candidateId, skillId);
        audit.record(candidateId, "VERIFICATION_EVALUATED", "VERIFICATION_RESULT", resultId, requestId, Map.of("skillId", skillId.toString(), "status", status, "policyVersion", policy.version()));
        return new VerificationDtos.VerificationResponse(resultId, skillId, skill.name(), status, policy.key(), policy.version(), explanation, evidence.completedEvidenceCount(), defenseGate, practicalGate, Instant.now());
    }

    public List<VerificationDtos.VerificationResponse> list(UUID candidateId) {
        return jdbc.query("""
            SELECT vr.id, vr.skill_id, s.name skill_name, vr.status, vp.key policy_key, vp.version policy_version, vr.explanation, vr.evaluated_at,
              (SELECT count(*) FROM evidence e JOIN evidence_source es ON es.id = e.evidence_source_id JOIN repository_snapshot rs ON rs.id = es.snapshot_id WHERE e.candidate_id = vr.candidate_id AND e.skill_id = vr.skill_id AND e.status <> 'DISPUTED' AND rs.analysis_status = 'COMPLETED') evidence_count,
              CASE WHEN vr.examination_id IS NOT NULL THEN true ELSE false END defense_passed,
              CASE WHEN vr.challenge_submission_id IS NOT NULL THEN true ELSE false END practical_passed
            FROM verification_result vr JOIN skill s ON s.id = vr.skill_id JOIN verification_policy vp ON vp.id = vr.policy_id
            WHERE vr.candidate_id = ? ORDER BY vr.evaluated_at DESC
            """, (rs, rowNum) -> new VerificationDtos.VerificationResponse(rs.getObject("id", UUID.class), rs.getObject("skill_id", UUID.class), rs.getString("skill_name"), rs.getString("status"), rs.getString("policy_key"), rs.getString("policy_version"), rs.getString("explanation"), rs.getInt("evidence_count"), rs.getBoolean("defense_passed"), rs.getBoolean("practical_passed"), rs.getObject("evaluated_at", OffsetDateTime.class).toInstant()), candidateId);
    }

    public VerificationDtos.VerificationResponse latestFor(UUID candidateId, UUID skillId) {
        try { return jdbc.queryForObject("""
            SELECT vr.id, vr.skill_id, s.name skill_name, vr.status, vp.key policy_key, vp.version policy_version, vr.explanation, vr.evaluated_at,
              (SELECT count(*) FROM evidence e JOIN evidence_source es ON es.id = e.evidence_source_id JOIN repository_snapshot rs ON rs.id = es.snapshot_id WHERE e.candidate_id = vr.candidate_id AND e.skill_id = vr.skill_id AND rs.analysis_status = 'COMPLETED') evidence_count,
              CASE WHEN vr.examination_id IS NOT NULL THEN true ELSE false END defense_passed,
              CASE WHEN vr.challenge_submission_id IS NOT NULL THEN true ELSE false END practical_passed
            FROM verification_result vr JOIN skill s ON s.id = vr.skill_id JOIN verification_policy vp ON vp.id = vr.policy_id
            WHERE vr.candidate_id = ? AND vr.skill_id = ? ORDER BY vr.evaluated_at DESC LIMIT 1
            """, (rs, rowNum) -> new VerificationDtos.VerificationResponse(rs.getObject("id", UUID.class), rs.getObject("skill_id", UUID.class), rs.getString("skill_name"), rs.getString("status"), rs.getString("policy_key"), rs.getString("policy_version"), rs.getString("explanation"), rs.getInt("evidence_count"), rs.getBoolean("defense_passed"), rs.getBoolean("practical_passed"), rs.getObject("evaluated_at", OffsetDateTime.class).toInstant()), candidateId, skillId); } catch (org.springframework.dao.EmptyResultDataAccessException ex) { return null; }
    }

    private UUID latestId(String sql, Object... args) { try { return jdbc.queryForObject(sql, UUID.class, args); } catch (org.springframework.dao.EmptyResultDataAccessException ex) { return null; } }
    private SkillRow findSkill(UUID skillId) { try { return jdbc.queryForObject("SELECT id, name FROM skill WHERE id = ? AND status = 'ACTIVE'", (rs, rowNum) -> new SkillRow(rs.getObject("id", UUID.class), rs.getString("name")), skillId); } catch (org.springframework.dao.EmptyResultDataAccessException ex) { throw new VerificationInputException("SKILL_NOT_FOUND", "That skill is not in the active taxonomy."); } }
    private PolicyRow activePolicy() { return jdbc.queryForObject("SELECT id, key, version FROM verification_policy WHERE key = 'skill-proof-baseline' AND active = true ORDER BY created_at DESC LIMIT 1", (rs, rowNum) -> new PolicyRow(rs.getObject("id", UUID.class), rs.getString("key"), rs.getString("version"))); }
    private record SkillRow(UUID id, String name) {}
    private record PolicyRow(UUID id, String key, String version) {}
    private record EvidenceFacts(int evidenceCount, int completedEvidenceCount, UUID snapshotId) {}
    private record ExamFacts(int passedCount, UUID examinationId) {}
    private record ChallengeFacts(int passedCount, UUID submissionId) {}
    public static class VerificationInputException extends RuntimeException { private final String code; public VerificationInputException(String code, String message) { super(message); this.code = code; } public String code() { return code; } }
}
