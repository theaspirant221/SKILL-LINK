# AI architecture

## Provider abstraction

The application should expose an internal interface such as:

```text
ProjectAnalyzer.analyze(snapshotContext) -> GroundedProjectAnalysis
Examiner.generateQuestion(examContext) -> GroundedQuestion
AnswerEvaluator.evaluate(answerContext) -> BoundedEvaluation
ChallengeGenerator.generate(gapContext) -> PracticalChallenge
JobCompiler.compile(jobText, taxonomy) -> ProofContractDraft
ProofExplainer.explain(verificationContext) -> GroundedExplanation
```

Adapters can target OpenAI, Gemini, Claude, Groq, or an internal model. Provider choice is configuration, not a domain decision. Routing can choose a lower-cost structured model for extraction and a stronger model for code reasoning.

## Prompt registry

Every important prompt is a versioned record:

- prompt ID and semantic version
- purpose and input schema
- template
- output JSON schema
- model parameters
- active flag
- created by/date

Prompt strings do not belong scattered through controller code. Important AI executions record provider, model, prompt version, input snapshot, output hash, latency, token/cost metadata, and validation status.

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

Validation rejects an assertion without a reference. A reference validator checks that the location and hash exist in the snapshot and that the model did not introduce a file outside the supplied context.

## Retrieval

Start with deterministic allowlists and repository structure. Add pgvector only when semantic retrieval is needed. Retrieval chunks should preserve file path, symbol, line range, commit, and redaction metadata so citations survive.

## Evaluation quality

Measure:

- grounded-reference precision/recall
- unsupported-assertion rate
- schema validity
- evaluator consistency across repeated runs
- false positive evidence rate
- candidate dispute rate
- time/cost per analysis

Use adversarial fixtures with prompt-injection comments, misleading READMEs, copied snippets, missing files, secrets, and huge files.

## Local/demo mode

The browser and FastAPI fixture can return deterministic contract-safe outputs without provider credentials. Demo output is labeled. It never claims live model execution and never writes `VERIFIED`. The Spring verification engine remains the only authority for final status.
