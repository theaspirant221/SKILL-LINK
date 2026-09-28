# Phase 0 and Phase 1 implementation notes

## Delivered

### Phase 0 foundation

- monorepo layout with `frontend`, `backend`, `ai-engine`, `docs`, `infrastructure`, and `scripts`
- dark/light-ready visual tokens and responsive accessible primitives
- React routing, candidate/recruiter shells, public verifier
- explicit demo adapter state with local persistence and reset
- Spring Boot 3 / Java 21 foundation, security boundary, health endpoint, Flyway migration
- normalized schema for users, projects, snapshots, skills, evidence, examination, challenges, verification, jobs, passports, disputes, audit, notifications
- AI engine schema contracts and grounding rules
- docs covering architecture, model, security, API, database, local development

### Phase 1 proof engine slice

- offline GitHub fixture connection is clearly labeled and non-deceptive
- repository analysis progresses through real local job states: connected, processing, completed
- evidence cards point to file paths and immutable commit `abc1234`
- normalized skills and living proof chain
- project-specific examiner with code-location, architecture, and security questions
- practical role-authorization task with bounded local rubric
- verification state updates only after defense/practical gates
- public/private passport surfaces and recruiter proof review
- job description to normalized proof contract

## Next coherent implementation slice

1. add real Spring API DTO/controllers for project/snapshot/evidence reads
2. implement GitHub OAuth state/PKCE and server-side token vault
3. implement worker queue and repository snapshot service
4. implement Java/JS/TS/Python AST adapters and secret redaction
5. replace local store data calls with TanStack Query API calls
6. add Testcontainers integration tests for authorization and Flyway schema
7. add isolated practical execution service
8. implement disputes and recruiter share grants end to end
