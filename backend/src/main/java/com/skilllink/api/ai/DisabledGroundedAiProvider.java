package com.skilllink.api.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "skilllink.ai.provider", havingValue = "disabled", matchIfMissing = true)
public class DisabledGroundedAiProvider implements GroundedAiProvider {
    @Override
    public GroundedAnalysis analyze(GroundedContext context) {
        return new GroundedAnalysis("disabled", "none", "grounded-ai.v1", "Optional AI interpretation is disabled; deterministic facts remain authoritative.", List.of(), List.of("AI provider disabled in this environment."));
    }
}
