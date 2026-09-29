package com.skilllink.api.github;

import com.skilllink.api.auth.SkillLinkPrincipal;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/github")
public class GithubOAuthController {
    private final GithubOAuthService service;
    public GithubOAuthController(GithubOAuthService service) { this.service = service; }

    @GetMapping("/connect")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<Void> connect(@AuthenticationPrincipal SkillLinkPrincipal principal) {
        return ResponseEntity.status(302).location(URI.create(service.begin(principal.id()))).build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state, @RequestParam(required = false) String error) {
        try {
            if (error != null) return ResponseEntity.status(302).location(URI.create(service.redirectFailure("GITHUB_" + error.toUpperCase()))).build();
            service.complete(code, state);
            return ResponseEntity.status(302).location(URI.create(service.redirectSuccess())).build();
        } catch (GithubOAuthService.GithubConfigurationException ex) {
            return ResponseEntity.status(302).location(URI.create(service.redirectFailure(ex.code()))).build();
        } catch (GithubClient.GithubException ex) {
            return ResponseEntity.status(302).location(URI.create(service.redirectFailure(ex.code()))).build();
        }
    }

    @GetMapping("/status")
    @PreAuthorize("hasRole('CANDIDATE')")
    public GithubDtos.ConnectionStatus status(@AuthenticationPrincipal SkillLinkPrincipal principal) { return service.status(principal.id()); }

    @GetMapping("/repositories")
    @PreAuthorize("hasRole('CANDIDATE')")
    public List<GithubDtos.RepositorySummary> repositories(@AuthenticationPrincipal SkillLinkPrincipal principal) { return service.repositories(principal.id()); }

    @PostMapping("/repositories/{githubRepositoryId}/select")
    @PreAuthorize("hasRole('CANDIDATE')")
    public GithubDtos.ProjectSelectionResponse select(@AuthenticationPrincipal SkillLinkPrincipal principal, @PathVariable @NotBlank String githubRepositoryId) { return service.selectRepository(principal.id(), githubRepositoryId); }

    @DeleteMapping("/connection")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal SkillLinkPrincipal principal) { service.disconnect(principal.id()); return ResponseEntity.noContent().build(); }
}
