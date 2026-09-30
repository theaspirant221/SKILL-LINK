package com.skilllink.api.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.skilllink.api.analysis.SecretRedactor;
import com.skilllink.api.github.GithubAppClient;
import com.skilllink.api.github.GithubClient;
import com.skilllink.api.github.GithubConnectionRepository;
import com.skilllink.api.github.GithubInstallationRepository;
import com.skilllink.api.github.GithubOAuthService;
import com.skilllink.api.github.GithubRepositoryJdbcRepository;
import com.skilllink.api.project.ProjectService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Checkpoint D: Immutable Repository Snapshot
 *
 * SkillLink must be able to take a candidate-selected GitHub repository and create a
 * reproducible, immutable snapshot at one exact commit SHA.
 *
 * Flow:
 * - Validate candidate owns project and repository belongs to active installation
 * - Resolve HEAD to commit SHA server-side (never trust frontend SHA alone)
 * - Fetch repository tree at exact SHA
 * - Apply file policy and resource limits
 * - Secret filtering before persistence
 * - Persist file manifest with hashes
 * - Calculate integrity hash
 * - Mark READY (immutable)
 */
@Service
public class SnapshotService {
    private final ProjectService projects;
    private final GithubRepositoryJdbcRepository githubRepositories;
    private final GithubOAuthService githubOAuth;
    private final GithubClient github;
    private final GithubAppClient appClient;
    private final GithubInstallationRepository installations;
    private final GithubConnectionRepository connections;
    private final SnapshotRepository snapshots;
    private final SnapshotFileRepository snapshotFiles;
    private final SnapshotFilePolicy filePolicy;
    private final SecretRedactor secretRedactor;
    private final JdbcTemplate jdbc;

    public SnapshotService(
        ProjectService projects,
        GithubRepositoryJdbcRepository githubRepositories,
        GithubOAuthService githubOAuth,
        GithubClient github,
        GithubAppClient appClient,
        GithubInstallationRepository installations,
        GithubConnectionRepository connections,
        SnapshotRepository snapshots,
        SnapshotFileRepository snapshotFiles,
        SnapshotFilePolicy filePolicy,
        SecretRedactor secretRedactor,
        JdbcTemplate jdbc
    ) {
        this.projects = projects;
        this.githubRepositories = githubRepositories;
        this.githubOAuth = githubOAuth;
        this.github = github;
        this.appClient = appClient;
        this.installations = installations;
        this.connections = connections;
        this.snapshots = snapshots;
        this.snapshotFiles = snapshotFiles;
        this.filePolicy = filePolicy;
        this.secretRedactor = secretRedactor;
        this.jdbc = jdbc;
    }

