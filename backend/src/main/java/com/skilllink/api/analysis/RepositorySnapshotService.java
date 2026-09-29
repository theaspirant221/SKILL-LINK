package com.skilllink.api.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skilllink.api.github.GithubClient;
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
import java.util.Map;
import java.util.UUID;

@Service
public class RepositorySnapshotService {
    private final GithubClient github;
    private final GithubOAuthService githubOAuth;
    private final ProjectService projects;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RepositoryFilePolicy policy;
    private final SecretRedactor redactor;

    public RepositorySnapshotService(GithubClient github, GithubOAuthService githubOAuth, ProjectService projects, JdbcTemplate jdbc, ObjectMapper objectMapper, RepositoryFilePolicy policy, SecretRedactor redactor) { this.github = github; this.githubOAuth = githubOAuth; this.projects = projects; this.jdbc = jdbc; this.objectMapper = objectMapper; this.policy = policy; this.redactor = redactor; }

    @Transactional
    public AnalysisModels.FetchedSnapshot fetch(AnalysisJobRepository.JobRecord job) {
        GithubRepositoryJdbcRepository.RepositoryRow repository = projects.repository(job.candidateId(), job.projectId());
        String token = githubOAuth.accessToken(job.candidateId());
        JsonNode commit = github.commit(repository.fullName(), repository.defaultBranch() == null ? "main" : repository.defaultBranch(), token);
        String commitSha = commit.path("sha").asText();
        String treeSha = commit.path("commit").path("tree").path("sha").asText();
        if (commitSha.isBlank() || treeSha.isBlank()) throw new RepositoryAnalysisException("SNAPSHOT_COMMIT_UNRESOLVED", "GitHub did not return a commit snapshot for this branch.");

        ExistingSnapshot existing = findExisting(repository.id(), commitSha);
        if (existing != null && "COMPLETED".equals(existing.status())) return new AnalysisModels.FetchedSnapshot(existing.id(), repository.id(), repository.projectId(), repository.fullName(), repository.defaultBranch(), commitSha, existing.treeSha(), List.of(), existing.treeEntries(), existing.contentSize(), existing.redactedValues(), true);

        UUID snapshotId = existing == null ? insertSnapshot(repository.id(), commitSha, repository.defaultBranch(), treeSha) : existing.id();
        updateSnapshotStatus(snapshotId, "FETCHING");
        JsonNode tree = github.tree(repository.fullName(), treeSha, token);
        if (tree.path("truncated").asBoolean(false) || tree.path("tree").size() > policy.maxTreeEntries()) throw new RepositoryAnalysisException("REPOSITORY_TREE_TOO_LARGE", "Repository tree is larger than the configured analysis limit.");
        List<AnalysisModels.TreeEntry> allEntries = new ArrayList<>();
        tree.path("tree").forEach(item -> { if ("blob".equals(item.path("type").asText())) allEntries.add(new AnalysisModels.TreeEntry(item.path("path").asText(), item.path("type").asText(), item.path("sha").asText(), item.path("size").asLong(0))); });
        List<AnalysisModels.TreeEntry> entries = allEntries.stream().filter(item -> policy.allowed(item.path(), item.size()) && policy.relevant(item.path())).sorted(Comparator.comparingInt(this::priority).thenComparing(AnalysisModels.TreeEntry::path)).limit(policy.maxContextFiles()).toList();
        List<AnalysisModels.SourceFile> files = new ArrayList<>(); long bytes = 0; int redactedValues = 0;
        for (AnalysisModels.TreeEntry entry : entries) {
            if (bytes + entry.size() > policy.maxRepositoryBytes()) break;
            String base64 = github.blob(repository.fullName(), entry.sha(), token);
            byte[] raw;
            try { raw = Base64.getDecoder().decode(base64.replaceAll("\\s", "")); } catch (IllegalArgumentException ignored) { continue; }
            if (raw.length > policy.maxRepositoryBytes() || bytes + raw.length > policy.maxRepositoryBytes()) break;
            String content = new String(raw, StandardCharsets.UTF_8);
            SecretRedactor.Redacted safe = redactor.redact(content);
            redactedValues += safe.count(); bytes += raw.length;
            files.add(new AnalysisModels.SourceFile(entry.path(), entry.sha(), raw.length, safe.content(), language(entry.path()), safe.count()));
        }
        String sourceHash = sha256Hex(entries.stream().map(item -> item.path() + ":" + item.sha()).reduce("", String::concat));
        String redactionJson = writeJson(Map.of("redactedValues", redactedValues, "excludedFiles", tree.path("tree").size() - files.size()));
        jdbc.update("""
            UPDATE repository_snapshot SET source_hash = ?, tree_sha = ?, file_count = ?, selected_file_count = ?, content_size_bytes = ?, redaction_summary = ?::jsonb, source_observed_at = now(), analysis_status = 'FETCHING', analysis_started_at = coalesce(analysis_started_at, now()) WHERE id = ?
            """, sourceHash, treeSha, entries.size(), files.size(), bytes, redactionJson, snapshotId);
        return new AnalysisModels.FetchedSnapshot(snapshotId, repository.id(), repository.projectId(), repository.fullName(), repository.defaultBranch(), commitSha, treeSha, files, entries.size(), bytes, redactedValues, false);
    }

