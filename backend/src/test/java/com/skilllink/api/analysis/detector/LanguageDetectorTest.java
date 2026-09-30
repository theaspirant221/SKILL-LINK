package com.skilllink.api.analysis.detector;

import com.skilllink.api.analysis.AnalysisObservationRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageDetectorTest {
    private final LanguageDetector detector = new LanguageDetector();

    @Test
    void detectsLanguagesAndFileCounts() {
        UUID runId = UUID.randomUUID();
        UUID snapId = UUID.randomUUID();
        var files = List.of(
            new LanguageDetector.SnapshotFile("src/App.java", "public class App {}", "Java", 100, "blob1", "hash1"),
            new LanguageDetector.SnapshotFile("src/Other.java", "public class Other {}", "Java", 200, "blob2", "hash2"),
            new LanguageDetector.SnapshotFile("README.md", "# readme", null, 50, "blob3", "hash3"),
            new LanguageDetector.SnapshotFile("pom.xml", "<xml>", null, 100, "blob4", "hash4")
        );

        var obs = detector.detect(runId, snapId, files);

        assertThat(obs).anyMatch(o -> "LANGUAGE_PRESENT".equals(o.observationType()) && "Java".equals(o.factKey()));
        assertThat(obs).anyMatch(o -> "FILE_COUNT_BY_LANGUAGE".equals(o.observationType()) && "Java".equals(o.factKey()) && "2".equals(o.factValue()));
        assertThat(obs).anyMatch(o -> "SOURCE_LINE_COUNT".equals(o.observationType()) && "Java".equals(o.factKey()));
    }

    @Test
    void detectsMultipleLanguages() {
        UUID runId = UUID.randomUUID();
        UUID snapId = UUID.randomUUID();
        var files = List.of(
            new LanguageDetector.SnapshotFile("src/App.java", "class", "Java", 100, "b1", "h1"),
            new LanguageDetector.SnapshotFile("frontend/App.tsx", "react", "TypeScript", 100, "b2", "h2"),
            new LanguageDetector.SnapshotFile("script.py", "print", "Python", 100, "b3", "h3")
        );

        var obs = detector.detect(runId, snapId, files);
        assertThat(obs).anyMatch(o -> "LANGUAGE_PRESENT".equals(o.observationType()) && "Java".equals(o.factKey()));
        assertThat(obs).anyMatch(o -> "LANGUAGE_PRESENT".equals(o.observationType()) && "TypeScript".equals(o.factKey()));
        assertThat(obs).anyMatch(o -> "LANGUAGE_PRESENT".equals(o.observationType()) && "Python".equals(o.factKey()));
    }
}
