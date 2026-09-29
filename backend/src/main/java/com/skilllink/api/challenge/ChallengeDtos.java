package com.skilllink.api.challenge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ChallengeDtos {
    private ChallengeDtos() {}
    public record CreateRequest(@NotNull UUID projectId, @NotNull UUID skillId) {}
    public record SubmissionRequest(@NotBlank String text) {}
    public record ReviewRequest(@NotBlank String outcome, @NotBlank String feedback) {}
    public record ChallengeResponse(UUID challengeId, UUID projectId, UUID skillId, String skillName, String title, String brief, List<String> acceptanceCriteria, String executionMode, String policyVersion, UUID latestSubmissionId, String latestSubmissionStatus, String feedback, Instant submittedAt) {}
}
