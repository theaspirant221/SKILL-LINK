package com.skilllink.api.analysis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class RepositoryFilePolicy {
    private static final Set<String> IGNORED_DIRS = Set.of(".git", "node_modules", "target", "dist", "build", "out", ".gradle", "vendor", "coverage", ".next", ".venv", "__pycache__");
    private static final Set<String> IGNORED_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".pdf", ".zip", ".jar", ".class", ".woff", ".woff2", ".mp4", ".mov", ".lock");
    private final long maxFileBytes;
    private final long maxRepositoryBytes;
    private final int maxContextFiles;
    private final int maxTreeEntries;

    public RepositoryFilePolicy(@Value("${skilllink.analysis.max-file-bytes:200000}") long maxFileBytes,
                                @Value("${skilllink.analysis.max-repository-bytes:5000000}") long maxRepositoryBytes,
                                @Value("${skilllink.analysis.max-context-files:80}") int maxContextFiles,
                                @Value("${skilllink.analysis.max-tree-entries:5000}") int maxTreeEntries) {
        this.maxFileBytes = maxFileBytes; this.maxRepositoryBytes = maxRepositoryBytes; this.maxContextFiles = maxContextFiles; this.maxTreeEntries = maxTreeEntries;
    }

    public boolean allowed(String path, long size) {
        if (path == null || path.isBlank() || size < 0 || size > maxFileBytes) return false;
        String normalized = path.replace('\\', '/');
        for (String segment : normalized.split("/")) if (IGNORED_DIRS.contains(segment.toLowerCase(Locale.ROOT))) return false;
        if (normalized.startsWith(".env") || normalized.contains("/.env") || normalized.toLowerCase(Locale.ROOT).contains("secret")) return false;
        String lower = normalized.toLowerCase(Locale.ROOT);
        return IGNORED_EXTENSIONS.stream().noneMatch(lower::endsWith);
    }

    public boolean relevant(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.equals("readme.md") || lower.endsWith("/readme.md") || lower.endsWith("pom.xml") || lower.endsWith("build.gradle") || lower.endsWith("build.gradle.kts") || lower.endsWith("package.json") || lower.endsWith("requirements.txt") || lower.endsWith("pyproject.toml") || lower.endsWith("dockerfile") || lower.contains(".github/workflows/") || lower.endsWith(".java") || lower.endsWith(".js") || lower.endsWith(".jsx") || lower.endsWith(".ts") || lower.endsWith(".tsx") || lower.endsWith(".py") || lower.endsWith(".sql") || lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".properties");
    }

    public int maxContextFiles() { return maxContextFiles; }
    public int maxTreeEntries() { return maxTreeEntries; }
    public long maxRepositoryBytes() { return maxRepositoryBytes; }
}
