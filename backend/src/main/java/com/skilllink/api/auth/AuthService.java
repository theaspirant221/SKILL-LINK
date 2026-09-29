package com.skilllink.api.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Duration refreshTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository users, JdbcTemplate jdbc, PasswordEncoder passwordEncoder, JwtService jwtService,
                       @org.springframework.beans.factory.annotation.Value("${skilllink.security.refresh-token-days:30}") long refreshDays) {
        this.users = users;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTtl = Duration.ofDays(refreshDays);
    }

    @Transactional
    public AuthResult register(AuthDtos.RegisterRequest request, String userAgent, String ipAddress) {
        String email = normalizeEmail(request.email());
        String role = "CANDIDATE";
        if (users.findByEmail(email).isPresent()) throw new AuthException("EMAIL_ALREADY_REGISTERED", "An account with that email already exists.");
        try {
            UserRepository.AppUser user = users.create(email, passwordEncoder.encode(request.password().trim()), request.displayName().trim(), role);
            return issue(user, userAgent, ipAddress);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("EMAIL_ALREADY_REGISTERED", "An account with that email already exists.");
        }
    }

    @Transactional
    public AuthResult login(AuthDtos.LoginRequest request, String userAgent, String ipAddress) {
        UserRepository.AppUser user = users.findByEmail(normalizeEmail(request.email())).orElseThrow(() -> new AuthException("INVALID_CREDENTIALS", "Email or password is incorrect."));
        if (user.passwordHash() == null || !passwordEncoder.matches(request.password(), user.passwordHash())) throw new AuthException("INVALID_CREDENTIALS", "Email or password is incorrect.");
        return issue(user, userAgent, ipAddress);
    }

    @Transactional
    public AuthResult refresh(String rawRefreshToken, String userAgent, String ipAddress) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) throw new AuthException("REFRESH_TOKEN_REQUIRED", "A refresh session is required.");
        String hash = sha256(rawRefreshToken);
        RefreshRow token;
        try {
            token = jdbc.queryForObject("""
                SELECT rt.id, rt.user_id, rt.expires_at, u.email, u.display_name, u.role
                FROM auth_refresh_token rt JOIN app_user u ON u.id = rt.user_id
                WHERE rt.token_hash = ? AND rt.revoked_at IS NULL AND rt.expires_at > now() AND u.deleted_at IS NULL
                """, (rs, rowNum) -> new RefreshRow(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getObject("expires_at", java.time.OffsetDateTime.class), rs.getString("email"), rs.getString("display_name"), rs.getString("role")), hash);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw new AuthException("INVALID_REFRESH_TOKEN", "Refresh session is invalid or expired.");
        }
        UserRepository.AppUser user = new UserRepository.AppUser(token.userId(), token.email(), null, token.displayName(), token.role());
        AuthResult next = issue(user, userAgent, ipAddress);
        jdbc.update("UPDATE auth_refresh_token SET revoked_at = now(), replaced_by = (SELECT id FROM auth_refresh_token WHERE token_hash = ?) WHERE id = ?", sha256(next.refreshToken()), token.id());
        return next;
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) jdbc.update("UPDATE auth_refresh_token SET revoked_at = coalesce(revoked_at, now()) WHERE token_hash = ?", sha256(rawRefreshToken));
    }

    private AuthResult issue(UserRepository.AppUser user, String userAgent, String ipAddress) {
        SkillLinkPrincipal principal = new SkillLinkPrincipal(user.id(), user.email(), user.displayName(), user.role());
        String access = jwtService.issueAccessToken(principal);
        String refresh = randomToken();
        jdbc.update("INSERT INTO auth_refresh_token(user_id, token_hash, expires_at, user_agent, ip_address) VALUES (?, ?, ?, ?, ?::inet)", user.id(), sha256(refresh), java.sql.Timestamp.from(Instant.now().plus(refreshTtl)), truncate(userAgent, 500), validIpOrNull(ipAddress));
        return new AuthResult(access, refresh, user);
    }

    private String validIpOrNull(String ip) { if (ip == null || ip.isBlank()) return null; String sanitized = ip.replaceAll("[^0-9a-fA-F:.]", ""); return sanitized.isBlank() ? null : sanitized.substring(0, Math.min(45, sanitized.length())); }
    private String randomToken() { byte[] bytes = new byte[48]; secureRandom.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    static String sha256(String value) { try { return HexFormatHolder.hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); } }
    private String normalizeEmail(String email) { return email.trim().toLowerCase(java.util.Locale.ROOT); }
    private String truncate(String value, int max) { return value == null ? null : value.substring(0, Math.min(value.length(), max)); }

    public record AuthResult(String accessToken, String refreshToken, UserRepository.AppUser user) {}
    private record RefreshRow(UUID id, UUID userId, java.time.OffsetDateTime expiresAt, String email, String displayName, String role) {}
    static final class HexFormatHolder { static String hex(byte[] bytes) { StringBuilder out = new StringBuilder(bytes.length * 2); for (byte b : bytes) out.append(String.format("%02x", b)); return out.toString(); } }
    public static class AuthException extends RuntimeException { private final String code; public AuthException(String code, String message) { super(message); this.code = code; } public String code() { return code; } }
}
