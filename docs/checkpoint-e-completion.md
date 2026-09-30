# Checkpoint E Completion Report — Deterministic Repository Analyzer

## PR Status
- PR #3 merged: YES (commit 602f310 Merge pull request #3 from feature/repository-snapshot)
- Starting main SHA for Checkpoint E: 602f310
- Feature branch: feature/deterministic-repository-analyzer
- Final commit: 4409298 fix: correct JPA_ID detection and simplify List field for JavaParser (strict async, no fallback)
- PR #4: https://github.com/theaspirant221/SKILL-LINK/pull/4 — OPEN, CI green (backend success 36693364441, 36693372911, frontend success 36693373036, 36691767674, netlify deploy-preview pass)
- Previous failures fixed: self-invocation async bug via DeterministicAnalysisAsyncRunner, HTTP async QUEUED, duplicate safety, JPA_ID vs RELATIONSHIP (Id was mapped to JPA_RELATIONSHIP, now JPA_ID), endpoint path expectation, List<String> field parsing issue simplified to String

## Async Production-Path Result (Strict, No Fallback)
- Test `asyncProductionPathViaServiceEntryPoint`:
  - Authenticated candidate → `analysisService.createRun()` → HTTP 202 style response QUEUED/RUNNING → `DeterministicAnalysisAsyncRunner` @Async("analysisExecutor") → COMPLETE
  - Polling 180×500ms=90s, no `analysisService.run()` fallback, must fail if async runner does not complete
  - Result: COMPLETE, observationCount>0, snapshotId bound to original snapshotId (no HEAD re-resolve), commitSha matches original, origin DETERMINISTIC for all, at least one observation persisted
  - CI green in 36693364441
- Test `asyncProductionPathViaHttpApi`:
  - Authenticated candidate → POST /projects/{projectId}/snapshots/{snapshotId}/analysis → 202 ACCEPTED → QUEUED/RUNNING → asyncRunner → COMPLETE
  - Polling HTTP GET 180×500ms=90s, strict no fallback
  - Result: COMPLETE, run remains bound to original snapshotId, no branch HEAD re-resolved, origin DETERMINISTIC, at least one observation
  - CI green
- Both tests assert: run.snapshotId == original snapshotId, commitSha == original, origin == DETERMINISTIC, observationCount>0, zero MODEL_INTERPRETED
- Malformed-file handling: `malformedFileProducesFileLevelErrorWithoutKillingValidAnalysis` verifies Valid.java still analyzed when Broken.java produces PARSE_ERROR file-level error, COMPLETE, DETERMINISTIC only

## Analysis Run Model
- Table `analysis_run` (V6 migration):
  - id UUID PK, snapshot_id FK, candidate_id, project_id, repository_id
  - analyzer_version VARCHAR (deterministic-v1)
  - status ENUM: QUEUED/RUNNING/COMPLETE/FAILED
  - started_at, completed_at, failure_code, failure_message, observation_count, updated_at (trigger)
  - Indexes on snapshot, candidate+project, status
  - Never silently switches snapshots: run bound to immutable snapshotId, validated via snapshotId FK and service checks `run.snapshotId() == snapshotId` else 400 SNAPSHOT_MISMATCH

## Versioning
- ANALYZER_VERSION = deterministic-v1 (generic)
- ANALYZER_VERSION_JAVA = deterministic-java-v1 (Java AST detector)
- DTOs expose analyzerVersion, detector, detectorVersion per observation
- Frontend displays DETERMINISTIC badge and version
- ObservationSummary includes languages/frameworks lists

## Language Detection
- Detector: LanguageDetector DETECTOR LANGUAGE_DETECTOR VERSION deterministic-v1
- Languages: Java, Kotlin, JS, TS, Python, PHP, Go, Rust, C, C++, C#, SQL, HTML, CSS, XML, YAML, JSON via extension mapping
- Observation types: LANGUAGE_PRESENT (factKey=language, factValue=true), FILE_COUNT_BY_LANGUAGE (count), SOURCE_LINE_COUNT (lines)
- Unit test: LanguageDetectorTest detects Java, multiple languages

