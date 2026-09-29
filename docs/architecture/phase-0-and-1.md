# Phase 0 and Phase 1 implementation notes

## Delivered

### Phase 0 foundation

- monorepo with `frontend`, `backend`, `ai-engine`, `docs`, and `infrastructure`
- responsive evidence-oriented UI tokens and semantic loading/empty/error states
- React routing with an explicitly labeled offline adapter and a separate server-backed path
- Spring Boot 3 / Java 21 modular monolith, PostgreSQL/Flyway, Security/JWT boundary, and structured errors
- normalized schema for identity, projects, snapshots, skills, evidence, examinations, challenges, verification, jobs, applications, contracts, passports, disputes, and audit
- optional grounded AI provider interface plus FastAPI schema contracts
- product, architecture, evidence, verification, database, security, AI, API, local-development, and QA docs

### Phase 1 proof engine slice

- GitHub OAuth state + PKCE, encrypted server-side token storage, status/list/select/disconnect
- async repository analysis job with persisted progress/retry/failure, commit/tree/blob snapshot, file/repository limits, secret redaction, deterministic Java/manifest/source analysis, evidence sources, and normalized candidate skills
- candidate evidence dispute and explicit visibility controls
- grounded server-side project defense sourced from persisted evidence references
- bounded practical challenge with recruiter-owned review state
- versioned policy evaluation requiring repository evidence + passed defense + reviewed practical task before `VERIFIED`
- privacy-safe Proof Passport with private/public-summary/public visibility
- recruiter jobs, taxonomy requirement extraction, applications, candidate status workflow, structured Proof Contract, and requirement review
- real frontend auth, GitHub connection, analysis polling, evidence, skills, examiner, passport, recruiter jobs/contracts; offline fixture remains clearly separated
- automated tests for auth/API contracts, worker orchestration, deterministic analysis, secret redaction, and mocked GitHub snapshot fetching

## Next coherent implementation slice

1. add Testcontainers/PostgreSQL CI coverage once a Docker-enabled runner is available
2. add provider HTTP client/reference validation and prompt execution telemetry
3. add isolated practical execution service with no network/host mounts
4. add cursor pagination, notification delivery, retention/deletion jobs, and richer dispute resolution
5. add candidate job discovery/application UI and candidate view of shared Proof Contracts
6. add institution/admin membership workflows and signed/verifiable credential export
7. strengthen GitHub webhook/re-analysis freshness and rate-limit handling
