package com.skilllink.api.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class GithubClient {
    private final GithubProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient rest;

    public GithubClient(GithubProperties properties, ObjectMapper objectMapper) { this.properties = properties; this.objectMapper = objectMapper; this.rest = RestClient.create(); }

    public OAuthToken exchangeCode(String code, String verifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId()); form.add("client_secret", properties.clientSecret()); form.add("code", code); form.add("redirect_uri", properties.redirectUri()); form.add("code_verifier", verifier);
        JsonNode json = rest.post().uri(properties.oauthUrl() + "/access_token").contentType(MediaType.APPLICATION_FORM_URLENCODED).accept(MediaType.APPLICATION_JSON).body(form).exchange((request, response) -> readResponse(response));
        if (json.path("access_token").isMissingNode()) throw new GithubException("GITHUB_TOKEN_EXCHANGE_FAILED", "GitHub authorization could not be completed.", 502);
        return new OAuthToken(json.path("access_token").asText(), json.path("scope").asText(""), json.path("expires_in").isNumber() ? Instant.now().plusSeconds(json.path("expires_in").asLong()) : null);
    }

    public GithubUser currentUser(String token) { JsonNode json = get("/user", token); return new GithubUser(json.path("id").asLong(), json.path("login").asText(), json.path("name").asText(json.path("login").asText())); }

    public List<GithubDtos.RepositorySummary> repositories(String token) {
        URI uri = UriComponentsBuilder.fromUriString(properties.apiUrl() + "/user/repos").queryParam("per_page", 100).queryParam("sort", "updated").queryParam("affiliation", "owner,collaborator,organization_member").build().toUri();
        JsonNode json = get(uri.toString(), token);
        List<GithubDtos.RepositorySummary> result = new ArrayList<>();
        if (!json.isArray()) return result;
        for (JsonNode node : json) {
            result.add(new GithubDtos.RepositorySummary(node.path("id").asText(), node.path("name").asText(), node.path("full_name").asText(), node.path("owner").path("login").asText(), node.path("private").asBoolean(), node.path("private").asBoolean() ? "PRIVATE" : "PUBLIC", node.path("default_branch").asText("main"), node.path("language").isNull() ? null : node.path("language").asText(), parseInstant(node.path("updated_at").asText(null)), node.path("size").asLong(0), node.path("description").isNull() ? null : node.path("description").asText()));
        }
        return result;
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
    private Instant parseInstant(String value) { try { return value == null ? null : Instant.parse(value); } catch (Exception ignored) { return null; } }
    public record OAuthToken(String value, String scope, Instant expiresAt) {}
    public record GithubUser(long id, String login, String name) {}
    public static class GithubException extends RuntimeException { private final String code; private final int status; public GithubException(String code, String message, int status) { super(message); this.code = code; this.status = status; } public String code() { return code; } public int status() { return status; } }
}
