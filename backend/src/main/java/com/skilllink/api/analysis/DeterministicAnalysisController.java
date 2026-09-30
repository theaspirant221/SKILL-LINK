package com.skilllink.api.analysis;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/snapshots/{snapshotId}/analysis")
@PreAuthorize("hasRole('CANDIDATE')")
public class DeterministicAnalysisController {
    private final DeterministicAnalysisService service;

    public DeterministicAnalysisController(DeterministicAnalysisService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DeterministicAnalysisDtos.AnalysisRunResponse> createAnalysis(
        @AuthenticationPrincipal(expression = "id") UUID candidateId,
        @PathVariable UUID projectId,
        @PathVariable UUID snapshotId
    ) {
        var run = service.createRun(candidateId, projectId, snapshotId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(run);
    }

    @GetMapping
    public ResponseEntity<List<DeterministicAnalysisDtos.AnalysisRunResponse>> listAnalysis(
        @AuthenticationPrincipal(expression = "id") UUID candidateId,
        @PathVariable UUID projectId,
        @PathVariable UUID snapshotId
    ) {
        var runs = service.listRuns(candidateId, projectId, snapshotId);
        return ResponseEntity.ok(runs);
    }

    @GetMapping("/{runId}")
    public ResponseEntity<DeterministicAnalysisDtos.AnalysisRunResponse> getAnalysis(
        @AuthenticationPrincipal(expression = "id") UUID candidateId,
        @PathVariable UUID projectId,
        @PathVariable UUID snapshotId,
        @PathVariable UUID runId
    ) {
        var run = service.getRun(candidateId, projectId, snapshotId, runId);
        return ResponseEntity.ok(run);
    }

    @GetMapping("/{runId}/detail")
    public ResponseEntity<DeterministicAnalysisDtos.AnalysisDetailResponse> getAnalysisDetail(
        @AuthenticationPrincipal(expression = "id") UUID candidateId,
        @PathVariable UUID projectId,
        @PathVariable UUID snapshotId,
        @PathVariable UUID runId
    ) {
        var detail = service.getRunDetail(candidateId, projectId, snapshotId, runId);
        return ResponseEntity.ok(detail);
    }

    @GetMapping("/{runId}/observations")
    public ResponseEntity<List<DeterministicAnalysisDtos.ObservationResponse>> getObservations(
        @AuthenticationPrincipal(expression = "id") UUID candidateId,
        @PathVariable UUID projectId,
        @PathVariable UUID snapshotId,
        @PathVariable UUID runId
    ) {
        var detail = service.getRunDetail(candidateId, projectId, snapshotId, runId);
        return ResponseEntity.ok(detail.observations());
    }
}
