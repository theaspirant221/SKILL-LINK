package com.skilllink.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Duration;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}
    public record RegisterRequest(@Email @NotBlank String email, @Size(min = 12, max = 128) String password, @NotBlank @Size(max = 120) String displayName) {}
    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    public record UserResponse(UUID id, String email, String displayName, String role) {
        static UserResponse from(UserRepository.AppUser user) { return new UserResponse(user.id(), user.email(), user.displayName(), user.role()); }
    }
    public record AuthResponse(String accessToken, long accessTokenExpiresInSeconds, UserResponse user) {}
    public record RefreshRequest(String refreshToken) {}
    public record MessageResponse(String message) {}
}
