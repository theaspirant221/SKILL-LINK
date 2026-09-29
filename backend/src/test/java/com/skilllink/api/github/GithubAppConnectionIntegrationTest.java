package com.skilllink.api.github;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Checkpoint C: real GitHub App connection over real HTTP. Installation and user authorization
 * are separate concepts; state is single-use and bound to the starting candidate; PKCE (S256)
 * protects the code exchange; tokens are persisted encrypted only; disconnect revokes and stops
 * access without deleting historical projects.
 */
class GithubAppConnectionIntegrationTest extends AbstractGithubIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired GithubOAuthService oauthService;

    @Test
    void connectRedirectIncludesStateAndPkceS256AndNeverTheClientSecret() {
        String token = registerCandidate();
        org.springframework.http.ResponseEntity<String> response = rest.exchange("/api/v1/github/connect", HttpMethod.GET, bearer(token), String.class);
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
        assertTrue(location.startsWith("https://github.com/login/oauth/authorize"), "authorize URL expected: " + location);
        assertTrue(location.contains("code_challenge_method=S256"), "PKCE S256 required: " + location);
        assertTrue(location.contains("code_challenge="), "PKCE challenge required: " + location);
        assertFalse(location.contains("secret"), "the client secret must never appear in a redirect: " + location);
        assertNotNull(queryParam(location, "state"));
    }

    @Test
    void installationFlowSeparatesIdentityFromRepositoryAccess() {
        String token = registerCandidate();
        String state = startUserAuth(token);
        completeUserAuth(token, state, "auth-code-a");

        // User is authorized (identity known) but has not installed the app yet.
        GithubDtos.ConnectionStatus afterAuth = status(token);
        assertEquals(GithubDtos.STATUS_INSTALLATION_MISSING, afterAuth.status());
        assertFalse(afterAuth.connected());

        org.springframework.http.ResponseEntity<String> install = rest.exchange("/api/v1/github/install", HttpMethod.GET, bearer(token), String.class);
        assertEquals(HttpStatus.FOUND, install.getStatusCode());
        assertTrue(install.getHeaders().getFirst(HttpHeaders.LOCATION).contains("github.com/apps/skilllink-test-app/installations/new"), "install redirect expected");

        completeInstall(token, queryParam(install.getHeaders().getFirst(HttpHeaders.LOCATION), "state"), 42_0002L);
        GithubDtos.ConnectionStatus connected = status(token);
        assertEquals(GithubDtos.STATUS_CONNECTED, connected.status());
        assertTrue(connected.connected());
    }

    @Test
    void invalidStateIsRejected() {
        String token = registerCandidate();
        org.springframework.http.ResponseEntity<String> response = rest.exchange("/api/v1/github/callback?code=abc&state=tampered-state", HttpMethod.GET, new HttpEntity<Void>(new HttpHeaders()), String.class);
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.LOCATION).contains("code=GITHUB_STATE_INVALID"), "state mismatch must abort the flow");
    }

    @Test
    void expiredStateIsRejected() {
        String token = registerCandidate();
        UUID candidateId = currentCandidateId(token);
        String state = "expired-state-value";
        states.create(candidateId, GithubOAuthStateRepository.PURPOSE_USER_AUTH, sha256Hex(state), cipher.encrypt("verifier"), "http://localhost:8080/api/v1/github/callback", Instant.now().minusSeconds(60));
        org.springframework.http.ResponseEntity<String> response = rest.exchange("/api/v1/github/callback?code=abc&state=" + state, HttpMethod.GET, new HttpEntity<Void>(new HttpHeaders()), String.class);
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.LOCATION).contains("code=GITHUB_STATE_INVALID"), "expired state must abort the flow");
    }

    @Test
    void stateReplayIsRejected() {
        String token = registerCandidate();
        String state = startUserAuth(token);
        completeUserAuth(token, state, "auth-code-once");

        when(github.exchangeCode(eq("auth-code-once"), any())).thenReturn(new GithubClient.OAuthToken("gho_second", "", Instant.now().plusSeconds(28800), null));
        org.springframework.http.ResponseEntity<String> replay = rest.exchange("/api/v1/github/callback?code=auth-code-once&state=" + state, HttpMethod.GET, new HttpEntity<Void>(new HttpHeaders()), String.class);
        assertEquals(HttpStatus.FOUND, replay.getStatusCode());
        assertTrue(replay.getHeaders().getFirst(HttpHeaders.LOCATION).contains("code=GITHUB_STATE_INVALID"), "a consumed state must never be reused");
    }

    @Test
    void invalidAuthorizationCodeSurfacesStructuredFailure() {
        String token = registerCandidate();
        String state = startUserAuth(token);
        when(github.exchangeCode(eq("bad-code"), any())).thenThrow(new GithubClient.GithubException("GITHUB_TOKEN_EXCHANGE_FAILED", "GitHub authorization could not be completed.", 502));
        org.springframework.http.ResponseEntity<String> response = rest.exchange("/api/v1/github/callback?code=bad-code&state=" + state, HttpMethod.GET, new HttpEntity<Void>(new HttpHeaders()), String.class);
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.LOCATION).contains("code=GITHUB_TOKEN_EXCHANGE_FAILED"));
    }

    @Test
    void installationStateCannotBeUsedForUserAuthorization() {
        String token = registerCandidate();
        String installState = startInstall(token);
        org.springframework.http.ResponseEntity<String> response = rest.exchange("/api/v1/github/callback?code=abc&state=" + installState, HttpMethod.GET, new HttpEntity<Void>(new HttpHeaders()), String.class);
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.LOCATION).contains("code=GITHUB_STATE_INVALID"), "install state must not complete user authorization");
    }

    @Test
    void missingInstallationBlocksRepositoryListing() {
        String token = registerCandidate();
        String state = startUserAuth(token);
        completeUserAuth(token, state, "auth-code-b");
        org.springframework.http.ResponseEntity<JsonNode> response = rest.exchange("/api/v1/github/repositories", HttpMethod.GET, bearer(token), JsonNode.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("GITHUB_INSTALLATION_MISSING", response.getBody().path("code").asText());
    }

    @Test
    void removedInstallationStopsRepositoryListing() {
        String token = registerCandidate();
        connectCandidate(token);
        installations.updateStatus(42_0001L, GithubInstallationRepository.STATUS_REMOVED);
        org.springframework.http.ResponseEntity<JsonNode> response = rest.exchange("/api/v1/github/repositories", HttpMethod.GET, bearer(token), JsonNode.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("GITHUB_INSTALLATION_REMOVED", response.getBody().path("code").asText());
        assertEquals(GithubDtos.STATUS_INSTALLATION_REMOVED, status(token).status());
    }

    @Test
    void repositoriesListOnlyInstallationAuthorizedEntriesWithEncryptedPersistence() {
        String token = registerCandidate();
        connectCandidate(token);
        when(appClient.installationRepositories(42_0001L)).thenReturn(List.of(
            new GithubAppClient.InstallationRepository("7001", "foodbridge", "acme-org/foodbridge", "acme-org", true, "main", "Java", "2026-09-01T10:00:00Z", 4200, "Fixture repo"),
            new GithubAppClient.InstallationRepository("7002", "other-tool", "acme-org/other-tool", "acme-org", false, "main", "Python", "2026-09-02T10:00:00Z", 100, null)));

        org.springframework.http.ResponseEntity<JsonNode> response = rest.exchange("/api/v1/github/repositories", HttpMethod.GET, bearer(token), JsonNode.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode repositories = response.getBody();
        assertEquals(2, repositories.size());
        assertEquals("acme-org/foodbridge", repositories.get(0).path("fullName").asText());
        assertEquals("acme-org", repositories.get(0).path("accountLogin").asText());
        assertEquals("PRIVATE", repositories.get(0).path("visibility").asText());

        // Tokens must only ever exist in encrypted form.
        String stored = jdbc.queryForObject("SELECT encrypted_access_token FROM github_connection WHERE disconnected_at IS NULL", String.class);
        assertNotEquals(ACCESS_TOKEN, stored);
        assertEquals(ACCESS_TOKEN, cipher.decrypt(stored));
        String storedRefresh = jdbc.queryForObject("SELECT encrypted_refresh_token FROM github_connection WHERE disconnected_at IS NULL", String.class);
        assertNotEquals(REFRESH_TOKEN, storedRefresh);
        assertEquals(REFRESH_TOKEN, cipher.decrypt(storedRefresh));
    }

    @Test
    void githubUnauthorizedResponseSurfacesTokenInvalid() {
        String token = registerCandidate();
        connectCandidate(token);
        when(appClient.installationRepositories(42_0001L)).thenThrow(new GithubClient.GithubException("GITHUB_API_ERROR", "GitHub API request failed.", 401));
        org.springframework.http.ResponseEntity<JsonNode> response = rest.exchange("/api/v1/github/repositories", HttpMethod.GET, bearer(token), JsonNode.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("GITHUB_TOKEN_INVALID", response.getBody().path("code").asText());
        assertEquals(GithubDtos.STATUS_TOKEN_INVALID, status(token).status());
    }

    @Test
    void selectionStaysExplicitAndOnlyAllowsAuthorizedRepositories() {
        String token = registerCandidate();
        connectCandidate(token);
        when(appClient.installationRepositories(42_0001L)).thenReturn(List.of(
            new GithubAppClient.InstallationRepository("7001", "foodbridge", "acme-org/foodbridge", "acme-org", true, "main", "Java", "2026-09-01T10:00:00Z", 4200, "Fixture repo")));

        org.springframework.http.ResponseEntity<JsonNode> selected = rest.exchange("/api/v1/github/repositories/7001/select", HttpMethod.POST, bearer(token), JsonNode.class);
        assertEquals(HttpStatus.OK, selected.getStatusCode());
        assertEquals("acme-org/foodbridge", selected.getBody().path("repositoryFullName").asText());

        org.springframework.http.ResponseEntity<JsonNode> unauthorized = rest.exchange("/api/v1/github/repositories/9999/select", HttpMethod.POST, bearer(token), JsonNode.class);
        assertEquals(HttpStatus.BAD_REQUEST, unauthorized.getStatusCode());
        assertEquals("REPOSITORY_NOT_AUTHORIZED", unauthorized.getBody().path("code").asText());
    }

    @Test
    void installationCannotBeLinkedToTwoCandidates() {
        String tokenA = registerCandidate();
        connectCandidate(tokenA);
        String tokenB = registerCandidate();
        String installState = startInstall(tokenB);
        when(appClient.installation(42_0001L)).thenReturn(new GithubAppClient.Installation(42_0001L, 5001, "acme-org", "Organization", "SELECTED"));
        org.springframework.http.ResponseEntity<String> response = rest.exchange("/api/v1/github/install/callback?installation_id=420001&state=" + installState, HttpMethod.GET, new HttpEntity<Void>(new HttpHeaders()), String.class);
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.LOCATION).contains("code=INSTALLATION_ALREADY_LINKED"), "an installation belongs to one SkillLink candidate: " + response.getHeaders().getFirst(HttpHeaders.LOCATION));
    }

    @Test
    void disconnectRevokesStopsAccessAndKeepsHistoricalProjects() {
        String token = registerCandidate();
        connectCandidate(token);
        when(appClient.installationRepositories(42_0001L)).thenReturn(List.of(
            new GithubAppClient.InstallationRepository("7001", "foodbridge", "acme-org/foodbridge", "acme-org", true, "main", "Java", "2026-09-01T10:00:00Z", 4200, "Fixture repo")));
        org.springframework.http.ResponseEntity<JsonNode> selected = rest.exchange("/api/v1/github/repositories/7001/select", HttpMethod.POST, bearer(token), JsonNode.class);
        assertEquals(HttpStatus.OK, selected.getStatusCode());

        org.springframework.http.ResponseEntity<Void> disconnect = rest.exchange("/api/v1/github/connection", HttpMethod.DELETE, bearer(token), Void.class);
        assertEquals(HttpStatus.NO_CONTENT, disconnect.getStatusCode());
        verify(github).revokeUserGrant(ACCESS_TOKEN);

        assertEquals(GithubDtos.STATUS_DISCONNECTED, status(token).status());
        org.springframework.http.ResponseEntity<JsonNode> repositories = rest.exchange("/api/v1/github/repositories", HttpMethod.GET, bearer(token), JsonNode.class);
        assertEquals(HttpStatus.BAD_REQUEST, repositories.getStatusCode());
        assertEquals("GITHUB_NOT_CONNECTED", repositories.getBody().path("code").asText());

        // Historical proof references survive a disconnect; deleting evidence is a separate action.
        Integer projects = jdbc.queryForObject("SELECT count(*) FROM project", Integer.class);
        assertTrue(projects >= 1, "the selected project must still exist after disconnect");
        Integer installationsLeft = jdbc.queryForObject("SELECT count(*) FROM github_installation", Integer.class);
        assertEquals(0, installationsLeft);
    }

    @Test
    void serverSideAuthorizationScopesConnectionAccess() {
        String tokenA = registerCandidate();
        connectCandidate(tokenA);
        String tokenB = registerCandidate();
        String recruiterToken = registerRecruiter();

        // Candidate B sees only their own (disconnected) state and cannot see A's repositories.
        assertEquals(GithubDtos.STATUS_DISCONNECTED, status(tokenB).status());
        org.springframework.http.ResponseEntity<JsonNode> repositoriesB = rest.exchange("/api/v1/github/repositories", HttpMethod.GET, bearer(tokenB), JsonNode.class);
        assertEquals(HttpStatus.BAD_REQUEST, repositoriesB.getStatusCode());
        assertEquals("GITHUB_NOT_CONNECTED", repositoriesB.getBody().path("code").asText());

        // Recruiters cannot use candidate GitHub endpoints.
        org.springframework.http.ResponseEntity<JsonNode> recruiter = rest.exchange("/api/v1/github/status", HttpMethod.GET, bearer(recruiterToken), JsonNode.class);
        assertEquals(HttpStatus.FORBIDDEN, recruiter.getStatusCode());

        // Anonymous callers are rejected.
        org.springframework.http.ResponseEntity<JsonNode> anonymous = rest.exchange("/api/v1/github/status", HttpMethod.GET, new HttpEntity<Void>(new HttpHeaders()), JsonNode.class);
        assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());
    }

    @Test
    void userTokenRefreshKeepsAccessWithoutReauthorization() {
        String token = registerCandidate();
        String state = startUserAuth(token);
        // Expired token with a refresh token available.
        when(github.exchangeCode(any(), any())).thenReturn(new GithubClient.OAuthToken("gho_old", "", Instant.now().minusSeconds(60), REFRESH_TOKEN));
        when(github.currentUser("gho_old")).thenReturn(new GithubClient.GithubUser(9001, "it-candidate", "It Candidate"));
        org.springframework.http.ResponseEntity<String> callback = rest.exchange("/api/v1/github/callback?code=code-expiring&state=" + state, HttpMethod.GET, new HttpEntity<Void>(new HttpHeaders()), String.class);
        assertEquals(HttpStatus.FOUND, callback.getStatusCode());

        when(github.refreshToken(REFRESH_TOKEN)).thenReturn(new GithubClient.OAuthToken("gho_refreshed", "", Instant.now().plusSeconds(28800), null));
        String refreshed = oauthService.accessToken(currentCandidateId(token));
        assertEquals("gho_refreshed", refreshed);
        String stored = jdbc.queryForObject("SELECT encrypted_access_token FROM github_connection WHERE disconnected_at IS NULL", String.class);
        assertEquals("gho_refreshed", cipher.decrypt(stored));
    }

    private UUID currentCandidateId(String candidateToken) {
        org.springframework.http.ResponseEntity<JsonNode> me = rest.exchange("/api/v1/auth/me", HttpMethod.GET, bearer(candidateToken), JsonNode.class);
        return candidateId(me.getBody().path("email").asText());
    }
}
