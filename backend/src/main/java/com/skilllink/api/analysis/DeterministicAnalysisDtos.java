package com.skilllink.api.analysis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DeterministicAnalysisDtos {
    private DeterministicAnalysisDtos() {}

    public static final String ANALYZER_VERSION = "deterministic-v1";
    public static final String ANALYZER_VERSION_JAVA = "deterministic-java-v1";

    public record CreateAnalysisRequest() {}

    public record AnalysisRunResponse(
        UUID analysisRunId,
        UUID snapshotId,
        UUID candidateId,
        UUID projectId,
        UUID repositoryId,
        String analyzerVersion,
        String status,
        Instant startedAt,
        Instant completedAt,
        String failureCode,
        String failureMessage,
        int observationCount,
        Instant createdAt,
        Instant updatedAt,
        String commitSha,
        String shortSha
    ) {
        public static AnalysisRunResponse from(AnalysisRunRepository.RunRow row, String commitSha) {
            String shortSha = commitSha != null && commitSha.length() >= 7 ? commitSha.substring(0, 7) : commitSha;
            return new AnalysisRunResponse(
                row.id(), row.snapshotId(), row.candidateId(), row.projectId(), row.repositoryId(),
                row.analyzerVersion(), row.status(), row.startedAt(), row.completedAt(),
                row.failureCode(), row.failureMessage(), row.observationCount(),
                row.createdAt(), row.updatedAt(), commitSha, shortSha
            );
        }
    }

    public record ObservationResponse(
        UUID observationId,
        UUID analysisRunId,
        UUID snapshotId,
        String observationType,
        String category,
        String factKey,
        String factValue,
        String language,
        String framework,
        String sourcePath,
        Integer startLine,
        Integer endLine,
        String symbol,
        String sourceHash,
        String detector,
        String detectorVersion,
        String confidence,
        String origin,
        Instant createdAt
    ) {
        public static ObservationResponse from(AnalysisObservationRepository.ObservationRow row) {
            return new ObservationResponse(
                row.id(), row.analysisRunId(), row.snapshotId(),
                row.observationType(), row.category(), row.factKey(), row.factValue(),
                row.language(), row.framework(), row.sourcePath(),
                row.startLine(), row.endLine(), row.symbol(), row.sourceHash(),
                row.detector(), row.detectorVersion(), row.confidence(), row.origin(),
                row.createdAt()
            );
        }
    }

    public record FileErrorResponse(
        UUID errorId,
        UUID analysisRunId,
        UUID snapshotId,
        String sourcePath,
        String errorCode,
        String errorMessage,
        String detector,
        Instant createdAt
    ) {
        public static FileErrorResponse from(AnalysisFileErrorRepository.FileErrorRow row) {
            return new FileErrorResponse(
                row.id(), row.analysisRunId(), row.snapshotId(),
                row.sourcePath(), row.errorCode(), row.errorMessage(),
                row.detector(), row.createdAt()
            );
        }
    }

    public record AnalysisDetailResponse(
        AnalysisRunResponse run,
        List<ObservationResponse> observations,
        List<FileErrorResponse> fileErrors,
        ObservationSummary summary
    ) {}

    public record ObservationSummary(
        int totalObservations,
        int languageCount,
        int frameworkCount,
        int dependencyCount,
        int endpointCount,
        int databaseCount,
        int securityCount,
        int testCount,
        int devopsCount,
        List<String> languages,
        List<String> frameworks
    ) {}

    public record GroupedObservations(
        List<ObservationResponse> languages,
        List<ObservationResponse> frameworks,
        List<ObservationResponse> dependencies,
        List<ObservationResponse> endpoints,
        List<ObservationResponse> database,
        List<ObservationResponse> security,
        List<ObservationResponse> testing,
        List<ObservationResponse> devops,
        List<ObservationResponse> other
    ) {
        public static GroupedObservations from(List<ObservationResponse> all) {
            return new GroupedObservations(
                all.stream().filter(o -> "LANGUAGE".equals(o.category())).toList(),
                all.stream().filter(o -> "FRAMEWORK".equals(o.category())).toList(),
                all.stream().filter(o -> "DEPENDENCY".equals(o.category())).toList(),
                all.stream().filter(o -> "API".equals(o.category())).toList(),
                all.stream().filter(o -> "DATABASE".equals(o.category())).toList(),
                all.stream().filter(o -> "SECURITY".equals(o.category())).toList(),
                all.stream().filter(o -> "TESTING".equals(o.category())).toList(),
                all.stream().filter(o -> "DEVOPS".equals(o.category())).toList(),
                all.stream().filter(o -> List.of("LANGUAGE","FRAMEWORK","DEPENDENCY","API","DATABASE","SECURITY","TESTING","DEVOPS").stream().noneMatch(c -> c.equals(o.category()))).toList()
            );
        }
    }
}
