-- Authentication and integration state is server-owned. Tokens are encrypted/hashed in application code.
CREATE TABLE auth_refresh_token (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  token_hash text NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  replaced_by uuid REFERENCES auth_refresh_token(id),
  user_agent text,
  ip_address inet,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_user_active ON auth_refresh_token(user_id, expires_at) WHERE revoked_at IS NULL;

CREATE TABLE github_connection (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  github_user_id bigint NOT NULL,
  github_login text NOT NULL,
  encrypted_access_token text NOT NULL,
  scopes text NOT NULL DEFAULT '',
  token_expires_at timestamptz,
  connected_at timestamptz NOT NULL DEFAULT now(),
  disconnected_at timestamptz,
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id),
  UNIQUE (github_user_id)
);
CREATE INDEX idx_github_connection_login ON github_connection(github_login) WHERE disconnected_at IS NULL;
CREATE TRIGGER github_connection_updated_at BEFORE UPDATE ON github_connection FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE github_oauth_state (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  state_hash text NOT NULL UNIQUE,
  encrypted_code_verifier text NOT NULL,
  redirect_uri text NOT NULL,
  expires_at timestamptz NOT NULL,
  consumed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_github_oauth_state_expiry ON github_oauth_state(expires_at) WHERE consumed_at IS NULL;

ALTER TABLE repository ADD COLUMN IF NOT EXISTS owner_login text;
ALTER TABLE repository ADD COLUMN IF NOT EXISTS github_node_id text;
ALTER TABLE repository ADD COLUMN IF NOT EXISTS primary_language text;
ALTER TABLE repository ADD COLUMN IF NOT EXISTS github_updated_at timestamptz;
ALTER TABLE repository ADD COLUMN IF NOT EXISTS last_synced_at timestamptz;

ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS tree_sha text;
ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS content_size_bytes bigint;
ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS selected_file_count integer;
ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS analysis_started_at timestamptz;
ALTER TABLE repository_snapshot ADD COLUMN IF NOT EXISTS analysis_completed_at timestamptz;
ALTER TABLE repository_snapshot DROP CONSTRAINT IF EXISTS repository_snapshot_analysis_status_check;
ALTER TABLE repository_snapshot ADD CONSTRAINT repository_snapshot_analysis_status_check CHECK (analysis_status IN ('QUEUED', 'FETCHING', 'ANALYZING', 'MAPPING', 'COMPLETED', 'FAILED', 'RETRYING'));

CREATE TABLE analysis_job (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  candidate_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  project_id uuid NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  repository_id uuid NOT NULL REFERENCES repository(id) ON DELETE CASCADE,
  repository_snapshot_id uuid REFERENCES repository_snapshot(id),
  state text NOT NULL DEFAULT 'QUEUED' CHECK (state IN ('QUEUED', 'FETCHING', 'ANALYZING', 'MAPPING', 'COMPLETED', 'FAILED')),
  progress integer NOT NULL DEFAULT 0 CHECK (progress >= 0 AND progress <= 100),
  stage text NOT NULL DEFAULT 'Queued',
  attempt integer NOT NULL DEFAULT 0,
  error_code text,
  error_message text,
  request_id text,
  created_at timestamptz NOT NULL DEFAULT now(),
  started_at timestamptz,
  completed_at timestamptz
);
CREATE INDEX idx_analysis_job_candidate ON analysis_job(candidate_id, created_at DESC);
CREATE INDEX idx_analysis_job_state ON analysis_job(state, created_at);

-- Initial normalized taxonomy. Aliases are intentionally separate from display names.
INSERT INTO skill (key, name, category, description) VALUES
  ('java', 'Java', 'Backend engineering', 'Java language and JVM application development.'),
  ('spring-boot', 'Spring Boot', 'Backend engineering', 'Spring Boot application configuration and runtime.'),
  ('spring-mvc', 'Spring MVC', 'API design', 'Spring MVC web and controller patterns.'),
  ('spring-security', 'Spring Security', 'Security', 'Spring Security authentication and authorization.'),
  ('rest-api-development', 'REST API Development', 'API design', 'Resource-oriented HTTP API design and implementation.'),
  ('jwt-authentication', 'JWT Authentication', 'Security', 'JSON Web Token generation, validation, and request authentication.'),
  ('postgresql', 'PostgreSQL', 'Data', 'PostgreSQL database usage and integration.'),
  ('mysql', 'MySQL', 'Data', 'MySQL database usage and integration.'),
  ('mongodb', 'MongoDB', 'Data', 'MongoDB document database usage and integration.'),
  ('jpa-hibernate', 'JPA / Hibernate', 'Data', 'JPA entity mapping and Hibernate persistence.'),
  ('unit-testing', 'Unit Testing', 'Quality', 'Unit and integration test design and execution.'),
  ('docker', 'Docker', 'Delivery', 'Container packaging and execution.'),
  ('react', 'React', 'Frontend engineering', 'React component and application development.'),
  ('javascript', 'JavaScript', 'Frontend engineering', 'JavaScript language and runtime development.'),
  ('typescript', 'TypeScript', 'Frontend engineering', 'TypeScript language and type-safe application development.'),
  ('python', 'Python', 'Backend engineering', 'Python language and application development.')
ON CONFLICT (key) DO UPDATE SET name = EXCLUDED.name, category = EXCLUDED.category, description = EXCLUDED.description, status = 'ACTIVE';

INSERT INTO skill_alias (skill_id, alias) SELECT id, 'Java' FROM skill WHERE key = 'java' ON CONFLICT DO NOTHING;
INSERT INTO skill_alias (skill_id, alias) SELECT id, 'Spring' FROM skill WHERE key = 'spring-boot' ON CONFLICT DO NOTHING;
INSERT INTO skill_alias (skill_id, alias) SELECT id, 'Spring Boot' FROM skill WHERE key = 'spring-boot' ON CONFLICT DO NOTHING;
INSERT INTO skill_alias (skill_id, alias) SELECT id, 'Spring Security' FROM skill WHERE key = 'spring-security' ON CONFLICT DO NOTHING;
INSERT INTO skill_alias (skill_id, alias) SELECT id, 'REST' FROM skill WHERE key = 'rest-api-development' ON CONFLICT DO NOTHING;
INSERT INTO skill_alias (skill_id, alias) SELECT id, 'REST API' FROM skill WHERE key = 'rest-api-development' ON CONFLICT DO NOTHING;
INSERT INTO skill_alias (skill_id, alias) SELECT id, 'JWT' FROM skill WHERE key = 'jwt-authentication' ON CONFLICT DO NOTHING;
INSERT INTO skill_alias (skill_id, alias) SELECT id, 'Postgres' FROM skill WHERE key = 'postgresql' ON CONFLICT DO NOTHING;
INSERT INTO skill_alias (skill_id, alias) SELECT id, 'PostgreSQL' FROM skill WHERE key = 'postgresql' ON CONFLICT DO NOTHING;
INSERT INTO skill_alias (skill_id, alias) SELECT id, 'Mongo' FROM skill WHERE key = 'mongodb' ON CONFLICT DO NOTHING;
INSERT INTO skill_alias (skill_id, alias) SELECT id, 'Hibernate' FROM skill WHERE key = 'jpa-hibernate' ON CONFLICT DO NOTHING;
INSERT INTO skill_alias (skill_id, alias) SELECT id, 'JUnit' FROM skill WHERE key = 'unit-testing' ON CONFLICT DO NOTHING;
INSERT INTO skill_alias (skill_id, alias) SELECT id, 'Unit tests' FROM skill WHERE key = 'unit-testing' ON CONFLICT DO NOTHING;
INSERT INTO skill_alias (skill_id, alias) SELECT id, 'JS' FROM skill WHERE key = 'javascript' ON CONFLICT DO NOTHING;
INSERT INTO skill_alias (skill_id, alias) SELECT id, 'TS' FROM skill WHERE key = 'typescript' ON CONFLICT DO NOTHING;
INSERT INTO skill_alias (skill_id, alias) SELECT id, 'FastAPI' FROM skill WHERE key = 'python' ON CONFLICT DO NOTHING;

INSERT INTO skill_relationship (source_skill_id, target_skill_id, relationship)
SELECT child.id, parent.id, 'RELATED' FROM skill child, skill parent
WHERE child.key = 'spring-security' AND parent.key = 'spring-boot'
ON CONFLICT DO NOTHING;
