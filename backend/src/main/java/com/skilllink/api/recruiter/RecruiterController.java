package com.skilllink.api.recruiter;

import com.skilllink.api.auth.SkillLinkPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class RecruiterController {
    private final RecruiterService recruiter;
    public RecruiterController(RecruiterService recruiter) { this.recruiter = recruiter; }

    @GetMapping("/recruiter/jobs")
    @PreAuthorize("hasRole('RECRUITER')")
    public List<RecruiterDtos.JobResponse> recruiterJobs(@AuthenticationPrincipal SkillLinkPrincipal principal) { return recruiter.recruiterJobs(principal.id()); }

    @PostMapping("/recruiter/jobs")
    @PreAuthorize("hasRole('RECRUITER')")
    public RecruiterDtos.JobResponse createJob(@AuthenticationPrincipal SkillLinkPrincipal principal, @Valid @RequestBody RecruiterDtos.CreateJobRequest request, @RequestHeader(value = "X-Request-Id", required = false) String requestId) { return recruiter.createJob(principal.id(), request, requestId); }

    @GetMapping("/recruiter/jobs/{jobId}")
    @PreAuthorize("hasRole('RECRUITER')")
    public RecruiterDtos.JobResponse recruiterJob(@AuthenticationPrincipal SkillLinkPrincipal principal, @PathVariable UUID jobId) { return recruiter.recruiterJob(principal.id(), jobId); }

    @GetMapping("/jobs")
    @PreAuthorize("hasRole('CANDIDATE')")
    public List<RecruiterDtos.JobResponse> openJobs() { return recruiter.openJobs(); }

    @PostMapping("/jobs/{jobId}/applications")
    @PreAuthorize("hasRole('CANDIDATE')")
    public RecruiterDtos.ApplicationResponse apply(@AuthenticationPrincipal SkillLinkPrincipal principal, @PathVariable UUID jobId, @Valid @RequestBody(required = false) RecruiterDtos.ApplicationRequest request, @RequestHeader(value = "X-Request-Id", required = false) String requestId) { return recruiter.apply(principal.id(), jobId, request == null ? new RecruiterDtos.ApplicationRequest(null) : request, requestId); }

    @GetMapping("/candidates/me/applications")
    @PreAuthorize("hasRole('CANDIDATE')")
    public List<RecruiterDtos.ApplicationResponse> candidateApplications(@AuthenticationPrincipal SkillLinkPrincipal principal) { return recruiter.candidateApplications(principal.id()); }

    @GetMapping("/recruiter/jobs/{jobId}/applications")
    @PreAuthorize("hasRole('RECRUITER')")
    public List<RecruiterDtos.ApplicationResponse> recruiterApplications(@AuthenticationPrincipal SkillLinkPrincipal principal, @PathVariable UUID jobId) { return recruiter.recruiterApplications(principal.id(), jobId); }

    @PatchMapping("/recruiter/applications/{applicationId}")
    @PreAuthorize("hasRole('RECRUITER')")
    public RecruiterDtos.ApplicationResponse updateApplication(@AuthenticationPrincipal SkillLinkPrincipal principal, @PathVariable UUID applicationId, @Valid @RequestBody RecruiterDtos.ApplicationStatusRequest request, @RequestHeader(value = "X-Request-Id", required = false) String requestId) { return recruiter.updateApplication(principal.id(), applicationId, request, requestId); }

    @PostMapping("/recruiter/applications/{applicationId}/proof-contract")
    @PreAuthorize("hasRole('RECRUITER')")
    public RecruiterDtos.ProofContractResponse createContract(@AuthenticationPrincipal SkillLinkPrincipal principal, @PathVariable UUID applicationId, @RequestHeader(value = "X-Request-Id", required = false) String requestId) { return recruiter.createOrRefreshContract(principal.id(), applicationId, requestId); }

    @GetMapping("/recruiter/proof-contracts/{contractId}")
    @PreAuthorize("hasRole('RECRUITER')")
    public RecruiterDtos.ProofContractResponse getContract(@AuthenticationPrincipal SkillLinkPrincipal principal, @PathVariable UUID contractId) { return recruiter.getContract(principal.id(), contractId); }

    @PatchMapping("/recruiter/proof-contracts/{contractId}/requirements/{requirementId}")
    @PreAuthorize("hasRole('RECRUITER')")
    public RecruiterDtos.ProofContractResponse reviewRequirement(@AuthenticationPrincipal SkillLinkPrincipal principal, @PathVariable UUID contractId, @PathVariable UUID requirementId, @Valid @RequestBody RecruiterDtos.ContractReviewRequest request, @RequestHeader(value = "X-Request-Id", required = false) String requestId) { return recruiter.reviewContractRequirement(principal.id(), contractId, requirementId, request, requestId); }
}
