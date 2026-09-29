package com.skilllink.api.auth;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public AppUser create(String email, String passwordHash, String displayName, String role) {
        UUID id = jdbc.queryForObject("""
            INSERT INTO app_user(email, password_hash, display_name, role, email_verified_at)
            VALUES (?, ?, ?, ?, now()) RETURNING id
            """, UUID.class, email, passwordHash, displayName, role);
        return findById(id).orElseThrow();
    }

    public Optional<AppUser> findByEmail(String email) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT id, email, password_hash, display_name, role
                FROM app_user WHERE lower(email) = lower(?) AND deleted_at IS NULL
                """, (rs, rowNum) -> new AppUser(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash"), rs.getString("display_name"), rs.getString("role")), email));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<AppUser> findById(UUID id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT id, email, password_hash, display_name, role
                FROM app_user WHERE id = ? AND deleted_at IS NULL
                """, (rs, rowNum) -> new AppUser(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash"), rs.getString("display_name"), rs.getString("role")), id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public record AppUser(UUID id, String email, String passwordHash, String displayName, String role) {}
}
