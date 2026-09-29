package com.skilllink.api.analysis;

import java.util.List;
import java.util.UUID;

public final class AnalysisModels {
    private AnalysisModels() {}
    public record SourceFile(String path, String blobSha, long size, String content, String language, int redactedValues) {}
    public record FetchedSnapshot(UUID snapshotId, UUID repositoryId, UUID projectId, String fullName, String branch, String commitSha, String treeSha, List<SourceFile> files, int treeEntries, long contentSizeBytes, int redactedValues, boolean alreadyAnalyzed) {}
    public record Signal(String skillKey, String sourceType, String sourceReference, String sourceLocation, String observation, String strength, String independentSignal, String blobSha) {}
    public record AnalysisOutput(List<String> languages, List<String> frameworks, List<Signal> signals, int fileCount, int testCount, long contentSizeBytes, int redactedValues, String summary) {}
    public record TreeEntry(String path, String type, String sha, long size) {}
}