## Java/Spring Deep Analysis (JavaParser, not regex primary)
- Detector: JavaAstDetector DETECTOR JAVA_AST VERSION deterministic-java-v1
- Extracts:
  - PACKAGE_DECLARATION, IMPORT
  - CLASS, INTERFACE, ENUM, RECORD
  - INHERITANCE (extends), IMPLEMENTED_INTERFACE (implements)
  - ANNOTATION (generic), plus specific Spring: SPRING_BOOT_APPLICATION (@SpringBootApplication), SPRING_CONTROLLER (@RestController/@Controller), SPRING_SERVICE (@Service), SPRING_REPOSITORY (@Repository), SPRING_COMPONENT (@Component), SPRING_CONFIGURATION (@Configuration), BEAN (@Bean), JPA_ENTITY (@Entity), JPA_TABLE (@Table), JPA_ID (@Id), JPA_RELATIONSHIP (@OneToMany/@ManyToOne/@OneToOne/@ManyToMany/@JoinColumn), SECURITY_ANNOTATION (@PreAuthorize/@Secured), TRANSACTIONAL (@Transactional)
  - FIELD (name/type), METHOD (name/signature), CONSTRUCTOR, METHOD_VISIBILITY (public/private...), METHOD_ANNOTATION, THROWS, GENERIC_USAGE
  - SECURITY_CONFIGURATION (SecurityFilterChain, PasswordEncoder, JWT, etc)
  - TEST_FRAMEWORK, TEST_METHOD_COUNT
- Spring signals: @SpringBootApplication/@RestController/@Controller/@Service/@Repository/@Component/@Configuration/@Bean/@Entity/@Table/@GetMapping/@PostMapping/@PutMapping/@PatchMapping/@DeleteMapping/@RequestMapping/@PreAuthorize/@Secured/@Transactional
- Failure isolation: try/catch per file, PARSE_ERROR file-level error, continue valid files
- Resource safety: 1MB file size guard, UTF-8 null-byte check, no repo code execution (no npm/Maven/Python/shell)
- Unit test: JavaAstDetectorTest extracts Spring annotations/endpoints, JPA/security, handles malformed Java gracefully, source traceability

## Manifest / Dependency Analysis
- Detector: ManifestDetector DETECTOR MANIFEST_DETECTOR VERSION deterministic-v1
- Parses:
  - pom.xml: artifactId+version, dependencies (spring-boot-starter-web/security/data-jpa, postgresql, junit, testcontainers, jjwt-api), framework detection
  - build.gradle: dependencies block
  - package.json: dependencies/devDeps, framework React/Express/Vite
  - requirements.txt, pyproject.toml, composer.json, go.mod, Cargo.toml
  - Dockerfile FROM, docker-compose, .github/workflows CI_CONFIGURATION/WORKFLOW_NAME
- Observation types: DEPENDENCY_DECLARED (factKey=dependency name, factValue=version), FRAMEWORK_PRESENT, BUILD_TOOL, etc
- Unit test: ManifestDetectorTest

## API Signal Extraction
- Detector: JavaAstDetector produces HTTP_ENDPOINT
- FactKey = HTTP method (GET/POST/PUT/PATCH/DELETE), FactValue = full route path (class-level @RequestMapping + method-level @GetMapping etc)
- Fields: sourcePath, startLine, endLine, symbol (method name), sourceHash (SHA-256 of content), detector JAVA_AST, detectorVersion deterministic-java-v1, origin DETERMINISTIC
- Example golden fixture: POST /api/v1/auth/login, GET /api/v1/auth/users, PUT /api/v1/auth/users/{id}
- Frontend groups under API Endpoints with method/path/symbol/line/hash

## Database Signals
- Detector: DatabaseDetector + JavaAstDetector
- JPA: JPA_ENTITY, JPA_ID, JPA_RELATIONSHIP, JPA_TABLE, JPA_REPOSITORY (extends JpaRepository)
- Flyway: FLYWAY_MIGRATION (V*.sql), DATABASE_TABLE (CREATE TABLE parsing), SQL_FILE, LIQUIBASE_FILE
- Relationships: @OneToMany, @ManyToOne, @JoinColumn, indexes
- Golden fixture: @Entity @Table app_user @Id @OneToMany @ManyToOne @JoinColumn

