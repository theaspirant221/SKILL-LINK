package com.skilllink.api.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final String REFRESH_COOKIE = "skilllink_refresh";
    private final AuthService auth;
    private final JwtService jwt;
    private final boolean secureCookies;

    public AuthController(AuthService auth, JwtService jwt, @Value("${skilllink.security.secure-cookies:false}") boolean secureCookies) { this.auth = auth; this.jwt = jwt; this.secureCookies = secureCookies; }

    @PostMapping("/register")
    public ResponseEntity<AuthDtos.AuthResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request, HttpServletRequest http, HttpServletResponse response) { return withSession(auth.register(request, http.getHeader("User-Agent"), http.getRemoteAddr()), response); }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.AuthResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request, HttpServletRequest http, HttpServletResponse response) { return withSession(auth.login(request, http.getHeader("User-Agent"), http.getRemoteAddr()), response); }

    @PostMapping("/refresh")
    public ResponseEntity<AuthDtos.AuthResponse> refresh(HttpServletRequest http, HttpServletResponse response) { return withSession(auth.refresh(readRefresh(http), http.getHeader("User-Agent"), http.getRemoteAddr()), response); }

    @PostMapping("/logout")
    public ResponseEntity<AuthDtos.MessageResponse> logout(HttpServletRequest http, HttpServletResponse response) { auth.logout(readRefresh(http)); response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie().toString()); return ResponseEntity.ok(new AuthDtos.MessageResponse("Signed out.")); }

    @GetMapping("/me")
    public AuthDtos.UserResponse me(@AuthenticationPrincipal SkillLinkPrincipal principal) { return new AuthDtos.UserResponse(principal.id(), principal.email(), principal.displayName(), principal.role()); }

    private ResponseEntity<AuthDtos.AuthResponse> withSession(AuthService.AuthResult result, HttpServletResponse response) { response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString()); return ResponseEntity.ok(new AuthDtos.AuthResponse(result.accessToken(), jwt.accessTokenTtl().toSeconds(), AuthDtos.UserResponse.from(result.user()))); }
    private String readRefresh(HttpServletRequest request) { if (request.getCookies() == null) return null; for (Cookie cookie : request.getCookies()) if (REFRESH_COOKIE.equals(cookie.getName())) return cookie.getValue(); return null; }
    private ResponseCookie refreshCookie(String token) { return ResponseCookie.from(REFRESH_COOKIE, token).httpOnly(true).secure(secureCookies).sameSite("Lax").path("/api/v1/auth").maxAge(Duration.ofDays(30)).build(); }
    private ResponseCookie expiredCookie() { return ResponseCookie.from(REFRESH_COOKIE, "").httpOnly(true).secure(secureCookies).sameSite("Lax").path("/api/v1/auth").maxAge(Duration.ZERO).build(); }
}