    /**
     * Creates or reuses an immutable snapshot for a candidate project.
     * Resolves the branch HEAD to an exact commit SHA server-side.
     */
    @Transactional
    public SnapshotDtos.SnapshotResponse createSnapshot(UUID candidateId, UUID projectId, String branchOrRef) {
        // Validate candidate owns project and get repository
        GithubRepositoryJdbcRepository.RepositoryRow repository = projects.repository(candidateId, projectId);
        String fullName = repository.fullName();
        String requestedBranch = branchOrRef != null && !branchOrRef.isBlank() ? branchOrRef : repository.defaultBranch();
        if (requestedBranch == null || requestedBranch.isBlank()) {
            requestedBranch = "main";
        }

        // Validate repository belongs to active installation
        validateRepositoryAccess(candidateId, fullName);

        String token = githubOAuth.accessToken(candidateId);

        // Resolve commit SHA server-side
        JsonNode commitNode;
        try {
            commitNode = github.commit(fullName, requestedBranch, token);
        } catch (GithubClient.GithubException ex) {
            if (ex.status() == 404) {
                throw new SnapshotException("BRANCH_NOT_FOUND", "Branch or ref '" + requestedBranch + "' not found in repository " + fullName, 404);
            }
            if (ex.status() == 401 || ex.status() == 403) {
                throw new SnapshotException("GITHUB_AUTH_FAILED", "GitHub authorization failed while resolving commit. Reconnect GitHub.", ex.status());
            }
            throw new SnapshotException("GITHUB_API_ERROR", "GitHub API error while resolving commit: " + ex.getMessage(), ex.status());
        }

        String commitSha = commitNode.path("sha").asText();
        String treeSha = commitNode.path("commit").path("tree").path("sha").asText();
        if (commitSha == null || commitSha.isBlank()) {
            throw new SnapshotException("SNAPSHOT_COMMIT_UNRESOLVED", "GitHub did not return a commit SHA for branch " + requestedBranch, 502);
        }
        if (treeSha == null || treeSha.isBlank()) {
            // Fallback: commit SHA itself can be used as tree ref? But we need tree SHA
            treeSha = commitSha;
        }

        // Idempotency: reuse existing READY snapshot for same repo, commit, policy version
        var existingReady = snapshots.findReadyByRepoCommitAndPolicy(repository.id(), commitSha, filePolicy.version());
        if (existingReady.isPresent()) {
            return SnapshotDtos.SnapshotResponse.from(existingReady.get());
        }

        // Extract commit metadata
        String commitAuthor = commitNode.path("commit").path("author").path("name").asText(null);
        String commitMessage = commitNode.path("commit").path("message").asText(null);
        Instant commitTimestamp = parseInstant(commitNode.path("commit").path("author").path("date").asText(null));
        if (commitAuthor == null) {
            commitAuthor = commitNode.path("author").path("login").asText(null);
        }

        // Create snapshot record with CREATED status
        UUID snapshotId = snapshots.create(
            candidateId, projectId, repository.id(),
            repository.fullName(), // using fullName as github_repository_id for now, but we have external_id in repository table
            repository.owner(), repository.fullName().substring(repository.fullName().lastIndexOf('/') + 1),
            repository.fullName(),
            commitSha, requestedBranch, treeSha,
            filePolicy.version(), "v1"
        );

        // Update commit metadata
        snapshots.updateCommitMetadata(snapshotId, commitAuthor, commitMessage, commitTimestamp);
        snapshots.updateStatus(snapshotId, "FETCHING");

        try {
            // Fetch tree at exact commit SHA (tree SHA from commit)
            JsonNode treeNode;
            try {
                treeNode = github.tree(fullName, treeSha, token);
            } catch (GithubClient.GithubException ex) {
                snapshots.markFailed(snapshotId, "TREE_FETCH_FAILED", "Failed to fetch repository tree at commit " + commitSha + ": " + ex.getMessage());
                throw new SnapshotException("TREE_FETCH_FAILED", "Failed to fetch repository tree: " + ex.getMessage(), ex.status());
            }

            if (treeNode.path("truncated").asBoolean(false)) {
                snapshots.markFailed(snapshotId, "REPOSITORY_TREE_TRUNCATED", "Repository tree is truncated and too large to process.");
                throw new SnapshotException("REPOSITORY_TREE_TRUNCATED", "Repository tree is too large (truncated).", 400);
            }

            int treeSize = treeNode.path("tree").size();
            if (treeSize > filePolicy.maxTreeEntries()) {
                snapshots.markFailed(snapshotId, "REPOSITORY_TREE_TOO_LARGE", "Repository tree has " + treeSize + " entries, exceeding limit " + filePolicy.maxTreeEntries());
                throw new SnapshotException("REPOSITORY_TREE_TOO_LARGE", "Repository tree too large: " + treeSize + " entries", 400);
            }

            // Build file list from tree
            List<GithubTreeEntry> allEntries = new ArrayList<>();
            treeNode.path("tree").forEach(item -> {
                if ("blob".equals(item.path("type").asText())) {
                    allEntries.add(new GithubTreeEntry(
                        item.path("path").asText(),
                        item.path("sha").asText(),
                        item.path("size").asLong(0)
                    ));
                }
            });

            // Apply file policy and resource limits
            List<SnapshotFileRepository.FileRow> fileRows = new ArrayList<>();
            List<GithubTreeEntry> includedEntries = new ArrayList<>();
            long totalBytes = 0;
            int includedCount = 0;
            int excludedCount = 0;
            int secretRedactedFiles = 0;
            int totalSecrets = 0;

            // Sort by relevance: prioritize config files, then source
            allEntries.sort(Comparator.comparingInt(this::policyPriority).thenComparing(GithubTreeEntry::path));

            for (GithubTreeEntry entry : allEntries) {
                if (fileRows.size() >= filePolicy.maxFiles()) {
                    // Record remaining as excluded due to limit
                    excludedCount++;
                    fileRows.add(new SnapshotFileRepository.FileRow(
                        UUID.randomUUID(), snapshotId, entry.path(), filePolicy.detectLanguage(entry.path()),
                        entry.size(), sha256Hex(entry.path() + ":" + entry.sha()), entry.sha(),
                        false, "MAX_FILES_EXCEEDED", false, 0
                    ));
                    continue;
                }

                SnapshotFilePolicy.PolicyDecision decision = filePolicy.decide(entry.path(), entry.size());
                if (!decision.included()) {
                    excludedCount++;
                    fileRows.add(new SnapshotFileRepository.FileRow(
                        UUID.randomUUID(), snapshotId, entry.path(), filePolicy.detectLanguage(entry.path()),
                        entry.size(), sha256Hex(entry.path() + ":" + entry.sha()), entry.sha(),
                        false, decision.reason(), false, 0
                    ));
                    continue;
                }

                // Check total bytes limit
                if (totalBytes + entry.size() > filePolicy.maxRepositoryBytes()) {
                    excludedCount++;
                    fileRows.add(new SnapshotFileRepository.FileRow(
                        UUID.randomUUID(), snapshotId, entry.path(), filePolicy.detectLanguage(entry.path()),
                        entry.size(), sha256Hex(entry.path() + ":" + entry.sha()), entry.sha(),
                        false, "MAX_BYTES_EXCEEDED", false, 0
                    ));
                    continue;
                }

                // Fetch blob content at exact SHA
                String base64Content;
                try {
                    base64Content = github.blob(fullName, entry.sha(), token);
                } catch (GithubClient.GithubException ex) {
                    if (ex.status() == 404) {
                        excludedCount++;
                        fileRows.add(new SnapshotFileRepository.FileRow(
                            UUID.randomUUID(), snapshotId, entry.path(), filePolicy.detectLanguage(entry.path()),
                            entry.size(), sha256Hex(entry.path() + ":" + entry.sha()), entry.sha(),
                            false, "BLOB_NOT_FOUND", false, 0
                        ));
                        continue;
                    }
                    throw ex;
                }

                byte[] rawBytes;
                try {
                    rawBytes = Base64.getDecoder().decode(base64Content.replaceAll("\\s", ""));
                } catch (IllegalArgumentException ex) {
                    excludedCount++;
                    fileRows.add(new SnapshotFileRepository.FileRow(
                        UUID.randomUUID(), snapshotId, entry.path(), filePolicy.detectLanguage(entry.path()),
                        entry.size(), sha256Hex(entry.path() + ":" + entry.sha()), entry.sha(),
                        false, "INVALID_BASE64", false, 0
                    ));
                    continue;
                }

                if (rawBytes.length > filePolicy.maxFileBytes()) {
                    excludedCount++;
                    fileRows.add(new SnapshotFileRepository.FileRow(
                        UUID.randomUUID(), snapshotId, entry.path(), filePolicy.detectLanguage(entry.path()),
                        rawBytes.length, sha256Hex(entry.path() + ":" + entry.sha()), entry.sha(),
                        false, "FILE_TOO_LARGE", false, 0
                    ));
                    continue;
                }

                // Secret filtering before persistence
                String content;
                try {
                    content = new String(rawBytes, StandardCharsets.UTF_8);
                } catch (Exception ex) {
                    excludedCount++;
                    fileRows.add(new SnapshotFileRepository.FileRow(
                        UUID.randomUUID(), snapshotId, entry.path(), filePolicy.detectLanguage(entry.path()),
                        rawBytes.length, sha256Hex(entry.path() + ":" + entry.sha()), entry.sha(),
                        false, "BINARY_OR_INVALID_UTF8", false, 0
                    ));
                    continue;
                }

                SecretRedactor.Redacted redacted = secretRedactor.redact(content);
                boolean secretRedacted = redacted.count() > 0;
                if (secretRedacted) {
                    secretRedactedFiles++;
                    totalSecrets += redacted.count();
                }

                // Content hash (SHA-256 of redacted content for integrity, but also keep original hash?)
                // For integrity we hash the redacted content + path + blob SHA
                String contentHash = sha256Hex(redacted.content());

                fileRows.add(new SnapshotFileRepository.FileRow(
                    UUID.randomUUID(), snapshotId, entry.path(), filePolicy.detectLanguage(entry.path()),
                    rawBytes.length, contentHash, entry.sha(),
                    true, null, secretRedacted, redacted.count()
                ));

                includedEntries.add(entry);
                totalBytes += rawBytes.length;
                includedCount++;
            }

            // Calculate integrity hash: deterministic hash of repo identity + commit SHA + manifest + policy version
            String integrityInput = fullName + "|" + commitSha + "|" + filePolicy.version() + "|" +
                fileRows.stream()
                    .filter(SnapshotFileRepository.FileRow::included)
                    .sorted(Comparator.comparing(SnapshotFileRepository.FileRow::path))
                    .map(f -> f.path() + ":" + f.contentHash() + ":" + f.blobSha())
                    .reduce("", (a, b) -> a + "|" + b);
            String integrityHash = sha256Hex(integrityInput);
            String sourceHash = sha256Hex(includedEntries.stream()
                .map(e -> e.path() + ":" + e.sha())
                .reduce("", String::concat));

            // Persist file manifest
            snapshotFiles.deleteBySnapshot(snapshotId); // clean if retry
            snapshotFiles.insertBatch(snapshotId, fileRows);

            // Mark READY (immutable from now)
            snapshots.markReady(snapshotId, fileRows.size(), includedCount, excludedCount, totalBytes, integrityHash, sourceHash);

            var readyRow = snapshots.findById(candidateId, projectId, snapshotId).orElseThrow();
            return SnapshotDtos.SnapshotResponse.from(readyRow);

        } catch (SnapshotException ex) {
            // Already marked failed if needed
            if (snapshots.findById(candidateId, projectId, snapshotId).map(r -> !"FAILED".equals(r.status())).orElse(false)) {
                snapshots.markFailed(snapshotId, ex.code(), ex.getMessage());
            }
            throw ex;
        } catch (Exception ex) {
            snapshots.markFailed(snapshotId, "SNAPSHOT_FAILED", "Snapshot creation failed: " + ex.getMessage());
            throw new SnapshotException("SNAPSHOT_FAILED", "Snapshot creation failed: " + ex.getMessage(), 500);
        }
    }

