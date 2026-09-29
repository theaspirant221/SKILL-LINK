package com.skilllink.api.passport;

import com.skilllink.api.auth.SkillLinkPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PassportController {
    private final PassportService passports;
    public PassportController(PassportService passports) { this.passports = passports; }

    @GetMapping("/candidates/me/passport")
    @PreAuthorize("hasRole('CANDIDATE')")
    public PassportDtos.PassportResponse get(@AuthenticationPrincipal SkillLinkPrincipal principal) { return passports.get(principal.id()); }

    @PostMapping("/candidates/me/passport/issue")
    @PreAuthorize("hasRole('CANDIDATE')")
    public PassportDtos.PassportResponse issue(@AuthenticationPrincipal SkillLinkPrincipal principal, @RequestHeader(value = "X-Request-Id", required = false) String requestId) { return passports.issue(principal.id(), requestId); }

    @PatchMapping("/candidates/me/passport/visibility")
    @PreAuthorize("hasRole('CANDIDATE')")
    public PassportDtos.PassportResponse visibility(@AuthenticationPrincipal SkillLinkPrincipal principal, @Valid @RequestBody PassportDtos.VisibilityRequest request, @RequestHeader(value = "X-Request-Id", required = false) String requestId) { return passports.visibility(principal.id(), request.visibility(), requestId); }

    @GetMapping("/public/passports/{identifier}")
    public PassportDtos.PublicPassportResponse publicPassport(@PathVariable String identifier) { return passports.publicPassport(identifier); }
}
