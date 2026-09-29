package com.skilllink.api.verification;

import java.time.Instant;
import java.util.UUID;

public final class VerificationDtos {
    private VerificationDtos() {}
    public record VerificationResponse(UUID verificationId, UUID skillId, String skillName, String status, String policyKey, String policyVersion, String explanation, int evidenceCount, boolean defensePassed, boolean practicalPassed, Instant evaluatedAt) {}
}
