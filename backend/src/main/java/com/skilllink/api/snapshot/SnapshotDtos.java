package com.skilllink.api.snapshot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SnapshotDtos {
    private SnapshotDtos() {}

    public record CreateSnapshotRequest(String branch, String ref) {}

    public record SnapshotResponse(
        UUID snapshotId,
        UUID candidateId,
        UUID projectId,
        UUID repositoryId,
        String githubRepositoryId,
        String owner,
        String repositoryName,
        String fullName,
        String commitSha,
        String shortSha,
        String branchName,
        String commitAuthor,
        String commitMessage,
        Instant commitTimestamp,
        Instant snapshotCreatedAt,
        String analysisVersion,
        String filePolicyVersion,
        String status,
        int fileCount,
        int includedFileCount,
        int excludedFileCount,
        long totalBytes,
        String integrityHash,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
    ) {
        public static SnapshotResponse from(SnapshotRepository.SnapshotRow row) {
            String shortSha = row.commitSha() != null && row.commitSha().length() >= 7 ? row.commitSha().substring(0, 7) : row.commitSha();
            return new SnapshotResponse(
                row.id(), row.candidateId(), row.projectId(), row.repositoryId(),
                row.githubRepositoryId(), row.ownerLogin(), row.repositoryName(), row.fullName(),
                row.commitSha(), shortSha, row.branchName(),
                row.commitAuthor(), row.commitMessage(), row.commitTimestamp(),
                row.snapshotCreatedAt(), row.analysisVersion(), row.filePolicyVersion(),
                row.status(), row.fileCount(), row.includedFileCount(), row.excludedFileCount(),
                row.totalBytes(), row.integrityHash(),
                row.errorCode(), row.errorMessage(),
                row.createdAt(), row.updatedAt()
            );
        }
    }

    public record SnapshotFileResponse(
        String path,
        String language,
        long sizeBytes,
        String contentHash,
        String blobSha,
        boolean included,
        String exclusionReason,
        boolean secretRedacted,
        int secretCount
    ) {
        public static SnapshotFileResponse from(SnapshotFileRepository.FileRow row) {
            return new SnapshotFileResponse(
                row.path(), row.language(), row.sizeBytes(),
                row.contentHash(), row.blobSha(),
                row.included(), row.exclusionReason(),
                row.secretRedacted(), row.secretCount()
            );
        }
    }

    public record SnapshotDetailResponse(
        SnapshotResponse snapshot,
        List<SnapshotFileResponse> files,
        ManifestSummary summary
    ) {}

    public record ManifestSummary(
        int totalFiles,
        int includedFiles,
        int excludedFiles,
        long totalBytes,
        int secretRedactedFiles,
        int totalSecrets,
        String integrityHash
    ) {}
}
