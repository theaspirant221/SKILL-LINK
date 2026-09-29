# AI architecture

## Provider boundary

The backend exposes `com.skilllink.api.ai.GroundedAiProvider` as an optional interpretation boundary. Its context contains a project, immutable snapshot/commit, deterministic facts, and explicit source references. The default `DisabledGroundedAiProvider` returns a clearly labeled disabled result. The existing FastAPI service implements the same contract shape for optional deployment.

Conceptually:

```text
ProjectAnalyzer.analyze(snapshotContext) -> GroundedProjectAnalysis
Examiner.generateQuestion(examContext) -> GroundedQuestion
AnswerEvaluator.evaluate(answerContext) -> BoundedEvaluation
ChallengeGenerator.generate(gapContext) -> PracticalChallenge
JobCompiler.compile(jobText, taxonomy) -> ProofContractDraft
ProofExplainer.explain(verificationContext) -> GroundedExplanation
```

Provider choice is configuration, not a domain decision. A provider may suggest observations/questions/explanations but must not mutate `candidate_skill.status` or create a `VERIFIED` result. `VerificationService` is the only policy writer.

## Grounding contract

```json
{
  "skillKey": "jwt-authentication",
  "observations": ["..."],
  "evidenceReferences": [
    {
      "sourceType": "STATIC_ANALYSIS",
      "location": "src/.../JwtService.java",
      "sourceHash": "...",
      "observation": "...",
      "strength": "DIRECT"
    }
  ]
}
```

Validation rejects an assertion without a reference. A reference validator must check that the location and hash exist in the requested immutable snapshot and that the model did not introduce a file outside the supplied context. Context is redacted before any optional provider call.

## Prompt and execution metadata

Important prompts are versioned records: prompt ID/version, input schema, output schema, parameters, active flag, provider/model, snapshot reference, output hash, latency, token/cost metadata, and validation status. The server-backed examiner currently uses `project-defense:v1` with a deterministic rubric so local behavior is reproducible; the provider boundary is ready for grounded structured generation.

## Retrieval and privacy

Start with deterministic allowlists and repository structure. Add pgvector only when semantic retrieval is needed. Retrieval chunks preserve file path, symbol, line range, commit, and redaction metadata. Private source is never emitted by the public passport or recruiter Proof Contract; visibility is a separate candidate-controlled mutation.

## Evaluation quality

Measure grounded-reference precision/recall, unsupported-assertion rate, schema validity, evaluator consistency, false-positive evidence rate, dispute rate, and time/cost per analysis. Adversarial fixtures should cover prompt-injection comments, misleading READMEs, copied snippets, missing files, secrets, and huge repositories.

## Local/demo mode

The browser fixture and FastAPI contract can return deterministic contract-safe outputs without provider credentials. They are explicitly labeled and never claim live model execution. The Spring verification engine remains the authority for final status.
