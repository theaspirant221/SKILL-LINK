# Evidence model

## Evidence is a provenance record

An evidence item is not a score. It is a bounded observation with a source, snapshot, strength, visibility, and method.

Required conceptual fields:

| Field | Meaning |
| --- | --- |
| `evidenceId` | immutable identifier |
| `candidateId` / `projectId` | who and which work it belongs to |
| `skillId` | normalized taxonomy node |
| `sourceType` | repository, dependency, static analysis, defense, practical, institutional |
| `sourceReference` | snapshot, commit, task, or institutional record |
| `sourceLocation` | file path, symbol, line range, endpoint, or artifact location |
| `observation` | what was directly observed |
| `evidenceStrength` | weak, moderate, strong, direct |
| `verificationMethod` | how the observation was produced |
| `visibility` | private, recruiter-shared, institution-shared, public summary, public |
| `observedAt` | when the source was observed |
| `sourceHash` | content/snapshot integrity reference |
| `independentSignal` | corroborating fact, if present |

## Observation vs judgment

**Observation:** `SecurityConfig.java` registers a `SecurityFilterChain` and disables server-side sessions.

**Judgment:** Candidate understands JWT authentication.

The first can be persisted as evidence. The second requires an examination/practical/policy path and must cite its inputs.

## Pipeline

```text
Provider metadata
  -> file allowlist
  -> secret detection and redaction
  -> deterministic manifest/dependency extraction
  -> AST and symbol analysis
  -> architecture heuristics
  -> evidence candidates
  -> bounded AI interpretation
  -> source-reference validator
  -> candidate review/dispute
```

The analyzer should prefer facts such as annotations, imports, dependency coordinates, route declarations, tests, and configuration. Filename matching alone is insufficient.

## Strength model

Strength is explainable and multi-factor:

- direct implementation vs mention
- scope and complexity
- recurrence across files or tests
- independent corroboration
- project context
- recency
- contribution attribution confidence
- runtime/practical confirmation

Strength is not a universal skill hierarchy. A dependency can be strong evidence of exposure but not necessarily competence. A direct implementation plus defense and task may be enough under a specific policy.

## Integrity signals

Display facts rather than accusation:

- ownership and fork status
- repository age and snapshot history
- contributor and commit patterns
- changed files and PR/review signals where permitted
- one-time upload patterns
- consistency between answers and files
- deployment or test evidence

The product must say `Evidence integrity signals` and explain observations. It must not label a person fraudulent from a heuristic.

## Visibility rules

- `PRIVATE`: holder only or explicitly authorized evaluator
- `RECRUITER_SHARED`: a recruiter can inspect the summary and allowed sources
- `INSTITUTION_SHARED`: institution/faculty audience
- `PUBLIC_SUMMARY`: skill, status, freshness, high-level provenance
- `PUBLIC`: only when explicitly safe and enabled

A public passport never contains private repository source, secrets, raw model prompts, or private examination answers.

## Disputes

A candidate can dispute an evidence ID or verification result. The dispute keeps the original record immutable, creates a review state, records the reviewer and resolution, and updates the derived status only through an auditable action.
