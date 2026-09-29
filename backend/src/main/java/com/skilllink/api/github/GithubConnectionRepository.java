package com.skilllink.api.github;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class GithubConnectionRepository {
    private final JdbcTemplate jdbc;
    public GithubConnectionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REAUTH_REQUIRED = "REAUTH_REQUIRED";
    public static final String STATUS_INSTALLATION_REMOVED = "INSTALLATION_REMOVED";
    public static final String STATUS_TOKEN_INVALID = "TOKEN_INVALID";

    public Optional<Connection> findActive(UUID userId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT id, user_id, github_user_id, github_login, encrypted_access_token, encrypted_refresh_token, scopes, token_expires_at, connected_at, last_validated_at, status
                FROM github_connection WHERE user_id = ? AND disconnected_at IS NULL
                """, (rs, rowNum) -> new Connection(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getLong("github_user_id"), rs.getString("github_login"), rs.getString("encrypted_access_token"), rs.getString("encrypted_refresh_token"), rs.getString("scopes"), rs.getObject("token_expires_at", java.time.OffsetDateTime.class) == null ? null : rs.getObject("token_expires_at", java.time.OffsetDateTime.class).toInstant(), rs.getObject("connected_at", java.time.OffsetDateTime.class).toInstant(), rs.getObject("last_validated_at", java.time.OffsetDateTime.class) == null ? null : rs.getObject("last_validated_at", java.time.OffsetDateTime.class).toInstant(), rs.getString("status")), userId));
        } catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
    }

    public void upsert(UUID userId, long githubUserId, String login, String encryptedToken, String encryptedRefreshToken, String scopes, Instant expiresAt) {
        jdbc.update("""
            INSERT INTO github_connection(user_id, github_user_id, github_login, encrypted_access_token, encrypted_refresh_token, scopes, token_expires_at, status, connected_at, disconnected_at, last_validated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', now(), NULL, now())
            ON CONFLICT (user_id) DO UPDATE SET github_user_id = EXCLUDED.github_user_id, github_login = EXCLUDED.github_login,
              encrypted_access_token = EXCLUDED.encrypted_access_token, encrypted_refresh_token = EXCLUDED.encrypted_refresh_token,
              scopes = EXCLUDED.scopes, token_expires_at = EXCLUDED.token_expires_at, status = 'ACTIVE',
              connected_at = now(), disconnected_at = NULL, last_validated_at = now(), updated_at = now()
            """, userId, githubUserId, login, encryptedToken, encryptedRefreshToken, scopes == null ? "" : scopes, expiresAt == null ? null : java.sql.Timestamp.from(expiresAt));
    }

    public void updateTokens(UUID userId, String encryptedToken, String encryptedRefreshToken, Instant expiresAt) {
        jdbc.update("UPDATE github_connection SET encrypted_access_token = ?, encrypted_refresh_token = ?, token_expires_at = ?, status = 'ACTIVE', last_validated_at = now(), updated_at = now() WHERE user_id = ? AND disconnected_at IS NULL", encryptedToken, encryptedRefreshToken, expiresAt == null ? null : java.sql.Timestamp.from(expiresAt), userId);
    }

    public void updateStatus(UUID userId, String status) {
        jdbc.update("UPDATE github_connection SET status = ?, last_validated_at = now(), updated_at = now() WHERE user_id = ? AND disconnected_at IS NULL", status, userId);
    }

    public void disconnect(UUID userId) { jdbc.update("UPDATE github_connection SET disconnected_at = now(), encrypted_access_token = '', encrypted_refresh_token = NULL, status = NULL, updated_at = now() WHERE user_id = ?", userId); }
    public void invalidate(UUID userId) { jdbc.update("UPDATE github_connection SET disconnected_at = now(), encrypted_access_token = '', encrypted_refresh_token = NULL, status = NULL, updated_at = now() WHERE user_id = ?", userId); }

    public record Connection(UUID id, UUID userId, long githubUserId, String login, String encryptedAccessToken, String encryptedRefreshToken, String scopes, Instant tokenExpiresAt, Instant connectedAt, Instant lastValidatedAt, String status) {}
}
