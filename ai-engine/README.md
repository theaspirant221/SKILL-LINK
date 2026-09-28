# SkillLink AI engine

Optional FastAPI boundary for repository interpretation and project-specific question generation.

## Grounding contract

- deterministic facts and redacted source context are inputs
- every observation must include a source reference
- structured Pydantic output is validated at the boundary
- the engine never returns `VERIFIED`
- provider/model/prompt metadata is part of important outputs
- secrets and unnecessary files are removed before the boundary

The current local implementation is a safe contract fixture. The Spring Boot modular monolith remains the system of record and owns verification policy, persistence, authorization, audit, and disputes.

Run when Python dependencies are available:

```bash
python -m venv .venv
. .venv/bin/activate
pip install -e '.[test]'
uvicorn app:app --host 0.0.0.0 --port 8000
```
