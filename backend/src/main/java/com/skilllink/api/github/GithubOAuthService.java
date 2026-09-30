package com.skilllink.api.github;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * GitHub App connection lifecycle. User authorization (who the GitHub user is) and installation
 * (which account/repositories SkillLink may access) are modeled as separate flows with separate
 * state purposes. OAuth state is random, hashed at rest, short-lived, bound to the starting
 * candidate, and single-use; PKCE (S256) protects the user authorization code exchange. Tokens
 * are stored encrypted and never leave the server.
 */
@Service
public class GithubOAuthService {
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final Duration TOKEN_REFRESH_MARGIN = Duration.ofSeconds(60);

    private final GithubProperties properties;
    private final GithubClient github;
    private final GithubAppClient appClient;
    private final GithubConnectionRepository connections;
    private final GithubInstallationRepository installations;
    private final GithubOAuthStateRepository states;
    private final TokenCipher cipher;
    private final com.skilllink.api.project.ProjectService projects;
    private final String frontendSuccessUrl;
    private final String frontendFailureUrl;
    private final SecureRandom random = new SecureRandom();

    public GithubOAuthService(GithubProperties properties, GithubClient github, GithubAppClient appClient, GithubConnectionRepository connections, GithubInstallationRepository installations, GithubOAuthStateRepository states, TokenCipher cipher, com.skilllink.api.project.ProjectService projects,
                              @Value("${skilllink.frontend-success-url:http://localhost:5173/app/github}") String frontendSuccessUrl,
                              @Value("${skilllink.frontend-failure-url:http://localhost:5173/app/github?github=error}") String frontendFailureUrl) {
        this.properties = properties; this.github = github; this.appClient = appClient; this.connections = connections; this.installations = installations; this.states = states; this.cipher = cipher; this.projects = projects; this.frontendSuccessUrl = frontendSuccessUrl; this.frontendFailureUrl = frontendFailureUrl;
    }

    /** Starts the GitHub App user authorization flow (state + PKCE S256). */
    public String begin(UUID userId) {
        if (!properties.userFlowConfigured()) throw new GithubConfigurationException("GITHUB_NOT_CONFIGURED", "The SkillLink GitHub App is not configured on this environment.");
        String state = randomUrlToken(32);
        String verifier = randomUrlToken(64);
        String challenge = base64Url(sha256(verifier));
        states.create(userId, GithubOAuthStateRepository.PURPOSE_USER_AUTH, sha256Hex(state), cipher.encrypt(verifier), properties.callbackUrl(), Instant.now().plus(STATE_TTL));
        return UriComponentsBuilder.fromUriString(properties.oauthUrl() + "/authorize")
            .queryParam("client_id", properties.clientId())
            .queryParam("redirect_uri", properties.callbackUrl())
            .queryParam("state", state)
            .queryParam("code_challenge", challenge)
            .queryParam("code_challenge_method", "S256")
            .queryParam("allow_signup", "false")
            .build().encode().toUriString();
    }

    /** Starts the GitHub App installation flow; the user chooses the account and selects repositories. */
    public String beginInstall(UUID userId) {
        if (!properties.userFlowConfigured() || !properties.appFlowConfigured()) throw new GithubConfigurationException("GITHUB_NOT_CONFIGURED", "The SkillLink GitHub App is not configured on this environment.");
        String state = randomUrlToken(32);
        states.create(userId, GithubOAuthStateRepository.PURPOSE_INSTALL, sha256Hex(state), cipher.encrypt(""), properties.setupUrl(), Instant.now().plus(STATE_TTL));
        return UriComponentsBuilder.fromUriString(properties.installUrl()).queryParam("state", state).build().encode().toUriString();
    }

    /** Completes user authorization: validates state, exchanges the code with the PKCE verifier, stores encrypted tokens. */
    @Transactional
    public void complete(String code, String state) {
        if (code == null || code.isBlank() || state == null || state.isBlank()) throw new GithubConfigurationException("GITHUB_CALLBACK_INVALID", "GitHub callback is missing required parameters.");
        GithubOAuthStateRepository.State oauthState = states.consume(sha256Hex(state), GithubOAuthStateRepository.PURPOSE_USER_AUTH).orElseThrow(() -> new GithubConfigurationException("GITHUB_STATE_INVALID", "GitHub authorization state is invalid or expired."));
        GithubClient.OAuthToken token = github.exchangeCode(code, cipher.decrypt(oauthState.encryptedCodeVerifier()));
        GithubClient.GithubUser user = github.currentUser(token.value());
        connections.upsert(oauthState.userId(), user.id(), user.login(), cipher.encrypt(token.value()), token.refreshToken() == null ? null : cipher.encrypt(token.refreshToken()), token.scope(), token.expiresAt());
    }

