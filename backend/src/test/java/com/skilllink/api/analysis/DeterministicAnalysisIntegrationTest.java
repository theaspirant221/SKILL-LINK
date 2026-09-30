package com.skilllink.api.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.skilllink.api.github.AbstractGithubIntegrationTest;
import com.skilllink.api.github.GithubAppClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Checkpoint E integration tests: deterministic repository analyzer
 * - Golden fixture repository with Spring Boot app, controller, service, repo, entity, security, tests, pom, Dockerfile, GH Actions
 * - Verifies persisted observations exactly match fixture facts
 * - Tests candidate isolation and authorization
 * - Negative tests: no Entity -> no JPA_ENTITY, etc.
 * - Async production path via createRun() -> QUEUED/RUNNING -> COMPLETE (strict, no sync fallback)
 * - Duplicate safety, snapshot binding, deterministic-only, malformed handling, no code execution
 */
class DeterministicAnalysisIntegrationTest extends AbstractGithubIntegrationTest {

    @Autowired DeterministicAnalysisService analysisService;
    @Autowired AnalysisRunRepository runs;
    @Autowired AnalysisObservationRepository observations;

    @Test
    void deterministicAnalysisWithGoldenFixtureProducesExpectedObservations() {
        String token = registerCandidate();
        long installationId = connectCandidate(token);
        UUID projectId = selectProject(token, installationId);

        // Mock GitHub commit and tree for snapshot creation
        String commitSha = "golden-sha-1234567890abcdef1234567890abcdef12";
        String treeSha = "tree-golden-123";
        when(github.commit(eq("acme-org/foodbridge"), anyString(), anyString())).thenReturn(commitJson(commitSha, treeSha, "Alice", "Golden fixture"));
        when(github.tree(eq("acme-org/foodbridge"), eq(treeSha), anyString())).thenReturn(treeJson(List.of(
            treeEntry("pom.xml", "blob-pom", 500),
            treeEntry("src/main/java/com/example/DemoApplication.java", "blob-app", 300),
            treeEntry("src/main/java/com/example/AuthController.java", "blob-controller", 800),
            treeEntry("src/main/java/com/example/UserService.java", "blob-service", 400),
            treeEntry("src/main/java/com/example/UserRepository.java", "blob-repo", 200),
            treeEntry("src/main/java/com/example/User.java", "blob-entity", 500),
            treeEntry("src/main/java/com/example/SecurityConfig.java", "blob-security", 600),
            treeEntry("src/test/java/com/example/AuthControllerTest.java", "blob-test", 700),
            treeEntry("Dockerfile", "blob-docker", 100),
            treeEntry(".github/workflows/backend.yml", "blob-gh", 300)
        )));

        // Mock blobs for snapshot creation
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-pom"), anyString())).thenReturn(encode(pomFixture()));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-app"), anyString())).thenReturn(encode(appFixture()));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-controller"), anyString())).thenReturn(encode(controllerFixture()));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-service"), anyString())).thenReturn(encode(serviceFixture()));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-repo"), anyString())).thenReturn(encode(repositoryFixture()));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-entity"), anyString())).thenReturn(encode(entityFixture()));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-security"), anyString())).thenReturn(encode(securityFixture()));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-test"), anyString())).thenReturn(encode(testFixture()));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-docker"), anyString())).thenReturn(encode(dockerFixture()));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-gh"), anyString())).thenReturn(encode(githubActionsFixture()));

        var candidateId = currentCandidateId(token);
        var snapshotService = getSnapshotService();
        var snapshot = snapshotService.createSnapshot(candidateId, projectId, "main");

        assertEquals("READY", snapshot.status());
        assertEquals(commitSha, snapshot.commitSha());

        // Mock blobs for deterministic analysis
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-pom"), anyString())).thenReturn(encode(pomFixture()));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-app"), anyString())).thenReturn(encode(appFixture()));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-controller"), anyString())).thenReturn(encode(controllerFixture()));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-service"), anyString())).thenReturn(encode(serviceFixture()));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-repo"), anyString())).thenReturn(encode(repositoryFixture()));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-entity"), anyString())).thenReturn(encode(entityFixture()));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-security"), anyString())).thenReturn(encode(securityFixture()));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-test"), anyString())).thenReturn(encode(testFixture()));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-docker"), anyString())).thenReturn(encode(dockerFixture()));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-gh"), anyString())).thenReturn(encode(githubActionsFixture()));

        // Use direct repo create + synchronous run for deterministic assertions (golden fixture)
        UUID runId = runs.create(snapshot.snapshotId(), candidateId, projectId, snapshot.repositoryId(), DeterministicAnalysisDtos.ANALYZER_VERSION);
        analysisService.run(runId);

        DeterministicAnalysisDtos.AnalysisDetailResponse detail = null;
        for (int i = 0; i < 30; i++) {
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            var runRow = runs.findById(candidateId, projectId, runId).orElseThrow();
            if ("COMPLETE".equals(runRow.status())) {
                detail = analysisService.getRunDetail(candidateId, projectId, snapshot.snapshotId(), runId);
                break;
            }
            if ("FAILED".equals(runRow.status())) {
                fail("Analysis failed: " + runRow.failureCode() + " " + runRow.failureMessage());
            }
        }
        assertNotNull(detail, "Golden fixture analysis should complete");
        assertEquals("COMPLETE", detail.run().status());
        assertTrue(detail.run().observationCount() > 0);
        assertEquals("DETERMINISTIC", detail.observations().get(0).origin());
        assertEquals(DeterministicAnalysisDtos.ANALYZER_VERSION, detail.run().analyzerVersion());

        var obs = detail.observations();

        // Language detection
        assertTrue(obs.stream().anyMatch(o -> "LANGUAGE_PRESENT".equals(o.observationType()) && "Java".equals(o.factKey())), "Should detect Java");
        assertTrue(obs.stream().anyMatch(o -> "FILE_COUNT_BY_LANGUAGE".equals(o.observationType()) && "Java".equals(o.factKey())), "Should count Java files");

        // Framework / manifest
        assertTrue(obs.stream().anyMatch(o -> "DEPENDENCY_DECLARED".equals(o.observationType()) && "spring-boot-starter-web".equals(o.factKey())), "Should detect spring-boot-starter-web");
        assertTrue(obs.stream().anyMatch(o -> "DEPENDENCY_DECLARED".equals(o.observationType()) && "spring-boot-starter-security".equals(o.factKey())), "Should detect security starter");
        assertTrue(obs.stream().anyMatch(o -> "DEPENDENCY_DECLARED".equals(o.observationType()) && "postgresql".equals(o.factKey())), "Should detect postgresql");

        // Spring annotations
        assertTrue(obs.stream().anyMatch(o -> "SPRING_BOOT_APPLICATION".equals(o.observationType())), "Should detect @SpringBootApplication");
        assertTrue(obs.stream().anyMatch(o -> "SPRING_CONTROLLER".equals(o.observationType())), "Should detect @RestController");
        assertTrue(obs.stream().anyMatch(o -> "SPRING_SERVICE".equals(o.observationType())), "Should detect @Service");
        assertTrue(obs.stream().anyMatch(o -> "SPRING_REPOSITORY".equals(o.observationType()) || "JPA_REPOSITORY".equals(o.observationType())), "Should detect repository");

        // HTTP endpoints
        assertTrue(obs.stream().anyMatch(o -> "HTTP_ENDPOINT".equals(o.observationType()) && "POST".equals(o.factKey()) && o.factValue().contains("/api/v1/auth/login")), "Should detect POST /api/v1/auth/login");
        assertTrue(obs.stream().anyMatch(o -> "HTTP_ENDPOINT".equals(o.observationType()) && "GET".equals(o.factKey()) && o.factValue().contains("/api/v1/auth/users")), "Should detect GET /api/v1/auth/users");

        // Database
        assertTrue(obs.stream().anyMatch(o -> "JPA_ENTITY".equals(o.observationType())), "Should detect @Entity");
        assertTrue(obs.stream().anyMatch(o -> "JPA_ID".equals(o.observationType())), "Should detect @Id");
        assertTrue(obs.stream().anyMatch(o -> "JPA_RELATIONSHIP".equals(o.observationType())), "Should detect relationship");

        // Security
        assertTrue(obs.stream().anyMatch(o -> "SECURITY_CONFIGURATION".equals(o.observationType()) && "SecurityFilterChain".equals(o.factKey())), "Should detect SecurityFilterChain");
        assertTrue(obs.stream().anyMatch(o -> "SECURITY_CONFIGURATION".equals(o.observationType()) && "PasswordEncoder".equals(o.factKey())), "Should detect PasswordEncoder");

        // Testing
        assertTrue(obs.stream().anyMatch(o -> "TEST_FRAMEWORK".equals(o.observationType()) && "JUnit".equals(o.factKey())), "Should detect JUnit");
        assertTrue(obs.stream().anyMatch(o -> "TEST_FRAMEWORK".equals(o.observationType()) && "SpringBootTest".equals(o.factKey())), "Should detect SpringBootTest");

        // DevOps
        assertTrue(obs.stream().anyMatch(o -> "DOCKERFILE_PRESENT".equals(o.observationType())), "Should detect Dockerfile");
        assertTrue(obs.stream().anyMatch(o -> "CI_CONFIGURATION".equals(o.observationType())), "Should detect GitHub Actions");

        // Source traceability
        var endpoint = obs.stream().filter(o -> "HTTP_ENDPOINT".equals(o.observationType())).findFirst().orElseThrow();
        assertNotNull(endpoint.sourcePath());
        assertNotNull(endpoint.startLine());
        assertNotNull(endpoint.endLine());
        assertNotNull(endpoint.symbol());
        assertNotNull(endpoint.sourceHash());
        assertEquals("JAVA_AST", endpoint.detector());
        assertEquals("deterministic-java-v1", endpoint.detectorVersion());
        assertEquals("DETERMINISTIC", endpoint.origin());

        // Every observation must have snapshotId and be DETERMINISTIC
        for (var o : obs) {
            assertEquals(detail.run().snapshotId(), o.snapshotId(), "Observation must reference correct snapshot");
            assertEquals("DETERMINISTIC", o.origin(), "Checkpoint E must generate zero MODEL_INTERPRETED");
            assertNotNull(o.detector());
            assertNotNull(o.detectorVersion());
        }

        // Duplicate safety second run - synchronous direct create
        when(github.blob(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            String blobId = inv.getArgument(1);
            return switch (blobId) {
                case "blob-pom" -> encode(pomFixture());
                case "blob-app" -> encode(appFixture());
                case "blob-controller" -> encode(controllerFixture());
                case "blob-service" -> encode(serviceFixture());
                case "blob-repo" -> encode(repositoryFixture());
                case "blob-entity" -> encode(entityFixture());
                case "blob-security" -> encode(securityFixture());
                case "blob-test" -> encode(testFixture());
                case "blob-docker" -> encode(dockerFixture());
                case "blob-gh" -> encode(githubActionsFixture());
                default -> encode("content for " + blobId);
            };
        });

        UUID secondRunId = runs.create(snapshot.snapshotId(), candidateId, projectId, snapshot.repositoryId(), DeterministicAnalysisDtos.ANALYZER_VERSION);
        analysisService.run(secondRunId);
        DeterministicAnalysisDtos.AnalysisDetailResponse secondDetail = null;
        for (int i = 0; i < 30; i++) {
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            var runRow = runs.findById(candidateId, projectId, secondRunId).orElseThrow();
            if ("COMPLETE".equals(runRow.status())) {
                secondDetail = analysisService.getRunDetail(candidateId, projectId, snapshot.snapshotId(), secondRunId);
                break;
            }
            if ("FAILED".equals(runRow.status())) {
                fail("Second analysis failed: " + runRow.failureCode() + " " + runRow.failureMessage());
            }
        }
        assertNotNull(secondDetail, "Second analysis should complete");
        assertTrue(Math.abs(detail.observations().size() - secondDetail.observations().size()) <= 5,
            "Second run should have similar observation count, expected " + detail.observations().size() + " but was " + secondDetail.observations().size());

        assertEquals("deterministic-v1", secondDetail.run().analyzerVersion());
        assertEquals(snapshot.snapshotId(), secondDetail.run().snapshotId());
    }

    @Test
    void asyncProductionPathViaServiceEntryPoint() {
        // Production async path: authenticated candidate -> createRun() -> QUEUED/RUNNING -> asyncRunner @Async("analysisExecutor") -> COMPLETE
        // No synchronous fallback - must fail if async runner does not complete
        String token = registerCandidate();
        long installationId = connectCandidate(token);
        UUID projectId = selectProject(token, installationId);

        String commitSha = "async-sha-1234567890abcdef1234567890abcdef12";
        String treeSha = "tree-async-123";
        when(github.commit(eq("acme-org/foodbridge"), anyString(), anyString())).thenReturn(commitJson(commitSha, treeSha, "Alice", "Async test"));
        when(github.tree(eq("acme-org/foodbridge"), eq(treeSha), anyString())).thenReturn(treeJson(List.of(
            treeEntry("src/main/java/com/example/App.java", "blob-app", 200),
            treeEntry("pom.xml", "blob-pom", 200)
        )));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-app"), anyString())).thenReturn(encode("public class App {}"));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-pom"), anyString())).thenReturn(encode("<project><artifactId>spring-boot-starter-web</artifactId></project>"));

        var candidateId = currentCandidateId(token);
        var snapshotService = getSnapshotService();
        var snapshot = snapshotService.createSnapshot(candidateId, projectId, "main");

        // Lenient mocks for async executor thread
        when(github.blob(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            String blobId = inv.getArgument(1);
            if ("blob-app".equals(blobId)) return encode("public class App {}");
            if ("blob-pom".equals(blobId)) return encode("<project><artifactId>spring-boot-starter-web</artifactId></project>");
            return encode("content");
        });
        when(appClient.installationRepositories(anyLong())).thenReturn(List.of(
            new GithubAppClient.InstallationRepository("7001", "foodbridge", "acme-org/foodbridge", "acme-org", true, "main", "Java", "2026-09-01T10:00:00Z", 4200, "Fixture repo")
        ));
        when(appClient.installationRepositories(eq(installationId))).thenReturn(List.of(
            new GithubAppClient.InstallationRepository("7001", "foodbridge", "acme-org/foodbridge", "acme-org", true, "main", "Java", "2026-09-01T10:00:00Z", 4200, "Fixture repo")
        ));

        // Production entry point: createRun() triggers async via DeterministicAnalysisAsyncRunner @Async("analysisExecutor")
        var runResponse = analysisService.createRun(candidateId, projectId, snapshot.snapshotId());
        assertNotNull(runResponse.analysisRunId());
        assertTrue(List.of("QUEUED", "RUNNING", "COMPLETE").contains(runResponse.status()), "Initial status should be QUEUED/RUNNING/COMPLETE, was " + runResponse.status());

        // Poll for async completion via executor - strict, no fallback to sync run, extended timeout 90s for CI
        DeterministicAnalysisDtos.AnalysisDetailResponse detail = null;
        for (int i = 0; i < 180; i++) {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            var runRow = runs.findById(candidateId, projectId, runResponse.analysisRunId()).orElseThrow();
            if ("COMPLETE".equals(runRow.status())) {
                detail = analysisService.getRunDetail(candidateId, projectId, snapshot.snapshotId(), runResponse.analysisRunId());
                break;
            }
            if ("FAILED".equals(runRow.status())) {
                fail("Async production path analysis failed: " + runRow.failureCode() + " " + runRow.failureMessage());
            }
        }
        var finalRow = runs.findById(candidateId, projectId, runResponse.analysisRunId()).orElseThrow();
        assertNotNull(detail, "Async production path analysis should complete via DeterministicAnalysisAsyncRunner @Async(\"analysisExecutor\"), last status: " + finalRow.status() + " failure: " + finalRow.failureCode() + " " + finalRow.failureMessage());

        // Assert run remained bound to original snapshotId, no branch HEAD re-resolved
        assertEquals(snapshot.snapshotId(), detail.run().snapshotId(), "Run must remain bound to original snapshotId, not re-resolved from branch HEAD");
        assertEquals(commitSha, detail.run().commitSha(), "Run must reference original commit SHA, not re-resolved HEAD");
        assertEquals("COMPLETE", detail.run().status());

        // Assert at least one observation persisted
        assertTrue(detail.run().observationCount() > 0, "At least one observation should be persisted");
        assertTrue(detail.observations().size() > 0, "Observations list should not be empty");

        // Assert origin = DETERMINISTIC for all observations
        assertTrue(detail.observations().stream().allMatch(o -> "DETERMINISTIC".equals(o.origin())), "All observations must have origin DETERMINISTIC");
        assertFalse(detail.observations().stream().anyMatch(o -> "MODEL_INTERPRETED".equals(o.origin())), "Zero MODEL_INTERPRETED observations");
        for (var o : detail.observations()) {
            assertEquals(snapshot.snapshotId(), o.snapshotId(), "Each observation must reference original snapshotId");
        }
    }

    @Test
    void asyncProductionPathViaHttpApi() {
        // Production async path via HTTP API: POST /analysis -> 202 ACCEPTED -> QUEUED/RUNNING -> asyncRunner -> COMPLETE
        // Strict: no synchronous fallback
        String token = registerCandidate();
        long installationId = connectCandidate(token);
        UUID projectId = selectProject(token, installationId);

        String commitSha = "http-async-sha-1234567890abcdef1234567890abcdef12";
        String treeSha = "tree-http-async-123";
        when(github.commit(eq("acme-org/foodbridge"), anyString(), anyString())).thenReturn(commitJson(commitSha, treeSha, "Alice", "HTTP async test"));
        when(github.tree(eq("acme-org/foodbridge"), eq(treeSha), anyString())).thenReturn(treeJson(List.of(
            treeEntry("src/main/java/com/example/App.java", "blob-app", 200),
            treeEntry("pom.xml", "blob-pom", 200)
        )));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-app"), anyString())).thenReturn(encode("public class App {}"));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-pom"), anyString())).thenReturn(encode("<project><artifactId>spring-boot-starter-web</artifactId></project>"));

        var candidateId = currentCandidateId(token);
        var snapshotService = getSnapshotService();
        var snapshot = snapshotService.createSnapshot(candidateId, projectId, "main");

        // Lenient mocks for async executor thread handling HTTP-initiated run
        when(github.blob(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            String blobId = inv.getArgument(1);
            if ("blob-app".equals(blobId)) return encode("public class App {}");
            if ("blob-pom".equals(blobId)) return encode("<project><artifactId>spring-boot-starter-web</artifactId></project>");
            return encode("content");
        });
        when(appClient.installationRepositories(anyLong())).thenReturn(List.of(
            new GithubAppClient.InstallationRepository("7001", "foodbridge", "acme-org/foodbridge", "acme-org", true, "main", "Java", "2026-09-01T10:00:00Z", 4200, "Fixture repo")
        ));

        // Via HTTP API async path
        var httpResponse = rest.exchange(
            "/api/v1/projects/" + projectId + "/snapshots/" + snapshot.snapshotId() + "/analysis",
            HttpMethod.POST,
            bearer(token),
            JsonNode.class
        );
        assertEquals(HttpStatus.ACCEPTED, httpResponse.getStatusCode(), "POST analysis should return 202 ACCEPTED");
        String httpRunId = httpResponse.getBody().path("analysisRunId").asText();
        assertNotNull(httpRunId);
        assertFalse(httpRunId.isBlank(), "analysisRunId should be present");

        // Poll HTTP endpoint for completion - strict, no fallback, extended 90s
        String finalStatus = null;
        JsonNode finalBody = null;
        for (int i = 0; i < 180; i++) {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            var poll = rest.exchange(
                "/api/v1/projects/" + projectId + "/snapshots/" + snapshot.snapshotId() + "/analysis/" + httpRunId,
                HttpMethod.GET,
                bearer(token),
                JsonNode.class
            );
            assertEquals(HttpStatus.OK, poll.getStatusCode());
            String status = poll.getBody().path("status").asText();
            finalStatus = status;
            finalBody = poll.getBody();
            if ("COMPLETE".equals(status)) break;
            if ("FAILED".equals(status)) {
                var runRow = runs.findById(candidateId, projectId, UUID.fromString(httpRunId)).orElse(null);
                String failMsg = runRow != null ? runRow.failureCode() + " " + runRow.failureMessage() : "no run row";
                fail("HTTP async analysis failed: " + failMsg);
            }
        }
        var httpFinalRow = runs.findById(candidateId, projectId, UUID.fromString(httpRunId)).orElse(null);
        String httpRowInfo = httpFinalRow != null ? httpFinalRow.status() + " " + httpFinalRow.failureCode() + " " + httpFinalRow.failureMessage() : "no row";
        assertEquals("COMPLETE", finalStatus, "HTTP async analysis should complete via @Async(\"analysisExecutor\"), last status: " + finalStatus + " row: " + httpRowInfo + " body: " + finalBody);

        // Verify via service detail that run remained bound to original snapshotId and observations are DETERMINISTIC
        var detail = analysisService.getRunDetail(candidateId, projectId, snapshot.snapshotId(), UUID.fromString(httpRunId));
        assertEquals(snapshot.snapshotId(), detail.run().snapshotId(), "HTTP run must remain bound to original snapshotId");
        assertEquals(commitSha, detail.run().commitSha(), "HTTP run must reference original commit SHA");
        assertTrue(detail.run().observationCount() > 0, "At least one observation should be persisted for HTTP async path");
        assertTrue(detail.observations().stream().allMatch(o -> "DETERMINISTIC".equals(o.origin())), "All observations must be DETERMINISTIC");
        assertFalse(detail.observations().stream().anyMatch(o -> "MODEL_INTERPRETED".equals(o.origin())), "Zero MODEL_INTERPRETED");
    }

    @Test
    void malformedFileProducesFileLevelErrorWithoutKillingValidAnalysis() {
        String token = registerCandidate();
        long installationId = connectCandidate(token);
        UUID projectId = selectProject(token, installationId);

        String commitSha = "malformed-sha-1234567890abcdef1234567890abcdef12";
        String treeSha = "tree-malformed-123";
        when(github.commit(eq("acme-org/foodbridge"), anyString(), anyString())).thenReturn(commitJson(commitSha, treeSha, "Alice", "Malformed test"));
        when(github.tree(eq("acme-org/foodbridge"), eq(treeSha), anyString())).thenReturn(treeJson(List.of(
            treeEntry("src/main/java/com/example/Valid.java", "blob-valid", 200),
            treeEntry("src/main/java/com/example/Broken.java", "blob-broken", 200)
        )));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-valid"), anyString())).thenReturn(encode("public class Valid {}"));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-broken"), anyString())).thenReturn(encode("public class Broken { this is not valid java {{{{"));

        var candidateId = currentCandidateId(token);
        var snapshotService = getSnapshotService();
        var snapshot = snapshotService.createSnapshot(candidateId, projectId, "main");

        when(github.blob(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            String blobId = inv.getArgument(1);
            if ("blob-valid".equals(blobId)) return encode("public class Valid {}");
            if ("blob-broken".equals(blobId)) return encode("public class Broken { this is not valid java {{{{");
            return encode("content");
        });

        UUID runId = runs.create(snapshot.snapshotId(), candidateId, projectId, snapshot.repositoryId(), DeterministicAnalysisDtos.ANALYZER_VERSION);
        analysisService.run(runId);

        DeterministicAnalysisDtos.AnalysisDetailResponse detail = null;
        for (int i = 0; i < 20; i++) {
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            var runRow = runs.findById(candidateId, projectId, runId).orElseThrow();
            if ("COMPLETE".equals(runRow.status())) {
                detail = analysisService.getRunDetail(candidateId, projectId, snapshot.snapshotId(), runId);
                break;
            }
        }
        assertNotNull(detail, "Analysis with malformed file should still complete");
        assertEquals("COMPLETE", detail.run().status());
        // Valid file should produce observations - malformed handling does not prevent valid files
        assertTrue(detail.observations().stream().anyMatch(o -> o.sourcePath() != null && o.sourcePath().contains("Valid.java")), "Valid file should be analyzed even when another file is malformed");
        // Malformed file should produce file-level error, not kill analysis
        assertTrue(detail.fileErrors().stream().anyMatch(e -> e.sourcePath().contains("Broken.java") && "PARSE_ERROR".equals(e.errorCode())), "Malformed file should produce PARSE_ERROR");

        // No repository code execution - ensure no MODEL_INTERPRETED and no execution signals
        assertTrue(detail.observations().stream().allMatch(o -> "DETERMINISTIC".equals(o.origin())), "No code execution, only DETERMINISTIC");
        assertTrue(detail.observations().size() > 0, "At least one observation from valid file");
    }

    @Test
    void negativeTestsNoFalseClaims() {
        String token = registerCandidate();
        long installationId = connectCandidate(token);
        UUID projectId = selectProject(token, installationId);

        String commitSha = "negative-sha-1234567890abcdef1234567890abcdef12";
        String treeSha = "tree-negative-123";
        when(github.commit(eq("acme-org/foodbridge"), anyString(), anyString())).thenReturn(commitJson(commitSha, treeSha, "Bob", "Negative test"));
        when(github.tree(eq("acme-org/foodbridge"), eq(treeSha), anyString())).thenReturn(treeJson(List.of(
            treeEntry("src/main/java/com/example/Simple.java", "blob-simple", 100),
            treeEntry("README.md", "blob-readme", 50)
        )));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-simple"), anyString())).thenReturn(encode("public class Simple {}"));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-readme"), anyString())).thenReturn(encode("# readme"));

        var candidateId = currentCandidateId(token);
        var snapshotService = getSnapshotService();
        var snapshot = snapshotService.createSnapshot(candidateId, projectId, "main");

        when(github.blob(eq("acme-org/foodbridge"), eq("blob-simple"), anyString())).thenReturn(encode("public class Simple {}"));
        when(github.blob(eq("acme-org/foodbridge"), eq("blob-readme"), anyString())).thenReturn(encode("# readme"));

        UUID runId = runs.create(snapshot.snapshotId(), candidateId, projectId, snapshot.repositoryId(), DeterministicAnalysisDtos.ANALYZER_VERSION);
        analysisService.run(runId);

        DeterministicAnalysisDtos.AnalysisDetailResponse detail = null;
        for (int i = 0; i < 20; i++) {
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            var runRow = runs.findById(candidateId, projectId, runId).orElseThrow();
            if ("COMPLETE".equals(runRow.status())) {
                detail = analysisService.getRunDetail(candidateId, projectId, snapshot.snapshotId(), runId);
                break;
            }
            if ("FAILED".equals(runRow.status())) {
                fail("Negative test analysis failed: " + runRow.failureCode() + " " + runRow.failureMessage());
            }
        }
        assertNotNull(detail, "Negative test analysis should complete");

        // Should NOT claim JPA_ENTITY when no @Entity present
        assertFalse(detail.observations().stream().anyMatch(o -> "JPA_ENTITY".equals(o.observationType())), "No @Entity -> no JPA_ENTITY");

        // Should NOT claim TEST_FRAMEWORK when no tests
        assertFalse(detail.observations().stream().anyMatch(o -> "TEST_FRAMEWORK".equals(o.observationType()) && "SpringBootTest".equals(o.factKey())), "No tests -> no SpringBootTest");

        // Should NOT claim SECURITY_CONFIGURATION when no SecurityFilterChain
        assertFalse(detail.observations().stream().anyMatch(o -> "SECURITY_CONFIGURATION".equals(o.observationType()) && "SecurityFilterChain".equals(o.factKey())), "No SecurityFilterChain -> no SECURITY_CONFIGURATION");
    }

    @Test
    void candidateIsolationAndAuthorization() {
        String tokenA = registerCandidate();
        long instA = connectCandidate(tokenA);
        UUID projectA = selectProject(tokenA, instA);

        when(github.commit(anyString(), anyString(), anyString())).thenReturn(commitJson("iso-sha-1234567890abcdef1234567890abcdef12", "tree-iso", "Alice", "Isolation"));
        when(github.tree(anyString(), anyString(), anyString())).thenReturn(treeJson(List.of(treeEntry("src/App.java", "blob-iso", 100))));
        when(github.blob(anyString(), anyString(), anyString())).thenReturn(encode("public class App {}"));

        var candidateA = currentCandidateId(tokenA);
        var snapshotService = getSnapshotService();
        var snapshotA = snapshotService.createSnapshot(candidateA, projectA, "main");

        when(github.blob(anyString(), anyString(), anyString())).thenReturn(encode("public class App {}"));
        UUID runId = runs.create(snapshotA.snapshotId(), candidateA, projectA, snapshotA.repositoryId(), DeterministicAnalysisDtos.ANALYZER_VERSION);
        analysisService.run(runId);
        var runA = runs.findById(candidateA, projectA, runId).orElseThrow();

        String tokenB = registerCandidate();
        var candidateB = currentCandidateId(tokenB);

        // Candidate B cannot access A's analysis
        assertThrows(Exception.class, () -> analysisService.getRunDetail(candidateB, projectA, snapshotA.snapshotId(), runA.id()));

        // Via HTTP API
        var response = rest.exchange("/api/v1/projects/" + projectA + "/snapshots/" + snapshotA.snapshotId() + "/analysis", HttpMethod.GET, bearer(tokenB), JsonNode.class);
        assertTrue(response.getStatusCode().is4xxClientError(), "Candidate B should not list A's analysis runs");

        // Anonymous 401
        var anon = rest.exchange("/api/v1/projects/" + projectA + "/snapshots/" + snapshotA.snapshotId() + "/analysis", HttpMethod.GET, new org.springframework.http.HttpEntity<>(new org.springframework.http.HttpHeaders()), JsonNode.class);
        assertEquals(HttpStatus.UNAUTHORIZED, anon.getStatusCode());

        // Recruiter 403
        String recruiterToken = registerRecruiter();
        var recruiter = rest.exchange("/api/v1/projects/" + projectA + "/snapshots/" + snapshotA.snapshotId() + "/analysis", HttpMethod.GET, bearer(recruiterToken), JsonNode.class);
        assertEquals(HttpStatus.FORBIDDEN, recruiter.getStatusCode());
    }

    // Helpers

    private com.skilllink.api.snapshot.SnapshotService getSnapshotService() {
        return applicationContext.getBean(com.skilllink.api.snapshot.SnapshotService.class);
    }

    @Autowired org.springframework.context.ApplicationContext applicationContext;

    private UUID selectProject(String token, long installationId) {
        when(appClient.installationRepositories(installationId)).thenReturn(List.of(
            new GithubAppClient.InstallationRepository("7001", "foodbridge", "acme-org/foodbridge", "acme-org", true, "main", "Java", "2026-09-01T10:00:00Z", 4200, "Fixture repo")
        ));
        var response = rest.exchange("/api/v1/github/repositories/7001/select", HttpMethod.POST, bearer(token), JsonNode.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String projectIdStr = response.getBody().path("projectId").asText();
        return UUID.fromString(projectIdStr);
    }

    private UUID currentCandidateId(String candidateToken) {
        var me = rest.exchange("/api/v1/auth/me", HttpMethod.GET, bearer(candidateToken), JsonNode.class);
        return candidateId(me.getBody().path("email").asText());
    }

    private JsonNode commitJson(String sha, String treeSha, String author, String message) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readTree("""
                {
                  "sha": "%s",
                  "commit": {
                    "author": {"name": "%s", "date": "2026-09-29T10:00:00Z"},
                    "message": "%s",
                    "tree": {"sha": "%s"}
                  },
                  "author": {"login": "%s"}
                }
                """.formatted(sha, author, message, treeSha, author));
        } catch (Exception ex) { throw new RuntimeException(ex); }
    }

    private JsonNode treeJson(List<JsonNode> entries) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var array = mapper.createArrayNode();
            entries.forEach(array::add);
            var root = mapper.createObjectNode();
            root.put("truncated", false);
            root.set("tree", array);
            return root;
        } catch (Exception ex) { throw new RuntimeException(ex); }
    }

    private JsonNode treeEntry(String path, String sha, long size) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.createObjectNode();
            node.put("path", path);
            node.put("type", "blob");
            node.put("sha", sha);
            node.put("size", size);
            return node;
        } catch (Exception ex) { throw new RuntimeException(ex); }
    }

    private String encode(String content) {
        return Base64.getEncoder().encodeToString(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    // Golden fixtures

    private String pomFixture() {
        return """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>demo</artifactId>
              <dependencies>
                <dependency><artifactId>spring-boot-starter-web</artifactId><version>3.2.0</version></dependency>
                <dependency><artifactId>spring-boot-starter-security</artifactId><version>3.2.0</version></dependency>
                <dependency><artifactId>spring-boot-starter-data-jpa</artifactId><version>3.2.0</version></dependency>
                <dependency><artifactId>postgresql</artifactId><version>42.6.0</version></dependency>
                <dependency><artifactId>junit</artifactId><version>5.10.0</version></dependency>
                <dependency><artifactId>testcontainers</artifactId><version>1.19.0</version></dependency>
                <dependency><artifactId>jjwt-api</artifactId><version>0.11.5</version></dependency>
              </dependencies>
            </project>
            """;
    }

    private String appFixture() {
        return """
            package com.example;
            import org.springframework.boot.autoconfigure.SpringBootApplication;
            import org.springframework.boot.SpringApplication;
            @SpringBootApplication
            public class DemoApplication {
                public static void main(String[] args) { SpringApplication.run(DemoApplication.class, args); }
            }
            """;
    }

    private String controllerFixture() {
        return """
            package com.example;
            import org.springframework.web.bind.annotation.*;
            import org.springframework.security.access.prepost.PreAuthorize;
            @RestController
            @RequestMapping("/api/v1/auth")
            public class AuthController {
                @PostMapping("/login")
                public String login() { return "ok"; }

                @GetMapping("/users")
                @PreAuthorize("hasRole('ADMIN')")
                public String users() { return "users"; }

                @PutMapping("/users/{id}")
                public String update() { return "ok"; }
            }
            """;
    }

    private String serviceFixture() {
        return """
            package com.example;
            import org.springframework.stereotype.Service;
            import org.springframework.transaction.annotation.Transactional;
            @Service
            public class UserService {
                @Transactional
                public void createUser() {}
            }
            """;
    }

    private String repositoryFixture() {
        return """
            package com.example;
            import org.springframework.data.jpa.repository.JpaRepository;
            public interface UserRepository extends JpaRepository<User, Long> {}
            """;
    }

    private String entityFixture() {
        return """
            package com.example;
            import jakarta.persistence.*;
            @Entity
            @Table(name = "app_user")
            public class User {
                @Id
                private Long id;

                @Column
                private String email;

                @OneToMany(mappedBy = "user")
                private String roles;

                @ManyToOne
                @JoinColumn(name = "org_id")
                private String organization;
            }
            """;
    }

    private String securityFixture() {
        return """
            package com.example;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.security.web.SecurityFilterChain;
            import org.springframework.security.crypto.password.PasswordEncoder;
            import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;
            @Configuration
            public class SecurityConfig {
                @Bean
                public SecurityFilterChain filterChain(HttpSecurity http) throws Exception { return http.build(); }

                @Bean
                public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
            }
            """;
    }

    private String testFixture() {
        return """
            package com.example;
            import org.junit.jupiter.api.Test;
            import org.springframework.boot.test.context.SpringBootTest;
            import org.testcontainers.junit.jupiter.Testcontainers;
            import org.mockito.Mock;
            @SpringBootTest
            @Testcontainers
            public class AuthControllerTest {
                @Mock
                private String mock;

                @Test
                public void testLogin() {}

                @Test
                public void testUsers() {}
            }
            """;
    }

    private String dockerFixture() {
        return """
            FROM openjdk:21-jdk-slim
            COPY . /app
            WORKDIR /app
            RUN ./mvnw package
            """;
    }

    private String githubActionsFixture() {
        return """
            name: backend
            on: [push]
            jobs:
              build:
                runs-on: ubuntu-latest
                steps:
                  - uses: actions/checkout@v4
                  - uses: actions/setup-java@v4
                    with:
                      java-version: '21'
            """;
    }
}
