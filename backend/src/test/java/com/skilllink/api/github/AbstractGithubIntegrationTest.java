package com.skilllink.api.github;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Shared harness for GitHub App integration tests: real HTTP, real Spring Security chain,
 * Flyway-migrated PostgreSQL via Testcontainers, with the GitHub HTTP clients replaced by
 * Mockito mocks. No real GitHub access is required.
 *
 * The Spring context is cached across the GitHub integration test classes, so the database
 * container is a JVM-wide singleton: a per-class container lifecycle would leave the shared
 * context pointing at a stopped database. Redirect-asserting calls use a client that does not
 * follow redirects, so no test ever contacts github.com.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class AbstractGithubIntegrationTest {
    protected static final String PASSWORD = "correct-horse-battery";
    protected static final String ACCESS_TOKEN = "gho_it_user_access_token";
    protected static final String REFRESH_TOKEN = "ghr_it_refresh_token";
    protected static final long ACCOUNT_ID = 5001;
    protected static final String ACCOUNT_LOGIN = "acme-org";

    /** Started once per test JVM and intentionally never stopped between classes. */
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    static {
        if (DockerClientFactory.instance().isDockerAvailable()) postgres.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired protected TestRestTemplate rest;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected TokenCipher cipher;
    @Autowired protected GithubOAuthStateRepository states;
    @Autowired protected GithubInstallationRepository installations;
    @MockBean protected GithubClient github;
    @MockBean protected GithubAppClient appClient;

    /** The random port of the running app; avoids the redirect-following TestRestTemplate for redirect assertions. */
    @Value("${local.server.port}") protected int port;
    @Value("${skilllink.github.webhook-secret}")
    protected String webhookSecret;

    private final List<String> registeredEmails = new ArrayList<>();

    /** Client that never follows redirects; used for every redirect-contract assertion. */
    private final RestTemplate noRedirects = new RestTemplate(new SimpleClientHttpRequestFactory() {
        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
            super.prepareConnection(connection, httpMethod);
            connection.setInstanceFollowRedirects(false);
        }
    });

    protected static String uniqueEmail() { return "it-" + UUID.randomUUID() + "@example.com"; }

    protected String registerCandidate() {
        String email = uniqueEmail();
        registeredEmails.add(email);
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/v1/auth/register", jsonBody(
            "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"displayName\":\"It Candidate\"}"), JsonNode.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), "registration should succeed: " + response.getBody());
        return response.getBody().path("accessToken").asText();
    }

    protected String registerRecruiter() {
        String email = uniqueEmail();
        registeredEmails.add(email);
        jdbc.update("INSERT INTO app_user(email, password_hash, display_name, role) VALUES (?, 'x', 'It Recruiter', 'RECRUITER')", email);
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/v1/auth/login", jsonBody(
            "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"), JsonNode.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), "recruiter login should succeed: " + response.getBody());
        return response.getBody().path("accessToken").asText();
    }

    /** Tests create global GitHub entities (unique github user / installation ids), so their SkillLink users are removed afterwards. */
    @AfterEach
    void removeTestUsers() {
        for (String email : registeredEmails) jdbc.update("DELETE FROM app_user WHERE email = ?", email);
        registeredEmails.clear();
    }

    protected UUID candidateId(String email) { return jdbc.queryForObject("SELECT id FROM app_user WHERE email = ?", UUID.class, email); }

    /** Starts user authorization and returns the raw state from the GitHub redirect. */
    protected String startUserAuth(String accessToken) {
        ResponseEntity<String> response = getWithoutRedirects("/api/v1/github/connect", bearer(accessToken));
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        return queryParam(location(response), "state");
    }

    /** Completes user authorization with a unique mocked GitHub user; asserts the redirect. */
    protected void completeUserAuth(String accessToken, String state, String code) {
        long githubUserId = 90_000 + System.nanoTime() % 1_000_000;
        when(github.exchangeCode(eq(code), any())).thenReturn(new GithubClient.OAuthToken(ACCESS_TOKEN, "", Instant.now().plusSeconds(28800), REFRESH_TOKEN));
        when(github.currentUser(ACCESS_TOKEN)).thenReturn(new GithubClient.GithubUser(githubUserId, "it-candidate-" + githubUserId, "It Candidate"));
        ResponseEntity<String> response = getWithoutRedirects("/api/v1/github/callback?code=" + code + "&state=" + state, new HttpEntity<Void>(new HttpHeaders()));
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
    }

    /** Starts the installation flow and returns the raw state from the GitHub install redirect. */
    protected String startInstall(String accessToken) {
        ResponseEntity<String> response = getWithoutRedirects("/api/v1/github/install", bearer(accessToken));
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        return queryParam(location(response), "state");
    }

    /** Completes the installation callback with a mocked installation lookup. */
    protected String completeInstall(String accessToken, String state, long installationId) {
        when(appClient.installation(installationId)).thenReturn(new GithubAppClient.Installation(installationId, ACCOUNT_ID, ACCOUNT_LOGIN, "Organization", "SELECTED"));
        ResponseEntity<String> response = getWithoutRedirects("/api/v1/github/install/callback?installation_id=" + installationId + "&state=" + state, new HttpEntity<Void>(new HttpHeaders()));
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        return location(response);
    }

    /** Fully connects a candidate with a unique mocked GitHub user and installation; returns the installation id. */
    protected long connectCandidate(String accessToken) {
        String state = startUserAuth(accessToken);
        completeUserAuth(accessToken, state, "auth-code-" + UUID.randomUUID());
        long installationId = 42_000_000 + System.nanoTime() % 1_000_000;
        String installState = startInstall(accessToken);
        completeInstall(accessToken, installState, installationId);
        return installationId;
    }

    protected GithubDtos.ConnectionStatus status(String accessToken) {
        ResponseEntity<JsonNode> response = rest.exchange("/api/v1/github/status", HttpMethod.GET, bearer(accessToken), JsonNode.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode body = response.getBody();
        return new GithubDtos.ConnectionStatus(body.path("status").asText(), body.path("connected").asBoolean(), null, null, null, null, null, null);
    }

    protected HttpEntity<Void> bearer(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }

    protected HttpEntity<String> jsonBody(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    protected ResponseEntity<JsonNode> postWebhook(String event, String deliveryId, String body, String signatureSecret) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-GitHub-Event", event);
        headers.set("X-GitHub-Delivery", deliveryId);
        headers.set("X-Hub-Signature-256", GithubWebhookSignatures.sign(body.getBytes(StandardCharsets.UTF_8), signatureSecret));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("/api/v1/github/webhooks", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }

    /** GET that never follows the redirect, keeping the Location header observable. */
    protected ResponseEntity<String> getWithoutRedirects(String path, HttpEntity<Void> entity) {
        return noRedirects.exchange(URI.create("http://localhost:" + port + path), HttpMethod.GET, entity, String.class);
    }

    protected static String location(ResponseEntity<String> response) { return response.getHeaders().getFirst(HttpHeaders.LOCATION); }

    protected static String queryParam(String url, String name) {
        for (String pair : url.substring(url.indexOf('?') + 1).split("&")) {
            String[] entry = pair.split("=", 2);
            if (entry.length == 2 && entry[0].equals(name)) return entry[1];
        }
        throw new AssertionError("query parameter " + name + " missing from " + url);
    }

    protected static String sha256Hex(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
