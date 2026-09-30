package com.skilllink.api.analysis.detector;

import com.skilllink.api.analysis.AnalysisObservationRepository;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class LanguageDetector {
    public static final String DETECTOR = "LANGUAGE_DETECTOR";
    public static final String VERSION = "deterministic-v1";

    public List<AnalysisObservationRepository.ObservationRow> detect(
        UUID runId,
        UUID snapshotId,
        List<SnapshotFile> files
    ) {
        Map<String, Integer> countByLang = new HashMap<>();
        Map<String, Integer> linesByLang = new HashMap<>();
        Set<String> languages = new HashSet<>();

        for (SnapshotFile file : files) {
            String lang = detectLanguage(file.path());
            if (lang != null) {
                languages.add(lang);
                countByLang.merge(lang, 1, Integer::sum);
                // Estimate lines if content available
                if (file.content() != null) {
                    int lines = file.content().split("\n").length;
                    linesByLang.merge(lang, lines, Integer::sum);
                }
            }
        }

        List<AnalysisObservationRepository.ObservationRow> obs = new ArrayList<>();
        for (String lang : languages) {
            obs.add(new AnalysisObservationRepository.ObservationRow(
                UUID.randomUUID(), runId, snapshotId,
                "LANGUAGE_PRESENT", "LANGUAGE", lang, "present",
                lang, null, null, null, null, lang, null,
                DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
            ));
        }
        for (Map.Entry<String, Integer> e : countByLang.entrySet()) {
            obs.add(new AnalysisObservationRepository.ObservationRow(
                UUID.randomUUID(), runId, snapshotId,
                "FILE_COUNT_BY_LANGUAGE", "LANGUAGE", e.getKey(), String.valueOf(e.getValue()),
                e.getKey(), null, null, null, null, e.getKey(), null,
                DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
            ));
        }
        for (Map.Entry<String, Integer> e : linesByLang.entrySet()) {
            obs.add(new AnalysisObservationRepository.ObservationRow(
                UUID.randomUUID(), runId, snapshotId,
                "SOURCE_LINE_COUNT", "LANGUAGE", e.getKey(), String.valueOf(e.getValue()),
                e.getKey(), null, null, null, null, e.getKey(), null,
                DETECTOR, VERSION, "MEDIUM", "DETERMINISTIC", null
            ));
        }
        return obs;
    }

    private String detectLanguage(String path) {
        if (path == null) return null;
        String lower = path.toLowerCase();
        if (lower.endsWith(".java")) return "Java";
        if (lower.endsWith(".kt") || lower.endsWith(".kts")) return "Kotlin";
        if (lower.endsWith(".js")) return "JavaScript";
        if (lower.endsWith(".jsx")) return "JavaScript";
        if (lower.endsWith(".ts")) return "TypeScript";
        if (lower.endsWith(".tsx")) return "TypeScript";
        if (lower.endsWith(".py")) return "Python";
        if (lower.endsWith(".php")) return "PHP";
        if (lower.endsWith(".go")) return "Go";
        if (lower.endsWith(".rs")) return "Rust";
        if (lower.endsWith(".c") || lower.endsWith(".h")) return "C";
        if (lower.endsWith(".cpp") || lower.endsWith(".cc") || lower.endsWith(".cxx") || lower.endsWith(".hpp")) return "C++";
        if (lower.endsWith(".cs")) return "C#";
        if (lower.endsWith(".sql")) return "SQL";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "HTML";
        if (lower.endsWith(".css")) return "CSS";
        if (lower.endsWith(".xml") && (lower.contains("pom.xml") || lower.endsWith(".xml"))) return "XML";
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return "YAML";
        if (lower.endsWith(".json")) return "JSON";
        if (lower.endsWith(".toml")) return "TOML";
        if (lower.endsWith(".gradle")) return "Gradle";
        if (lower.endsWith(".md")) return "Markdown";
        return null;
    }

    public record SnapshotFile(String path, String content, String language, long size, String blobSha, String contentHash) {}
}
