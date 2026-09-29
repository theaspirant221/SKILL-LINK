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

    public Optional<Connection> findActive(UUID userId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT id, user_id, github_user_id, github_login, encrypted_access_token, scopes, connected_at
                FROM github_connection WHERE user_id = ? AND disconnected_at IS NULL
                """, (rs, rowNum) -> new Connection(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getLong("github_user_id"), rs.getString("github_login"), rs.getString("encrypted_access_token"), rs.getString("scopes"), rs.getObject("connected_at", java.time.OffsetDateTime.class).toInstant()), userId));
        } catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
    }

    public void upsert(UUID userId, long githubUserId, String login, String encryptedToken, String scopes, Instant expiresAt) {
        jdbc.update("""
            INSERT INTO github_connection(user_id, github_user_id, github_login, encrypted_access_token, scopes, token_expires_at, connected_at, disconnected_at)
            VALUES (?, ?, ?, ?, ?, ?, now(), NULL)
            ON CONFLICT (user_id) DO UPDATE SET github_user_id = EXCLUDED.github_user_id, github_login = EXCLUDED.github_login,
              encrypted_access_token = EXCLUDED.encrypted_access_token, scopes = EXCLUDED.scopes, token_expires_at = EXCLUDED.token_expires_at,
              connected_at = now(), disconnected_at = NULL, updated_at = now()
            """, userId, githubUserId, login, encryptedToken, scopes == null ? "" : scopes, expiresAt == null ? null : java.sql.Timestamp.from(expiresAt));
    }

    public void disconnect(UUID userId) { jdbc.update("UPDATE github_connection SET disconnected_at = now(), encrypted_access_token = '', updated_at = now() WHERE user_id = ?", userId); }
    public void invalidate(UUID userId) { jdbc.update("UPDATE github_connection SET disconnected_at = now(), encrypted_access_token = '', updated_at = now() WHERE user_id = ?", userId); }
    public record Connection(UUID id, UUID userId, long githubUserId, String login, String encryptedAccessToken, String scopes, Instant connectedAt) {}
}