    public List<SnapshotDtos.SnapshotResponse> listSnapshots(UUID candidateId, UUID projectId) {
        // Validate ownership
        projects.repository(candidateId, projectId);
        return snapshots.listByProject(candidateId, projectId).stream()
            .map(SnapshotDtos.SnapshotResponse::from)
            .toList();
    }

    public SnapshotDtos.SnapshotDetailResponse getSnapshotDetail(UUID candidateId, UUID projectId, UUID snapshotId) {
        var snapshot = snapshots.findById(candidateId, projectId, snapshotId)
            .orElseThrow(() -> new SnapshotException("SNAPSHOT_NOT_FOUND", "Snapshot not found", 404));
        var files = snapshotFiles.listBySnapshot(snapshotId).stream()
            .map(SnapshotDtos.SnapshotFileResponse::from)
            .toList();

        int secretRedactedFiles = (int) files.stream().filter(SnapshotDtos.SnapshotFileResponse::secretRedacted).count();
        int totalSecrets = files.stream().mapToInt(SnapshotDtos.SnapshotFileResponse::secretCount).sum();

        var summary = new SnapshotDtos.ManifestSummary(
            snapshot.fileCount(),
            snapshot.includedFileCount(),
            snapshot.excludedFileCount(),
            snapshot.totalBytes(),
            secretRedactedFiles,
            totalSecrets,
            snapshot.integrityHash()
        );

        return new SnapshotDtos.SnapshotDetailResponse(
            SnapshotDtos.SnapshotResponse.from(snapshot),
            files,
            summary
        );
    }

