package com.skilllink.api.verification;

import com.skilllink.api.auth.SkillLinkPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/candidates/me/verifications")
@PreAuthorize("hasRole('CANDIDATE')")
public class VerificationController {
    private final VerificationService verification;
    public VerificationController(VerificationService verification) { this.verification = verification; }

    @GetMapping
    public List<VerificationDtos.VerificationResponse> list(@AuthenticationPrincipal SkillLinkPrincipal principal) { return verification.list(principal.id()); }

    @PostMapping("/skills/{skillId}/evaluate")
    public VerificationDtos.VerificationResponse evaluate(@AuthenticationPrincipal SkillLinkPrincipal principal, @PathVariable UUID skillId, @RequestHeader(value = "X-Request-Id", required = false) String requestId) { return verification.evaluate(principal.id(), skillId, requestId); }
}
