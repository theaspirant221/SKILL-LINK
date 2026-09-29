package com.skilllink.api.evidence;

import java.time.Instant;
import java.util.UUID;

public final class EvidenceDtos {
    private EvidenceDtos() {}
    public record EvidenceResponse(UUID evidenceId, UUID skillId, String skillName, String projectName, String repositoryFullName, UUID snapshotId, String commitSha, String sourceType, String sourceLocation, String observation, String evidenceStrength, String verificationMethod, String status, String visibility, Instant observedAt, String sourceHash, String independentSignal) {}
    public record SkillResponse(UUID skillId, String key, String name, String category, String status, String freshnessState, Instant lastVerifiedAt, Instant latestEvidenceAt, int evidenceCount) {}
    public record DisputeRequest(String reason) {}
    public record VisibilityRequest(String visibility) {}
}
