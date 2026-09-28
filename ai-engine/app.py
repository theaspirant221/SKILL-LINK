"""SkillLink AI boundary.

This service is deliberately boring at the edge: deterministic facts arrive first,
then provider adapters may interpret only the cited context. There is no endpoint
here that can mark a skill VERIFIED; that decision belongs to the Spring policy
engine after evidence, defense, and practical results are available.
"""
from __future__ import annotations

from datetime import datetime, timezone
from typing import Literal
from uuid import uuid4

from fastapi import FastAPI
from pydantic import BaseModel, Field

app = FastAPI(title="SkillLink AI Engine", version="0.1.0")


class EvidenceReference(BaseModel):
    source_type: Literal["REPOSITORY", "DEPENDENCY", "STATIC_ANALYSIS", "TEST"]
    location: str
    source_hash: str
    observation: str
    strength: Literal["WEAK", "MODERATE", "STRONG", "DIRECT"]


class AnalyzerContext(BaseModel):
    repository_id: str
    project_id: str
    commit_sha: str
    deterministic_facts: dict = Field(default_factory=dict)
    redacted_files: list[dict] = Field(default_factory=list)
    prompt_version: str = "project-analyzer.v1"


class SkillObservation(BaseModel):
    skill_key: str
    observations: list[str]
    evidence_references: list[EvidenceReference]


class ProjectAnalysis(BaseModel):
    execution_id: str
    project_summary: str
    languages: list[str]
    frameworks: list[str]
    skills: list[SkillObservation]
    grounding_warnings: list[str] = Field(default_factory=list)
    provider: str
    model: str
    prompt_version: str
    generated_at: datetime


class ExaminerRequest(BaseModel):
    project_id: str
    snapshot_id: str
    skill_keys: list[str]
    evidence_references: list[EvidenceReference]
    prior_questions: list[str] = Field(default_factory=list)
    prompt_version: str = "examiner.v1"


class ExaminerQuestion(BaseModel):
    question_id: str
    category: Literal["CONCEPTUAL", "CODE_LOCATION", "ARCHITECTURE", "DEBUGGING", "SECURITY", "TESTING", "PRACTICAL_MODIFICATION"]
    prompt: str
    context_references: list[EvidenceReference]
    expected_signals: list[str]


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "service": "skilllink-ai-engine"}


@app.post("/v1/analyze", response_model=ProjectAnalysis)
def analyze(context: AnalyzerContext) -> ProjectAnalysis:
    """Return a schema-valid, grounded fixture response.

    Real provider routing is intentionally behind this interface. This local
    implementation only echoes deterministic facts and refuses to infer proof
    from a missing source reference.
    """
    facts = context.deterministic_facts
    languages = [str(item) for item in facts.get("languages", [])]
    frameworks = [str(item) for item in facts.get("frameworks", [])]
    observations: list[SkillObservation] = []
    for item in facts.get("skill_observations", []):
        refs = [EvidenceReference.model_validate(ref) for ref in item.get("evidence_references", [])]
        if not refs:
            continue
        observations.append(SkillObservation(skill_key=item["skill_key"], observations=item.get("observations", []), evidence_references=refs))
    return ProjectAnalysis(
        execution_id=str(uuid4()),
        project_summary=str(facts.get("project_summary", "Summary unavailable from deterministic facts.")),
        languages=languages,
        frameworks=frameworks,
        skills=observations,
        grounding_warnings=["Provider calls are disabled in local mode."] if not observations else [],
        provider="local-contract",
        model="deterministic-fixture",
        prompt_version=context.prompt_version,
        generated_at=datetime.now(timezone.utc),
    )


@app.post("/v1/examiner/questions", response_model=list[ExaminerQuestion])
def examiner_questions(request: ExaminerRequest) -> list[ExaminerQuestion]:
    """Generate questions only from the supplied references.

    The production adapter will call a structured-output provider with the same
    contract and run a reference validator before returning questions.
    """
    if not request.evidence_references:
        return []
    refs = request.evidence_references[:3]
    return [
        ExaminerQuestion(
            question_id=str(uuid4()),
            category="CODE_LOCATION",
            prompt="Locate the implementation behind this observation and explain the request or data flow.",
            context_references=refs,
            expected_signals=[ref.location for ref in refs],
        ),
        ExaminerQuestion(
            question_id=str(uuid4()),
            category="DEBUGGING",
            prompt="Describe one failure mode for this implementation and the test or inspection you would use to isolate it.",
            context_references=refs,
            expected_signals=["failure mode", "test", refs[0].location],
        ),
    ]
