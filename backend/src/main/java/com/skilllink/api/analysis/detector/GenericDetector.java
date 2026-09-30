package com.skilllink.api.analysis.detector;

import com.skilllink.api.analysis.AnalysisObservationRepository;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GenericDetector {
    public static final String DETECTOR = "GENERIC_DETECTOR";
    public static final String VERSION = "deterministic-v1";

    public List<AnalysisObservationRepository.ObservationRow> detect(
        UUID runId,
        UUID snapshotId,
        List<LanguageDetector.SnapshotFile> files
    ) {
        List<AnalysisObservationRepository.ObservationRow> obs = new ArrayList<>();
        for (LanguageDetector.SnapshotFile file : files) {
            String path = file.path();
            String lower = path.toLowerCase();
            String content = file.content();
            if (content == null) continue;

            // Detect framework markers via file content
            if (lower.endsWith(".py")) {
                if (content.contains("from flask") || content.contains("import flask") || content.contains("Flask(")) {
                    obs.add(observation(runId, snapshotId, "FRAMEWORK_PRESENT", "FRAMEWORK", "Flask", "present",
                        "Python", "Flask", path, file.contentHash()));
                }
                if (content.contains("from fastapi") || content.contains("FastAPI(")) {
                    obs.add(observation(runId, snapshotId, "FRAMEWORK_PRESENT", "FRAMEWORK", "FastAPI", "present",
                        "Python", "FastAPI", path, file.contentHash()));
                }
                if (content.contains("from django") || content.contains("import django")) {
                    obs.add(observation(runId, snapshotId, "FRAMEWORK_PRESENT", "FRAMEWORK", "Django", "present",
                        "Python", "Django", path, file.contentHash()));
                }
            }

            if (lower.endsWith(".js") || lower.endsWith(".jsx") || lower.endsWith(".ts") || lower.endsWith(".tsx")) {
                if (content.contains("from 'react'") || content.contains("from \"react\"") || content.contains("React.")) {
                    obs.add(observation(runId, snapshotId, "FRAMEWORK_PRESENT", "FRAMEWORK", "React", "present",
                        "JavaScript", "React", path, file.contentHash()));
                }
                if (content.contains("express") && (content.contains(".get(") || content.contains(".post("))) {
                    obs.add(observation(runId, snapshotId, "FRAMEWORK_PRESENT", "FRAMEWORK", "Express", "present",
                        "JavaScript", "Express", path, file.contentHash()));
                }
            }

            // Test files
            if (lower.contains("/test/") || lower.contains("/tests/") || lower.endsWith("_test.py") || lower.endsWith(".test.js") || lower.endsWith(".spec.ts")) {
                obs.add(observation(runId, snapshotId, "TEST_FILE", "TESTING", "test_file", path,
                    detectLanguage(lower), null, path, file.contentHash()));
            }

            // Config files
            if (lower.endsWith(".sql")) {
                obs.add(observation(runId, snapshotId, "SQL_FILE", "DATABASE", "sql_file", path,
                    "SQL", null, path, file.contentHash()));
            }

            // Mark depth
            String depth = "SHALLOW";
            if (lower.endsWith(".java")) depth = "DEEP";
            else if (lower.endsWith("pom.xml") || lower.endsWith("package.json") || lower.endsWith("requirements.txt")) depth = "MANIFEST_ONLY";

            obs.add(new AnalysisObservationRepository.ObservationRow(
                UUID.randomUUID(), runId, snapshotId,
                "ANALYZER_DEPTH", "SYSTEM", path, depth,
                detectLanguage(lower), null, path, null, null, path, file.contentHash(),
                DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
            ));
        }
        return obs;
    }

    private String detectLanguage(String lower) {
        if (lower.endsWith(".java")) return "Java";
        if (lower.endsWith(".kt")) return "Kotlin";
        if (lower.endsWith(".js") || lower.endsWith(".jsx")) return "JavaScript";
        if (lower.endsWith(".ts") || lower.endsWith(".tsx")) return "TypeScript";
        if (lower.endsWith(".py")) return "Python";
        if (lower.endsWith(".php")) return "PHP";
        if (lower.endsWith(".go")) return "Go";
        if (lower.endsWith(".rs")) return "Rust";
        if (lower.endsWith(".sql")) return "SQL";
        return null;
    }

    private AnalysisObservationRepository.ObservationRow observation(
        UUID runId, UUID snapshotId, String type, String category, String factKey, String factValue,
        String language, String framework, String path, String contentHash
    ) {
        return new AnalysisObservationRepository.ObservationRow(
            UUID.randomUUID(), runId, snapshotId,
            type, category, factKey, factValue,
            language, framework, path, null, null, factKey, contentHash,
            DETECTOR, VERSION, "MEDIUM", "DETERMINISTIC", null
        );
    }
}
