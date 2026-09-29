package com.skilllink.api.github;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class GithubDtos {
    private GithubDtos() {}

    /** Explicit connection states; the server is the source of truth, never the frontend. */
    public static final String STATUS_DISCONNECTED = "DISCONNECTED";
    public static final String STATUS_CONNECTED = "CONNECTED";
    public static final String STATUS_REAUTH_REQUIRED = "REAUTH_REQUIRED";
    public static final String STATUS_INSTALLATION_MISSING = "INSTALLATION_MISSING";
    public static final String STATUS_INSTALLATION_REMOVED = "INSTALLATION_REMOVED";
    public static final String STATUS_TOKEN_INVALID = "TOKEN_INVALID";

    public record InstallationView(long installationId, long accountId, String accountLogin, String accountType, String repositorySelection, String status) {}
    public record ConnectionStatus(String status, boolean connected, String githubLogin, Long githubUserId, String scopes, Instant connectedAt, Instant lastValidatedAt, InstallationView installation) {}
    public record RepositorySummary(String id, String name, String fullName, String owner, boolean privateRepository, String visibility, String defaultBranch, String primaryLanguage, Instant updatedAt, long sizeKb, String description, String accountLogin) {}
    public record ProjectSelectionResponse(UUID projectId, String projectName, String repositoryFullName, String commitBranch) {}
    public record OAuthStartResponse(String authorizationUrl) {}
    public record GithubError(String code, String message) {}
}
