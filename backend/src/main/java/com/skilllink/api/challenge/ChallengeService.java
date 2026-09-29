package com.skilllink.api.challenge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skilllink.api.audit.AuditService;
import com.skilllink.api.project.ProjectService;
import com.skilllink.api.verification.VerificationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ChallengeService {
    private static final String POLICY_VERSION = "skill-proof-baseline:v1.0";
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ProjectService projects;
    private final AuditService audit;
    private final VerificationService verification;

    public ChallengeService(JdbcTemplate jdbc, ObjectMapper objectMapper, ProjectService projects, AuditService audit, VerificationService verification) { this.jdbc = jdbc; this.objectMapper = objectMapper; this.projects = projects; this.audit = audit; this.verification = verification; }

    @Transactional
    public ChallengeDtos.ChallengeResponse create(UUID candidateId, ChallengeDtos.CreateRequest request, String requestId) {
        projects.get(candidateId, request.projectId());
        SkillRow skill = findSkill(request.skillId());
        Long evidenceCount = jdbc.queryForObject("SELECT count(*) FROM evidence e JOIN evidence_source es ON es.id = e.evidence_source_id JOIN repository_snapshot rs ON rs.id = es.snapshot_id WHERE e.candidate_id = ? AND e.project_id = ? AND e.skill_id = ? AND e.status <> 'DISPUTED' AND rs.analysis_status = 'COMPLETED'", Long.class, candidateId, request.projectId(), request.skillId());
        if (evidenceCount == null || evidenceCount == 0) throw new ChallengeInputException("EVIDENCE_REQUIRED", "A practical task can only be created from completed source evidence.");
        UUID existing = existingChallenge(candidateId, request.projectId(), request.skillId());
        UUID challengeId = existing == null ? jdbc.queryForObject("""
            INSERT INTO practical_challenge(skill_id, project_id, title, brief, acceptance_criteria, execution_mode, policy_version)
            VALUES (?, ?, ?, ?, ?::jsonb, 'TEXT_PATCH', ?) RETURNING id
            """, UUID.class, request.skillId(), request.projectId(), "Bounded " + skill.name() + " verification", "Submit a concise implementation plan or patch explanation for a bounded " + skill.name() + " task. Explain the change, the relevant failure path, and the test that would prove it.", json(List.of("References the selected project source", "Names an implementation change or guard", "Describes a negative-path or regression test")), POLICY_VERSION) : existing;
        audit.record(candidateId, "CHALLENGE_CREATED", "PRACTICAL_CHALLENGE", challengeId, requestId, Map.of("projectId", request.projectId().toString(), "skillId", request.skillId().toString()));
        return get(candidateId, challengeId);
    }

    public ChallengeDtos.ChallengeResponse get(UUID candidateId, UUID challengeId) { return row(candidateId, challengeId); }

    @Transactional
    public ChallengeDtos.ChallengeResponse submit(UUID candidateId, UUID challengeId, String text, String requestId) {
        if (text == null || text.isBlank() || text.trim().length() < 60 || text.length() > 10000) throw new ChallengeInputException("SUBMISSION_INVALID", "Submit a bounded practical response between 60 and 10000 characters.");
        ChallengeRow challenge = findChallenge(candidateId, challengeId);
        String hash = sha256(text.trim());
        UUID submissionId = jdbc.queryForObject("""
            INSERT INTO challenge_submission(challenge_id, candidate_id, submission_reference, submission_hash, status, execution_summary, feedback)
            VALUES (?, ?, 'candidate-text-patch', ?, 'REVIEW_REQUIRED', ?::jsonb, 'Awaiting an authorized reviewer; this submission is not automatically verified.') RETURNING id
            """, UUID.class, challengeId, candidateId, hash, json(Map.of("mode", "TEXT_PATCH", "automatedExecution", false, "policyVersion", POLICY_VERSION)));
        audit.record(candidateId, "CHALLENGE_SUBMITTED", "CHALLENGE_SUBMISSION", submissionId, requestId, Map.of("challengeId", challengeId.toString(), "status", "REVIEW_REQUIRED"));
        return get(candidateId, challengeId);
    }

    @Transactional
    public ChallengeDtos.ChallengeResponse review(UUID reviewerId, UUID submissionId, ChallengeDtos.ReviewRequest request, String requestId) {
        String outcome = request.outcome() == null ? "" : request.outcome().trim().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("PASSED", "NEEDS_CHANGES", "REVIEW_REQUIRED").contains(outcome)) throw new ChallengeInputException("REVIEW_OUTCOME_INVALID", "Review outcome must be PASSED, NEEDS_CHANGES, or REVIEW_REQUIRED.");
        ReviewTarget target = jdbc.queryForObject("""
            SELECT cs.id submission_id, cs.challenge_id, cs.candidate_id, pc.project_id, pc.skill_id
            FROM challenge_submission cs JOIN practical_challenge pc ON pc.id = cs.challenge_id
            WHERE cs.id = ? AND EXISTS (SELECT 1 FROM job_application ja JOIN job j ON j.id = ja.job_id WHERE ja.candidate_id = cs.candidate_id AND j.created_by = ?)
            """, (rs, rowNum) -> new ReviewTarget(rs.getObject("submission_id", UUID.class), rs.getObject("challenge_id", UUID.class), rs.getObject("candidate_id", UUID.class), rs.getObject("project_id", UUID.class), rs.getObject("skill_id", UUID.class)), submissionId, reviewerId);
        jdbc.update("UPDATE challenge_submission SET status = ?, feedback = ? WHERE id = ?", outcome, request.feedback().trim(), submissionId);
        audit.record(reviewerId, "CHALLENGE_REVIEWED", "CHALLENGE_SUBMISSION", submissionId, requestId, Map.of("outcome", outcome, "candidateId", target.candidateId().toString()));
        if ("PASSED".equals(outcome)) verification.evaluate(target.candidateId(), target.skillId(), requestId);
        return get(target.candidateId(), target.challengeId());
    }

    private ChallengeDtos.ChallengeResponse row(UUID candidateId, UUID challengeId) { ChallengeRow row = findChallenge(candidateId, challengeId); return new ChallengeDtos.ChallengeResponse(row.id(), row.projectId(), row.skillId(), row.skillName(), row.title(), row.brief(), row.criteria(), row.executionMode(), row.policyVersion(), row.submissionId(), row.submissionStatus(), row.feedback(), row.submittedAt()); }
    private ChallengeRow findChallenge(UUID candidateId, UUID challengeId) { try { return jdbc.queryForObject("""
        SELECT pc.id, pc.project_id, pc.skill_id, s.name skill_name, pc.title, pc.brief, pc.acceptance_criteria, pc.execution_mode, pc.policy_version, cs.id submission_id, cs.status submission_status, cs.feedback, cs.submitted_at
        FROM practical_challenge pc JOIN skill s ON s.id = pc.skill_id LEFT JOIN LATERAL (SELECT id, status, feedback, submitted_at FROM challenge_submission WHERE challenge_id = pc.id AND candidate_id = ? ORDER BY submitted_at DESC LIMIT 1) cs ON true
        WHERE pc.id = ? AND EXISTS (SELECT 1 FROM project p WHERE p.id = pc.project_id AND p.candidate_id = ?)
        """, (rs, rowNum) -> new ChallengeRow(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class), rs.getObject("skill_id", UUID.class), rs.getString("skill_name"), rs.getString("title"), rs.getString("brief"), readList(rs.getString("acceptance_criteria")), rs.getString("execution_mode"), rs.getString("policy_version"), rs.getObject("submission_id", UUID.class), rs.getString("submission_status"), rs.getString("feedback"), instant(rs, "submitted_at")), challengeId, candidateId, candidateId); } catch (org.springframework.dao.EmptyResultDataAccessException ex) { throw new ChallengeNotFoundException(); } }
    private UUID existingChallenge(UUID candidateId, UUID projectId, UUID skillId) { try { return jdbc.queryForObject("SELECT pc.id FROM practical_challenge pc JOIN project p ON p.id = pc.project_id WHERE pc.project_id = ? AND pc.skill_id = ? AND p.candidate_id = ? ORDER BY pc.created_at DESC LIMIT 1", UUID.class, projectId, skillId, candidateId); } catch (org.springframework.dao.EmptyResultDataAccessException ex) { return null; } }
    private SkillRow findSkill(UUID skillId) { try { return jdbc.queryForObject("SELECT id, name FROM skill WHERE id = ? AND status = 'ACTIVE'", (rs, rowNum) -> new SkillRow(rs.getObject("id", UUID.class), rs.getString("name")), skillId); } catch (org.springframework.dao.EmptyResultDataAccessException ex) { throw new ChallengeInputException("SKILL_NOT_FOUND", "That skill is not in the active taxonomy."); } }
    private Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException { OffsetDateTime value = rs.getObject(column, OffsetDateTime.class); return value == null ? null : value.toInstant(); }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private List<String> readList(String value) { try { return value == null ? List.of() : objectMapper.readValue(value, new TypeReference<>() {}); } catch (Exception ex) { return List.of(); } }
    private String sha256(String value) { try { byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder out = new StringBuilder(); for (byte b : digest) out.append(String.format("%02x", b)); return out.toString(); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private record SkillRow(UUID id, String name) {}
    private record ChallengeRow(UUID id, UUID projectId, UUID skillId, String skillName, String title, String brief, List<String> criteria, String executionMode, String policyVersion, UUID submissionId, String submissionStatus, String feedback, Instant submittedAt) {}
    private record ReviewTarget(UUID submissionId, UUID challengeId, UUID candidateId, UUID projectId, UUID skillId) {}
    public static class ChallengeNotFoundException extends RuntimeException {}
    public static class ChallengeInputException extends RuntimeException { private final String code; public ChallengeInputException(String code, String message) { super(message); this.code = code; } public String code() { return code; } }
}
