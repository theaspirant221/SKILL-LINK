package com.skilllink.api.analysis.detector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skilllink.api.analysis.AnalysisObservationRepository;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ManifestDetector {
    public static final String DETECTOR = "MANIFEST_DETECTOR";
    public static final String VERSION = "deterministic-v1";

    private final ObjectMapper mapper = new ObjectMapper();
    private static final Pattern MAVEN_DEP = Pattern.compile("<artifactId>\\s*([^<]+)\\s*</artifactId>\\s*<version>\\s*([^<]+)\\s*</version>|<artifactId>\\s*([^<]+)\\s*</artifactId>");
    private static final Pattern GRADLE_DEP = Pattern.compile("(?:implementation|api|compile|testImplementation|runtimeOnly)\\s*[\\(]?\\s*['\"]([^:]+):([^:]+):([^'\"]+)['\"]|['\"]([^:]+):([^:]+)['\"]");
    private static final Pattern DOCKER_FROM = Pattern.compile("(?i)^FROM\\s+([^\\s]+)");

    public List<AnalysisObservationRepository.ObservationRow> detect(
        UUID runId,
        UUID snapshotId,
        List<LanguageDetector.SnapshotFile> files
    ) {
        List<AnalysisObservationRepository.ObservationRow> obs = new ArrayList<>();
        for (LanguageDetector.SnapshotFile file : files) {
            String path = file.path();
            String content = file.content();
            if (content == null) continue;
            String lower = path.toLowerCase();
            if (lower.endsWith("pom.xml")) {
                obs.addAll(parsePom(runId, snapshotId, file));
            } else if (lower.endsWith("build.gradle") || lower.endsWith("build.gradle.kts")) {
                obs.addAll(parseGradle(runId, snapshotId, file));
            } else if (lower.endsWith("package.json")) {
                obs.addAll(parsePackageJson(runId, snapshotId, file));
            } else if (lower.endsWith("requirements.txt")) {
                obs.addAll(parseRequirements(runId, snapshotId, file));
            } else if (lower.endsWith("pyproject.toml")) {
                obs.addAll(parsePyproject(runId, snapshotId, file));
            } else if (lower.endsWith("go.mod")) {
                obs.addAll(parseGoMod(runId, snapshotId, file));
            } else if (lower.endsWith("cargo.toml")) {
                obs.addAll(parseCargo(runId, snapshotId, file));
            } else if (lower.endsWith("composer.json")) {
                obs.addAll(parseComposer(runId, snapshotId, file));
            } else if (lower.equals("dockerfile") || lower.endsWith("/dockerfile")) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "DOCKERFILE_PRESENT", "DEVOPS", "Dockerfile", "present",
                    null, "Docker", path, null, null, "Dockerfile", file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
                // Parse FROM
                for (String line : content.split("\n")) {
                    Matcher m = DOCKER_FROM.matcher(line.trim());
                    if (m.find()) {
                        String base = m.group(1);
                        obs.add(new AnalysisObservationRepository.ObservationRow(
                            UUID.randomUUID(), runId, snapshotId,
                            "DOCKER_BASE_IMAGE", "DEVOPS", "base_image", base,
                            null, "Docker", path, null, null, "FROM", file.contentHash(),
                            DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                        ));
                    }
                }
            } else if (lower.contains(".github/workflows/") && (lower.endsWith(".yml") || lower.endsWith(".yaml"))) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "CI_CONFIGURATION", "DEVOPS", "github_actions", path,
                    "YAML", null, path, null, null, path, file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
                // Extract workflow name if present
                for (String line : content.split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("name:")) {
                        String name = trimmed.substring(5).trim().replaceAll("[\"']", "");
                        obs.add(new AnalysisObservationRepository.ObservationRow(
                            UUID.randomUUID(), runId, snapshotId,
                            "CI_WORKFLOW_NAME", "DEVOPS", "workflow_name", name,
                            "YAML", null, path, null, null, name, file.contentHash(),
                            DETECTOR, VERSION, "MEDIUM", "DETERMINISTIC", null
                        ));
                        break;
                    }
                }
            }
        }
        return obs;
    }

    private List<AnalysisObservationRepository.ObservationRow> parsePom(UUID runId, UUID snapshotId, LanguageDetector.SnapshotFile file) {
        List<AnalysisObservationRepository.ObservationRow> obs = new ArrayList<>();
        String content = file.content();
        // Very simple regex based parsing for deterministic facts
        Pattern artifactPattern = Pattern.compile("<artifactId>([^<]+)</artifactId>\\s*(?:<version>([^<]+)</version>)?");
        Matcher m = artifactPattern.matcher(content);
        Set<String> seen = new HashSet<>();
        while (m.find()) {
            String artifact = m.group(1).trim();
            String version = m.group(2) != null ? m.group(2).trim() : null;
            if (seen.contains(artifact)) continue;
            seen.add(artifact);
            String lower = artifact.toLowerCase();
            String framework = null;
            if (lower.contains("spring-boot")) framework = "Spring Boot";
            else if (lower.contains("spring-security")) framework = "Spring Security";
            else if (lower.contains("spring-data-jpa")) framework = "Spring Data JPA";
            else if (lower.contains("postgresql")) framework = "PostgreSQL";
            else if (lower.contains("junit")) framework = "JUnit";
            else if (lower.contains("testcontainers")) framework = "Testcontainers";
            else if (lower.contains("mockito")) framework = "Mockito";
            else if (lower.contains("jjwt") || lower.contains("jwt")) framework = "JWT";

            String factValue = version != null ? version : "declared";
            obs.add(new AnalysisObservationRepository.ObservationRow(
                UUID.randomUUID(), runId, snapshotId,
                "DEPENDENCY_DECLARED", "DEPENDENCY", artifact, factValue,
                "Java", framework, file.path(), null, null, artifact, file.contentHash(),
                DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
            ));
        }
        // Framework detection from pom
        if (content.contains("spring-boot-starter-web")) {
            obs.add(new AnalysisObservationRepository.ObservationRow(
                UUID.randomUUID(), runId, snapshotId,
                "FRAMEWORK_PRESENT", "FRAMEWORK", "Spring Boot", "spring-boot-starter-web",
                "Java", "Spring Boot", file.path(), null, null, "spring-boot-starter-web", file.contentHash(),
                DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
            ));
        }
        if (content.contains("spring-boot-starter-security")) {
            obs.add(new AnalysisObservationRepository.ObservationRow(
                UUID.randomUUID(), runId, snapshotId,
                "FRAMEWORK_PRESENT", "FRAMEWORK", "Spring Security", "spring-boot-starter-security",
                "Java", "Spring Security", file.path(), null, null, "spring-security", file.contentHash(),
                DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
            ));
        }
        return obs;
    }

    private List<AnalysisObservationRepository.ObservationRow> parseGradle(UUID runId, UUID snapshotId, LanguageDetector.SnapshotFile file) {
        List<AnalysisObservationRepository.ObservationRow> obs = new ArrayList<>();
        String content = file.content();
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("implementation") || trimmed.startsWith("api") || trimmed.startsWith("testImplementation")) {
                // Extract artifact
                Matcher m = GRADLE_DEP.matcher(trimmed);
                if (m.find()) {
                    String group = m.group(1) != null ? m.group(1) : m.group(4);
                    String artifact = m.group(2) != null ? m.group(2) : m.group(5);
                    String version = m.group(3);
                    if (artifact != null) {
                        obs.add(new AnalysisObservationRepository.ObservationRow(
                            UUID.randomUUID(), runId, snapshotId,
                            "DEPENDENCY_DECLARED", "DEPENDENCY", artifact, version != null ? version : "declared",
                            "Java", null, file.path(), null, null, artifact, file.contentHash(),
                            DETECTOR, VERSION, "MEDIUM", "DETERMINISTIC", null
                        ));
                    }
                }
            }
        }
        return obs;
    }

    private List<AnalysisObservationRepository.ObservationRow> parsePackageJson(UUID runId, UUID snapshotId, LanguageDetector.SnapshotFile file) {
        List<AnalysisObservationRepository.ObservationRow> obs = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(file.content());
            JsonNode deps = root.path("dependencies");
            JsonNode devDeps = root.path("devDependencies");
            parseJsonDeps(runId, snapshotId, file, deps, obs);
            parseJsonDeps(runId, snapshotId, file, devDeps, obs);

            // Framework detection
            if (deps.has("react") || devDeps.has("react")) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "FRAMEWORK_PRESENT", "FRAMEWORK", "React", deps.has("react") ? deps.get("react").asText() : devDeps.get("react").asText(),
                    "JavaScript", "React", file.path(), null, null, "react", file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
            }
            if (deps.has("express") || devDeps.has("express")) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "FRAMEWORK_PRESENT", "FRAMEWORK", "Express", "present",
                    "JavaScript", "Express", file.path(), null, null, "express", file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
            }
            if (deps.has("vite") || devDeps.has("vite")) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "BUILD_TOOL", "DEVOPS", "Vite", "present",
                    "JavaScript", "Vite", file.path(), null, null, "vite", file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
            }
        } catch (Exception ignored) {}
        return obs;
    }

    private void parseJsonDeps(UUID runId, UUID snapshotId, LanguageDetector.SnapshotFile file, JsonNode deps, List<AnalysisObservationRepository.ObservationRow> obs) {
        if (deps.isObject()) {
            deps.fields().forEachRemaining(entry -> {
                String name = entry.getKey();
                String version = entry.getValue().asText();
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "DEPENDENCY_DECLARED", "DEPENDENCY", name, version,
                    "JavaScript", null, file.path(), null, null, name, file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
            });
        }
    }

    private List<AnalysisObservationRepository.ObservationRow> parseRequirements(UUID runId, UUID snapshotId, LanguageDetector.SnapshotFile file) {
        List<AnalysisObservationRepository.ObservationRow> obs = new ArrayList<>();
        for (String line : file.content().split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            String[] parts = trimmed.split("==|>=|<=|~=|>|<");
            String name = parts[0].trim();
            String version = parts.length > 1 ? parts[1].trim() : "declared";
            obs.add(new AnalysisObservationRepository.ObservationRow(
                UUID.randomUUID(), runId, snapshotId,
                "DEPENDENCY_DECLARED", "DEPENDENCY", name, version,
                "Python", null, file.path(), null, null, name, file.contentHash(),
                DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
            ));
        }
        return obs;
    }

    private List<AnalysisObservationRepository.ObservationRow> parsePyproject(UUID runId, UUID snapshotId, LanguageDetector.SnapshotFile file) {
        List<AnalysisObservationRepository.ObservationRow> obs = new ArrayList<>();
        String content = file.content();
        // Simple toml parsing for dependencies
        boolean inDeps = false;
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[tool.poetry.dependencies]") || trimmed.startsWith("[project.dependencies]") || trimmed.equals("dependencies = [")) {
                inDeps = true;
                continue;
            }
            if (inDeps) {
                if (trimmed.startsWith("[") || trimmed.isEmpty()) {
                    if (trimmed.startsWith("[")) inDeps = false;
                    continue;
                }
                if (trimmed.contains("=")) {
                    String[] parts = trimmed.split("=");
                    String name = parts[0].trim().replaceAll("[\"']", "");
                    String version = parts.length > 1 ? parts[1].trim().replaceAll("[\",']", "") : "declared";
                    if (!name.isEmpty() && !name.startsWith("#")) {
                        obs.add(new AnalysisObservationRepository.ObservationRow(
                            UUID.randomUUID(), runId, snapshotId,
                            "DEPENDENCY_DECLARED", "DEPENDENCY", name, version,
                            "Python", null, file.path(), null, null, name, file.contentHash(),
                            DETECTOR, VERSION, "MEDIUM", "DETERMINISTIC", null
                        ));
                    }
                }
            }
        }
        return obs;
    }

    private List<AnalysisObservationRepository.ObservationRow> parseGoMod(UUID runId, UUID snapshotId, LanguageDetector.SnapshotFile file) {
        List<AnalysisObservationRepository.ObservationRow> obs = new ArrayList<>();
        for (String line : file.content().split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("require") || trimmed.contains(" ")) {
                String[] parts = trimmed.split("\\s+");
                if (parts.length >= 2) {
                    String name = parts[0].replace("require", "").trim();
                    String version = parts.length > 1 ? parts[1] : "declared";
                    if (!name.isEmpty() && !name.startsWith("//")) {
                        obs.add(new AnalysisObservationRepository.ObservationRow(
                            UUID.randomUUID(), runId, snapshotId,
                            "DEPENDENCY_DECLARED", "DEPENDENCY", name, version,
                            "Go", null, file.path(), null, null, name, file.contentHash(),
                            DETECTOR, VERSION, "MEDIUM", "DETERMINISTIC", null
                        ));
                    }
                }
            }
        }
        return obs;
    }

    private List<AnalysisObservationRepository.ObservationRow> parseCargo(UUID runId, UUID snapshotId, LanguageDetector.SnapshotFile file) {
        List<AnalysisObservationRepository.ObservationRow> obs = new ArrayList<>();
        boolean inDeps = false;
        for (String line : file.content().split("\n")) {
            String trimmed = line.trim();
            if (trimmed.equals("[dependencies]")) { inDeps = true; continue; }
            if (trimmed.startsWith("[") && !trimmed.equals("[dependencies]")) { inDeps = false; continue; }
            if (inDeps && trimmed.contains("=")) {
                String[] parts = trimmed.split("=");
                String name = parts[0].trim();
                String version = parts.length > 1 ? parts[1].trim().replaceAll("[\"']", "") : "declared";
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "DEPENDENCY_DECLARED", "DEPENDENCY", name, version,
                    "Rust", null, file.path(), null, null, name, file.contentHash(),
                    DETECTOR, VERSION, "MEDIUM", "DETERMINISTIC", null
                ));
            }
        }
        return obs;
    }

    private List<AnalysisObservationRepository.ObservationRow> parseComposer(UUID runId, UUID snapshotId, LanguageDetector.SnapshotFile file) {
        List<AnalysisObservationRepository.ObservationRow> obs = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(file.content());
            JsonNode deps = root.path("require");
            if (deps.isObject()) {
                deps.fields().forEachRemaining(entry -> {
                    String name = entry.getKey();
                    String version = entry.getValue().asText();
                    obs.add(new AnalysisObservationRepository.ObservationRow(
                        UUID.randomUUID(), runId, snapshotId,
                        "DEPENDENCY_DECLARED", "DEPENDENCY", name, version,
                        "PHP", null, file.path(), null, null, name, file.contentHash(),
                        DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                    ));
                });
            }
        } catch (Exception ignored) {}
        return obs;
    }
}