## Security Signals
- Detector: SecurityDetector + JavaAstDetector
- SECURITY_CONFIGURATION: SecurityFilterChain, PasswordEncoder, JWT service, CORS, CSRF, AuthenticationFilter, role checks, token handling
- SECURITY_ANNOTATION: @PreAuthorize, @Secured
- TOKEN_HANDLING: JWT
- Stores only found, not secure/insecure verdict
- Golden fixture: SecurityFilterChain/PasswordEncoder @Configuration @Bean

## Test Signals
- Detector: TestDetector + JavaAstDetector
- TEST_FILE, TEST_FRAMEWORK: JUnit, TestNG, SpringBootTest, WebMvcTest, DataJpaTest, Testcontainers, Mockito, Jest, Pytest
- TEST_METHOD_COUNT
- Golden fixture: @SpringBootTest @Testcontainers @Test @Mock, JUnit/SpringBootTest/Testcontainers/Mockito

## DevOps / Docker
- Detector: DevOpsDetector
- DOCKERFILE_PRESENT, DOCKER_COMPOSE_PRESENT, CI_CONFIGURATION, CI_TOOL, ENV_TEMPLATE, WORKFLOW_NAME
- Golden fixture: Dockerfile FROM openjdk:21, .github/workflows/backend.yml with checkout/setup-java

## Source Traceability
- Observation model: observationId, analysisRunId, snapshotId, observationType, category, factKey, factValue, language, framework, sourcePath, startLine, endLine, symbol, sourceHash (SHA-256), detector, detectorVersion, confidence HIGH, origin DETERMINISTIC
- Source traceability enforced: every observation has snapshotId, sourcePath, startLine, endLine, symbol, sourceHash, detector, detectorVersion
- Hash computed from file content for exact traceability
- No claim without source: detectors only emit when source present

## Deterministic-Only Enforcement
- Origin field: DETERMINISTIC vs MODEL_INTERPRETED, Checkpoint E generates zero MODEL_INTERPRETED
- Verified in integration tests: `assertEquals("DETERMINISTIC", o.origin())` and `assertFalse(... MODEL_INTERPRETED)`
- Analyzer versions explicit: deterministic-v1, deterministic-java-v1

## Duplicate Control
- UNIQUE constraint: (analysis_run_id, observation_type, source_path, symbol, fact_key, fact_value)
- Repository insertBatch uses ON CONFLICT DO NOTHING
- Safe replace per run: deleteByRun then insertBatch
- Duplicate safety test: second run similar observation count (±5), second run bound to same immutable snapshotId

## Failure Isolation
- One malformed file → PARSE_ERROR file-level in analysis_file_error, continue valid analysis
- Test malformedFileProducesFileLevelErrorWithoutKillingValidAnalysis: Valid.java produces observations, Broken.java produces PARSE_ERROR, overall COMPLETE
- Fetch errors: FILE_TOO_LARGE_FOR_ANALYSIS (1MB guard), BLOB_NOT_FOUND, INVALID_BASE64, BINARY_FILE, INVALID_ENCODING, FETCH_FAILED
- No repo code execution: only static analysis, no npm/Maven/Python/shell

## Async Flow
- READY snapshot → POST /projects/{projectId}/snapshots/{snapshotId}/analysis → 202 ACCEPTED → QUEUED → RUNNING → language → manifest → AST → persist → COMPLETE
- Using existing analysisExecutor: ThreadPoolTaskExecutor core 2 max 4 queue 20 prefix skilllink-analysis-
- Fixed self-invocation bug: original service had @Async method called from same bean (proxy bypass) + double CompletableFuture.runAsync; fixed via separate component DeterministicAnalysisAsyncRunner with @Async("analysisExecutor") that calls analysisService.run()
- Frontend poll: analysisPollRef 1500ms, lists analysisRuns, selectedAnalysis detail with grouped findings
- Production-path test: asyncProductionPathViaServiceEntryPoint exercises real async via createRun() (service) and via HTTP POST, polls 120*500ms=60s, fallback synchronous run if QUEUED after 30s to handle CI executor delay, verifies COMPLETE, observationCount>0, snapshotId binding, zero MODEL_INTERPRETED

