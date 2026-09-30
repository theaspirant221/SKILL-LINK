package com.skilllink.api.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-to-server GitHub App operations: installation lookup, short-lived installation access
 * tokens (cached only within a safe expiry window and never persisted or exposed to clients),
 * and installation repository listing. All calls are authenticated with the GitHub App JWT or an
 * installation token; the SkillLink client secret never reaches the browser.
 */
@Component
public class GithubAppClient {
    private static final long TOKEN_EXPIRY_MARGIN_SECONDS = 300; // refresh tokens well before GitHub's 1-hour expiry.

    private final GithubProperties properties;
    private final GithubAppJwtService appJwt;
    private final ObjectMapper objectMapper;
    private final RestClient rest;
    private final Map<Long, CachedToken> tokenCache = new ConcurrentHashMap<>();

    public GithubAppClient(GithubProperties properties, GithubAppJwtService appJwt, ObjectMapper objectMapper) {
        this.properties = properties; this.appJwt = appJwt; this.objectMapper = objectMapper; this.rest = RestClient.create();
    }

    public record Installation(long installationId, long accountId, String accountLogin, String accountType, String repositorySelection) {}
    public record InstallationRepository(String id, String name, String fullName, String owner, boolean privateRepository, String defaultBranch, String primaryLanguage, String updatedAt, long sizeKb, String description) {}
    record InstallationAccessToken(String token, Instant expiresAt) {}
    private record CachedToken(String token, Instant refreshAfter) {}

    /** GET /app/installations/{id} — installation identity, authorized with the GitHub App JWT. */
    public Installation installation(long installationId) {
        JsonNode json = rest.get().uri(properties.apiUrl() + "/app/installations/" + installationId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + appJwt.createAppJwt())
            .header("X-GitHub-Api-Version", properties.apiVersion())
            .accept(MediaType.APPLICATION_JSON)
            .exchange((request, response) -> readResponse(response));
        return new Installation(json.path("id").asLong(), json.path("account").path("id").asLong(),
            json.path("account").path("login").asText(""), json.path("account").path("type").asText(""),
            json.path("repository_selection").asText("selected").toUpperCase(java.util.Locale.ROOT));
    }

    /**
     * Short-lived installation access token, generated server-side and cached only until shortly
     * before expiry. Never persisted to the database and never returned to a client.
     */
    public InstallationAccessToken installationAccessToken(long installationId) {
        CachedToken cached = tokenCache.get(installationId);
        if (cached != null && Instant.now().isBefore(cached.refreshAfter())) return new InstallationAccessToken(cached.token(), cached.refreshAfter());
        JsonNode json = rest.post().uri(properties.apiUrl() + "/app/installations/" + installationId + "/access_tokens")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + appJwt.createAppJwt())
            .header("X-GitHub-Api-Version", properties.apiVersion())
            .accept(MediaType.APPLICATION_JSON)
            .exchange((request, response) -> readResponse(response));
        String token = json.path("token").asText("");
        if (token.isBlank()) throw new GithubClient.GithubException("GITHUB_INSTALLATION_TOKEN_FAILED", "GitHub did not issue an installation access token.", 502);
        Instant expiresAt = parseInstant(json.path("expires_at").asText(null));
        if (expiresAt == null) expiresAt = Instant.now().plusSeconds(3600);
        Instant refreshAfter = expiresAt.minusSeconds(TOKEN_EXPIRY_MARGIN_SECONDS);
        tokenCache.put(installationId, new CachedToken(token, refreshAfter));
        return new InstallationAccessToken(token, expiresAt);
    }

    /** POST-deletes any cached installation token (used when access is revoked or removed). */
    public void evictInstallationToken(long installationId) { tokenCache.remove(installationId); }

    /** GET /installation/repositories — only repositories the installation actually grants access to. */
    public List<InstallationRepository> installationRepositories(long installationId) {
        String token = installationAccessToken(installationId).token();
        JsonNode json = rest.get().uri(properties.apiUrl() + "/installation/repositories?per_page=100")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .header("X-GitHub-Api-Version", properties.apiVersion())
            .accept(MediaType.APPLICATION_JSON)
            .exchange((request, response) -> readResponse(response));
        List<InstallationRepository> result = new ArrayList<>();
        for (JsonNode node : json.path("repositories")) {
            result.add(new InstallationRepository(node.path("id").asText(), node.path("name").asText(), node.path("full_name").asText(),
                node.path("owner").path("login").asText(), node.path("private").asBoolean(), node.path("default_branch").asText("main"),
                node.path("language").isNull() ? null : node.path("language").asText(), node.path("updated_at").asText(null),
                node.path("size").asLong(0), node.path("description").isNull() ? null : node.path("description").asText()));
        }
        return result;
    }

    private JsonNode readResponse(org.springframework.http.client.ClientHttpResponse response) throws java.io.IOException {
        if (!response.getStatusCode().is2xxSuccessful()) throw new GithubClient.GithubException("GITHUB_API_ERROR", "GitHub API request failed.", response.getStatusCode().value());
        return objectMapper.readTree(response.getBody());
    }

    private Instant parseInstant(String value) { try { return value == null ? null : Instant.from(OffsetDateTime.parse(value)); } catch (Exception ignored) { return null; } }
}
