# API overview

Base path: `/api/v1`. Protected endpoints require a server-issued access token. Refresh sessions are HttpOnly cookies scoped to `/api/v1/auth`; access tokens are never persisted by the API in browser-readable cookies.

Errors use this envelope:

```json
{
  "code": "REPOSITORY_TREE_TOO_LARGE",
  "message": "Repository tree is larger than the configured analysis limit.",
  "requestId": "uuid",
  "timestamp": "2026-09-28T...Z",
  "details": {}
}
```

## Authentication and source connection

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | `/auth/register` | create a candidate or recruiter account |
| POST | `/auth/login` | issue access + refresh session |
| POST | `/auth/refresh` | rotate a non-revoked refresh session |
| POST | `/auth/logout` | revoke the presented refresh session and expire the cookie |
| GET | `/auth/me` | current identity |
| GET | `/github/connect` | begin GitHub OAuth state + PKCE flow |
| GET | `/github/callback` | consume one-time state and exchange code server-side |
| GET | `/github/status` | connection status; never returns an access token |
| GET | `/github/repositories` | list authorized repository metadata |
| POST | `/github/repositories/{githubRepositoryId}/select` | deliberately create/select one project source |
| DELETE | `/github/connection` | disconnect and invalidate the source connection |

## Candidate proof engine

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/projects` | candidate-owned selected projects |
| POST | `/projects/{projectId}/analysis` | queue an async commit snapshot analysis |
| GET | `/projects/{projectId}/analysis/{jobId}` | persisted job state/progress/error |
| POST | `/projects/{projectId}/analysis/{jobId}/retry` | retry only a failed job |
| GET | `/projects/{projectId}/evidence` | snapshot/source-backed evidence DTOs |
| GET | `/candidates/me/skills` | normalized skill projection and freshness |
| POST | `/evidence/{evidenceId}/dispute` | open a candidate dispute |
| PATCH | `/evidence/{evidenceId}/visibility` | explicitly change source visibility |
| POST | `/projects/{projectId}/examinations` | create a grounded project defense from persisted evidence |
| GET | `/examinations/{examinationId}` | defense state and source references |
| POST | `/examinations/{examinationId}/questions/{questionId}/answers` | store one auditable answer and deterministic rubric result |
| POST | `/candidates/me/challenges` | create a bounded practical task for an evidence-backed skill |
| GET | `/candidates/me/challenges/{challengeId}` | challenge and latest submission |
| POST | `/candidates/me/challenges/{challengeId}/submissions` | submit a text/patch explanation for review |
| GET | `/candidates/me/verifications` | immutable policy evaluation history |
| POST | `/candidates/me/verifications/skills/{skillId}/evaluate` | evaluate explicit policy gates; never an AI-only decision |
| GET | `/candidates/me/passport` | private passport and verified items |
| POST | `/candidates/me/passport/issue` | issue/refresh only after a verified result exists |
| PATCH | `/candidates/me/passport/visibility` | private, public summary, or public |

A defense can produce `PASSED`, but it cannot independently write `VERIFIED`. A practical submission starts as `REVIEW_REQUIRED`. A recruiter with a candidate application must review it as `PASSED` before the policy engine can verify a skill.

## Recruiter workflow

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/recruiter/jobs` | recruiter-owned jobs |
| POST | `/recruiter/jobs` | create an open job and normalize recognized taxonomy requirements |
| GET | `/recruiter/jobs/{jobId}` | job and normalized requirements |
| GET | `/jobs` | candidate-visible open jobs |
| POST | `/jobs/{jobId}/applications` | candidate application |
| GET | `/candidates/me/applications` | candidate application history |
| GET | `/recruiter/jobs/{jobId}/applications` | recruiter application queue |
| PATCH | `/recruiter/applications/{applicationId}` | recruiter status (`REVIEWING`, `SHORTLISTED`, `REJECTED`) |
| POST | `/recruiter/applications/{applicationId}/proof-contract` | generate/refresh structured contract |
| GET | `/recruiter/proof-contracts/{contractId}` | contract rows and privacy-safe proof summaries |
| PATCH | `/recruiter/proof-contracts/{contractId}/requirements/{requirementId}` | human review of one requirement |
| POST | `/recruiter/challenge-submissions/{submissionId}/review` | reviewer-owned practical outcome |

Proof Contract rows contain normalized requirements, outcome, policy-backed summary, and only recruiter-visible evidence counts. They do not expose private source code or raw source locations.

## Public proof

`GET /public/passports/{identifier}` returns a dedicated public DTO only when the holder selected `PUBLIC_SUMMARY` or `PUBLIC` and the passport is not revoked/expired. It contains verified skill summaries and policy versions, never access tokens, private repository content, or evidence source locations.

## Long-running jobs and failure semantics

Analysis returns an envelope with `jobId`, `state`, `progress`, `stage`, `snapshotId`, and safe `errorCode`/`errorMessage`. The server is the source of truth; clients poll actual state and never invent progress. Repository limits, revoked GitHub access, missing configuration, malformed snapshots, and provider failures become structured job failures.

## Authorization principles

- candidate endpoints are scoped to `principal.id()` and candidate role
- recruiter jobs/contracts require `created_by` ownership
- practical review requires the reviewer to own a job with an application from that candidate
- visibility is an explicit candidate mutation
- public endpoints use dedicated redacted DTOs
- recruiter output is decision support only; there is no automatic hiring decision endpoint
