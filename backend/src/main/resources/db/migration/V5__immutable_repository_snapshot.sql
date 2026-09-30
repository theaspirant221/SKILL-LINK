-- Checkpoint D: Immutable Repository Snapshot
-- SkillLink must be able to take a candidate-selected GitHub repository and create a
-- reproducible, immutable snapshot at one exact commit SHA.

-- Enhance repository_snapshot to carry full immutable snapshot metadata
ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS candidate_id uuid REFERENCES app_user(id) ON DELETE CASCADE;
ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS project_id uuid REFERENCES project(id) ON DELETE CASCADE;
ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS github_repository_id text;
ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS owner_login text;
ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS repository_name text;
ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS full_name text;
ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS commit_author text;
ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS commit_message text;
ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS commit_timestamp timestamptz;
ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS file_policy_version text NOT NULL DEFAULT 'v1';
ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS analysis_version text NOT NULL DEFAULT 'v1';
ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS integrity_hash text;
ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS snapshot_created_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS status text NOT NULL DEFAULT 'CREATED' CHECK (status IN ('CREATED','FETCHING','READY','FAILED'));
ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS error_code text;
ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS error_message text;
ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS branch_name text;
ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS included_file_count integer NOT NULL DEFAULT 0;
ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS excluded_file_count integer NOT NULL DEFAULT 0;
ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS total_bytes bigint NOT NULL DEFAULT 0;

-- Backfill branch_name from branch for existing rows
UPDATE repository_snapshot SET branch_name = branch WHERE branch_name IS NULL AND branch IS NOT NULL;

-- For existing snapshots, set status based on analysis_status
UPDATE repository_snapshot SET status = CASE
  WHEN analysis_status = 'COMPLETED' THEN 'READY'
  WHEN analysis_status = 'FAILED' THEN 'FAILED'
  WHEN analysis_status IN ('QUEUED','FETCHING','ANALYZING','MAPPING','RETRYING') THEN 'FETCHING'
  ELSE 'CREATED'
END WHERE status = 'CREATED';

-- File manifest for immutable snapshots
CREATE TABLE IF NOT EXISTS repository_snapshot_file (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  snapshot_id uuid NOT NULL REFERENCES repository_snapshot(id) ON DELETE CASCADE,
  path text NOT NULL,
  language text,
  size_bytes bigint NOT NULL DEFAULT 0,
  content_hash text NOT NULL,
  blob_sha text,
  included boolean NOT NULL DEFAULT true,
  exclusion_reason text,
  secret_redacted boolean NOT NULL DEFAULT false,
  secret_count integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(snapshot_id, path)
);
CREATE INDEX IF NOT EXISTS idx_snapshot_file_snapshot ON repository_snapshot_file(snapshot_id);
CREATE INDEX IF NOT EXISTS idx_snapshot_file_included ON repository_snapshot_file(snapshot_id, included);
CREATE INDEX IF NOT EXISTS idx_snapshot_file_path ON repository_snapshot_file(snapshot_id, path);

-- Idempotency: same repository, commit SHA, and policy version should reuse READY snapshot
CREATE UNIQUE INDEX IF NOT EXISTS uq_snapshot_repo_commit_policy ON repository_snapshot(repository_id, commit_sha, file_policy_version) WHERE status = 'READY';

-- For listing and candidate isolation
CREATE INDEX IF NOT EXISTS idx_snapshot_project ON repository_snapshot(project_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_snapshot_candidate ON repository_snapshot(candidate_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_snapshot_repo ON repository_snapshot(repository_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_snapshot_status_new ON repository_snapshot(status, created_at DESC);

-- Ensure repository_snapshot_file is cleaned when snapshot is deleted (already CASCADE)
-- Add trigger for updated_at if not exists
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'repository_snapshot_updated_at') THEN
    CREATE TRIGGER repository_snapshot_updated_at BEFORE UPDATE ON repository_snapshot FOR EACH ROW EXECUTE FUNCTION set_updated_at();
  END IF;
END $$;
