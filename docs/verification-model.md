# Verification model

## Statuses

- `SELF_CLAIM`: user-declared, no accepted evidence yet
- `EVIDENCE_FOUND`: source-backed observations exist
- `PARTIAL`: some policy gates are met, others are open
- `VERIFIED`: all required policy gates pass and no material contradiction is unresolved
- `STALE` / `EXPIRED`: previous result needs refresh
- `NOT_VERIFIED`: no accepted path or failed gates
- `DISPUTED`: candidate or reviewer has an open dispute that affects trust

## Policy inputs

```text
repository evidence
+ evidence provenance and snapshot
+ project-specific defense
+ bounded practical task
+ contribution evidence where relevant
+ skill-specific policy version
+ freshness policy
+ unresolved disputes / contradictions
```

An LLM can propose a grounded evaluation for an answer or generate a challenge. It cannot write `VERIFIED` directly. The deterministic policy engine combines explicit inputs.

## Example policy: JWT Authentication v0.1

```text
EVIDENCE_FOUND:
  at least 2 direct/strong implementation observations

PARTIAL:
  evidence found + defense meets bar OR practical task passes

VERIFIED:
  at least 2 direct/strong observations
  AND defense result = PASSED
  AND practical result = PASSED
  AND no unresolved material dispute
  AND snapshot/policy metadata recorded
```

A future policy may weight security skills differently from a language skill. Policies are versioned and stored so a result can be reconstructed later.

## Examiner evaluation

The examiner evaluates:

- code navigation: can the candidate locate the implementation?
- understanding: can they explain the flow?
- reasoning: can they explain a trade-off?
- debugging: can they isolate a failure?
- modification: can they propose a bounded change?
- consistency: does the answer align with the snapshot?

Do not use keyword presence as the production evaluator. The demo uses a small deterministic rubric only to keep the offline loop explorable; the production contract requires grounded structured output, a rubric, model/provider metadata, and a review path.

## Practical verification

Challenges are bounded and project-specific. Initial text/patch submissions can become isolated container execution later. Production sandbox requirements:

- no production-network access
- ephemeral filesystem and branch
- CPU/memory/time quotas
- no host mounts
- secret-free environment
- artifact allowlist
- static checks and test result capture
- deletion after retention period

Record files inspected, changes, tests, final result, and explanation where the execution model allows it.

## Freshness

Each result stores `lastVerifiedAt`, `latestEvidenceAt`, `freshnessState`, `refreshRequiredAt`, and the policy that calculated it. Skill category rules are configurable. A stale result remains historical evidence but should not silently appear as current proof.

## Human responsibility

Recruiters see proof rows and can request more proof. SkillLink does not recommend hire/reject, infer protected attributes, or replace recruiter judgment.
