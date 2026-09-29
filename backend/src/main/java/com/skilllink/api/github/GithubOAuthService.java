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

@Service
public class GithubOAuthService {
    private final GithubProperties properties;
    private final GithubClient github;
    private final GithubConnectionRepository connections;
    private final GithubOAuthStateRepository states;
    private final TokenCipher cipher;
    private final com.skilllink.api.project.ProjectService projects;
    private final String frontendSuccessUrl;
    private final String frontendFailureUrl;
    private final SecureRandom random = new SecureRandom();

    public GithubOAuthService(GithubProperties properties, GithubClient github, GithubConnectionRepository connections, GithubOAuthStateRepository states, TokenCipher cipher, com.skilllink.api.project.ProjectService projects,
                              @Value("${skilllink.frontend-success-url:http://localhost:5173/app/projects}") String frontendSuccessUrl,
                              @Value("${skilllink.frontend-failure-url:http://localhost:5173/app/projects?github=error}") String frontendFailureUrl) {
        this.properties = properties; this.github = github; this.connections = connections; this.states = states; this.cipher = cipher; this.projects = projects; this.frontendSuccessUrl = frontendSuccessUrl; this.frontendFailureUrl = frontendFailureUrl;
    }

    public String begin(UUID userId) {
        if (!properties.configured()) throw new GithubConfigurationException("GITHUB_NOT_CONFIGURED", "GitHub OAuth is not configured on this environment.");
        String state = randomUrlToken(32);
        String verifier = randomUrlToken(64);
        String challenge = base64Url(sha256(verifier));
        states.create(userId, sha256Hex(state), cipher.encrypt(verifier), properties.redirectUri(), Instant.now().plus(Duration.ofMinutes(10)));
        return UriComponentsBuilder.fromUriString(properties.oauthUrl() + "/authorize")
            .queryParam("client_id", properties.clientId())
            .queryParam("redirect_uri", properties.redirectUri())
            .queryParam("scope", String.join(" ", properties.scopeValues()))
            .queryParam("state", state)
            .queryParam("code_challenge", challenge)
            .queryParam("code_challenge_method", "S256")
            .queryParam("allow_signup", "false")
            .build().encode().toUriString();
    }

    @Transactional
    public void complete(String code, String state) {
        if (code == null || code.isBlank() || state == null || state.isBlank()) throw new GithubConfigurationException("GITHUB_CALLBACK_INVALID", "GitHub callback is missing required parameters.");
        GithubOAuthStateRepository.State oauthState = states.consume(sha256Hex(state)).orElseThrow(() -> new GithubConfigurationException("GITHUB_STATE_INVALID", "GitHub authorization state is invalid or expired."));
        GithubClient.OAuthToken token = github.exchangeCode(code, cipher.decrypt(oauthState.encryptedCodeVerifier()));
        GithubClient.GithubUser user = github.currentUser(token.value());
        connections.upsert(oauthState.userId(), user.id(), user.login(), cipher.encrypt(token.value()), token.scope(), token.expiresAt());
    }

    public String accessToken(UUID userId) {
        GithubConnectionRepository.Connection connection = connections.findActive(userId).orElseThrow(() -> new GithubConfigurationException("GITHUB_NOT_CONNECTED", "Connect GitHub before starting analysis."));
        return cipher.decrypt(connection.encryptedAccessToken());
    }

    public GithubDtos.ConnectionStatus status(UUID userId) {
        return connections.findActive(userId).map(item -> new GithubDtos.ConnectionStatus(true, item.login(), item.scopes(), item.connectedAt())).orElseGet(() -> new GithubDtos.ConnectionStatus(false, null, null, null));
    }

    public List<GithubDtos.RepositorySummary> repositories(UUID userId) {
        GithubConnectionRepository.Connection connection = connections.findActive(userId).orElseThrow(() -> new GithubConfigurationException("GITHUB_NOT_CONNECTED", "Connect GitHub before listing repositories."));
        try { return github.repositories(cipher.decrypt(connection.encryptedAccessToken())); }
        catch (GithubClient.GithubException ex) { if (ex.status() == 401 || ex.status() == 403) connections.invalidate(userId); throw ex; }
    }

    public GithubDtos.ProjectSelectionResponse selectRepository(UUID userId, String githubRepositoryId) {
        connections.findActive(userId).orElseThrow(() -> new GithubConfigurationException("GITHUB_NOT_CONNECTED", "Connect GitHub before selecting a repository."));
        GithubDtos.RepositorySummary repository = repositories(userId).stream().filter(item -> item.id().equals(githubRepositoryId)).findFirst().orElseThrow(() -> new GithubConfigurationException("REPOSITORY_NOT_AUTHORIZED", "That repository is not available to this account."));
        return projects.selectGithubRepository(userId, repository);
    }

    @Transactional
    public void disconnect(UUID userId) { connections.disconnect(userId); }

    public String redirectSuccess() { return frontendSuccessUrl; }
    public String redirectFailure(String code) { return UriComponentsBuilder.fromUriString(frontendFailureUrl).replaceQueryParam("code", safeError(code)).build().toUriString(); }

    private String safeError(String code) { return code == null ? "GITHUB_AUTH_FAILED" : code.replaceAll("[^A-Za-z0-9_\\-]", ""); }
    private String randomUrlToken(int bytes) { byte[] value = new byte[bytes]; random.nextBytes(value); return base64Url(value); }
    private String base64Url(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private byte[] sha256(String value) { try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private String sha256Hex(String value) { byte[] bytes = sha256(value); StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format("%02x", b)); return out.toString(); }
    public static class GithubConfigurationException extends RuntimeException { private final String code; public GithubConfigurationException(String code, String message) { super(message); this.code = code; } public String code() { return code; } }
}
