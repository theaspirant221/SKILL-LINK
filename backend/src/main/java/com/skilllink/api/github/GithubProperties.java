package com.skilllink.api.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GitHub App configuration. SkillLink integrates as a GitHub App (not a classic OAuth app):
 * the installation controls repository access (Contents/Metadata read-only), while user
 * authorization identifies the GitHub user. Secrets are provided through the environment and
 * must never be committed.
 */
@ConfigurationProperties(prefix = "skilllink.github")
public record GithubProperties(
    String appId,
    String clientId,
    String clientSecret,
    String privateKey,
    String appName,
    String callbackUrl,
    String setupUrl,
    String webhookSecret,
    String apiUrl,
    String oauthUrl,
    String apiVersion
) {
    /** User authorization requires the app's OAuth client credentials. */
    public boolean userFlowConfigured() { return notBlank(clientId) && notBlank(clientSecret); }
    /** Server-to-server app operations require the app id and its private key. */
    public boolean appFlowConfigured() { return notBlank(appId) && notBlank(privateKey) && notBlank(clientId); }
    /** Webhook processing requires a shared secret; unsigned webhooks are never trusted. */
    public boolean webhookConfigured() { return notBlank(webhookSecret); }
    /** Install/setup URL redirect target, derived from the app name. */
    public String installUrl() { return "https://github.com/apps/" + appName + "/installations/new"; }
    private static boolean notBlank(String value) { return value != null && !value.isBlank(); }
}
