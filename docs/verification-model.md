# Verification model

## Statuses

- `SELF_CLAIM`: user-declared, no accepted evidence yet
- `EVIDENCE_FOUND`: completed source-backed observations exist
- `PARTIAL`: some independent policy gates are met, others remain open
- `VERIFIED`: all required policy gates pass and no material contradiction is unresolved
- `STALE` / `EXPIRED`: previous result needs refresh
- `NOT_VERIFIED`: no accepted path or failed gates
- `DISPUTED`: an open dispute affects trust

## Active baseline policy

The applied Flyway seed is `skill-proof-baseline:v1.0`. Its rule set is stored in `verification_policy.rules` and the result stores the policy row/version used.

```text
repository gate:
  at least one evidence item attached to a COMPLETED repository snapshot

project defense gate:
  at least one project examination for that candidate/skill has result PASSED

practical gate:
  at least one practical challenge submission for that candidate/skill has been
  explicitly reviewed by an authorized recruiter and has status PASSED

VERIFIED:
  repository gate AND project defense gate AND practical gate

PARTIAL:
  repository gate AND exactly one of the independent gates

NOT_VERIFIED:
  repository gate is missing, or neither independent gate passes
```

The initial policy is intentionally conservative about authority: repository analysis and a candidate answer cannot directly mark a skill verified. `VerificationService` is the only application path that updates `candidate_skill` to `VERIFIED`, and it records an immutable `verification_result` with explanation, policy version, snapshot/examination/challenge references, and evaluator metadata.

## Evidence provenance

Evidence must point to an immutable repository snapshot, commit, source location, source hash where available, observation, strength, and visibility. Disputed observations are excluded from policy gates. Raw source stays private by default; recruiter-visible counts are computed from explicit visibility grants.

## Examiner evaluation

The current server-backed examiner generates up to three questions from persisted evidence references. It stores a prompt version, policy version, source context, answer text, evaluator metadata, and a deterministic bounded rubric. It requires the answer to reference supplied project context and include concrete reasoning or validation. This rubric is a safe local boundary, not an assertion that an LLM has understood the code. The optional FastAPI AI contract is schema/grounding constrained and cannot write verification status.

## Practical verification

A challenge is created only for a skill with completed source evidence. The current execution mode is `TEXT_PATCH`: a candidate submits a bounded implementation explanation, stored with a content hash and `REVIEW_REQUIRED`. A recruiter who owns a job application from that candidate can record `PASSED`, `NEEDS_CHANGES`, or `REVIEW_REQUIRED`. Isolated execution is the next provider boundary and must include:

- no production-network access
- ephemeral filesystem and branch
- CPU/memory/time quotas
- no host mounts
- secret-free environment
- artifact allowlist
- static checks and test result capture
- deletion after retention period

## Freshness and disputes

Every candidate skill stores latest evidence time, last verified time, freshness state, refresh deadline, and policy version. Every verification decision is append-only. Evidence disputes change the evidence status and are visible in audit history; they do not silently disappear.

## Human responsibility

Recruiters see proof rows and can request more proof. SkillLink does not recommend hire/reject, infer protected attributes, or replace recruiter judgment. Proof Contracts are structured decision support, not an automatic hiring decision.
