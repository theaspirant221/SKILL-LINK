package com.skilllink.api.challenge;

import com.skilllink.api.auth.SkillLinkPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ChallengeController {
    private final ChallengeService challenges;
    public ChallengeController(ChallengeService challenges) { this.challenges = challenges; }

    @PostMapping("/candidates/me/challenges")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ChallengeDtos.ChallengeResponse create(@AuthenticationPrincipal SkillLinkPrincipal principal, @Valid @RequestBody ChallengeDtos.CreateRequest request, @RequestHeader(value = "X-Request-Id", required = false) String requestId) { return challenges.create(principal.id(), request, requestId); }

    @GetMapping("/candidates/me/challenges/{challengeId}")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ChallengeDtos.ChallengeResponse get(@AuthenticationPrincipal SkillLinkPrincipal principal, @PathVariable UUID challengeId) { return challenges.get(principal.id(), challengeId); }

    @PostMapping("/candidates/me/challenges/{challengeId}/submissions")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ChallengeDtos.ChallengeResponse submit(@AuthenticationPrincipal SkillLinkPrincipal principal, @PathVariable UUID challengeId, @Valid @RequestBody ChallengeDtos.SubmissionRequest request, @RequestHeader(value = "X-Request-Id", required = false) String requestId) { return challenges.submit(principal.id(), challengeId, request.text(), requestId); }

    @PostMapping("/recruiter/challenge-submissions/{submissionId}/review")
    @PreAuthorize("hasRole('RECRUITER')")
    public ChallengeDtos.ChallengeResponse review(@AuthenticationPrincipal SkillLinkPrincipal principal, @PathVariable UUID submissionId, @Valid @RequestBody ChallengeDtos.ReviewRequest request, @RequestHeader(value = "X-Request-Id", required = false) String requestId) { return challenges.review(principal.id(), submissionId, request, requestId); }
}
