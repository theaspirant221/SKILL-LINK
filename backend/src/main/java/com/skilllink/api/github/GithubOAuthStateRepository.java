package com.skilllink.api.github;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class GithubOAuthStateRepository {
    private final JdbcTemplate jdbc;
    public GithubOAuthStateRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void create(UUID userId, String stateHash, String encryptedVerifier, String redirectUri, Instant expiresAt) {
        jdbc.update("INSERT INTO github_oauth_state(user_id, state_hash, encrypted_code_verifier, redirect_uri, expires_at) VALUES (?, ?, ?, ?, ?)", userId, stateHash, encryptedVerifier, redirectUri, java.sql.Timestamp.from(expiresAt));
    }
    @Transactional
    public Optional<State> consume(String stateHash) {
        try {
            State state = jdbc.queryForObject("SELECT id, user_id, encrypted_code_verifier, redirect_uri, expires_at FROM github_oauth_state WHERE state_hash = ? AND consumed_at IS NULL AND expires_at > now() FOR UPDATE", (rs, rowNum) -> new State(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getString("encrypted_code_verifier"), rs.getString("redirect_uri"), rs.getObject("expires_at", java.time.OffsetDateTime.class).toInstant()), stateHash);
            jdbc.update("UPDATE github_oauth_state SET consumed_at = now() WHERE id = ?", state.id());
            return Optional.of(state);
        } catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
    }
    public record State(UUID id, UUID userId, String encryptedCodeVerifier, String redirectUri, Instant expiresAt) {}
}
