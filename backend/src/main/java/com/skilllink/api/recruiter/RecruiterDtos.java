package com.skilllink.api.recruiter;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RecruiterDtos {
    private RecruiterDtos() {}
    public record CreateJobRequest(@NotBlank String title, @NotBlank String company, @NotBlank String description, String location) {}
    public record JobRequirementResponse(UUID requirementId, UUID skillId, String skillName, String kind, String sourcePhrase, boolean recruiterEdited) {}
    public record JobResponse(UUID jobId, String title, String company, String location, String status, String originalDescription, Instant createdAt, List<JobRequirementResponse> requirements) {}
    public record ApplicationRequest(String note) {}
    public record ApplicationStatusRequest(@NotBlank String status) {}
    public record ApplicationResponse(UUID applicationId, UUID jobId, UUID candidateId, String candidateDisplayName, String jobTitle, String status, String note, Instant createdAt) {}
    public record ContractRequirementResponse(UUID contractRequirementId, UUID jobRequirementId, UUID skillId, String skillName, String requiredKind, String outcome, String proofSummary, int visibleEvidenceCount, UUID verificationResultId, String reviewerNote, Instant reviewedAt) {}
    public record ProofContractResponse(UUID contractId, UUID jobId, UUID candidateId, int version, String status, Instant createdAt, List<ContractRequirementResponse> requirements) {}
    public record ContractReviewRequest(@NotBlank String outcome, String reviewerNote) {}
    // Candidate-facing projection of a shared Proof Contract. Deliberately omits recruiter reviewer notes and
    // reviewer identity so candidates can inspect what was shared without exposing reviewer-private context.
    public record CandidateContractRequirementResponse(UUID contractRequirementId, UUID skillId, String skillName, String requiredKind, String outcome, String proofSummary, int visibleEvidenceCount, Instant reviewedAt) {}
    public record CandidateProofContractResponse(UUID contractId, UUID jobId, String jobTitle, String company, int version, String status, Instant createdAt, List<CandidateContractRequirementResponse> requirements) {}
}
