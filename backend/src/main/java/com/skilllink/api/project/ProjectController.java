package com.skilllink.api.project;

import com.skilllink.api.analysis.AnalysisJobService;
import com.skilllink.api.auth.SkillLinkPrincipal;
import com.skilllink.api.github.GithubDtos;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@PreAuthorize("hasRole('CANDIDATE')")
public class ProjectController {
    private final ProjectService projects;
    private final AnalysisJobService analysis;
    public ProjectController(ProjectService projects, AnalysisJobService analysis) { this.projects = projects; this.analysis = analysis; }
    @GetMapping public List<ProjectService.ProjectResponse> list(@AuthenticationPrincipal SkillLinkPrincipal principal) { return projects.list(principal.id()); }
    @GetMapping("/{projectId}") public ProjectService.ProjectResponse get(@AuthenticationPrincipal SkillLinkPrincipal principal, @PathVariable UUID projectId) { return projects.get(principal.id(), projectId); }
    @PostMapping("/{projectId}/analysis") public AnalysisJobService.JobResponse analyze(@AuthenticationPrincipal SkillLinkPrincipal principal, @PathVariable UUID projectId) { return analysis.start(principal.id(), projectId); }
    @GetMapping("/{projectId}/analysis/{jobId}") public AnalysisJobService.JobResponse job(@AuthenticationPrincipal SkillLinkPrincipal principal, @PathVariable UUID projectId, @PathVariable UUID jobId) { return analysis.get(principal.id(), projectId, jobId); }
    @PostMapping("/{projectId}/analysis/{jobId}/retry") public AnalysisJobService.JobResponse retry(@AuthenticationPrincipal SkillLinkPrincipal principal, @PathVariable UUID projectId, @PathVariable UUID jobId) { return analysis.retry(principal.id(), projectId, jobId); }
}
