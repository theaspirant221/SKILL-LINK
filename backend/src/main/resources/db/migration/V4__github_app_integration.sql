-- Checkpoint C: GitHub App integration.
-- An installation is a separate concept from user authorization: the installation controls which
-- GitHub account/repositories SkillLink may access; user authorization identifies the GitHub user.
CREATE TABLE github_installation (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  candidate_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  installation_id bigint NOT NULL UNIQUE,
  account_id bigint NOT NULL,
  account_login text NOT NULL,
  account_type text NOT NULL,
  repository_selection text NOT NULL DEFAULT 'SELECTED' CHECK (repository_selection IN ('ALL', 'SELECTED')),
  status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REMOVED', 'SUSPENDED')),
  installed_at timestamptz NOT NULL DEFAULT now(),
  last_validated_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_github_installation_candidate ON github_installation(candidate_id, status);
CREATE TRIGGER github_installation_updated_at BEFORE UPDATE ON github_installation FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- User access tokens issued by GitHub App user authorization can expire and be refreshed.
ALTER TABLE github_connection ADD COLUMN IF NOT EXISTS encrypted_refresh_token text;
ALTER TABLE github_connection ADD COLUMN IF NOT EXISTS last_validated_at timestamptz;
ALTER TABLE github_connection ADD COLUMN IF NOT EXISTS status text;

-- OAuth state now carries a purpose so user-authorization and installation states cannot be confused.
ALTER TABLE github_oauth_state ADD COLUMN IF NOT EXISTS purpose text NOT NULL DEFAULT 'USER_AUTH';
ALTER TABLE github_oauth_state DROP CONSTRAINT IF EXISTS github_oauth_state_purpose_check;
ALTER TABLE github_oauth_state ADD CONSTRAINT github_oauth_state_purpose_check CHECK (purpose IN ('USER_AUTH', 'INSTALL'));

-- Webhook deliveries are recorded to prevent duplicate processing.
CREATE TABLE github_webhook_event (
  delivery_id text PRIMARY KEY,
  event text NOT NULL,
  action text,
  processed_at timestamptz NOT NULL DEFAULT now()
);

-- Repository access changes reuse the existing repository.connection_status column
-- ('REVOKED' stops future synchronization without deleting historical evidence).