    /** Completes the installation callback: validates state and links the installation to the starting candidate. */
    @Transactional
    public void completeInstallation(long installationId, String state) {
        if (installationId <= 0 || state == null || state.isBlank()) throw new GithubConfigurationException("GITHUB_CALLBACK_INVALID", "GitHub installation callback is missing required parameters.");
        GithubOAuthStateRepository.State installState = states.consume(sha256Hex(state), GithubOAuthStateRepository.PURPOSE_INSTALL).orElseThrow(() -> new GithubConfigurationException("GITHUB_STATE_INVALID", "GitHub installation state is invalid or expired."));
        GithubAppClient.Installation installation;
        try {
            installation = appClient.installation(installationId);
        } catch (GithubClient.GithubException ex) {
            if (ex.status() == 404) throw new GithubConfigurationException("GITHUB_INSTALLATION_NOT_FOUND", "That GitHub App installation does not exist or was removed.");
            throw ex;
        }
        installations.upsertForCandidate(installState.userId(), installation);
        connections.updateStatus(installState.userId(), GithubConnectionRepository.STATUS_ACTIVE);
    }

    /** Explicit, server-computed connection status. Never inferred by the frontend. */
    public GithubDtos.ConnectionStatus status(UUID userId) {
        GithubConnectionRepository.Connection connection = connections.findActive(userId).orElse(null);
        if (connection == null) return new GithubDtos.ConnectionStatus(GithubDtos.STATUS_DISCONNECTED, false, null, null, null, null, null, null);
        GithubInstallationRepository.InstallationRow installation = installations.findActiveByCandidate(userId).orElse(null);
        String status = deriveStatus(userId, connection, installation);
        GithubDtos.InstallationView view = installation == null ? null : new GithubDtos.InstallationView(installation.installationId(), installation.accountId(), installation.accountLogin(), installation.accountType(), installation.repositorySelection(), installation.status());
        boolean connected = GithubDtos.STATUS_CONNECTED.equals(status);
        return new GithubDtos.ConnectionStatus(status, connected, connection.login(), connection.githubUserId(), connection.scopes(), connection.connectedAt(), connection.lastValidatedAt(), view);
    }

    private String deriveStatus(UUID userId, GithubConnectionRepository.Connection connection, GithubInstallationRepository.InstallationRow installation) {
        if (installation == null) {
            boolean removed = installations.listByCandidate(userId).stream().anyMatch(item -> GithubInstallationRepository.STATUS_REMOVED.equals(item.status()) || GithubInstallationRepository.STATUS_SUSPENDED.equals(item.status()));
            return removed ? GithubDtos.STATUS_INSTALLATION_REMOVED : GithubDtos.STATUS_INSTALLATION_MISSING;
        }
        if (connection.tokenExpiresAt() != null && Instant.now().isAfter(connection.tokenExpiresAt().minus(TOKEN_REFRESH_MARGIN))) {
            if (refreshIfPossible(userId, connection)) return GithubDtos.STATUS_CONNECTED;
            connections.updateStatus(userId, GithubConnectionRepository.STATUS_REAUTH_REQUIRED);
            return GithubDtos.STATUS_REAUTH_REQUIRED;
        }
        if (GithubConnectionRepository.STATUS_TOKEN_INVALID.equals(connection.status())) return GithubDtos.STATUS_TOKEN_INVALID;
        return GithubDtos.STATUS_CONNECTED;
    }

