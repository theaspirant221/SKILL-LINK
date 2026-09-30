package com.skilllink.api.analysis.detector;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JavaAstDetectorTest {
    private final JavaAstDetector detector = new JavaAstDetector();

    @Test
    void extractsSpringAnnotationsAndEndpoints() {
        UUID runId = UUID.randomUUID();
        UUID snapId = UUID.randomUUID();
        String javaContent = """
            package com.example;

            import org.springframework.web.bind.annotation.*;
            import org.springframework.stereotype.Service;
            import org.springframework.security.access.prepost.PreAuthorize;

            @RestController
            @RequestMapping("/api/v1/auth")
            public class AuthController {

                @Service
                public static class MyService {}

                @GetMapping("/login")
                @PreAuthorize("hasRole('CANDIDATE')")
                public String login() { return "ok"; }

                @PostMapping(value = "/register")
                public String register() { return "ok"; }
            }
            """;
        var files = List.of(new LanguageDetector.SnapshotFile("src/main/java/com/example/AuthController.java", javaContent, "Java", 500, "blob1", "hash1"));
        var result = detector.detect(runId, snapId, files);

        assertThat(result.observations).anyMatch(o -> "SPRING_CONTROLLER".equals(o.observationType()) && "RestController".equals(o.factKey()));
        assertThat(result.observations).anyMatch(o -> "HTTP_ENDPOINT".equals(o.observationType()) && "GET".equals(o.factKey()) && o.factValue().contains("/api/v1/auth/login"));
        assertThat(result.observations).anyMatch(o -> "HTTP_ENDPOINT".equals(o.observationType()) && "POST".equals(o.factKey()) && o.factValue().contains("/register"));
        assertThat(result.observations).anyMatch(o -> "SECURITY_ANNOTATION".equals(o.observationType()) && "PreAuthorize".equals(o.factKey()));
        assertThat(result.observations).anyMatch(o -> "CLASS".equals(o.observationType()) && "AuthController".equals(o.factKey()));
        assertThat(result.observations).anyMatch(o -> "METHOD".equals(o.observationType()) && "login".equals(o.factKey()));
        assertThat(result.errors).isEmpty();
    }

    @Test
    void extractsJpaEntityAndSecuritySignals() {
        UUID runId = UUID.randomUUID();
        UUID snapId = UUID.randomUUID();
        String entityContent = """
            package com.example;

            import jakarta.persistence.*;
            import org.springframework.stereotype.Component;
            import org.springframework.security.web.SecurityFilterChain;
            import org.springframework.security.crypto.password.PasswordEncoder;

            @Entity
            @Table(name = "app_user")
            public class User {
                @Id
                private Long id;

                @OneToMany
                private java.util.List<String> roles;

                @ManyToOne
                @JoinColumn(name = "org_id")
                private String org;
            }

            @Component
            public class SecurityConfig {
                SecurityFilterChain chain;
                PasswordEncoder encoder;
            }
            """;
        var files = List.of(new LanguageDetector.SnapshotFile("src/main/java/com/example/User.java", entityContent, "Java", 500, "blob1", "hash1"));
        var result = detector.detect(runId, snapId, files);

        assertThat(result.observations).anyMatch(o -> "JPA_ENTITY".equals(o.observationType()) && "Entity".equals(o.factKey()));
        assertThat(result.observations).anyMatch(o -> "JPA_ID".equals(o.observationType()));
        assertThat(result.observations).anyMatch(o -> "JPA_RELATIONSHIP".equals(o.observationType()) && "OneToMany".equals(o.factKey()));
        assertThat(result.observations).anyMatch(o -> "SECURITY_CONFIGURATION".equals(o.observationType()) && "SecurityFilterChain".equals(o.factKey()));
        assertThat(result.observations).anyMatch(o -> "SECURITY_CONFIGURATION".equals(o.observationType()) && "PasswordEncoder".equals(o.factKey()));
    }

    @Test
    void handlesMalformedJavaGracefully() {
        UUID runId = UUID.randomUUID();
        UUID snapId = UUID.randomUUID();
        String malformed = "public class Broken { this is not valid java {{{{";
        var files = List.of(new LanguageDetector.SnapshotFile("src/Broken.java", malformed, "Java", 100, "blob1", "hash1"));
        var result = detector.detect(runId, snapId, files);

        assertThat(result.observations).isEmpty();
        assertThat(result.errors).hasSize(1);
        assertThat(result.errors.get(0).errorCode()).isEqualTo("PARSE_ERROR");
        assertThat(result.errors.get(0).sourcePath()).isEqualTo("src/Broken.java");
    }

    @Test
    void extractsTestSignals() {
        UUID runId = UUID.randomUUID();
        UUID snapId = UUID.randomUUID();
        String testContent = """
            package com.example;

            import org.junit.jupiter.api.Test;
            import org.springframework.boot.test.context.SpringBootTest;
            import org.testcontainers.junit.jupiter.Testcontainers;
            import org.mockito.Mock;

            @SpringBootTest
            @Testcontainers
            public class MyTest {
                @Mock
                private String mock;

                @Test
                public void testSomething() {}

                @Test
                public void testOther() {}
            }
            """;
        var files = List.of(new LanguageDetector.SnapshotFile("src/test/java/com/example/MyTest.java", testContent, "Java", 500, "blob1", "hash1"));
        var result = detector.detect(runId, snapId, files);

        assertThat(result.observations).anyMatch(o -> "TEST_FRAMEWORK".equals(o.observationType()) && "JUnit".equals(o.factKey()));
        assertThat(result.observations).anyMatch(o -> "TEST_FRAMEWORK".equals(o.observationType()) && "SpringBootTest".equals(o.factKey()));
        assertThat(result.observations).anyMatch(o -> "TEST_FRAMEWORK".equals(o.observationType()) && "Testcontainers".equals(o.factKey()));
        assertThat(result.observations).anyMatch(o -> "TEST_FRAMEWORK".equals(o.observationType()) && "Mockito".equals(o.factKey()));
        assertThat(result.observations).anyMatch(o -> "TEST_METHOD_COUNT".equals(o.observationType()));
    }

    @Test
    void sourceTraceability() {
        UUID runId = UUID.randomUUID();
        UUID snapId = UUID.randomUUID();
        String content = """
            package com.example;
            @RestController
            public class MyController {
                @GetMapping("/test")
                public String test() { return "ok"; }
            }
            """;
        var files = List.of(new LanguageDetector.SnapshotFile("src/MyController.java", content, "Java", 200, "blobSha123", "contentHash123"));
        var result = detector.detect(runId, snapId, files);

        var endpoint = result.observations.stream().filter(o -> "HTTP_ENDPOINT".equals(o.observationType())).findFirst().orElseThrow();
        assertThat(endpoint.sourcePath()).isEqualTo("src/MyController.java");
        assertThat(endpoint.startLine()).isNotNull();
        assertThat(endpoint.endLine()).isNotNull();
        assertThat(endpoint.symbol()).isNotNull();
        assertThat(endpoint.sourceHash()).isEqualTo("contentHash123");
        assertThat(endpoint.detector()).isEqualTo("JAVA_AST");
        assertThat(endpoint.detectorVersion()).isEqualTo("deterministic-java-v1");
        assertThat(endpoint.origin()).isEqualTo("DETERMINISTIC");
    }
}
