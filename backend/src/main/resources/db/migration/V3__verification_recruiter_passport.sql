-- Phase 1 workflow state: candidate-to-recruiter applications, policy-backed review, and portable proof summaries.
CREATE TABLE job_application (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  job_id uuid NOT NULL REFERENCES job(id) ON DELETE CASCADE,
  candidate_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  status text NOT NULL DEFAULT 'APPLIED' CHECK (status IN ('APPLIED', 'REVIEWING', 'SHORTLISTED', 'REJECTED', 'WITHDRAWN')),
  candidate_note text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (job_id, candidate_id)
);
CREATE INDEX idx_job_application_candidate ON job_application(candidate_id, created_at DESC);
CREATE INDEX idx_job_application_job ON job_application(job_id, status, created_at DESC);
CREATE TRIGGER job_application_updated_at BEFORE UPDATE ON job_application FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE proof_contract (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  job_id uuid NOT NULL REFERENCES job(id) ON DELETE CASCADE,
  candidate_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  version integer NOT NULL DEFAULT 1,
  status text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'SHARED', 'REVIEWED', 'ARCHIVED')),
  created_by uuid NOT NULL REFERENCES app_user(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (job_id, candidate_id, version)
);
CREATE INDEX idx_proof_contract_candidate ON proof_contract(candidate_id, created_at DESC);
CREATE TRIGGER proof_contract_updated_at BEFORE UPDATE ON proof_contract FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE proof_contract_requirement (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  contract_id uuid NOT NULL REFERENCES proof_contract(id) ON DELETE CASCADE,
  job_requirement_id uuid NOT NULL REFERENCES job_requirement(id) ON DELETE CASCADE,
  outcome text NOT NULL DEFAULT 'UNREVIEWED' CHECK (outcome IN ('UNREVIEWED', 'SUPPORTED', 'PARTIAL', 'MISSING', 'DISPUTED')),
  proof_summary text NOT NULL,
  evidence_count integer NOT NULL DEFAULT 0,
  verification_result_id uuid REFERENCES verification_result(id),
  reviewer_note text,
  reviewed_by uuid REFERENCES app_user(id),
  reviewed_at timestamptz,
  UNIQUE (contract_id, job_requirement_id)
);
CREATE INDEX idx_proof_contract_requirement ON proof_contract_requirement(contract_id, outcome);

-- One explicit, explainable baseline policy. It is a rule set, never a model score.
INSERT INTO verification_policy(key, version, skill_id, rules, active)
VALUES ('skill-proof-baseline', 'v1.0', NULL,
  '{"repositoryEvidence":"at_least_one","projectDefense":"required_for_verification","practicalVerification":"required_for_verification","freshnessDays":180,"decisionOwner":"policy_engine"}'::jsonb,
  true)
ON CONFLICT (key, version) DO UPDATE SET rules = EXCLUDED.rules, active = EXCLUDED.active;

CREATE UNIQUE INDEX IF NOT EXISTS uq_examination_answer_question ON examination_answer(question_id);
CREATE INDEX IF NOT EXISTS idx_verification_active ON verification_policy(active, key);
CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_log(created_at DESC);
