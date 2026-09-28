# QA strategy

## Current checks

From the repository root:

```bash
npm run test
npm run build
```

The fixture tests assert that evidence points to the project snapshot, that JWT does not start as verified, and that the seeded job is requirement-by-requirement.

## Vertical E2E target

The first Playwright flow should cover:

```text
landing -> demo sign-in -> connect fixture -> analyze -> evidence
-> start defense -> answer three grounded questions -> submit practical task
-> JWT becomes VERIFIED -> passport -> recruiter switch -> job contract -> proof review
```

## Regression suites

- frontend route rendering and responsive layouts
- analysis state transitions and retry/error states
- source-reference rendering and visibility
- verification policy truth table
- cross-role/resource authorization
- public passport redaction and revocation
- GitHub token lifecycle and disconnect
- prompt schema validation and grounding references
- practical sandbox quotas and no-network boundary
- accessibility keyboard/focus/status announcements

No test should assert a numeric opaque match score as the reason for a recruiter outcome.
