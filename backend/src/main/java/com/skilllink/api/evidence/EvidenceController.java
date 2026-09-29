package com.skilllink.api.evidence;

import com.skilllink.api.analysis.AnalysisJobService;
import com.skilllink.api.auth.SkillLinkPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('CANDIDATE')")
public class EvidenceController {
    private final EvidenceService evidence;
    public EvidenceController(EvidenceService evidence) { this.evidence = evidence; }
    @GetMapping("/projects/{projectId}/evidence") public List<EvidenceDtos.EvidenceResponse> list(@AuthenticationPrincipal SkillLinkPrincipal principal, @PathVariable UUID projectId) { return evidence.list(principal.id(), projectId); }
    @GetMapping("/candidates/me/skills") public List<EvidenceDtos.SkillResponse> skills(@AuthenticationPrincipal SkillLinkPrincipal principal) { return evidence.skills(principal.id()); }
    @PostMapping("/evidence/{evidenceId}/dispute") public ResponseEntity<Void> dispute(@AuthenticationPrincipal SkillLinkPrincipal principal, @PathVariable UUID evidenceId, @Valid @RequestBody EvidenceDtos.DisputeRequest request) { evidence.dispute(principal.id(), evidenceId, request.reason()); return ResponseEntity.accepted().build(); }
    @PatchMapping("/evidence/{evidenceId}/visibility") public ResponseEntity<Void> visibility(@AuthenticationPrincipal SkillLinkPrincipal principal, @PathVariable UUID evidenceId, @Valid @RequestBody EvidenceDtos.VisibilityRequest request) { evidence.visibility(principal.id(), evidenceId, request.visibility()); return ResponseEntity.noContent().build(); }
}