    public SnapshotDtos.SnapshotResponse getSnapshot(UUID candidateId, UUID projectId, UUID snapshotId) {
        var snapshot = snapshots.findById(candidateId, projectId, snapshotId)
            .orElseThrow(() -> new SnapshotException("SNAPSHOT_NOT_FOUND", "Snapshot not found", 404));
        return SnapshotDtos.SnapshotResponse.from(snapshot);
    }

    private void validateRepositoryAccess(UUID candidateId, String fullName) {
        // Check active installation and that repository is authorized
        try {
            var repos = githubOAuth.repositories(candidateId);
            boolean authorized = repos.stream().anyMatch(r -> r.fullName().equals(fullName));
            if (!authorized) {
                // Check if installation was removed
                var status = githubOAuth.status(candidateId);
                if (GithubInstallationRepository.STATUS_REMOVED.equals(status.installation() != null ? status.installation().status() : null) ||
                    "INSTALLATION_REMOVED".equals(status.status())) {
                    throw new SnapshotException("GITHUB_INSTALLATION_REMOVED", "GitHub App installation was removed. Reinstall to access repositories.", 400);
                }
                throw new SnapshotException("REPOSITORY_NOT_AUTHORIZED", "Repository " + fullName + " is not authorized through your GitHub App installation.", 400);
            }
        } catch (com.skilllink.api.github.GithubOAuthService.GithubConfigurationException ex) {
            throw new SnapshotException(ex.code(), ex.getMessage(), 400);
        }
    }

    private int policyPriority(GithubTreeEntry entry) {
        String path = entry.path().toLowerCase();
        if (path.endsWith("pom.xml") || path.endsWith("package.json") || path.endsWith("pyproject.toml") || path.endsWith("requirements.txt")) return 0;
        if (path.contains("security") || path.contains("auth") || path.contains("controller") || path.contains("route")) return 1;
        if (path.endsWith("dockerfile") || path.contains("docker-compose")) return 2;
        if (path.contains("test")) return 4;
        return 3;
    }

    private Instant parseInstant(String value) {
        try {
            return value == null ? null : Instant.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String sha256Hex(String input) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record GithubTreeEntry(String path, String sha, long size) {}

    public static class SnapshotException extends RuntimeException {
        private final String code;
        private final int httpStatus;

        public SnapshotException(String code, String message, int httpStatus) {
            super(message);
            this.code = code;
            this.httpStatus = httpStatus;
        }

        public String code() { return code; }
        public int httpStatus() { return httpStatus; }
    }
}
