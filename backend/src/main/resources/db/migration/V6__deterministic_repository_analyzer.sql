-- Checkpoint E: Deterministic Repository Analyzer
-- What objectively exists inside that immutable snapshot?

-- Analysis run that references immutable snapshot
CREATE TABLE IF NOT EXISTS analysis_run (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  snapshot_id uuid NOT NULL REFERENCES repository_snapshot(id) ON DELETE CASCADE,
  candidate_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  project_id uuid NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  repository_id uuid NOT NULL REFERENCES repository(id) ON DELETE CASCADE,
  analyzer_version text NOT NULL DEFAULT 'deterministic-v1',
  status text NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','RUNNING','COMPLETE','FAILED')),
  started_at timestamptz,
  completed_at timestamptz,
  failure_code text,
  failure_message text,
  observation_count integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_analysis_run_snapshot ON analysis_run(snapshot_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_analysis_run_project ON analysis_run(project_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_analysis_run_candidate ON analysis_run(candidate_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_analysis_run_status ON analysis_run(status, created_at DESC);

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'analysis_run_updated_at') THEN
    CREATE TRIGGER analysis_run_updated_at BEFORE UPDATE ON analysis_run FOR EACH ROW EXECUTE FUNCTION set_updated_at();
  END IF;
END $$;

-- Deterministic observations
CREATE TABLE IF NOT EXISTS analysis_observation (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  analysis_run_id uuid NOT NULL REFERENCES analysis_run(id) ON DELETE CASCADE,
  snapshot_id uuid NOT NULL REFERENCES repository_snapshot(id) ON DELETE CASCADE,
  observation_type text NOT NULL,
  category text NOT NULL,
  fact_key text NOT NULL,
  fact_value text,
  language text,
  framework text,
  source_path text,
  start_line integer,
  end_line integer,
  symbol text,
  source_hash text,
  detector text NOT NULL,
  detector_version text NOT NULL,
  confidence text NOT NULL DEFAULT 'HIGH',
  origin text NOT NULL DEFAULT 'DETERMINISTIC' CHECK (origin IN ('DETERMINISTIC','MODEL_INTERPRETED')),
  created_at timestamptz NOT NULL DEFAULT now(),
  -- Duplicate control: same run, type, path, symbol, fact should be unique
  UNIQUE(analysis_run_id, observation_type, source_path, symbol, fact_key, fact_value)
);
CREATE INDEX IF NOT EXISTS idx_observation_run ON analysis_observation(analysis_run_id, category, observation_type);
CREATE INDEX IF NOT EXISTS idx_observation_snapshot ON analysis_observation(snapshot_id, category);
CREATE INDEX IF NOT EXISTS idx_observation_type ON analysis_observation(observation_type, category);
CREATE INDEX IF NOT EXISTS idx_observation_path ON analysis_observation(analysis_run_id, source_path);
CREATE INDEX IF NOT EXISTS idx_observation_category ON analysis_observation(analysis_run_id, category);

-- For failure isolation, track file-level parse errors per run
CREATE TABLE IF NOT EXISTS analysis_file_error (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  analysis_run_id uuid NOT NULL REFERENCES analysis_run(id) ON DELETE CASCADE,
  snapshot_id uuid NOT NULL REFERENCES repository_snapshot(id) ON DELETE CASCADE,
  source_path text NOT NULL,
  error_code text NOT NULL,
  error_message text NOT NULL,
  detector text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(analysis_run_id, source_path, error_code)
);
CREATE INDEX IF NOT EXISTS idx_file_error_run ON analysis_file_error(analysis_run_id);
