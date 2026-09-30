package com.skilllink.api.analysis.detector;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ManifestDetectorTest {
    private final ManifestDetector detector = new ManifestDetector();

    @Test
    void parsesPomDependencies() {
        UUID runId = UUID.randomUUID();
        UUID snapId = UUID.randomUUID();
        String pom = """
            <project>
              <dependencies>
                <dependency><artifactId>spring-boot-starter-web</artifactId><version>3.2.0</version></dependency>
                <dependency><artifactId>spring-boot-starter-security</artifactId><version>3.2.0</version></dependency>
                <dependency><artifactId>postgresql</artifactId><version>42.6.0</version></dependency>
                <dependency><artifactId>junit</artifactId><version>5.10.0</version></dependency>
              </dependencies>
            </project>
            """;
        var files = List.of(new LanguageDetector.SnapshotFile("pom.xml", pom, null, 100, "blob", "hash"));
        var obs = detector.detect(runId, snapId, files);

        assertThat(obs).anyMatch(o -> "DEPENDENCY_DECLARED".equals(o.observationType()) && "spring-boot-starter-web".equals(o.factKey()));
        assertThat(obs).anyMatch(o -> "DEPENDENCY_DECLARED".equals(o.observationType()) && "postgresql".equals(o.factKey()));
        assertThat(obs).anyMatch(o -> "FRAMEWORK_PRESENT".equals(o.observationType()) && "Spring Boot".equals(o.factKey()));
    }

    @Test
    void parsesPackageJson() {
        UUID runId = UUID.randomUUID();
        UUID snapId = UUID.randomUUID();
        String pkg = """
            {
              "dependencies": {
                "react": "^18.0.0",
                "express": "^4.18.0"
              },
              "devDependencies": {
                "vite": "^5.0.0"
              }
            }
            """;
        var files = List.of(new LanguageDetector.SnapshotFile("package.json", pkg, null, 100, "blob", "hash"));
        var obs = detector.detect(runId, snapId, files);

        assertThat(obs).anyMatch(o -> "DEPENDENCY_DECLARED".equals(o.observationType()) && "react".equals(o.factKey()));
        assertThat(obs).anyMatch(o -> "FRAMEWORK_PRESENT".equals(o.observationType()) && "React".equals(o.factKey()));
        assertThat(obs).anyMatch(o -> "BUILD_TOOL".equals(o.observationType()) && "Vite".equals(o.factKey()));
    }

    @Test
    void detectsDockerfileAndGithubActions() {
        UUID runId = UUID.randomUUID();
        UUID snapId = UUID.randomUUID();
        var files = List.of(
            new LanguageDetector.SnapshotFile("Dockerfile", "FROM openjdk:21\nCOPY .", null, 100, "blob1", "hash1"),
            new LanguageDetector.SnapshotFile(".github/workflows/backend.yml", "name: backend\non: push", null, 100, "blob2", "hash2")
        );
        var obs = detector.detect(runId, snapId, files);

        assertThat(obs).anyMatch(o -> "DOCKERFILE_PRESENT".equals(o.observationType()));
        assertThat(obs).anyMatch(o -> "DOCKER_BASE_IMAGE".equals(o.observationType()) && "openjdk:21".equals(o.factValue()));
        assertThat(obs).anyMatch(o -> "CI_CONFIGURATION".equals(o.observationType()));
    }
}
