package com.skilllink.api.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

/**
 * User-authorization and repository-content operations against GitHub. The user access token
 * (and refresh token, when GitHub issues one) are treated as opaque secrets: they are exchanged
 * server-side, stored encrypted, and never sent to the browser.
 */
@Component
public class GithubClient {
    private final GithubProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient rest;

    public GithubClient(GithubProperties properties, ObjectMapper objectMapper) { this.properties = properties; this.objectMapper = objectMapper; this.rest = RestClient.create(); }

    public record OAuthToken(String value, String scope, Instant expiresAt, String refreshToken) {}

    /** Exchanges the authorization code together with the PKCE code verifier, server-side only. */
    public OAuthToken exchangeCode(String code, String verifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId()); form.add("client_secret", properties.clientSecret()); form.add("code", code); form.add("redirect_uri", properties.callbackUrl()); form.add("code_verifier", verifier);
        JsonNode json = rest.post().uri(properties.oauthUrl() + "/access_token").contentType(MediaType.APPLICATION_FORM_URLENCODED).accept(MediaType.APPLICATION_JSON).body(form).exchange((request, response) -> readResponse(response));
        if (json.path("access_token").isMissingNode()) throw new GithubException("GITHUB_TOKEN_EXCHANGE_FAILED", "GitHub authorization could not be completed.", 502);
        return new OAuthToken(json.path("access_token").asText(), json.path("scope").asText(""), json.path("expires_in").isNumber() ? Instant.now().plusSeconds(json.path("expires_in").asLong()) : null, json.path("refresh_token").isMissingNode() ? null : json.path("refresh_token").asText());
    }

    /** Refreshes an expiring user access token using GitHub's refresh-token grant, when enabled for the app. */
    public OAuthToken refreshToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId()); form.add("client_secret", properties.clientSecret()); form.add("grant_type", "refresh_token"); form.add("refresh_token", refreshToken);
        JsonNode json = rest.post().uri(properties.oauthUrl() + "/access_token").contentType(MediaType.APPLICATION_FORM_URLENCODED).accept(MediaType.APPLICATION_JSON).body(form).exchange((request, response) -> readResponse(response));
        if (json.path("access_token").isMissingNode()) throw new GithubException("GITHUB_REFRESH_FAILED", "The GitHub session could not be refreshed.", 502);
        return new OAuthToken(json.path("access_token").asText(), json.path("scope").asText(""), json.path("expires_in").isNumber() ? Instant.now().plusSeconds(json.path("expires_in").asLong()) : null, json.path("refresh_token").isMissingNode() ? null : json.path("refresh_token").asText());
    }

    public GithubUser currentUser(String token) { JsonNode json = get("/user", token); return new GithubUser(json.path("id").asLong(), json.path("login").asText(), json.path("name").asText(json.path("login").asText())); }

    /** Revokes the user OAuth grant identified by the token. Best-effort during disconnect. */
    public void revokeUserGrant(String token) {
        rest.method(HttpMethod.DELETE).uri(properties.apiUrl() + "/applications/" + properties.clientId() + "/grant")
            .headers(headers -> headers.setBasicAuth(properties.clientId(), properties.clientSecret()))
            .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
            .body(Map.of("access_token", token))
            .exchange((request, response) -> { if (!response.getStatusCode().is2xxSuccessful()) throw new GithubException("GITHUB_REVOKE_FAILED", "GitHub did not revoke the authorization.", response.getStatusCode().value()); return Void.TYPE; });
    }

    public JsonNode commit(String fullName, String ref, String token) { return get("/repos/" + safePath(fullName) + "/commits/" + safePath(ref), token); }
    public JsonNode tree(String fullName, String treeSha, String token) { return get("/repos/" + safePath(fullName) + "/git/trees/" + safePath(treeSha) + "?recursive=1", token); }
    public String blob(String fullName, String blobSha, String token) { return get("/repos/" + safePath(fullName) + "/git/blobs/" + safePath(blobSha), token).path("content").asText("").replace("\n", ""); }

    private JsonNode get(String path, String token) {
        String uri = path.startsWith("http") ? path : properties.apiUrl() + path;
        return rest.get().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).header("X-GitHub-Api-Version", properties.apiVersion()).accept(MediaType.APPLICATION_JSON).exchange((request, response) -> readResponse(response));
    }

    private JsonNode readResponse(org.springframework.http.client.ClientHttpResponse response) throws java.io.IOException {
        if (!response.getStatusCode().is2xxSuccessful()) throw new GithubException("GITHUB_API_ERROR", "GitHub API request failed.", response.getStatusCode().value());
        return objectMapper.readTree(response.getBody());
    }
    private String safePath(String value) { return value.replace("\\", "").replace("..", ""); }
    public record GithubUser(long id, String login, String name) {}
    public static class GithubException extends RuntimeException { private final String code; private final int status; public GithubException(String code, String message, int status) { super(message); this.code = code; this.status = status; } public String code() { return code; } public int status() { return status; } }
}
