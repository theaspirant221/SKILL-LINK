package com.skilllink.api.snapshot;

import com.skilllink.api.auth.SkillLinkPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/snapshots")
@PreAuthorize("hasRole('CANDIDATE')")
public class SnapshotController {
    private final SnapshotService service;

    public SnapshotController(SnapshotService service) {
        this.service = service;
    }

    @PostMapping
    public SnapshotDtos.SnapshotResponse createSnapshot(
        @AuthenticationPrincipal SkillLinkPrincipal principal,
        @PathVariable UUID projectId,
        @RequestBody(required = false) SnapshotDtos.CreateSnapshotRequest request
    ) {
        String branch = request != null ? (request.branch() != null ? request.branch() : request.ref()) : null;
        return service.createSnapshot(principal.id(), projectId, branch);
    }

    @GetMapping
    public List<SnapshotDtos.SnapshotResponse> listSnapshots(
        @AuthenticationPrincipal SkillLinkPrincipal principal,
        @PathVariable UUID projectId
    ) {
        return service.listSnapshots(principal.id(), projectId);
    }

    @GetMapping("/{snapshotId}")
    public SnapshotDtos.SnapshotDetailResponse getSnapshot(
        @AuthenticationPrincipal SkillLinkPrincipal principal,
        @PathVariable UUID projectId,
        @PathVariable UUID snapshotId
    ) {
        return service.getSnapshotDetail(principal.id(), projectId, snapshotId);
    }

    @GetMapping("/{snapshotId}/files")
    public List<SnapshotDtos.SnapshotFileResponse> listFiles(
        @AuthenticationPrincipal SkillLinkPrincipal principal,
        @PathVariable UUID projectId,
        @PathVariable UUID snapshotId
    ) {
        // Validate snapshot belongs to candidate/project via detail fetch
        var detail = service.getSnapshotDetail(principal.id(), projectId, snapshotId);
        return detail.files();
    }

    @GetMapping("/{snapshotId}/summary")
    public ResponseEntity<SnapshotDtos.SnapshotResponse> getSummary(
        @AuthenticationPrincipal SkillLinkPrincipal principal,
        @PathVariable UUID projectId,
        @PathVariable UUID snapshotId
    ) {
        var snapshot = service.getSnapshot(principal.id(), projectId, snapshotId);
        return ResponseEntity.ok(snapshot);
    }
}
