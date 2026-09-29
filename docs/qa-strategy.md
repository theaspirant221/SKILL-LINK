# QA strategy

## Current checks

```bash
cd frontend
npm run build
npm test -- --run

cd ../backend
JAVA_HOME=/path/to/jdk-21 ./mvnw -B test
```

The current backend suite covers:

- health/API response contract
- auth refresh-required, revoked-refresh, logout cookie clearing, and structured errors
- deterministic manifest/AST/source mapping and secret redaction
- mocked GitHub commit/tree/blob snapshot filtering and immutable metadata
- analysis worker success/failure state transitions

The frontend fixture suite asserts that evidence points to a snapshot, JWT does not start verified, and the seeded demo job remains requirement-by-requirement.

## Vertical E2E targets

### Offline fixture

```text
landing -> explicitly labeled offline sign-in -> fixture source -> analysis states
-> evidence -> grounded demo defense -> bounded demo task -> passport
-> recruiter job -> proof contract -> review
```

### Server-backed

```text
candidate registration -> GitHub OAuth -> explicit repository selection
-> async analysis -> persisted evidence -> visibility share
-> grounded defense -> practical submission
-> recruiter application/review -> policy evaluation -> private/public passport
-> recruiter job -> Proof Contract -> requirement review
```

## Regression suites

- frontend route rendering and responsive layouts
- analysis state transitions, retry, and provider/configuration errors
- source-reference rendering, dispute, and visibility boundaries
- verification policy truth table and immutable history
- cross-role/resource authorization
- public passport redaction, expiry, and revocation
- GitHub token lifecycle, state/PKCE, and disconnect
- prompt schema validation and grounding references
- practical sandbox quotas and no-network boundary
- accessibility keyboard/focus/status announcements

No test should assert a numeric opaque match score as the reason for a recruiter outcome. When Docker is available, add Testcontainers PostgreSQL/Flyway and browser E2E coverage in CI.
