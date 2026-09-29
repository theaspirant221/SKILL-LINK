package com.skilllink.api.project;

import com.skilllink.api.auth.SkillLinkPrincipal;
import com.skilllink.api.github.GithubDtos;
import com.skilllink.api.github.GithubRepositoryJdbcRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {
    private final GithubRepositoryJdbcRepository repositories;
    public ProjectService(GithubRepositoryJdbcRepository repositories) { this.repositories = repositories; }

    @Transactional
    public GithubDtos.ProjectSelectionResponse selectGithubRepository(UUID candidateId, GithubDtos.RepositorySummary remote) {
        GithubRepositoryJdbcRepository.RepositoryRow row = repositories.findByCandidateAndGithubId(candidateId, remote.id()).orElseGet(() -> repositories.insert(candidateId, remote));
        return new GithubDtos.ProjectSelectionResponse(row.projectId(), remote.name(), row.fullName(), row.defaultBranch());
    }

    public List<ProjectResponse> list(UUID candidateId) { return repositories.listByCandidate(candidateId).stream().map(ProjectResponse::from).toList(); }
    public ProjectResponse get(UUID candidateId, UUID projectId) { return repositories.findByProjectAndCandidate(candidateId, projectId).map(ProjectResponse::from).orElseThrow(() -> new ProjectNotFoundException()); }
    public GithubRepositoryJdbcRepository.RepositoryRow repository(UUID candidateId, UUID projectId) { return repositories.findByProjectAndCandidate(candidateId, projectId).orElseThrow(() -> new ProjectNotFoundException()); }
    public record ProjectResponse(UUID projectId, UUID repositoryId, String name, String fullName, String branch, String owner, String primaryLanguage, String visibility) { static ProjectResponse from(GithubRepositoryJdbcRepository.RepositoryRow row) { return new ProjectResponse(row.projectId(), row.id(), row.fullName().substring(row.fullName().lastIndexOf('/') + 1), row.fullName(), row.defaultBranch(), row.owner(), row.primaryLanguage(), row.visibility()); } }
    public static class ProjectNotFoundException extends RuntimeException {}
}
