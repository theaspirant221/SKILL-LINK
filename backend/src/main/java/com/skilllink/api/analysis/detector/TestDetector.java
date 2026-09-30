package com.skilllink.api.analysis.detector;

import com.skilllink.api.analysis.AnalysisObservationRepository;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class TestDetector {
    public static final String DETECTOR = "TEST_DETECTOR";
    public static final String VERSION = "deterministic-v1";

    public List<AnalysisObservationRepository.ObservationRow> detect(
        UUID runId,
        UUID snapshotId,
        List<LanguageDetector.SnapshotFile> files
    ) {
        List<AnalysisObservationRepository.ObservationRow> obs = new ArrayList<>();
        for (LanguageDetector.SnapshotFile file : files) {
            String path = file.path();
            String lowerPath = path.toLowerCase();
            String content = file.content();
            if (content == null) continue;
            String lowerContent = content.toLowerCase();

            boolean isTestFile = lowerPath.contains("/test/") || lowerPath.contains("/tests/") ||
                lowerPath.endsWith("test.java") || lowerPath.endsWith("tests.java") ||
                lowerPath.endsWith("_test.py") || lowerPath.endsWith(".test.js") ||
                lowerPath.endsWith(".spec.ts") || lowerPath.endsWith(".test.ts") ||
                lowerPath.contains("test") && lowerPath.endsWith(".java");

            if (isTestFile) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "TEST_FILE", "TESTING", "test_file", path,
                    detectLanguage(lowerPath), null, path, null, null, path, file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
            }

            if (lowerContent.contains("import org.junit") || lowerContent.contains("org.junit.jupiter") || lowerContent.contains("@test")) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "TEST_FRAMEWORK", "TESTING", "JUnit", "present",
                    "Java", "JUnit", path, null, null, "JUnit", file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
            }
            if (lowerContent.contains("testng")) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "TEST_FRAMEWORK", "TESTING", "TestNG", "present",
                    "Java", "TestNG", path, null, null, "TestNG", file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
            }
            if (lowerContent.contains("springboottest")) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "TEST_FRAMEWORK", "TESTING", "SpringBootTest", "present",
                    "Java", "Spring Boot Test", path, null, null, "SpringBootTest", file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
            }
            if (lowerContent.contains("webmvctest")) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "TEST_FRAMEWORK", "TESTING", "WebMvcTest", "present",
                    "Java", "Spring Test", path, null, null, "WebMvcTest", file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
            }
            if (lowerContent.contains("datajpatest")) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "TEST_FRAMEWORK", "TESTING", "DataJpaTest", "present",
                    "Java", "Spring Test", path, null, null, "DataJpaTest", file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
            }
            if (lowerContent.contains("testcontainers")) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "TEST_FRAMEWORK", "TESTING", "Testcontainers", "present",
                    "Java", "Testcontainers", path, null, null, "Testcontainers", file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
            }
            if (lowerContent.contains("mockito") || lowerContent.contains("@mock") || lowerContent.contains("mockbean") || lowerContent.contains("@mockbean")) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "TEST_FRAMEWORK", "TESTING", "Mockito", "present",
                    "Java", "Mockito", path, null, null, "Mockito", file.contentHash(),
                    DETECTOR, VERSION, "MEDIUM", "DETERMINISTIC", null
                ));
            }
            if (lowerContent.contains("jest")) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "TEST_FRAMEWORK", "TESTING", "Jest", "present",
                    "JavaScript", "Jest", path, null, null, "Jest", file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
            }
            if (lowerContent.contains("pytest")) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "TEST_FRAMEWORK", "TESTING", "Pytest", "present",
                    "Python", "Pytest", path, null, null, "Pytest", file.contentHash(),
                    DETECTOR, VERSION, "HIGH", "DETERMINISTIC", null
                ));
            }

            // Count test methods
            long testCount = content.split("@Test").length - 1;
            if (testCount > 0) {
                obs.add(new AnalysisObservationRepository.ObservationRow(
                    UUID.randomUUID(), runId, snapshotId,
                    "TEST_METHOD_COUNT", "TESTING", "test_methods", String.valueOf(testCount),
                    detectLanguage(lowerPath), "JUnit", path, null, null, "test_methods", file.contentHash(),
                    DETECTOR, VERSION, "MEDIUM", "DETERMINISTIC", null
                ));
            }
        }
        return obs;
    }

    private String detectLanguage(String lower) {
        if (lower.endsWith(".java")) return "Java";
        if (lower.endsWith(".py")) return "Python";
        if (lower.endsWith(".js") || lower.endsWith(".jsx")) return "JavaScript";
        if (lower.endsWith(".ts") || lower.endsWith(".tsx")) return "TypeScript";
        return null;
    }
}
