package com.skilllink.api.snapshot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Checkpoint D file policy: determines which files are included in an immutable snapshot.
 * Includes relevant source/config/documentation, excludes build artifacts, binaries, etc.
 * Enforces resource limits to prevent abusive ingestion.
 */
@Component
public class SnapshotFilePolicy {
    private static final Set<String> IGNORED_DIRS = Set.of(
        ".git", "node_modules", "target", "dist", "build", "out", ".gradle",
        "vendor", "coverage", ".next", ".nuxt", ".output", ".parcel-cache",
        ".turbo", ".svelte-kit", ".vite", ".venv", "__pycache__", ".mypy_cache",
        ".pytest_cache", ".ruff_cache", ".tox", ".nox", ".cache", ".idea", ".vscode"
    );

    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
        ".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".bmp", ".svg",
        ".pdf", ".zip", ".jar", ".class", ".war", ".ear",
        ".woff", ".woff2", ".ttf", ".eot",
        ".mp4", ".mov", ".avi", ".mkv", ".mp3", ".wav", ".flac",
        ".exe", ".dll", ".so", ".dylib",
        ".lock", ".bin"
    );

    private static final Set<String> INCLUDED_EXTENSIONS = Set.of(
        ".java", ".kt", ".kts", ".py", ".js", ".jsx", ".ts", ".tsx",
        ".go", ".rs", ".php", ".cs", ".cpp", ".c", ".h", ".hpp",
        ".sql", ".sh", ".bash",
        ".xml", ".gradle", ".properties", ".yml", ".yaml", ".json", ".toml",
        ".md", ".txt", ".dockerfile"
    );

    private static final Set<String> INCLUDED_EXACT_NAMES = Set.of(
        "pom.xml", "build.gradle", "build.gradle.kts", "package.json",
        "requirements.txt", "pyproject.toml", "Dockerfile", "docker-compose.yml",
        "docker-compose.yaml", "Makefile", "README.md", "README", "readme.md"
    );

    private final long maxFileBytes;
    private final long maxRepositoryBytes;
    private final int maxFiles;
    private final int maxTreeEntries;
    private final int maxDepth;
    private final String version;

    public SnapshotFilePolicy(
        @Value("${skilllink.snapshot.max-file-bytes:500000}") long maxFileBytes,
        @Value("${skilllink.snapshot.max-repository-bytes:20000000}") long maxRepositoryBytes,
        @Value("${skilllink.snapshot.max-files:1000}") int maxFiles,
        @Value("${skilllink.snapshot.max-tree-entries:10000}") int maxTreeEntries,
        @Value("${skilllink.snapshot.max-depth:20}") int maxDepth,
        @Value("${skilllink.snapshot.file-policy-version:v1}") String version
    ) {
        this.maxFileBytes = maxFileBytes;
        this.maxRepositoryBytes = maxRepositoryBytes;
        this.maxFiles = maxFiles;
        this.maxTreeEntries = maxTreeEntries;
        this.maxDepth = maxDepth;
        this.version = version;
    }

    public record PolicyDecision(boolean included, String reason) {}

    public PolicyDecision decide(String path, long size) {
        if (path == null || path.isBlank()) {
            return new PolicyDecision(false, "EMPTY_PATH");
        }
        String normalized = path.replace('\\', '/').trim();
        if (normalized.isEmpty()) {
            return new PolicyDecision(false, "EMPTY_PATH");
        }

        // Depth check
        int depth = normalized.split("/").length;
        if (depth > maxDepth) {
            return new PolicyDecision(false, "MAX_DEPTH_EXCEEDED");
        }

        // Size check
        if (size < 0) {
            return new PolicyDecision(false, "INVALID_SIZE");
        }
        if (size > maxFileBytes) {
            return new PolicyDecision(false, "FILE_TOO_LARGE");
        }

        // Ignored directories
        for (String segment : normalized.split("/")) {
            if (IGNORED_DIRS.contains(segment.toLowerCase(Locale.ROOT))) {
                return new PolicyDecision(false, "IGNORED_DIRECTORY:" + segment);
            }
        }

        // .env and secret files
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith(".env") || lower.contains("/.env") || lower.endsWith(".env")) {
            return new PolicyDecision(false, "ENV_FILE");
        }

        // Binary extensions
        for (String ext : IGNORED_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return new PolicyDecision(false, "BINARY_EXTENSION:" + ext);
            }
        }

        // Check if explicitly included
        String fileName = normalized.contains("/") ? normalized.substring(normalized.lastIndexOf('/') + 1) : normalized;
        String lowerFileName = fileName.toLowerCase(Locale.ROOT);

        if (INCLUDED_EXACT_NAMES.contains(fileName) || INCLUDED_EXACT_NAMES.contains(lowerFileName)) {
            return new PolicyDecision(true, null);
        }

        // Dockerfile (case-insensitive, no extension)
        if (lowerFileName.equals("dockerfile") || lowerFileName.startsWith("dockerfile.")) {
            return new PolicyDecision(true, null);
        }

        // Check included extensions
        for (String ext : INCLUDED_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return new PolicyDecision(true, null);
            }
        }

        // Special: .github/workflows
        if (lower.contains(".github/workflows/")) {
            return new PolicyDecision(true, null);
        }

        // Default: exclude if not explicitly included
        return new PolicyDecision(false, "NOT_RELEVANT");
    }

    public boolean isRelevant(String path) {
        return decide(path, 0).included();
    }

    public String detectLanguage(String path) {
        if (path == null) return null;
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".java")) return "Java";
        if (lower.endsWith(".kt") || lower.endsWith(".kts")) return "Kotlin";
        if (lower.endsWith(".py")) return "Python";
        if (lower.endsWith(".js")) return "JavaScript";
        if (lower.endsWith(".jsx")) return "JavaScript";
        if (lower.endsWith(".ts")) return "TypeScript";
        if (lower.endsWith(".tsx")) return "TypeScript";
        if (lower.endsWith(".go")) return "Go";
        if (lower.endsWith(".rs")) return "Rust";
        if (lower.endsWith(".php")) return "PHP";
        if (lower.endsWith(".cs")) return "C#";
        if (lower.endsWith(".cpp") || lower.endsWith(".c") || lower.endsWith(".h") || lower.endsWith(".hpp")) return "C/C++";
        if (lower.endsWith(".sql")) return "SQL";
        if (lower.endsWith(".xml")) return "XML";
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return "YAML";
        if (lower.endsWith(".json")) return "JSON";
        if (lower.endsWith(".toml")) return "TOML";
        if (lower.endsWith(".md")) return "Markdown";
        if (lower.endsWith(".sh")) return "Shell";
        if (lower.endsWith(".gradle") || lower.endsWith(".properties")) return "Config";
        if (lower.endsWith("dockerfile") || lower.contains("dockerfile")) return "Docker";
        return null;
    }

    public long maxFileBytes() { return maxFileBytes; }
    public long maxRepositoryBytes() { return maxRepositoryBytes; }
    public int maxFiles() { return maxFiles; }
    public int maxTreeEntries() { return maxTreeEntries; }
    public int maxDepth() { return maxDepth; }
    public String version() { return version; }
}
