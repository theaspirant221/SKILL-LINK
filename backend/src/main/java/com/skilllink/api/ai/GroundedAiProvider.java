package com.skilllink.api.ai;

import java.util.List;
import java.util.Map;

/**
 * Optional interpretation boundary. Implementations may explain only the
 * supplied references; they cannot write candidate_skill or verification_result.
 */
public interface GroundedAiProvider {
    GroundedAnalysis analyze(GroundedContext context);

    record GroundedContext(String projectId, String snapshotId, String commitSha, Map<String, Object> deterministicFacts, List<SourceReference> references) {}
    record SourceReference(String sourceType, String location, String sourceHash, String observation, String strength) {}
    record GroundedAnalysis(String provider, String model, String promptVersion, String summary, List<GroundedObservation> observations, List<String> groundingWarnings) {}
    record GroundedObservation(String skillKey, String observation, List<SourceReference> references) {}
}
