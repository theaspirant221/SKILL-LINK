package com.skilllink.api.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey signingKey;
    private final Duration accessTokenTtl;

    public JwtService(@Value("${skilllink.security.jwt-secret}") String base64Secret,
                      @Value("${skilllink.security.access-token-minutes:15}") long accessTokenMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.accessTokenTtl = Duration.ofMinutes(accessTokenMinutes);
    }

    public String issueAccessToken(SkillLinkPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(principal.id().toString())
            .claim("email", principal.email())
            .claim("displayName", principal.displayName())
            .claim("role", principal.role())
            .claim("type", "access")
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessTokenTtl)))
            .signWith(signingKey)
            .compact();
    }

    public SkillLinkPrincipal parseAccessToken(String token) {
        Jws<Claims> parsed = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token);
        Claims claims = parsed.getPayload();
        if (!"access".equals(claims.get("type", String.class))) throw new JwtException("not an access token");
        return new SkillLinkPrincipal(UUID.fromString(claims.getSubject()), claims.get("email", String.class), claims.get("displayName", String.class), claims.get("role", String.class));
    }

    public Duration accessTokenTtl() { return accessTokenTtl; }
}
