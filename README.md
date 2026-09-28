# SkillLink 2.0

> Don't claim your skills. Prove them.

SkillLink is an evidence-first proof-of-skill and talent verification platform. The first vertical slice turns a repository snapshot into traceable evidence, runs a project-specific defense, evaluates a bounded practical task, produces an explainable verification result, and lets a recruiter compare that proof against a structured job requirement.

## Current build status

This repository contains a working Phase 0 / Phase 1-oriented demo slice:

- premium responsive React/Vite product surface
- deterministic offline repository fixture for `FoodBridge API`
- repository connection and analysis job UX with real job-state transitions
- traceable evidence cards tied to files and a commit snapshot
- project-specific AI Proof Examiner UX
- bounded practical verification task
- explainable skill status and freshness model
- recruiter job-to-proof contract and evidence review
- public Proof Passport verification view
- Spring Boot 3 / Java 21 modular-monolith foundation
- AI-engine contract and prompt registry foundation
- Flyway schema for core proof entities
- architecture, security, AI, verification, API, and local-development docs

The browser preview uses an explicit **Demo workspace**. It never presents the offline fixture as a live GitHub analysis. Live GitHub OAuth, external LLM calls, and sandboxed code execution are represented by production contracts and backend scaffolding, and are intentionally not faked in the demo.

## Run the working preview

```bash
cd SKILL-LINK/frontend
npm install
npm run dev -- --host 0.0.0.0
```

Or from the repository root:

```bash
npm install
npm run dev
```

The Vite app opens at the local URL printed by Vite. Use the demo sign-in to enter the workspace.

## Demo path

1. Open **Build My Proof**.
2. Sign in with any values (the banner explains this is local demo mode).
3. Open **Projects → FoodBridge API**.
4. Connect the explicitly labeled offline GitHub fixture and run analysis.
5. Review file-backed evidence.
6. Open **AI Examiner** and answer the project-specific defense.
7. Complete the practical role-authorization task.
8. Open **Proof Passport** to see JWT Authentication become `VERIFIED`.
9. Switch to **Recruiter view** from the profile menu.
10. Create a job or use the seeded `Java Backend Developer` proof contract.
11. Inspect the requirement-by-requirement proof review.

## Production direction

The browser demo is a local adapter. The production path is:

```text
React client
  -> /api/v1
Spring Boot modular monolith
  -> PostgreSQL + Flyway
  -> queue/worker boundary for analysis jobs
  -> GitHub OAuth adapter
  -> AI provider adapter / optional FastAPI engine
  -> object storage for redacted snapshots and artifacts
```

See `docs/local-development.md`, `docs/system-architecture.md`, and `docs/api-overview.md` for boundaries and next implementation steps.