    private boolean refreshIfPossible(UUID userId, GithubConnectionRepository.Connection connection) {
        if (connection.encryptedRefreshToken() == null || connection.encryptedRefreshToken().isBlank()) return false;
        try {
            GithubClient.OAuthToken refreshed = github.refreshToken(cipher.decrypt(connection.encryptedRefreshToken()));
            connections.updateTokens(userId, cipher.encrypt(refreshed.value()), refreshed.refreshToken() == null ? null : cipher.encrypt(refreshed.refreshToken()), refreshed.expiresAt());
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** User access token for permitted user-to-server operations, refreshed transparently when expiring. */
    public String accessToken(UUID userId) {
        GithubConnectionRepository.Connection connection = connections.findActive(userId).orElseThrow(() -> new GithubConfigurationException("GITHUB_NOT_CONNECTED", "Connect GitHub before starting analysis."));
        if (connection.tokenExpiresAt() != null && Instant.now().isAfter(connection.tokenExpiresAt().minus(TOKEN_REFRESH_MARGIN))) {
            if (!refreshIfPossible(userId, connection)) {
                connections.updateStatus(userId, GithubConnectionRepository.STATUS_REAUTH_REQUIRED);
                throw new GithubConfigurationException("GITHUB_REAUTH_REQUIRED", "The GitHub session expired. Reconnect GitHub to continue.");
            }
            connection = connections.findActive(userId).orElseThrow(() -> new GithubConfigurationException("GITHUB_NOT_CONNECTED", "Connect GitHub before starting analysis."));
        }
        return cipher.decrypt(connection.encryptedAccessToken());
    }

    /** Lists only repositories available through the candidate's active GitHub App installation. */
    public List<GithubDtos.RepositorySummary> repositories(UUID userId) {
        GithubConnectionRepository.Connection connection = connections.findActive(userId).orElseThrow(() -> new GithubConfigurationException("GITHUB_NOT_CONNECTED", "Connect GitHub before listing repositories."));
        GithubInstallationRepository.InstallationRow installation = installations.findActiveByCandidate(userId).orElse(null);
        if (installation == null) {
            boolean removed = installations.listByCandidate(userId).stream().anyMatch(item -> GithubInstallationRepository.STATUS_REMOVED.equals(item.status()) || GithubInstallationRepository.STATUS_SUSPENDED.equals(item.status()));
            if (removed) {
                connections.updateStatus(userId, GithubConnectionRepository.STATUS_INSTALLATION_REMOVED);
                throw new GithubConfigurationException("GITHUB_INSTALLATION_REMOVED", "The SkillLink GitHub App installation was removed. Reinstall it to list repositories.");
            }
            throw new GithubConfigurationException("GITHUB_INSTALLATION_MISSING", "Install the SkillLink GitHub App and grant repository access first.");
        }
        List<GithubAppClient.InstallationRepository> repositories;
        try {
            repositories = appClient.installationRepositories(installation.installationId());
        } catch (GithubClient.GithubException ex) {
            if (ex.status() == 404) {
                installations.updateStatus(installation.installationId(), GithubInstallationRepository.STATUS_REMOVED);
                connections.updateStatus(userId, GithubConnectionRepository.STATUS_INSTALLATION_REMOVED);
                throw new GithubConfigurationException("GITHUB_INSTALLATION_REMOVED", "The SkillLink GitHub App installation was removed. Reinstall it to list repositories.");
            }
            if (ex.status() == 401 || ex.status() == 403) {
                connections.updateStatus(userId, GithubConnectionRepository.STATUS_TOKEN_INVALID);
                throw new GithubConfigurationException("GITHUB_TOKEN_INVALID", "GitHub rejected SkillLink's access. Reconnect GitHub or review the app installation.");
            }
            throw ex;
        }
        connections.updateStatus(userId, GithubConnectionRepository.STATUS_ACTIVE);
        return repositories.stream().map(item -> new GithubDtos.RepositorySummary(item.id(), item.name(), item.fullName(), item.owner(), item.privateRepository(), item.privateRepository() ? "PRIVATE" : "PUBLIC", item.defaultBranch(), item.primaryLanguage(), parseInstant(item.updatedAt()), item.sizeKb(), item.description(), installation.accountLogin())).toList();
    }

    /** Selection stays explicit: only a repository currently authorized through the installation can be selected. */
    public GithubDtos.ProjectSelectionResponse selectRepository(UUID userId, String githubRepositoryId) {
        GithubDtos.RepositorySummary repository = repositories(userId).stream().filter(item -> item.id().equals(githubRepositoryId)).findFirst().orElseThrow(() -> new GithubConfigurationException("REPOSITORY_NOT_AUTHORIZED", "That repository is not available through the GitHub App installation."));
        return projects.selectGithubRepository(userId, repository);
    }

    /**
     * Disconnects the integration: revokes the stored user grant where applicable, removes the
     * local connection and installation associations, and stops further repository access.
     * Historical evidence is intentionally preserved; deleting it is a separate explicit action.
     */
    @Transactional
    public void disconnect(UUID userId) {
        GithubConnectionRepository.Connection connection = connections.findActive(userId).orElse(null);
        if (connection == null) return;
        for (GithubInstallationRepository.InstallationRow installation : installations.listByCandidate(userId)) appClient.evictInstallationToken(installation.installationId());
        try {
            github.revokeUserGrant(cipher.decrypt(connection.encryptedAccessToken()));
        } catch (RuntimeException ignored) {
            // Revocation is best-effort; local credentials are removed regardless.
        }
        installations.deleteForCandidate(userId);
        connections.disconnect(userId);
    }

    public String redirectSuccess() { return frontendSuccessUrl; }
    public String redirectFailure(String code) { return UriComponentsBuilder.fromUriString(frontendFailureUrl).replaceQueryParam("code", safeError(code)).build().toUriString(); }

    private String safeError(String code) { return code == null ? "GITHUB_AUTH_FAILED" : code.replaceAll("[^A-Za-z0-9_\\-]", ""); }
    private String randomUrlToken(int bytes) { byte[] value = new byte[bytes]; random.nextBytes(value); return base64Url(value); }
    private String base64Url(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private byte[] sha256(String value) { try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private String sha256Hex(String value) { byte[] bytes = sha256(value); StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format("%02x", b)); return out.toString(); }
    private Instant parseInstant(String value) { try { return value == null ? null : Instant.parse(value); } catch (Exception ignored) { return null; } }
    public static class GithubConfigurationException extends RuntimeException { private final String code; public GithubConfigurationException(String code, String message) { super(message); this.code = code; } public String code() { return code; } }
}
