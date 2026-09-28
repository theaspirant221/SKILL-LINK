# API overview

Base path: `/api/v1`

All protected endpoints require a server-issued access token. Responses should include `X-Request-Id` and use the error envelope below.

```json
{
  "code": "PROJECT_ANALYSIS_FAILED",
  "message": "Project analysis could not be completed.",
  "requestId": "uuid",
  "details": {}
}
```

## Core endpoints

| Area | Endpoint | Purpose |
| --- | --- | --- |
| auth | `POST /auth/signup` | create identity and profile |
| auth | `POST /auth/login` | issue access + refresh tokens |
| auth | `POST /auth/refresh` | rotate refresh token |
| github | `GET /github/connect` | begin OAuth state/PKCE flow |
| github | `GET /github/repositories` | list permitted repositories |
| projects | `POST /projects` | create project from repository |
| projects | `POST /projects/{id}/analysis` | queue snapshot analysis |
| projects | `GET /projects/{id}` | project and latest snapshot |
| evidence | `GET /projects/{id}/evidence` | paginated source-backed evidence |
| skills | `GET /skills` | taxonomy search and aliases |
| examinations | `POST /examinations` | create project-specific defense |
| examinations | `POST /examinations/{id}/answers` | save an answer |
| examinations | `POST /examinations/{id}/submit` | finish bounded session |
| challenges | `POST /challenges` | create targeted practical task |
| challenges | `POST /challenges/{id}/submissions` | submit patch/text/artifact |
| verifications | `GET /candidates/me/skills` | derived skill status/freshness |
| verifications | `GET /verifications/{id}` | explainable result and policy |
| jobs | `POST /jobs` | create job and draft proof contract |
| jobs | `GET /jobs/{id}/proof-contract` | normalized requirements |
| proof | `GET /proof-passports/me` | holder passport |
| proof | `POST /proof-passports/{id}/share` | update visibility/share grant |
| public | `GET /public/proof-passports/{identifier}` | public redacted verification |
| disputes | `POST /disputes` | dispute evidence/result |

## Long-running jobs

Analysis, code execution, report generation, and credential issuance return a job envelope:

```json
{
  "jobId": "uuid",
  "state": "QUEUED",
  "resourceId": "uuid",
  "createdAt": "2026-09-28T...Z"
}
```

Clients poll or subscribe to a notification channel. The server is the source of truth; the UI does not invent progress.

## Pagination

Use cursor pagination for evidence, candidates, audit logs, and notifications:

```text
?limit=25&cursor=...
```

Every list supports documented filter/sort fields. Reject unbounded queries.

## Privacy and authorization

Every controller calls an authorization service that checks role, organization membership, resource ownership, and share grants. Public endpoints return a dedicated DTO, never the internal evidence entity.
