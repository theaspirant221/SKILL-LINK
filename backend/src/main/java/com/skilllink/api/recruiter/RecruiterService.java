package com.skilllink.api.recruiter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skilllink.api.audit.AuditService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RecruiterService {
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public RecruiterService(JdbcTemplate jdbc, AuditService audit) { this.jdbc = jdbc; this.audit = audit; }

    @Transactional
    public RecruiterDtos.JobResponse createJob(UUID recruiterId, RecruiterDtos.CreateJobRequest request, String requestId) {
        UUID organizationId = ensureRecruiterOrganization(recruiterId, request.company().trim());
        UUID jobId = jdbc.queryForObject("INSERT INTO job(organization_id, created_by, title, location, original_description, status) VALUES (?, ?, ?, ?, ?, 'OPEN') RETURNING id", UUID.class, organizationId, recruiterId, request.title().trim(), blankToNull(request.location()), request.description().trim());
        List<SkillMatch> matches = matchSkills(request.description());
        for (SkillMatch match : matches) jdbc.update("INSERT INTO job_requirement(job_id, skill_id, kind, source_phrase) VALUES (?, ?, ?, ?)", jobId, match.skillId(), match.kind(), match.sourcePhrase());
        audit.record(recruiterId, "JOB_CREATED", "JOB", jobId, requestId, Map.of("requirementCount", matches.size(), "organizationId", organizationId.toString()));
        return getOwnedJob(recruiterId, jobId);
    }

    public List<RecruiterDtos.JobResponse> recruiterJobs(UUID recruiterId) { return jdbc.query("SELECT id FROM job WHERE created_by = ? ORDER BY created_at DESC", (rs, rowNum) -> rs.getObject("id", UUID.class), recruiterId).stream().map(id -> getOwnedJob(recruiterId, id)).toList(); }
    public List<RecruiterDtos.JobResponse> openJobs() { return jdbc.query("SELECT id FROM job WHERE status = 'OPEN' ORDER BY created_at DESC", (rs, rowNum) -> rs.getObject("id", UUID.class)).stream().map(this::getJob).toList(); }
    public RecruiterDtos.JobResponse recruiterJob(UUID recruiterId, UUID jobId) { return getOwnedJob(recruiterId, jobId); }

    @Transactional
    public RecruiterDtos.ApplicationResponse apply(UUID candidateId, UUID jobId, RecruiterDtos.ApplicationRequest request, String requestId) {
        JobRow job = findJob(jobId);
        if (!"OPEN".equals(job.status())) throw new RecruiterInputException("JOB_NOT_OPEN", "This job is not accepting applications.");
        try {
            UUID id = jdbc.queryForObject("INSERT INTO job_application(job_id, candidate_id, candidate_note) VALUES (?, ?, ?) RETURNING id", UUID.class, jobId, candidateId, blankToNull(request == null ? null : request.note()));
            audit.record(candidateId, "JOB_APPLIED", "JOB_APPLICATION", id, requestId, Map.of("jobId", jobId.toString()));
            return application(id);
        } catch (DataIntegrityViolationException ex) { throw new RecruiterInputException("APPLICATION_EXISTS", "You already have an application for this job."); }
    }

    public List<RecruiterDtos.ApplicationResponse> candidateApplications(UUID candidateId) { return jdbc.query("SELECT ja.id FROM job_application ja WHERE ja.candidate_id = ? ORDER BY ja.created_at DESC", (rs, rowNum) -> rs.getObject("id", UUID.class), candidateId).stream().map(this::application).toList(); }

    public List<RecruiterDtos.CandidateProofContractResponse> candidateContracts(UUID candidateId) {
        return jdbc.query("SELECT pc.id FROM proof_contract pc WHERE pc.candidate_id = ? AND pc.status <> 'ARCHIVED' ORDER BY pc.created_at DESC", (rs, rowNum) -> rs.getObject("id", UUID.class), candidateId).stream().map(id -> candidateContract(candidateId, id)).toList();
    }

    private RecruiterDtos.CandidateProofContractResponse candidateContract(UUID candidateId, UUID contractId) {
        CandidateContractRow row = jdbc.queryForObject("""
            SELECT pc.id, pc.job_id, j.title job_title, o.name company, pc.version, pc.status, pc.created_at
            FROM proof_contract pc JOIN job j ON j.id = pc.job_id JOIN organization o ON o.id = j.organization_id
            WHERE pc.id = ? AND pc.candidate_id = ?
            """, (rs, rowNum) -> new CandidateContractRow(rs.getObject("id", UUID.class), rs.getObject("job_id", UUID.class), rs.getString("job_title"), rs.getString("company"), rs.getInt("version"), rs.getString("status"), instant(rs, "created_at")), contractId, candidateId);
        List<RecruiterDtos.CandidateContractRequirementResponse> requirements = jdbc.query("""
            SELECT pcr.id, jr.skill_id, s.name skill_name, jr.kind, pcr.outcome, pcr.proof_summary, pcr.evidence_count, pcr.reviewed_at
            FROM proof_contract_requirement pcr JOIN job_requirement jr ON jr.id = pcr.job_requirement_id JOIN skill s ON s.id = jr.skill_id
            WHERE pcr.contract_id = ? ORDER BY jr.kind, s.name
            """, (rs, rowNum) -> new RecruiterDtos.CandidateContractRequirementResponse(rs.getObject("id", UUID.class), rs.getObject("skill_id", UUID.class), rs.getString("skill_name"), rs.getString("kind"), rs.getString("outcome"), rs.getString("proof_summary"), rs.getInt("evidence_count"), instant(rs, "reviewed_at")), contractId);
        return new RecruiterDtos.CandidateProofContractResponse(row.id(), row.jobId(), row.jobTitle(), row.company(), row.version(), row.status(), row.createdAt(), requirements);
    }

    public List<RecruiterDtos.ApplicationResponse> recruiterApplications(UUID recruiterId, UUID jobId) { getOwnedJob(recruiterId, jobId); return jdbc.query("SELECT ja.id FROM job_application ja WHERE ja.job_id = ? ORDER BY ja.created_at DESC", (rs, rowNum) -> rs.getObject("id", UUID.class), jobId).stream().map(this::application).toList(); }

    @Transactional
    public RecruiterDtos.ApplicationResponse updateApplication(UUID recruiterId, UUID applicationId, RecruiterDtos.ApplicationStatusRequest request, String requestId) {
        String status = request.status().trim().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("REVIEWING", "SHORTLISTED", "REJECTED").contains(status)) throw new RecruiterInputException("APPLICATION_STATUS_INVALID", "Application status must be REVIEWING, SHORTLISTED, or REJECTED.");
        ApplicationRow application = findApplication(applicationId);
        getOwnedJob(recruiterId, application.jobId());
        jdbc.update("UPDATE job_application SET status = ?, updated_at = now() WHERE id = ?", status, applicationId);
        audit.record(recruiterId, "APPLICATION_STATUS_UPDATED", "JOB_APPLICATION", applicationId, requestId, Map.of("status", status));
        return application(applicationId);
    }

    @Transactional
    public RecruiterDtos.ProofContractResponse createOrRefreshContract(UUID recruiterId, UUID applicationId, String requestId) {
        ApplicationRow application = findApplication(applicationId);
        getOwnedJob(recruiterId, application.jobId());
        UUID contractId = existingContract(application.jobId(), application.candidateId());
        if (contractId == null) contractId = jdbc.queryForObject("INSERT INTO proof_contract(job_id, candidate_id, version, status, created_by) VALUES (?, ?, 1, 'SHARED', ?) RETURNING id", UUID.class, application.jobId(), application.candidateId(), recruiterId);
        List<RequirementFacts> requirements = requirementFacts(application.jobId(), application.candidateId());
        for (RequirementFacts requirement : requirements) {
            jdbc.update("""
                INSERT INTO proof_contract_requirement(contract_id, job_requirement_id, outcome, proof_summary, evidence_count, verification_result_id)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (contract_id, job_requirement_id) DO UPDATE SET outcome = CASE WHEN proof_contract_requirement.reviewed_by IS NULL THEN EXCLUDED.outcome ELSE proof_contract_requirement.outcome END, proof_summary = EXCLUDED.proof_summary, evidence_count = EXCLUDED.evidence_count, verification_result_id = EXCLUDED.verification_result_id
                """, contractId, requirement.requirementId(), requirement.outcome(), requirement.summary(), requirement.evidenceCount(), requirement.verificationResultId());
        }
        audit.record(recruiterId, "PROOF_CONTRACT_GENERATED", "PROOF_CONTRACT", contractId, requestId, Map.of("jobId", application.jobId().toString(), "candidateId", application.candidateId().toString()));
        return contract(recruiterId, contractId);
    }

    public RecruiterDtos.ProofContractResponse getContract(UUID recruiterId, UUID contractId) { return contract(recruiterId, contractId); }

    @Transactional
    public RecruiterDtos.ProofContractResponse reviewContractRequirement(UUID recruiterId, UUID contractId, UUID contractRequirementId, RecruiterDtos.ContractReviewRequest request, String requestId) {
        String outcome = request.outcome().trim().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("SUPPORTED", "PARTIAL", "MISSING", "DISPUTED", "UNREVIEWED").contains(outcome)) throw new RecruiterInputException("CONTRACT_OUTCOME_INVALID", "That proof contract outcome is not supported.");
        ContractOwner owner = jdbc.queryForObject("SELECT pc.job_id, pc.candidate_id FROM proof_contract pc JOIN job j ON j.id = pc.job_id WHERE pc.id = ? AND j.created_by = ?", (rs, rowNum) -> new ContractOwner(rs.getObject("job_id", UUID.class), rs.getObject("candidate_id", UUID.class)), contractId, recruiterId);
        int updated = jdbc.update("UPDATE proof_contract_requirement SET outcome = ?, reviewer_note = ?, reviewed_by = ?, reviewed_at = now() WHERE id = ? AND contract_id = ?", outcome, blankToNull(request.reviewerNote()), recruiterId, contractRequirementId, contractId);
        if (updated == 0) throw new RecruiterNotFoundException("CONTRACT_REQUIREMENT_NOT_FOUND", "That proof contract requirement is not available.");
        jdbc.update("UPDATE proof_contract SET status = 'REVIEWED', updated_at = now() WHERE id = ?", contractId);
        audit.record(recruiterId, "PROOF_CONTRACT_REQUIREMENT_REVIEWED", "PROOF_CONTRACT_REQUIREMENT", contractRequirementId, requestId, Map.of("outcome", outcome, "candidateId", owner.candidateId().toString()));
        return contract(recruiterId, contractId);
    }

    private UUID existingContract(UUID jobId, UUID candidateId) { try { return jdbc.queryForObject("SELECT id FROM proof_contract WHERE job_id = ? AND candidate_id = ? AND status <> 'ARCHIVED' ORDER BY version DESC LIMIT 1", UUID.class, jobId, candidateId); } catch (org.springframework.dao.EmptyResultDataAccessException ex) { return null; } }
    private RecruiterDtos.ProofContractResponse contract(UUID recruiterId, UUID contractId) {
        ContractRow contract = jdbc.queryForObject("SELECT pc.id, pc.job_id, pc.candidate_id, pc.version, pc.status, pc.created_at FROM proof_contract pc JOIN job j ON j.id = pc.job_id WHERE pc.id = ? AND j.created_by = ?", (rs, rowNum) -> new ContractRow(rs.getObject("id", UUID.class), rs.getObject("job_id", UUID.class), rs.getObject("candidate_id", UUID.class), rs.getInt("version"), rs.getString("status"), instant(rs, "created_at")), contractId, recruiterId);
        List<RecruiterDtos.ContractRequirementResponse> requirements = jdbc.query("""
            SELECT pcr.id, pcr.job_requirement_id, jr.skill_id, s.name skill_name, jr.kind, pcr.outcome, pcr.proof_summary, pcr.evidence_count, pcr.verification_result_id, pcr.reviewer_note, pcr.reviewed_at
            FROM proof_contract_requirement pcr JOIN job_requirement jr ON jr.id = pcr.job_requirement_id JOIN skill s ON s.id = jr.skill_id
            WHERE pcr.contract_id = ? ORDER BY jr.kind, s.name
            """, (rs, rowNum) -> new RecruiterDtos.ContractRequirementResponse(rs.getObject("id", UUID.class), rs.getObject("job_requirement_id", UUID.class), rs.getObject("skill_id", UUID.class), rs.getString("skill_name"), rs.getString("kind"), rs.getString("outcome"), rs.getString("proof_summary"), rs.getInt("evidence_count"), rs.getObject("verification_result_id", UUID.class), rs.getString("reviewer_note"), instant(rs, "reviewed_at")), contractId);
        return new RecruiterDtos.ProofContractResponse(contract.id(), contract.jobId(), contract.candidateId(), contract.version(), contract.status(), contract.createdAt(), requirements);
    }

    private List<RequirementFacts> requirementFacts(UUID jobId, UUID candidateId) {
        return jdbc.query("""
            SELECT jr.id requirement_id, jr.skill_id, s.name skill_name, jr.kind,
              coalesce((SELECT count(*) FROM evidence e JOIN evidence_source es ON es.id = e.evidence_source_id WHERE e.candidate_id = ? AND e.skill_id = jr.skill_id AND e.status <> 'DISPUTED' AND es.visibility IN ('RECRUITER_SHARED','INSTITUTION_SHARED','PUBLIC_SUMMARY','PUBLIC')), 0) visible_evidence,
              (SELECT vr.id FROM verification_result vr WHERE vr.candidate_id = ? AND vr.skill_id = jr.skill_id ORDER BY vr.evaluated_at DESC LIMIT 1) verification_id,
              coalesce((SELECT vr.status FROM verification_result vr WHERE vr.candidate_id = ? AND vr.skill_id = jr.skill_id ORDER BY vr.evaluated_at DESC LIMIT 1), 'NOT_EVALUATED') verification_status,
              coalesce((SELECT cs.status FROM candidate_skill cs WHERE cs.candidate_id = ? AND cs.skill_id = jr.skill_id), 'NOT_FOUND') skill_status
            FROM job_requirement jr JOIN skill s ON s.id = jr.skill_id WHERE jr.job_id = ? ORDER BY jr.kind, s.name
            """, (rs, rowNum) -> {
                int visible = rs.getInt("visible_evidence"); String verificationStatus = rs.getString("verification_status"); String skillStatus = rs.getString("skill_status");
                String outcome = "VERIFIED".equals(verificationStatus) || "VERIFIED".equals(skillStatus) ? "SUPPORTED" : visible > 0 || "PARTIAL".equals(verificationStatus) || "EVIDENCE_FOUND".equals(skillStatus) ? "PARTIAL" : "MISSING";
                String summary = "SUPPORTED".equals(outcome) ? "Policy-backed verification is present; raw source remains hidden unless explicitly shared." : "PARTIAL".equals(outcome) ? "" + visible + " recruiter-visible evidence item(s) are shared; verification is incomplete." : "No recruiter-visible proof is currently available for this requirement.";
                return new RequirementFacts(rs.getObject("requirement_id", UUID.class), rs.getObject("skill_id", UUID.class), rs.getString("skill_name"), rs.getString("kind"), outcome, summary, visible, rs.getObject("verification_id", UUID.class));
            }, candidateId, candidateId, candidateId, candidateId, jobId);
    }

    private RecruiterDtos.ApplicationResponse application(UUID id) { ApplicationRow row = findApplication(id); return new RecruiterDtos.ApplicationResponse(row.id(), row.jobId(), row.candidateId(), row.candidateName(), row.jobTitle(), row.status(), row.note(), row.createdAt()); }
    private ApplicationRow findApplication(UUID id) { try { return jdbc.queryForObject("SELECT ja.id, ja.job_id, ja.candidate_id, u.display_name candidate_name, j.title job_title, ja.status, ja.candidate_note, ja.created_at FROM job_application ja JOIN app_user u ON u.id = ja.candidate_id JOIN job j ON j.id = ja.job_id WHERE ja.id = ?", (rs, rowNum) -> new ApplicationRow(rs.getObject("id", UUID.class), rs.getObject("job_id", UUID.class), rs.getObject("candidate_id", UUID.class), rs.getString("candidate_name"), rs.getString("job_title"), rs.getString("status"), rs.getString("candidate_note"), instant(rs, "created_at")), id); } catch (org.springframework.dao.EmptyResultDataAccessException ex) { throw new RecruiterNotFoundException("APPLICATION_NOT_FOUND", "That application is not available."); } }
    private RecruiterDtos.JobResponse getOwnedJob(UUID recruiterId, UUID jobId) { JobRow job = findJob(jobId); if (!job.createdBy().equals(recruiterId)) throw new RecruiterNotFoundException("JOB_NOT_FOUND", "That job is not available."); return jobResponse(job); }
    private RecruiterDtos.JobResponse getJob(UUID jobId) { return jobResponse(findJob(jobId)); }
    private RecruiterDtos.JobResponse jobResponse(JobRow job) { List<RecruiterDtos.JobRequirementResponse> requirements = jdbc.query("SELECT jr.id, jr.skill_id, s.name, jr.kind, jr.source_phrase, jr.recruiter_edited FROM job_requirement jr JOIN skill s ON s.id = jr.skill_id WHERE jr.job_id = ? ORDER BY jr.kind, s.name", (rs, rowNum) -> new RecruiterDtos.JobRequirementResponse(rs.getObject("id", UUID.class), rs.getObject("skill_id", UUID.class), rs.getString("name"), rs.getString("kind"), rs.getString("source_phrase"), rs.getBoolean("recruiter_edited")), job.id()); return new RecruiterDtos.JobResponse(job.id(), job.title(), job.company(), job.location(), job.status(), job.description(), job.createdAt(), requirements); }
    private JobRow findJob(UUID jobId) { try { return jdbc.queryForObject("SELECT j.id, j.created_by, o.name company, j.title, j.location, j.original_description, j.status, j.created_at FROM job j JOIN organization o ON o.id = j.organization_id WHERE j.id = ?", (rs, rowNum) -> new JobRow(rs.getObject("id", UUID.class), rs.getObject("created_by", UUID.class), rs.getString("company"), rs.getString("title"), rs.getString("location"), rs.getString("original_description"), rs.getString("status"), instant(rs, "created_at")), jobId); } catch (org.springframework.dao.EmptyResultDataAccessException ex) { throw new RecruiterNotFoundException("JOB_NOT_FOUND", "That job is not available."); } }
    private UUID ensureRecruiterOrganization(UUID recruiterId, String company) { String slug = "recruiter-" + recruiterId; try { UUID id = jdbc.queryForObject("SELECT id FROM organization WHERE slug = ?", UUID.class, slug); jdbc.update("INSERT INTO organization_member(organization_id, user_id, member_role) VALUES (?, ?, 'OWNER') ON CONFLICT DO NOTHING", id, recruiterId); return id; } catch (org.springframework.dao.EmptyResultDataAccessException ex) { UUID id = jdbc.queryForObject("INSERT INTO organization(name, kind, slug) VALUES (?, 'RECRUITER', ?) RETURNING id", UUID.class, company, slug); jdbc.update("INSERT INTO organization_member(organization_id, user_id, member_role) VALUES (?, ?, 'OWNER')", id, recruiterId); return id; } }
    private List<SkillMatch> matchSkills(String description) { String lower = description.toLowerCase(java.util.Locale.ROOT); return jdbc.query("SELECT DISTINCT ON (s.id) s.id, s.name, sa.alias FROM skill_alias sa JOIN skill s ON s.id = sa.skill_id WHERE s.status = 'ACTIVE' AND strpos(?, lower(sa.alias)) > 0 ORDER BY s.id, length(sa.alias) DESC", (rs, rowNum) -> new SkillMatch(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("alias"), "PREFERRED"), lower).stream().map(item -> new SkillMatch(item.skillId(), item.skillName(), item.sourcePhrase(), lower.contains("must") || lower.contains("required") ? "REQUIRED" : item.kind())).toList(); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException { OffsetDateTime value = rs.getObject(column, OffsetDateTime.class); return value == null ? null : value.toInstant(); }
    private record SkillMatch(UUID skillId, String skillName, String sourcePhrase, String kind) {}
    private record JobRow(UUID id, UUID createdBy, String company, String title, String location, String description, String status, Instant createdAt) {}
    private record ApplicationRow(UUID id, UUID jobId, UUID candidateId, String candidateName, String jobTitle, String status, String note, Instant createdAt) {}
    private record ContractRow(UUID id, UUID jobId, UUID candidateId, int version, String status, Instant createdAt) {}
    private record CandidateContractRow(UUID id, UUID jobId, String jobTitle, String company, int version, String status, Instant createdAt) {}
    private record ContractOwner(UUID jobId, UUID candidateId) {}
    private record RequirementFacts(UUID requirementId, UUID skillId, String skillName, String kind, String outcome, String summary, int evidenceCount, UUID verificationResultId) {}
    public static class RecruiterNotFoundException extends RuntimeException { private final String code; public RecruiterNotFoundException(String code, String message) { super(message); this.code = code; } public String code() { return code; } }
    public static class RecruiterInputException extends RuntimeException { private final String code; public RecruiterInputException(String code, String message) { super(message); this.code = code; } public String code() { return code; } }
}
