package com.skilllink.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checkpoint B: real authentication verification over actual HTTP against a real PostgreSQL
 * instance (Flyway-migrated). Covers registration, login, JWT access, /me, refresh rotation,
 * RBAC, and logout/revocation. Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthenticationFlowIntegrationTest {
    private static final String PASSWORD = "correct-horse-battery";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;

    private static String uniqueEmail() { return "it-" + UUID.randomUUID() + "@example.com"; }

    private static HttpEntity<String> jsonBody(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private static ResponseEntity<JsonNode> postJson(String path, String body) { return rest.postForEntity(path, jsonBody(body), JsonNode.class); }

    private static HttpEntity<Void> bearerEntity(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }

    private static HttpEntity<Void> refreshCookieEntity(String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "skilllink_refresh=" + refreshToken);
        return new HttpEntity<>(headers);
    }

    private static String refreshSetCookie(ResponseEntity<?> response) {
        for (String header : response.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, java.util.List.of())) {
            if (header.startsWith("skilllink_refresh=")) return header;
        }
        return null;
    }

    private static String refreshCookieValue(String setCookie) {
        return setCookie.substring("skilllink_refresh=".length(), setCookie.indexOf(';'));
    }

    private static String register(String email) {
        ResponseEntity<JsonNode> response = postJson("/api/v1/auth/register",
            "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"displayName\":\"It Candidate\"}");
        assertEquals(HttpStatus.OK, response.getStatusCode(), "registration should succeed: " + response.getBody());
        return response.getBody().path("accessToken").asText();
    }

    @Test
    void registrationCreatesCandidateSessionAndJwtAccess() {
        String email = uniqueEmail();
        ResponseEntity<JsonNode> response = postJson("/api/v1/auth/register",
            "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"displayName\":\"It Candidate\"}");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String accessToken = response.getBody().path("accessToken").asText();
        assertFalse(accessToken.isBlank(), "an access token must be issued");
        assertEquals(3, accessToken.split("\\.").length, "the access token must be a compact JWT");
        assertTrue(response.getBody().path("accessTokenExpiresInSeconds").asLong() > 0);
        assertEquals("CANDIDATE", response.getBody().path("user").path("role").asText());
        assertEquals(email, response.getBody().path("user").path("email").asText());

        String setCookie = refreshSetCookie(response);
        assertNotNull(setCookie, "a refresh session cookie must be issued");
        assertTrue(setCookie.contains("HttpOnly"), "the refresh cookie must be HttpOnly: " + setCookie);
        assertTrue(setCookie.contains("Path=/api/v1/auth"), "the refresh cookie must be scoped to the auth path: " + setCookie);
        assertTrue(setCookie.contains("SameSite=Lax"), "unexpected SameSite policy: " + setCookie);

        ResponseEntity<JsonNode> me = rest.exchange("/api/v1/auth/me", HttpMethod.GET, bearerEntity(accessToken), JsonNode.class);
        assertEquals(HttpStatus.OK, me.getStatusCode());
        assertEquals(email, me.getBody().path("email").asText());
        assertEquals("CANDIDATE", me.getBody().path("role").asText());
        assertFalse(me.getBody().has("password"), "identity responses must never contain password material");
    }

    @Test
    void registrationIgnoresClientSuppliedRole() {
        String email = uniqueEmail();
        ResponseEntity<JsonNode> response = postJson("/api/v1/auth/register",
            "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"displayName\":\"It Candidate\",\"role\":\"RECRUITER\"}");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("CANDIDATE", response.getBody().path("user").path("role").asText(), "self-registration must never grant the recruiter role");

        ResponseEntity<JsonNode> forbidden = rest.exchange("/api/v1/recruiter/jobs", HttpMethod.GET,
            bearerEntity(response.getBody().path("accessToken").asText()), JsonNode.class);
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
        assertEquals("FORBIDDEN", forbidden.getBody().path("code").asText());
    }

    @Test
    void duplicateRegistrationIsRejectedWithStructuredError() {
        String email = uniqueEmail();
        assertEquals(HttpStatus.OK, postJson("/api/v1/auth/register",
            "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"displayName\":\"It Candidate\"}").getStatusCode());

        ResponseEntity<JsonNode> duplicate = postJson("/api/v1/auth/register",
            "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"displayName\":\"It Candidate Again\"}");
        assertEquals(HttpStatus.BAD_REQUEST, duplicate.getStatusCode());
        assertEquals("EMAIL_ALREADY_REGISTERED", duplicate.getBody().path("code").asText());
        assertFalse(duplicate.getBody().path("requestId").asText().isBlank());
        assertTrue(duplicate.getBody().path("details").isObject());
    }

    @Test
    void weakPasswordAndMalformedBodyReturnStructuredErrors() {
        ResponseEntity<JsonNode> weak = postJson("/api/v1/auth/register",
            "{\"email\":\"" + uniqueEmail() + "\",\"password\":\"short\",\"displayName\":\"It Candidate\"}");
        assertEquals(HttpStatus.BAD_REQUEST, weak.getStatusCode());
        assertEquals("VALIDATION_FAILED", weak.getBody().path("code").asText());
        assertTrue(weak.getBody().path("details").path("fields").toString().contains("password"));

        ResponseEntity<JsonNode> malformed = postJson("/api/v1/auth/register", "{not-valid-json");
        assertEquals(HttpStatus.BAD_REQUEST, malformed.getStatusCode());
        assertEquals("MALFORMED_REQUEST", malformed.getBody().path("code").asText());
    }

    @Test
    void loginIssuesSessionAndRejectsWrongPassword() {
        String email = uniqueEmail();
        register(email);

        ResponseEntity<JsonNode> wrongPassword = postJson("/api/v1/auth/login",
            "{\"email\":\"" + email + "\",\"password\":\"definitely-not-the-password\"}");
        assertEquals(HttpStatus.UNAUTHORIZED, wrongPassword.getStatusCode());
        assertEquals("INVALID_CREDENTIALS", wrongPassword.getBody().path("code").asText());

        ResponseEntity<JsonNode> login = postJson("/api/v1/auth/login",
            "{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"" + PASSWORD + "\"}");
        assertEquals(HttpStatus.OK, login.getStatusCode(), "login should tolerate email case: " + login.getBody());
        assertEquals("CANDIDATE", login.getBody().path("user").path("role").asText());
        String accessToken = login.getBody().path("accessToken").asText();
        assertFalse(accessToken.isBlank());
        assertNotNull(refreshSetCookie(login), "login must issue a refresh session cookie");

        ResponseEntity<JsonNode> me = rest.exchange("/api/v1/auth/me", HttpMethod.GET, bearerEntity(accessToken), JsonNode.class);
        assertEquals(HttpStatus.OK, me.getStatusCode());
        assertEquals(email, me.getBody().path("email").asText());
    }

    @Test
    void meRequiresAValidJwt() {
        ResponseEntity<JsonNode> anonymous = rest.exchange("/api/v1/auth/me", HttpMethod.GET, new HttpEntity<Void>(new HttpHeaders()), JsonNode.class);
        assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());
        assertEquals("UNAUTHENTICATED", anonymous.getBody().path("code").asText());

        HttpHeaders garbage = new HttpHeaders();
        garbage.setBearerAuth("this-is-not-a-jwt");
        ResponseEntity<JsonNode> invalid = rest.exchange("/api/v1/auth/me", HttpMethod.GET, new HttpEntity<Void>(garbage), JsonNode.class);
        assertEquals(HttpStatus.UNAUTHORIZED, invalid.getStatusCode());
        assertEquals("UNAUTHENTICATED", invalid.getBody().path("code").asText());
    }

    @Test
    void refreshRotatesSessionAndRevokesThePreviousToken() {
        String email = uniqueEmail();
        ResponseEntity<JsonNode> registration = postJson("/api/v1/auth/register",
            "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"displayName\":\"It Candidate\"}");
        String firstRefresh = refreshCookieValue(refreshSetCookie(registration));

        ResponseEntity<JsonNode> rotation = rest.exchange("/api/v1/auth/refresh", HttpMethod.POST, refreshCookieEntity(firstRefresh), JsonNode.class);
        assertEquals(HttpStatus.OK, rotation.getStatusCode(), "refresh should rotate: " + rotation.getBody());
        String rotatedAccess = rotation.getBody().path("accessToken").asText();
        assertFalse(rotatedAccess.isBlank());
        assertNotEquals(registration.getBody().path("accessToken").asText(), rotatedAccess, "rotation must issue a fresh access token");
        String rotatedCookie = refreshSetCookie(rotation);
        assertNotNull(rotatedCookie, "rotation must issue a fresh refresh cookie");
        String secondRefresh = refreshCookieValue(rotatedCookie);
        assertNotEquals(firstRefresh, secondRefresh, "rotation must replace the refresh token");

        Integer revokedRows = jdbc.queryForObject("SELECT count(*) FROM auth_refresh_token WHERE token_hash = ? AND revoked_at IS NOT NULL", Integer.class, AuthService.sha256(firstRefresh));
        assertEquals(1, revokedRows, "the superseded refresh token must be revoked in the database");

        ResponseEntity<JsonNode> reuse = rest.exchange("/api/v1/auth/refresh", HttpMethod.POST, refreshCookieEntity(firstRefresh), JsonNode.class);
        assertEquals(HttpStatus.UNAUTHORIZED, reuse.getStatusCode(), "a rotated refresh token must not be reusable");
        assertEquals("INVALID_REFRESH_TOKEN", reuse.getBody().path("code").asText());

        ResponseEntity<JsonNode> chain = rest.exchange("/api/v1/auth/refresh", HttpMethod.POST, refreshCookieEntity(secondRefresh), JsonNode.class);
        assertEquals(HttpStatus.OK, chain.getStatusCode(), "the rotated refresh token must remain usable");
    }

    @Test
    void logoutRevokesTheRefreshSession() {
        String email = uniqueEmail();
        ResponseEntity<JsonNode> registration = postJson("/api/v1/auth/register",
            "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"displayName\":\"It Candidate\"}");
        String refreshToken = refreshCookieValue(refreshSetCookie(registration));

        ResponseEntity<JsonNode> logout = rest.exchange("/api/v1/auth/logout", HttpMethod.POST, refreshCookieEntity(refreshToken), JsonNode.class);
        assertEquals(HttpStatus.OK, logout.getStatusCode());
        assertEquals("Signed out.", logout.getBody().path("message").asText());
        String expiredCookie = refreshSetCookie(logout);
        assertNotNull(expiredCookie, "logout must expire the refresh cookie");
        assertTrue(expiredCookie.contains("Max-Age=0"), "logout must clear the cookie: " + expiredCookie);

        ResponseEntity<JsonNode> afterLogout = rest.exchange("/api/v1/auth/refresh", HttpMethod.POST, refreshCookieEntity(refreshToken), JsonNode.class);
        assertEquals(HttpStatus.UNAUTHORIZED, afterLogout.getStatusCode());
        assertEquals("INVALID_REFRESH_TOKEN", afterLogout.getBody().path("code").asText());
    }

    @Test
    void rbacSeparatesCandidateAndRecruiterSurfaces() {
        String candidateToken = register(uniqueEmail());
        ResponseEntity<JsonNode> candidateOnRecruiter = rest.exchange("/api/v1/recruiter/jobs", HttpMethod.GET, bearerEntity(candidateToken), JsonNode.class);
        assertEquals(HttpStatus.FORBIDDEN, candidateOnRecruiter.getStatusCode());

        // Recruiters are provisioned server-side; self-registration cannot create them.
        String recruiterEmail = uniqueEmail();
        jdbc.update("INSERT INTO app_user(email, password_hash, display_name, role) VALUES (?, ?, ?, 'RECRUITER')",
            recruiterEmail, passwordEncoder.encode(PASSWORD), "It Recruiter");

        ResponseEntity<JsonNode> login = postJson("/api/v1/auth/login",
            "{\"email\":\"" + recruiterEmail + "\",\"password\":\"" + PASSWORD + "\"}");
        assertEquals(HttpStatus.OK, login.getStatusCode());
        assertEquals("RECRUITER", login.getBody().path("user").path("role").asText());
        String recruiterToken = login.getBody().path("accessToken").asText();

        ResponseEntity<JsonNode> recruiterSurface = rest.exchange("/api/v1/recruiter/jobs", HttpMethod.GET, bearerEntity(recruiterToken), JsonNode.class);
        assertEquals(HttpStatus.OK, recruiterSurface.getStatusCode());
        assertTrue(recruiterSurface.getBody().isArray(), "an empty recruiter job list is a valid response");

        ResponseEntity<JsonNode> recruiterOnCandidate = rest.exchange("/api/v1/candidates/me/skills", HttpMethod.GET, bearerEntity(recruiterToken), JsonNode.class);
        assertEquals(HttpStatus.FORBIDDEN, recruiterOnCandidate.getStatusCode());
        assertEquals("FORBIDDEN", recruiterOnCandidate.getBody().path("code").asText());
    }

    @Test
    void unknownRoutesAndUnsupportedMethodsReturnStructuredErrors() {
        String token = register(uniqueEmail());

        ResponseEntity<JsonNode> unknown = rest.exchange("/api/v1/definitely-not-a-route", HttpMethod.GET, bearerEntity(token), JsonNode.class);
        assertEquals(HttpStatus.NOT_FOUND, unknown.getStatusCode());
        assertEquals("NOT_FOUND", unknown.getBody().path("code").asText());

        ResponseEntity<JsonNode> method = rest.exchange("/api/v1/auth/me", HttpMethod.DELETE, bearerEntity(token), JsonNode.class);
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, method.getStatusCode());
        assertEquals("METHOD_NOT_ALLOWED", method.getBody().path("code").asText());
    }
}