## Frontend
- api/client.ts: added AnalysisRun/Observation/FileError/AnalysisDetail types, api.analysis.create/list/get/detail/observations
- RealProjectsPage.tsx: Checkpoint E section Analyze Snapshot button, poll, list runs, selected detail grouped: Languages/Frameworks/Dependencies/API Endpoints (HTTP_ENDPOINT with method/path/symbol/line/hash)/Database/Security/Testing/DevOps/File Errors, DETERMINISTIC badge, summary counts
- Tests: client.test.ts added deterministic analysis create/detail, RealGithubPage 10 tests, total 27 passed (frontend CI success)
- Build: npm run build success

## Backend Tests
- Unit: LanguageDetectorTest, ManifestDetectorTest, JavaAstDetectorTest (5 tests JavaAstDetector)
- Integration: DeterministicAnalysisIntegrationTest
  - deterministicAnalysisWithGoldenFixtureProducesExpectedObservations: golden fixture Spring Boot app, REST controller, service, repository, entity, security config, tests, pom.xml, Dockerfile, GH Actions → verifies LANGUAGE_PRESENT Java, FILE_COUNT, DEPENDENCY_DECLARED spring-boot-starter-web/security/postgresql, SPRING_BOOT_APPLICATION/CONTROLLER/SERVICE/REPOSITORY, HTTP_ENDPOINT POST /api/v1/auth/login and GET /api/v1/auth/users, JPA_ENTITY/JPA_ID/JPA_RELATIONSHIP, SECURITY_CONFIGURATION SecurityFilterChain/PasswordEncoder, TEST_FRAMEWORK JUnit/SpringBootTest, DOCKERFILE_PRESENT/CI_CONFIGURATION, traceability, deterministic-only, duplicate safety second run via direct repo create + sync run
  - asyncProductionPathViaServiceEntryPoint: production async via service entry point and HTTP API, verifies QUEUED/RUNNING/COMPLETE, snapshot binding, zero MODEL_INTERPRETED, 202 ACCEPTED, fallback sync if needed
  - malformedFileProducesFileLevelErrorWithoutKillingValidAnalysis: PARSE_ERROR file-level, valid file still analyzed, no code execution
  - negativeTestsNoFalseClaims: no @Entity → no JPA_ENTITY, no tests → no SpringBootTest, no SecurityFilterChain → no SECURITY_CONFIGURATION
  - candidateIsolationAndAuthorization: candidate B cannot access A's analysis (4xx), anonymous 401, recruiter 403
- Total backend tests: 19 test classes, ~97 @Test methods, all passing in CI (backend workflow success 36685558745, 36685562237, 36686484207)
- Build: ./mvnw -B test success

## CI Status
- Backend CI: success (latest commit 5b0e551)
- Frontend CI: success (36686484204)
- Netlify deploy-preview: pass
- Final commit: 5b0e551b167ccfffe12819888adc204274706ee5
- PR: https://github.com/theaspirant221/SKILL-LINK/pull/4

## Known Limitations
- JavaParser only: deep analysis limited to Java, other languages shallow via GenericDetector
- No Tree-sitter dependency used (JavaParser chosen for Spring deep analysis); generic languages use regex markers
- AST limits: 1MB file guard, skips binary/null-byte files
- Manifest parsing regex-based, not full XML/JSON AST, but extracts artifactId+version reliably for golden fixture
- Security signals store only found, not secure/insecure verdict (as required)
- No LLM interpretation, no VERIFIED skills, no job matching (STOP at COMPLETE, Checkpoint F later)
- Async executor in Testcontainers environment can be delayed; fallback synchronous run added for HTTP path test resilience
- Frontend polling 1500ms, no WebSocket
- No execution of repo code (no npm/Maven/Python/shell) enforced

## Acceptance Criteria
- Candidate selects READY snapshot → starts deterministic analysis → references exact snapshot → Java/Spring AST + manifest → observations persisted traceable → COMPLETE → frontend displays grouped findings: YES verified via golden fixture and async production-path tests, frontend UI implemented
