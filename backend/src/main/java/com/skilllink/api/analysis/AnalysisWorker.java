package com.skilllink.api.analysis;

import com.skilllink.api.evidence.EvidencePersister;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AnalysisWorker {
    private final AnalysisJobRepository jobs;
    private final RepositorySnapshotService snapshots;
    private final DeterministicRepositoryAnalyzer analyzer;
    private final EvidencePersister evidence;
    public AnalysisWorker(AnalysisJobRepository jobs, RepositorySnapshotService snapshots, DeterministicRepositoryAnalyzer analyzer, EvidencePersister evidence) { this.jobs = jobs; this.snapshots = snapshots; this.analyzer = analyzer; this.evidence = evidence; }

    @Async("analysisExecutor")
    public void runAsync(UUID jobId) { run(jobId); }

    public void run(UUID jobId) {
        AnalysisJobRepository.JobRecord job = find(jobId);
        try {
            jobs.updateState(jobId, "FETCHING", 15, "Resolving branch and commit");
            AnalysisModels.FetchedSnapshot snapshot = snapshots.fetch(job);
            jobs.attachSnapshot(jobId, snapshot.snapshotId());
            if (snapshot.alreadyAnalyzed()) { jobs.complete(jobId); return; }
            jobs.updateState(jobId, "ANALYZING", 45, "Inspecting manifests and source structure");
            AnalysisModels.AnalysisOutput output = analyzer.analyze(snapshot);
            jobs.updateState(jobId, "MAPPING", 78, "Mapping observations to normalized skills");
            evidence.persist(job.candidateId(), job.projectId(), snapshot, output);
            snapshots.markCompleted(snapshot.snapshotId(), output);
            jobs.complete(jobId);
        } catch (Exception ex) {
            try { AnalysisJobRepository.JobRecord current = find(jobId); if (current.snapshotId() != null) snapshots.markFailed(current.snapshotId()); } catch (Exception ignored) {}
            jobs.fail(jobId, safeCode(ex), safeMessage(ex));
        }
    }

    private AnalysisJobRepository.JobRecord find(UUID id) { return jobs.findById(id).orElseThrow(); }
    private String safeCode(Exception ex) { if (ex instanceof RepositorySnapshotService.RepositoryAnalysisException typed) return typed.code(); if (ex instanceof com.skilllink.api.github.GithubClient.GithubException typed) return typed.code(); if (ex instanceof com.skilllink.api.github.GithubOAuthService.GithubConfigurationException typed) return typed.code(); return "ANALYSIS_FAILED"; }
    private String safeMessage(Exception ex) { String message = ex.getMessage(); return message == null ? "Repository analysis failed." : message.substring(0, Math.min(message.length(), 500)); }
}
