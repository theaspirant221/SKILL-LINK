package com.skilllink.api.analysis;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Separate component to ensure @Async proxy is applied (avoid self-invocation).
 * Checkpoint E requires READY -> enqueue deterministic -> RUNNING -> ... -> COMPLETE via analysisExecutor.
 */
@Component
public class DeterministicAnalysisAsyncRunner {

    private final DeterministicAnalysisService analysisService;

    public DeterministicAnalysisAsyncRunner(DeterministicAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @Async("analysisExecutor")
    public CompletableFuture<Void> runAsync(UUID runId) {
        try {
            analysisService.run(runId);
        } catch (Exception ignored) {
            // run() already marks FAILED internally
        }
        return CompletableFuture.completedFuture(null);
    }
}