    public void markCompleted(UUID snapshotId, AnalysisModels.AnalysisOutput output) { jdbc.update("UPDATE repository_snapshot SET analysis_status = 'COMPLETED', analysis_completed_at = now(), deterministic_facts = ?::jsonb, file_count = ?, selected_file_count = ?, content_size_bytes = ? WHERE id = ?", writeJson(Map.of("languages", output.languages(), "frameworks", output.frameworks(), "summary", output.summary(), "redactedValues", output.redactedValues())), output.fileCount(), output.fileCount(), output.contentSizeBytes(), snapshotId); }
    public void markFailed(UUID snapshotId) { jdbc.update("UPDATE repository_snapshot SET analysis_status = 'FAILED' WHERE id = ?", snapshotId); }
    public void updateSnapshotStatus(UUID snapshotId, String status) { jdbc.update("UPDATE repository_snapshot SET analysis_status = ? WHERE id = ?", status, snapshotId); }

    private ExistingSnapshot findExisting(UUID repositoryId, String commitSha) { try { return jdbc.queryForObject("SELECT id, analysis_status, tree_sha, file_count, content_size_bytes, coalesce((redaction_summary->>'redactedValues')::int, 0) redacted_values FROM repository_snapshot WHERE repository_id = ? AND commit_sha = ?", (rs, rowNum) -> new ExistingSnapshot(rs.getObject("id", UUID.class), rs.getString("analysis_status"), rs.getString("tree_sha"), rs.getInt("file_count"), rs.getLong("content_size_bytes"), rs.getInt("redacted_values")), repositoryId, commitSha); } catch (org.springframework.dao.EmptyResultDataAccessException ex) { return null; } }
    private UUID insertSnapshot(UUID repositoryId, String commitSha, String branch, String treeSha) { return jdbc.queryForObject("INSERT INTO repository_snapshot(repository_id, commit_sha, branch, source_hash, source_observed_at, analysis_status, tree_sha) VALUES (?, ?, ?, ?, now(), 'QUEUED', ?) RETURNING id", UUID.class, repositoryId, commitSha, branch, "pending:" + commitSha, treeSha); }
    private int priority(AnalysisModels.TreeEntry entry) { String path = entry.path().toLowerCase(); if (path.endsWith("pom.xml") || path.endsWith("package.json") || path.endsWith("pyproject.toml") || path.endsWith("requirements.txt")) return 0; if (path.contains("security") || path.contains("auth") || path.contains("controller") || path.contains("route")) return 1; if (path.contains("test")) return 2; return 3; }
    private String language(String path) { String p = path.toLowerCase(); if (p.endsWith(".java")) return "Java"; if (p.endsWith(".ts") || p.endsWith(".tsx")) return "TypeScript"; if (p.endsWith(".js") || p.endsWith(".jsx")) return "JavaScript"; if (p.endsWith(".py")) return "Python"; if (p.endsWith(".sql")) return "SQL"; return null; }
    private String writeJson(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private String sha256Hex(String input) { try { byte[] bytes = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)); StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format("%02x", b)); return out.toString(); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private record ExistingSnapshot(UUID id, String status, String treeSha, int treeEntries, long contentSize, int redactedValues) {}
    public static class RepositoryAnalysisException extends RuntimeException { private final String code; public RepositoryAnalysisException(String code, String message) { super(message); this.code = code; } public String code() { return code; } }
}
