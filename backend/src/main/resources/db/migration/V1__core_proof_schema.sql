CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE app_user (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email text NOT NULL UNIQUE,
  password_hash text,
  display_name text NOT NULL,
  role text NOT NULL CHECK (role IN ('CANDIDATE', 'RECRUITER', 'ORGANIZATION_ADMIN', 'COLLEGE_ADMIN', 'FACULTY', 'PLACEMENT_COORDINATOR', 'PLATFORM_ADMIN')),
  email_verified_at timestamptz,
  deleted_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_app_user_role ON app_user(role) WHERE deleted_at IS NULL;
CREATE TRIGGER app_user_updated_at BEFORE UPDATE ON app_user FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE profile (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL UNIQUE REFERENCES app_user(id),
  headline text,
  location text,
  institution_name text,
  graduation_year integer,
  avatar_uri text,
  visibility text NOT NULL DEFAULT 'PRIVATE' CHECK (visibility IN ('PRIVATE', 'PUBLIC_SUMMARY', 'PUBLIC')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TRIGGER profile_updated_at BEFORE UPDATE ON profile FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE organization (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  kind text NOT NULL CHECK (kind IN ('RECRUITER', 'INSTITUTION', 'ISSUER', 'PLATFORM')),
  slug text NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TRIGGER organization_updated_at BEFORE UPDATE ON organization FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE organization_member (
  organization_id uuid NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  member_role text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (organization_id, user_id)
);
CREATE INDEX idx_org_member_user ON organization_member(user_id);

CREATE TABLE institution_department (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  institution_id uuid NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
  name text NOT NULL,
  code text,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (institution_id, name)
);

CREATE TABLE institution_batch (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  institution_id uuid NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
  name text NOT NULL,
  graduation_year integer,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (institution_id, name)
);

CREATE TABLE skill (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  key text NOT NULL UNIQUE,
  name text NOT NULL,
  category text NOT NULL,
  description text,
  taxonomy_version text NOT NULL DEFAULT 'v1',
  status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DEPRECATED')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TRIGGER skill_updated_at BEFORE UPDATE ON skill FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE skill_alias (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  skill_id uuid NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
  alias text NOT NULL,
  locale text NOT NULL DEFAULT 'en',
  UNIQUE (skill_id, alias, locale)
);
CREATE INDEX idx_skill_alias_lookup ON skill_alias(lower(alias));

CREATE TABLE skill_relationship (
  source_skill_id uuid NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
  target_skill_id uuid NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
  relationship text NOT NULL CHECK (relationship IN ('PARENT', 'RELATED', 'PREREQUISITE', 'REPLACED_BY')),
  PRIMARY KEY (source_skill_id, target_skill_id, relationship)
);

CREATE TABLE candidate_skill (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  candidate_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  skill_id uuid NOT NULL REFERENCES skill(id),
  status text NOT NULL DEFAULT 'SELF_CLAIM' CHECK (status IN ('SELF_CLAIM', 'EVIDENCE_FOUND', 'PARTIAL', 'VERIFIED', 'STALE', 'EXPIRED', 'NOT_VERIFIED', 'DISPUTED')),
  freshness_state text NOT NULL DEFAULT 'NOT_APPLICABLE' CHECK (freshness_state IN ('CURRENT', 'AGING', 'STALE', 'EXPIRED', 'NOT_APPLICABLE')),
  last_verified_at timestamptz,
  latest_evidence_at timestamptz,
  refresh_required_at timestamptz,
  verification_policy_version text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (candidate_id, skill_id)
);
CREATE INDEX idx_candidate_skill_status ON candidate_skill(candidate_id, status, freshness_state);
CREATE TRIGGER candidate_skill_updated_at BEFORE UPDATE ON candidate_skill FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE project (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  candidate_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  name text NOT NULL,
  kind text,
  summary text,
  visibility text NOT NULL DEFAULT 'PRIVATE' CHECK (visibility IN ('PRIVATE', 'RECRUITER_SHARED', 'INSTITUTION_SHARED', 'PUBLIC_SUMMARY', 'PUBLIC')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_project_candidate ON project(candidate_id, updated_at DESC);
CREATE TRIGGER project_updated_at BEFORE UPDATE ON project FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE repository (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id uuid NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  provider text NOT NULL CHECK (provider IN ('GITHUB', 'GITLAB', 'BITBUCKET', 'UPLOAD')),
  external_id text NOT NULL,
  full_name text NOT NULL,
  visibility text NOT NULL CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
  default_branch text,
  connection_status text NOT NULL DEFAULT 'CONNECTED' CHECK (connection_status IN ('CONNECTED', 'DISCONNECTED', 'REVOKED')),
  encrypted_access_token text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (provider, external_id)
);
CREATE TRIGGER repository_updated_at BEFORE UPDATE ON repository FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE repository_snapshot (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  repository_id uuid NOT NULL REFERENCES repository(id) ON DELETE CASCADE,
  commit_sha text NOT NULL,
  branch text,
  source_hash text NOT NULL,
  source_observed_at timestamptz NOT NULL,
  analysis_status text NOT NULL DEFAULT 'QUEUED' CHECK (analysis_status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED', 'RETRYING')),
  analysis_error_code text,
  file_count integer,
  test_count integer,
  deterministic_facts jsonb NOT NULL DEFAULT '{}'::jsonb,
  redaction_summary jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (repository_id, commit_sha)
);
CREATE INDEX idx_snapshot_status ON repository_snapshot(analysis_status, created_at);

CREATE TABLE evidence_source (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  snapshot_id uuid NOT NULL REFERENCES repository_snapshot(id) ON DELETE CASCADE,
  source_type text NOT NULL CHECK (source_type IN ('REPOSITORY', 'DEPENDENCY', 'STATIC_ANALYSIS', 'COMMIT', 'PULL_REQUEST', 'DEPLOYMENT', 'PROJECT_DEFENSE', 'PRACTICAL_VERIFICATION', 'INSTITUTIONAL')),
  source_reference text NOT NULL,
  source_location text,
  source_hash text,
  visibility text NOT NULL DEFAULT 'PRIVATE' CHECK (visibility IN ('PRIVATE', 'RECRUITER_SHARED', 'INSTITUTION_SHARED', 'PUBLIC_SUMMARY', 'PUBLIC')),
  observed_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_evidence_source_snapshot ON evidence_source(snapshot_id, source_type);

CREATE TABLE evidence (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  candidate_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  project_id uuid NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  skill_id uuid NOT NULL REFERENCES skill(id),
  evidence_source_id uuid NOT NULL REFERENCES evidence_source(id),
  observation text NOT NULL,
  evidence_strength text NOT NULL CHECK (evidence_strength IN ('WEAK', 'MODERATE', 'STRONG', 'DIRECT')),
  verification_method text NOT NULL,
  status text NOT NULL DEFAULT 'OBSERVED' CHECK (status IN ('OBSERVED', 'VERIFIED', 'DISPUTED', 'REDACTED')),
  independent_signal text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_evidence_candidate_skill ON evidence(candidate_id, skill_id, created_at DESC);
CREATE INDEX idx_evidence_project ON evidence(project_id, created_at DESC);
CREATE TRIGGER evidence_updated_at BEFORE UPDATE ON evidence FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE examination (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  candidate_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  project_id uuid NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  status text NOT NULL DEFAULT 'NOT_STARTED' CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
  result text CHECK (result IN ('PASSED', 'PARTIAL', 'NEEDS_REVIEW')),
  policy_version text NOT NULL,
  prompt_version text NOT NULL,
  model_provider text,
  model_name text,
  started_at timestamptz,
  completed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_examination_candidate ON examination(candidate_id, created_at DESC);

CREATE TABLE examination_question (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  examination_id uuid NOT NULL REFERENCES examination(id) ON DELETE CASCADE,
  sequence_no integer NOT NULL,
  category text NOT NULL,
  prompt text NOT NULL,
  context_references jsonb NOT NULL DEFAULT '[]'::jsonb,
  expected_signals jsonb NOT NULL DEFAULT '[]'::jsonb,
  UNIQUE (examination_id, sequence_no)
);

CREATE TABLE examination_answer (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  question_id uuid NOT NULL REFERENCES examination_question(id) ON DELETE CASCADE,
  answer_text_redacted text NOT NULL,
  evaluation_status text NOT NULL DEFAULT 'PENDING' CHECK (evaluation_status IN ('PENDING', 'MEETS_BAR', 'NEEDS_DEPTH', 'REVIEW_REQUIRED')),
  feedback text,
  evaluator_provider text,
  evaluator_model text,
  evaluator_prompt_version text,
  submitted_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE practical_challenge (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  skill_id uuid NOT NULL REFERENCES skill(id),
  project_id uuid REFERENCES project(id),
  title text NOT NULL,
  brief text NOT NULL,
  acceptance_criteria jsonb NOT NULL DEFAULT '[]'::jsonb,
  execution_mode text NOT NULL CHECK (execution_mode IN ('TEXT_PATCH', 'ISOLATED_SANDBOX', 'BROWSER_IDE')),
  policy_version text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE challenge_submission (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  challenge_id uuid NOT NULL REFERENCES practical_challenge(id) ON DELETE CASCADE,
  candidate_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  submission_reference text,
  submission_hash text,
  status text NOT NULL DEFAULT 'SUBMITTED' CHECK (status IN ('SUBMITTED', 'PROCESSING', 'PASSED', 'NEEDS_CHANGES', 'REVIEW_REQUIRED')),
  execution_summary jsonb NOT NULL DEFAULT '{}'::jsonb,
  feedback text,
  submitted_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE verification_policy (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  key text NOT NULL,
  version text NOT NULL,
  skill_id uuid REFERENCES skill(id),
  rules jsonb NOT NULL,
  active boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (key, version)
);

CREATE TABLE verification_result (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  candidate_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  skill_id uuid NOT NULL REFERENCES skill(id),
  policy_id uuid NOT NULL REFERENCES verification_policy(id),
  status text NOT NULL CHECK (status IN ('VERIFIED', 'PARTIAL', 'NOT_VERIFIED', 'DISPUTED')),
  explanation text NOT NULL,
  repository_snapshot_id uuid REFERENCES repository_snapshot(id),
  examination_id uuid REFERENCES examination(id),
  challenge_submission_id uuid REFERENCES challenge_submission(id),
  evaluated_at timestamptz NOT NULL DEFAULT now(),
  model_metadata jsonb NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX idx_verification_candidate_skill ON verification_result(candidate_id, skill_id, evaluated_at DESC);

CREATE TABLE job (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid NOT NULL REFERENCES organization(id),
  created_by uuid NOT NULL REFERENCES app_user(id),
  title text NOT NULL,
  location text,
  original_description text NOT NULL,
  status text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TRIGGER job_updated_at BEFORE UPDATE ON job FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE job_requirement (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  job_id uuid NOT NULL REFERENCES job(id) ON DELETE CASCADE,
  skill_id uuid NOT NULL REFERENCES skill(id),
  kind text NOT NULL CHECK (kind IN ('REQUIRED', 'PREFERRED')),
  source_phrase text,
  recruiter_edited boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (job_id, skill_id)
);

CREATE TABLE proof_passport (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  candidate_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  public_identifier text NOT NULL UNIQUE,
  visibility text NOT NULL DEFAULT 'PRIVATE' CHECK (visibility IN ('PRIVATE', 'PUBLIC_SUMMARY', 'PUBLIC')),
  revoked_at timestamptz,
  expires_at timestamptz,
  issued_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_passport_public ON proof_passport(public_identifier) WHERE revoked_at IS NULL;
CREATE TRIGGER passport_updated_at BEFORE UPDATE ON proof_passport FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE proof_passport_item (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  passport_id uuid NOT NULL REFERENCES proof_passport(id) ON DELETE CASCADE,
  skill_id uuid NOT NULL REFERENCES skill(id),
  verification_result_id uuid REFERENCES verification_result(id),
  visibility text NOT NULL DEFAULT 'PUBLIC_SUMMARY',
  display_summary text NOT NULL,
  sort_order integer NOT NULL DEFAULT 0,
  UNIQUE (passport_id, skill_id)
);

CREATE TABLE dispute (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  submitted_by uuid NOT NULL REFERENCES app_user(id),
  candidate_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  evidence_id uuid REFERENCES evidence(id),
  verification_result_id uuid REFERENCES verification_result(id),
  reason text NOT NULL,
  status text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'UNDER_REVIEW', 'RESOLVED', 'REJECTED')),
  resolution text,
  resolved_by uuid REFERENCES app_user(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  resolved_at timestamptz
);

CREATE TABLE audit_log (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_user_id uuid REFERENCES app_user(id),
  action text NOT NULL,
  resource_type text NOT NULL,
  resource_id uuid,
  request_id text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_resource ON audit_log(resource_type, resource_id, created_at DESC);
CREATE INDEX idx_audit_actor ON audit_log(actor_user_id, created_at DESC);

CREATE TABLE notification (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  kind text NOT NULL,
  title text NOT NULL,
  body text NOT NULL,
  read_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_user ON notification(user_id, read_at, created_at DESC);
