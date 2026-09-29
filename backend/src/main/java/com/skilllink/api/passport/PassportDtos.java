package com.skilllink.api.passport;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PassportDtos {
    private PassportDtos() {}
    public record VisibilityRequest(@NotBlank String visibility) {}
    public record PassportItem(UUID skillId, String skillName, String category, String status, String policyVersion, String displaySummary, Instant verifiedAt) {}
    public record PassportResponse(UUID passportId, String publicIdentifier, UUID candidateId, String visibility, Instant issuedAt, Instant expiresAt, boolean revoked, List<PassportItem> items) {}
    public record PublicPassportResponse(String publicIdentifier, String candidateDisplayName, String visibility, Instant issuedAt, Instant expiresAt, List<PassportItem> items) {}
}
