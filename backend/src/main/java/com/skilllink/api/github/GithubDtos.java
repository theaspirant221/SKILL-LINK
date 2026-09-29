package com.skilllink.api.github;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class GithubDtos {
    private GithubDtos() {}
    public record ConnectionStatus(boolean connected, String login, String scopes, Instant connectedAt) {}
    public record RepositorySummary(String id, String name, String fullName, String owner, boolean privateRepository, String visibility, String defaultBranch, String primaryLanguage, Instant updatedAt, long sizeKb, String description) {}
    public record ProjectSelectionResponse(UUID projectId, String projectName, String repositoryFullName, String commitBranch) {}
    public record OAuthStartResponse(String authorizationUrl) {}
    public record GithubError(String code, String message) {}
}
