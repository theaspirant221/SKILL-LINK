package com.skilllink.api.analysis;

import com.skilllink.api.analysis.detector.*;
import com.skilllink.api.github.GithubClient;
import com.skilllink.api.github.GithubOAuthService;
import com.skilllink.api.project.ProjectService;
import com.skilllink.api.snapshot.SnapshotFileRepository;
import com.skilllink.api.snapshot.SnapshotRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class DeterministicAnalysisService {
    public static final String ANALYZER_VERSION = DeterministicAnalysisDtos.ANALYZER_VERSION;
    public static final String ANALYZER_VERSION_JAVA = DeterministicAnalysisDtos.ANALYZER_VERSION_JAVA;

    private final ProjectService projects;
    private final SnapshotRepository snapshots;
    private final SnapshotFileRepository snapshotFiles;
    private final AnalysisRunRepository runs;
    private final AnalysisObservationRepository observations;
    private final AnalysisFileErrorRepository fileErrors;
    private final GithubClient github;
    private final GithubOAuthService githubOAuth;
    private DeterministicAnalysisAsyncRunner asyncRunner;

    private final LanguageDetector languageDetector;
    private final ManifestDetector manifestDetector;
    private final JavaAstDetector javaAstDetector;
    private final GenericDetector genericDetector;
    private final DatabaseDetector databaseDetector;
    private final SecurityDetector securityDetector;
    private final TestDetector testDetector;
    private final DevOpsDetector devOpsDetector;

    public DeterministicAnalysisService(
        ProjectService projects,
        SnapshotRepository snapshots,
        SnapshotFileRepository snapshotFiles,
        AnalysisRunRepository runs,
        AnalysisObservationRepository observations,
        AnalysisFileErrorRepository fileErrors,
        GithubClient github,
        GithubOAuthService githubOAuth,
        LanguageDetector languageDetector,
        ManifestDetector manifestDetector,
        JavaAstDetector javaAstDetector,
        GenericDetector genericDetector,
        DatabaseDetector databaseDetector,
        SecurityDetector securityDetector,
        TestDetector testDetector,
        DevOpsDetector devOpsDetector
    ) {
        this.projects = projects;
        this.snapshots = snapshots;
        this.snapshotFiles = snapshotFiles;
        this.runs = runs;
        this.observations = observations;
        this.fileErrors = fileErrors;
        this.github = github;
        this.githubOAuth = githubOAuth;
        this.languageDetector = languageDetector;
        this.manifestDetector = manifestDetector;
        this.javaAstDetector = javaAstDetector;
        this.genericDetector = genericDetector;
        this.databaseDetector = databaseDetector;
        this.securityDetector = securityDetector;
        this.testDetector = testDetector;
        this.devOpsDetector = devOpsDetector;
    }

    // Setter for async runner to avoid circular constructor dependency
    @org.springframework.beans.factory.annotation.Autowired
    public void setAsyncRunner(@Lazy DeterministicAnalysisAsyncRunner asyncRunner) {
        this.asyncRunner = asyncRunner;
    }

    @Transactional
    public DeterministicAnalysisDtos.AnalysisRunResponse createRun(UUID candidateId, UUID projectId, UUID snapshotId) {
        // Validate candidate owns project and snapshot
        var repository = projects.repository(candidateId, projectId);
        var snapshot = snapshots.findById(candidateId, projectId, snapshotId)
            .orElseThrow(() -> new AnalysisException("SNAPSHOT_NOT_FOUND", "Snapshot not found", 404));

        if (!"READY".equals(snapshot.status())) {
            throw new AnalysisException("SNAPSHOT_NOT_READY", "Snapshot must be READY before analysis", 400);
        }

        // Validate repository access still active (candidate isolation)
        validateRepositoryAccess(candidateId, snapshot.fullName());

        UUID runId = runs.create(snapshotId, candidateId, projectId, repository.id(), ANALYZER_VERSION);

        // Async execution via separate component to ensure @Async proxy applies
        if (asyncRunner != null) {
            asyncRunner.runAsync(runId);
        } else {
            // Fallback synchronous if async runner not wired (e.g., unit tests)
            run(runId);
        }

        var runRow = runs.findById(candidateId, projectId, runId).orElseThrow();
        return DeterministicAnalysisDtos.AnalysisRunResponse.from(runRow, snapshot.commitSha());
    }

    public void run(UUID runId) {
        var runOpt = runs.findByIdAnyCandidate(runId);
        if (runOpt.isEmpty()) return;
        var run = runOpt.get();

        try {
            runs.markRunning(runId);

            var snapshotOpt = snapshots.findByIdAnyCandidate(run.snapshotId());
            if (snapshotOpt.isEmpty()) {
                runs.markFailed(runId, "SNAPSHOT_NOT_FOUND", "Snapshot not found for analysis");
                return;
            }
            var snapshot = snapshotOpt.get();

            // Load included files from manifest
            var fileRows = snapshotFiles.listBySnapshot(snapshot.id()).stream()
                .filter(SnapshotFileRepository.FileRow::included)
                .toList();

            // Fetch content for each file via GitHub API at exact blob SHA
            String token;
            try {
                token = githubOAuth.accessToken(run.candidateId());
            } catch (Exception ex) {
                runs.markFailed(runId, "GITHUB_AUTH_FAILED", "GitHub authorization failed: " + ex.getMessage());
                return;
            }

            List<LanguageDetector.SnapshotFile> filesWithContent = new ArrayList<>();
            List<AnalysisFileErrorRepository.FileErrorRow> fetchErrors = new ArrayList<>();

            for (var fileRow : fileRows) {
                try {
                    // Guard: skip files too large for AST (resource safety)
                    if (fileRow.sizeBytes() > 1_000_000) { // 1MB limit for analysis content
                        fetchErrors.add(new AnalysisFileErrorRepository.FileErrorRow(
                            UUID.randomUUID(), runId, snapshot.id(), fileRow.path(),
                            "FILE_TOO_LARGE_FOR_ANALYSIS", "File exceeds 1MB analysis limit",
                            "FETCH", null
                        ));
                        continue;
                    }

                    String base64Content;
                    try {
                        base64Content = github.blob(snapshot.fullName(), fileRow.blobSha(), token);
                    } catch (GithubClient.GithubException ex) {
                        if (ex.status() == 404) {
                            fetchErrors.add(new AnalysisFileErrorRepository.FileErrorRow(
                                UUID.randomUUID(), runId, snapshot.id(), fileRow.path(),
                                "BLOB_NOT_FOUND", "Blob not found at SHA " + fileRow.blobSha(),
                                "FETCH", null
                            ));
                            continue;
                        }
                        throw ex;
                    }

                    byte[] rawBytes;
                    try {
                        rawBytes = Base64.getDecoder().decode(base64Content.replaceAll("\\s", ""));
                    } catch (IllegalArgumentException ex) {
                        fetchErrors.add(new AnalysisFileErrorRepository.FileErrorRow(
                            UUID.randomUUID(), runId, snapshot.id(), fileRow.path(),
                            "INVALID_BASE64", "Invalid Base64 content",
                            "FETCH", null
                        ));
                        continue;
                    }

                    // Check encoding - only process UTF-8 text files
                    String content;
                    try {
                        content = new String(rawBytes, StandardCharsets.UTF_8);
                        // Check if content contains null bytes (binary)
                        if (content.contains("\0")) {
                            fetchErrors.add(new AnalysisFileErrorRepository.FileErrorRow(
                                UUID.randomUUID(), runId, snapshot.id(), fileRow.path(),
                                "BINARY_FILE", "File appears to be binary",
                                "FETCH", null
                            ));
                            continue;
                        }
                    } catch (Exception ex) {
                        fetchErrors.add(new AnalysisFileErrorRepository.FileErrorRow(
                            UUID.randomUUID(), runId, snapshot.id(), fileRow.path(),
                            "INVALID_ENCODING", "File is not valid UTF-8",
                            "FETCH", null
                        ));
                        continue;
                    }

                    filesWithContent.add(new LanguageDetector.SnapshotFile(
                        fileRow.path(), content, fileRow.language(), fileRow.sizeBytes(), fileRow.blobSha(), fileRow.contentHash()
                    ));

                } catch (Exception ex) {
                    // Failure isolation: one file failure should not destroy entire analysis
                    fetchErrors.add(new AnalysisFileErrorRepository.FileErrorRow(
                        UUID.randomUUID(), runId, snapshot.id(), fileRow.path(),
                        "FETCH_FAILED", ex.getMessage() != null ? ex.getMessage().substring(0, Math.min(ex.getMessage().length(), 500)) : "Fetch failed",
                        "FETCH", null
                    ));
                }
            }

            // Run detectors
            List<AnalysisObservationRepository.ObservationRow> allObservations = new ArrayList<>();
            List<AnalysisFileErrorRepository.FileErrorRow> allErrors = new ArrayList<>(fetchErrors);

            // Language detection
            allObservations.addAll(languageDetector.detect(runId, snapshot.id(), filesWithContent));

            // Manifest analysis
            allObservations.addAll(manifestDetector.detect(runId, snapshot.id(), filesWithContent));

            // Java AST deep analysis
            var javaResult = javaAstDetector.detect(runId, snapshot.id(), filesWithContent);
            allObservations.addAll(javaResult.observations);
            allErrors.addAll(javaResult.errors);

            // Generic shallow
            allObservations.addAll(genericDetector.detect(runId, snapshot.id(), filesWithContent));

            // Database signals
            allObservations.addAll(databaseDetector.detect(runId, snapshot.id(), filesWithContent));

            // Security signals
            allObservations.addAll(securityDetector.detect(runId, snapshot.id(), filesWithContent));

            // Test signals
            allObservations.addAll(testDetector.detect(runId, snapshot.id(), filesWithContent));

            // DevOps signals
            allObservations.addAll(devOpsDetector.detect(runId, snapshot.id(), filesWithContent));

            // Persist with duplicate control: delete existing for this run then insert
            observations.deleteByRun(runId);
            fileErrors.deleteByRun(runId);

            observations.insertBatch(runId, allObservations);
            fileErrors.insertBatch(runId, allErrors);

            runs.markComplete(runId, allObservations.size());

        } catch (Exception ex) {
            try {
                runs.markFailed(runId, "ANALYSIS_FAILED", ex.getMessage() != null ? ex.getMessage().substring(0, Math.min(ex.getMessage().length(), 500)) : "Analysis failed");
            } catch (Exception ignored) {}
        }
    }

    public List<DeterministicAnalysisDtos.AnalysisRunResponse> listRuns(UUID candidateId, UUID projectId, UUID snapshotId) {
        // Validate ownership
        projects.repository(candidateId, projectId);
        var snapshot = snapshots.findById(candidateId, projectId, snapshotId)
            .orElseThrow(() -> new AnalysisException("SNAPSHOT_NOT_FOUND", "Snapshot not found", 404));

        return runs.listBySnapshot(candidateId, projectId, snapshotId).stream()
            .map(row -> DeterministicAnalysisDtos.AnalysisRunResponse.from(row, snapshot.commitSha()))
            .toList();
    }

    public DeterministicAnalysisDtos.AnalysisDetailResponse getRunDetail(UUID candidateId, UUID projectId, UUID snapshotId, UUID runId) {
        projects.repository(candidateId, projectId);
        var snapshot = snapshots.findById(candidateId, projectId, snapshotId)
            .orElseThrow(() -> new AnalysisException("SNAPSHOT_NOT_FOUND", "Snapshot not found", 404));

        var run = runs.findById(candidateId, projectId, runId)
            .orElseThrow(() -> new AnalysisException("ANALYSIS_RUN_NOT_FOUND", "Analysis run not found", 404));

        if (!run.snapshotId().equals(snapshotId)) {
            throw new AnalysisException("ANALYSIS_RUN_SNAPSHOT_MISMATCH", "Analysis run does not belong to this snapshot", 400);
        }

        var obsRows = observations.listByRun(runId);
        var errorRows = fileErrors.listByRun(runId);

        var obsResponses = obsRows.stream().map(DeterministicAnalysisDtos.ObservationResponse::from).toList();
        var errorResponses = errorRows.stream().map(DeterministicAnalysisDtos.FileErrorResponse::from).toList();

        // Summary
        var languages = obsResponses.stream().filter(o -> "LANGUAGE_PRESENT".equals(o.observationType())).map(DeterministicAnalysisDtos.ObservationResponse::factKey).distinct().toList();
        var frameworks = obsResponses.stream().filter(o -> "FRAMEWORK_PRESENT".equals(o.observationType())).map(DeterministicAnalysisDtos.ObservationResponse::factKey).distinct().toList();

        var summary = new DeterministicAnalysisDtos.ObservationSummary(
            obsResponses.size(),
            (int) obsResponses.stream().filter(o -> "LANGUAGE".equals(o.category())).count(),
            (int) obsResponses.stream().filter(o -> "FRAMEWORK".equals(o.category())).count(),
            (int) obsResponses.stream().filter(o -> "DEPENDENCY".equals(o.category())).count(),
            (int) obsResponses.stream().filter(o -> "HTTP_ENDPOINT".equals(o.observationType())).count(),
            (int) obsResponses.stream().filter(o -> "DATABASE".equals(o.category())).count(),
            (int) obsResponses.stream().filter(o -> "SECURITY".equals(o.category())).count(),
            (int) obsResponses.stream().filter(o -> "TESTING".equals(o.category())).count(),
            (int) obsResponses.stream().filter(o -> "DEVOPS".equals(o.category())).count(),
            languages,
            frameworks
        );

        var runResponse = DeterministicAnalysisDtos.AnalysisRunResponse.from(run, snapshot.commitSha());

        return new DeterministicAnalysisDtos.AnalysisDetailResponse(runResponse, obsResponses, errorResponses, summary);
    }

    public DeterministicAnalysisDtos.AnalysisRunResponse getRun(UUID candidateId, UUID projectId, UUID snapshotId, UUID runId) {
        projects.repository(candidateId, projectId);
        var snapshot = snapshots.findById(candidateId, projectId, snapshotId)
            .orElseThrow(() -> new AnalysisException("SNAPSHOT_NOT_FOUND", "Snapshot not found", 404));

        var run = runs.findById(candidateId, projectId, runId)
            .orElseThrow(() -> new AnalysisException("ANALYSIS_RUN_NOT_FOUND", "Analysis run not found", 404));

        if (!run.snapshotId().equals(snapshotId)) {
            throw new AnalysisException("ANALYSIS_RUN_SNAPSHOT_MISMATCH", "Analysis run does not belong to this snapshot", 400);
        }

        return DeterministicAnalysisDtos.AnalysisRunResponse.from(run, snapshot.commitSha());
    }

    private void validateRepositoryAccess(UUID candidateId, String fullName) {
        try {
            var repos = githubOAuth.repositories(candidateId);
            boolean authorized = repos.stream().anyMatch(r -> r.fullName().equals(fullName));
            if (!authorized) {
                var status = githubOAuth.status(candidateId);
                if ("INSTALLATION_REMOVED".equals(status.status())) {
                    throw new AnalysisException("GITHUB_INSTALLATION_REMOVED", "GitHub App installation was removed", 400);
                }
                throw new AnalysisException("REPOSITORY_NOT_AUTHORIZED", "Repository not authorized", 400);
            }
        } catch (com.skilllink.api.github.GithubOAuthService.GithubConfigurationException ex) {
            throw new AnalysisException(ex.code(), ex.getMessage(), 400);
        }
    }

    public static class AnalysisException extends RuntimeException {
        private final String code;
        private final int httpStatus;
        public AnalysisException(String code, String message, int httpStatus) {
            super(message);
            this.code = code;
            this.httpStatus = httpStatus;
        }
        public String code() { return code; }
        public int httpStatus() { return httpStatus; }
    }
}
