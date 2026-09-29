# SkillLink 2.0

> Don't claim your skills. Prove them.

SkillLink is an evidence-first proof-of-skill platform. The working vertical slice starts with a candidate-owned repository source, pins analysis to a commit, persists traceable evidence, normalizes skills, supports a grounded project defense, requires a reviewer-owned practical verification, evaluates a versioned policy, publishes a privacy-controlled Proof Passport, and lets a recruiter review a structured Proof Contract.

## What is implemented

### Offline demo mode
The default Vite experience is an explicitly labeled deterministic fixture. It is useful for reviewing the product language and interaction model without credentials. It never masquerades as live GitHub, live AI, or sandbox execution.

### Server-backed mode
Set `VITE_DEMO_MODE=false` to use the real React/API path. The current backend slice includes:

- Java 21 / Spring Boot 3 modular monolith with PostgreSQL and Flyway
- password registration/login, short-lived JWT access tokens, hashed rotating refresh tokens, logout revocation, RBAC, structured errors, and audit events
- GitHub OAuth state + PKCE flow, encrypted server-side access tokens, repository listing/selection, disconnect, and explicit missing-configuration errors
- async analysis jobs with persisted state/progress/retry, immutable commit/tree snapshots, file/repository limits, secret redaction, deterministic Java/manifest/source analysis, normalized taxonomy mapping, evidence sources, and candidate-skill projections
- evidence listing, candidate disputes, explicit visibility controls, and privacy-safe recruiter summaries
- grounded project defense questions generated from persisted evidence references; answers are stored with rubric/prompt metadata and cannot independently verify a skill
- bounded practical text-patch challenge with reviewer-owned `PASSED`/`NEEDS_CHANGES` outcomes
- versioned policy evaluation requiring completed repository evidence, a passed project defense, and a reviewed practical result before `VERIFIED`
- recruiter job creation with controlled-taxonomy requirement extraction, applications, status review, and requirement-by-requirement Proof Contracts
- candidate job discovery with normalized requirement previews, applications with optional notes, application history, and a privacy-safe candidate view of shared Proof Contracts that never includes recruiter reviewer notes
- private/public-summary Proof Passport issuance containing only policy-verified skills; public DTOs exclude source code and source locations, and the public verification page resolves real public identifiers
- optional FastAPI structured-output AI boundary; the policy engine remains the only writer of verified status

## Run locally

### 1. PostgreSQL

```sql
CREATE USER skilllink PASSWORD 'change-me';
CREATE DATABASE skilllink OWNER skilllink;
```

Docker Compose is available under `infrastructure/docker-compose.yml`, but Docker must be installed and running.

### 2. Backend

```bash
cd SKILL-LINK/backend
export JAVA_HOME=/path/to/jdk-21
export DATABASE_URL=jdbc:postgresql://localhost:5432/skilllink
export DATABASE_USERNAME=skilllink
export DATABASE_PASSWORD=change-me
# base64-encoded secret; replace in real environments
export JWT_SECRET=c2tpbGxsaW5rLWxvY2FsLXNlY3JldC1jaGFuZ2UtYmVmb3JlLXByb2R1Y3Rpb24tcGxlYXNlLXZlcnktbG9uZw==
./mvnw spring-boot:run
```

Flyway applies V1–V3 on startup. GitHub credentials are intentionally optional; without them, the API returns `GITHUB_NOT_CONFIGURED` rather than loading fixture repositories.

### 3. Frontend

```bash
cd SKILL-LINK/frontend
npm install
printf 'VITE_DEMO_MODE=false\nVITE_API_BASE_URL=/api/v1\nVITE_BACKEND_URL=http://localhost:8080\n' > .env.local
npm run dev -- --host 0.0.0.0
```

For the offline fixture, omit `.env.local` or set `VITE_DEMO_MODE=true`.

### 4. Optional AI engine

```bash
cd SKILL-LINK/ai-engine
python -m venv .venv
. .venv/bin/activate
pip install -e '.[test]'
uvicorn app:app --host 0.0.0.0 --port 8000
```

Provider calls are disabled by default. The AI contract accepts only redacted, source-referenced context and has no endpoint that can mark a skill verified.

## Verification commands

```bash
# frontend
cd frontend && npm run build && npm test -- --run

# backend, Java 21
cd backend && ./mvnw -B test
```

The backend tests cover auth error/cookie behavior, deterministic analysis, secret redaction, mocked GitHub commit/tree/blob snapshot fetching, worker state transitions, health/API contracts, and the candidate Proof Contract projection (including that reviewer notes are never exposed). The frontend tests cover the offline fixture contract and the candidate API client request/response envelope.

## Real proof loop

1. Create a candidate account in server-backed mode.
2. Connect GitHub and explicitly select one repository.
3. Start analysis and poll the persisted job state.
4. Inspect evidence and choose what may be recruiter-shared.
5. Start the project defense; answers remain auditable and source-grounded.
6. Create and submit the bounded practical challenge.
7. A recruiter with an application reviews the submission; only then can policy evaluation produce `VERIFIED`.
8. Issue a private Proof Passport or explicitly change its visibility.
9. A recruiter creates a job, receives an application, generates a Proof Contract, and reviews each requirement.
10. From the candidate Jobs page, discover open roles, apply with an optional note, and inspect the shared Proof Contract without reviewer notes.

See `docs/local-development.md`, `docs/api-overview.md`, `docs/verification-model.md`, and `docs/security-model.md` for boundaries and operating assumptions.
