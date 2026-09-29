package com.skilllink.api.analysis;

import com.skilllink.api.project.ProjectService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AnalysisJobService {
    private final ProjectService projects;
    private final AnalysisJobRepository jobs;
    private final AnalysisWorker worker;
    public AnalysisJobService(ProjectService projects, AnalysisJobRepository jobs, AnalysisWorker worker) { this.projects = projects; this.jobs = jobs; this.worker = worker; }

    public JobResponse start(UUID candidateId, UUID projectId) {
        var repository = projects.repository(candidateId, projectId);
        UUID jobId = jobs.create(candidateId, projectId, repository.id(), UUID.randomUUID().toString());
        worker.runAsync(jobId);
        return get(candidateId, projectId, jobId);
    }

    public JobResponse get(UUID candidateId, UUID projectId, UUID jobId) { return jobs.find(candidateId, projectId, jobId).map(JobResponse::from).orElseThrow(() -> new AnalysisJobNotFoundException()); }

    public JobResponse retry(UUID candidateId, UUID projectId, UUID jobId) {
        AnalysisJobRepository.JobRecord job = jobs.find(candidateId, projectId, jobId).orElseThrow(() -> new AnalysisJobNotFoundException());
        if (!"FAILED".equals(job.state())) throw new AnalysisJobStateException("Only failed jobs can be retried.");
        jobs.retry(jobId); worker.runAsync(jobId); return get(candidateId, projectId, jobId);
    }

    public record JobResponse(UUID jobId, UUID projectId, UUID snapshotId, String state, int progress, String stage, String errorCode, String errorMessage, Instant createdAt, Instant startedAt, Instant completedAt) { static JobResponse from(AnalysisJobRepository.JobRecord item) { return new JobResponse(item.id(), item.projectId(), item.snapshotId(), item.state(), item.progress(), item.stage(), item.errorCode(), item.errorMessage(), item.createdAt(), item.startedAt(), item.completedAt()); } }
    public static class AnalysisJobNotFoundException extends RuntimeException {}
    public static class AnalysisJobStateException extends RuntimeException { public AnalysisJobStateException(String message) { super(message); } }
}
