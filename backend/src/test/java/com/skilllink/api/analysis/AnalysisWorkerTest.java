package com.skilllink.api.analysis;

import com.skilllink.api.evidence.EvidencePersister;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisWorkerTest {
    @Mock AnalysisJobRepository jobs;
    @Mock RepositorySnapshotService snapshots;
    @Mock DeterministicRepositoryAnalyzer analyzer;
    @Mock EvidencePersister evidence;
    @InjectMocks AnalysisWorker worker;

    @Test
    void successfulRunPersistsEvidenceAndCompletesTheJob() {
        UUID jobId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        var job = new AnalysisJobRepository.JobRecord(jobId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "QUEUED", 0, "Queued", 0, null, null, null, null, null);
        var snapshot = new AnalysisModels.FetchedSnapshot(snapshotId, job.repositoryId(), job.projectId(), "acme/foodbridge", "main", "abcdef0123", "tree", List.of(), 0, 0, 0, false);
        var output = new AnalysisModels.AnalysisOutput(List.of("Java"), List.of("Spring"), List.of(), 0, 0, 0, 0, "summary");
        when(jobs.findById(jobId)).thenReturn(Optional.of(job));
        when(snapshots.fetch(job)).thenReturn(snapshot);
        when(analyzer.analyze(snapshot)).thenReturn(output);

        worker.run(jobId);

        verify(jobs).updateState(jobId, "FETCHING", 15, "Resolving branch and commit");
        verify(jobs).attachSnapshot(jobId, snapshotId);
        verify(jobs).updateState(jobId, "ANALYZING", 45, "Inspecting manifests and source structure");
        verify(evidence).persist(job.candidateId(), job.projectId(), snapshot, output);
        verify(snapshots).markCompleted(snapshotId, output);
        verify(jobs).complete(jobId);
        verify(jobs, never()).fail(any(), anyString(), anyString());
    }

    @Test
    void failedSnapshotMarksJobFailedWithProviderSafeCode() {
        UUID jobId = UUID.randomUUID();
        var job = new AnalysisJobRepository.JobRecord(jobId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "QUEUED", 0, "Queued", 0, null, null, null, null, null);
        when(jobs.findById(jobId)).thenReturn(Optional.of(job));
        when(snapshots.fetch(job)).thenThrow(new RepositorySnapshotService.RepositoryAnalysisException("REPOSITORY_TREE_TOO_LARGE", "Repository tree is larger than the configured analysis limit."));

        worker.run(jobId);

        verify(jobs).fail(jobId, "REPOSITORY_TREE_TOO_LARGE", "Repository tree is larger than the configured analysis limit.");
        verify(jobs, never()).complete(any());
    }
}
