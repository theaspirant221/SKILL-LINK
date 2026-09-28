# Product vision

## North star

**Capability should be inspectable.**

SkillLink is the verification layer between work and opportunity. It turns real project work into source-backed evidence, then combines that evidence with a bounded project defense and practical verification. The product helps a candidate demonstrate capability and helps a recruiter inspect why a capability is considered verified.

SkillLink is intentionally not a job board, generic resume builder, social network, certificate marketplace, opaque talent score, or AI hiring decision-maker.

## Proof loop

```text
Claim -> Real work -> Evidence -> Project defense -> Practical verification
     -> Policy evaluation -> Freshness -> Job proof -> Portable passport
```

The fundamental unit is **proof**, not profile. A person can declare a skill, but the declaration should never be confused with a verified capability.

## MVP vertical slice

The first coherent slice is:

1. candidate enters a local/demo workspace
2. candidate connects an explicitly labeled repository fixture
3. an async-shaped analysis job advances through meaningful stages
4. deterministic facts and source locations become evidence cards
5. evidence maps to a normalized skill taxonomy
6. a bounded examiner asks questions about the actual project
7. a practical authorization task can be submitted
8. policy state changes from evidence-found to verified only when gates are present
9. the passport exposes status, freshness, and provenance
10. a recruiter compiles a job description into a proof contract
11. recruiter compares requirement rows and opens a proof replay

The browser preview makes its demo boundary visible. It does not pretend to have performed live GitHub OAuth, external LLM calls, or arbitrary code execution.

## Personas

- **Candidate:** owns claims, projects, evidence visibility, examinations, challenges, disputes, and passport sharing.
- **Recruiter:** defines proof contracts, inspects permitted evidence, requests targeted verification, and makes the human hiring decision.
- **College:** later manages students, departments, batches, projects, verification progress, readiness, and gaps.
- **Platform operator:** manages taxonomy, policies, prompts, issuers, disputes, security events, and model configuration.
- **Public verifier:** sees only the holder-authorized passport surface.

## Product principles

1. **No claim without evidence.** Claims may exist, but are labeled as claims.
2. **No verified skill without a policy path.** AI interpretation is not a verification decision.
3. **Source before summary.** Deterministic parsing and static analysis precede expensive interpretation.
4. **Explainability over scores.** A recruiter should see requirement, proof, method, date, freshness, and gaps.
5. **Privacy by default.** Public proof is a redacted summary, never an accidental source-code dump.
6. **Human responsibility.** SkillLink supports hiring; it does not decide whom to hire.
7. **Freshness matters.** Proof is time-bound and refreshable.
8. **Disputes are first-class.** Candidates can challenge observations and evaluations.

## Success metrics

- evidence grounding accuracy and unsupported-assertion rate
- percentage of analyses that produce source-linked evidence
- defense and practical verification completion rate
- dispute rate and resolution time
- time from repository sync to inspectable proof
- recruiter proof rows inspected per candidate
- candidate proof shared with explicit permission
- stale-proof refresh completion

Do not optimize likes, feed engagement, opaque match scores, or inferred personality traits.
