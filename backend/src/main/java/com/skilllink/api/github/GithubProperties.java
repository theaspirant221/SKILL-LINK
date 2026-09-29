package com.skilllink.api.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "skilllink.github")
public record GithubProperties(
    String clientId,
    String clientSecret,
    String redirectUri,
    String apiUrl,
    String oauthUrl,
    String oauthScopes,
    String apiVersion
) {
    public String[] scopeValues() { return oauthScopes == null ? new String[0] : oauthScopes.trim().split("\\s+"); }
    public boolean configured() { return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank(); }
}
