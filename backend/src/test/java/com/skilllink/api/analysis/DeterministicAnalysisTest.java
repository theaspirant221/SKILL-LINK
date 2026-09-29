package com.skilllink.api.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicAnalysisTest {
    private final DeterministicRepositoryAnalyzer analyzer = new DeterministicRepositoryAnalyzer(new ManifestAnalyzer(), new JavaAstAnalyzer(), new GenericSourceAnalyzer());

    @Test
    void mapsManifestAndSourceSignalsWithoutInventingUnsupportedSkills() {
        var snapshot = new AnalysisModels.FetchedSnapshot(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "acme/foodbridge", "main", "abcdef0123456789", "tree-1",
            List.of(
                new AnalysisModels.SourceFile("pom.xml", "blob-pom", 120, "<artifactId>spring-boot-starter-web</artifactId><artifactId>jjwt</artifactId><artifactId>junit</artifactId>", null, 0),
                new AnalysisModels.SourceFile("src/main/java/AppController.java", "blob-java", 220, "@RestController class AppController { @GetMapping String list() { return \"ok\"; } }", "Java", 0),
                new AnalysisModels.SourceFile("src/test/java/AppControllerTest.java", "blob-test", 90, "class AppControllerTest {}", "Java", 0)
            ), 3, 430, 0, false);

        AnalysisModels.AnalysisOutput output = analyzer.analyze(snapshot);
        var keys = output.signals().stream().map(AnalysisModels.Signal::skillKey).toList();

        assertThat(keys).contains("spring-boot", "jwt-authentication", "unit-testing", "rest-api-development", "java");
        assertThat(output.fileCount()).isEqualTo(3);
        assertThat(output.testCount()).isEqualTo(1);
        assertThat(keys).doesNotContain("invented-skill");
        assertThat(output.summary()).contains("acme/foodbridge", "abcdef01");
    }

    @Test
    void redactsSecretsBeforeAnalysisContextIsProduced() {
        SecretRedactor.Redacted redacted = new SecretRedactor().redact("api_key=super-secret-value\njwt=eyJabcdefghijklmnop.qrstuvwxyzabcdef.ghijklmnopqrstu");
        assertThat(redacted.count()).isEqualTo(2);
        assertThat(redacted.content()).doesNotContain("super-secret-value", "eyJabcdefghijklmnop");
        assertThat(redacted.content()).contains("[REDACTED_SECRET]", "[REDACTED_JWT]");
    }
}
