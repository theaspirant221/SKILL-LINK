# GitHub App setup

SkillLink connects to GitHub as a **GitHub App**, not a classic OAuth app. Two concepts are kept deliberately separate:

- **Installation** — which GitHub account and which selected repositories SkillLink may access. The candidate chooses the account and grants access to chosen repositories (or all repositories) during installation. SkillLink requests only `Contents: Read` and `Metadata: Read`; no write permissions, no Issues, Pull Requests, Workflows, or Administration access.
- **User authorization** — identifies the GitHub user through the app's OAuth flow, protected with server-side `state` and PKCE (`code_challenge_method=S256`). This is separate from repository access.

## Creating the GitHub App

1. On GitHub, open **Settings → Developer settings → GitHub Apps → New GitHub App** (or have an organization owner do it).
2. Suggested settings:
   - **GitHub App name:** your chosen name (set `GITHUB_APP_NAME` to match; the install URL is derived from it)
   - **Callback URL:** `http://localhost:8080/api/v1/github/callback` locally, or your API origin + `/api/v1/github/callback`
   - **Setup URL:** `http://localhost:8080/api/v1/github/install/callback`, with **Redirect on update** enabled (so repository-selection changes flow back to SkillLink)
   - **Webhook → Active**, **Webhook URL:** `<api-origin>/api/v1/github/webhooks`, and set a webhook secret
   - **Permissions → Repository permissions:**
     - `Contents: Read-only`
     - `Metadata: Read-only` (mandatory)
     - Nothing else. Do not request write permissions.
   - **Where can this GitHub App be installed:** any account (users choose "Only select repositories" during install).
3. After creation, generate a private key (`<app>-.pem`) and store it in a secret manager; never commit it.

## Environment variables

```bash
GITHUB_APP_ID=123456                      # numeric App id from the app settings page
GITHUB_APP_CLIENT_ID=Iv1.xxxxxxxxxxxxxxxx # client id used for user authorization
GITHUB_APP_CLIENT_SECRET=...              # client secret from the app settings page
GITHUB_APP_PRIVATE_KEY=-----BEGIN RSA PRIVATE KEY-----\nMIIE...\n-----END RSA PRIVATE KEY-----
GITHUB_APP_NAME=skilllink-dev             # app name; drives the install URL
GITHUB_CALLBACK_URL=http://localhost:8080/api/v1/github/callback
GITHUB_SETUP_URL=http://localhost:8080/api/v1/github/install/callback
GITHUB_WEBHOOK_SECRET=...                 # random string, also configured on the GitHub App
```

Notes:

- `GITHUB_APP_PRIVATE_KEY` may be a real multi-line PEM or a single-line value with literal `\n` escapes (common for environment variables and container secrets). Both PKCS#8 (`BEGIN PRIVATE KEY`) and PKCS#1 (`BEGIN RSA PRIVATE KEY`) formats are supported. The key is never logged and never appears in error messages.
- Without these variables the API returns `GITHUB_NOT_CONFIGURED` instead of pretending to connect. There is no fixture fallback in server-backed mode.
- The GitHub App JWT used for installation tokens is generated server-side from the private key and is completely separate from the SkillLink application JWT that authenticates candidates.

## Connection lifecycle

```text
Candidate opens /app/github → Connect GitHub (user authorization, state + PKCE S256)
  → GitHub redirects back, SkillLink stores encrypted user tokens
  → Install SkillLink GitHub App (setup URL, single-use state)
  → Candidate chooses account + "Only select repositories"
  → GitHub redirects to the setup callback, SkillLink stores the installation identity
  → Status = CONNECTED; only installation-authorized repositories are listed
  → Disconnect revokes the stored user grant, removes local associations, and stops
    repository access; existing evidence is preserved (deleting evidence is separate)
```

GitHub delivers `installation` and `installation_repositories` webhooks to `<api-origin>/api/v1/github/webhooks`. Every delivery must carry a valid `X-Hub-Signature-256` HMAC (computed with `GITHUB_WEBHOOK_SECRET`); unsigned or wrongly signed deliveries are rejected, and delivery ids are recorded so duplicates are never processed twice. When an installation is removed or a repository is revoked, SkillLink stops future access and marks the source accordingly — historical evidence is never deleted automatically.

## Token handling summary

| Token | Lifetime | Storage | Exposure |
| --- | --- | --- | --- |
| GitHub App JWT | ≤ 10 minutes, generated on demand | memory only | server only |
| Installation access token | ≤ 1 hour, cached only until shortly before expiry | memory only | server only, never persisted, never sent to the browser |
| User access token | until expiry (refresh enabled) | encrypted at rest (`TOKEN_ENCRYPTION_KEY`) | server only; revoked on disconnect |
| User refresh token | rotated by GitHub | encrypted at rest | server only |

## Local development

Set the variables above (plus `DATABASE_URL`, `JWT_SECRET`, `TOKEN_ENCRYPTION_KEY`), start the backend, sign in as a candidate in server-backed mode, and open the GitHub page. Use a GitHub App you own for testing; install it on a personal account with one or two scratch repositories selected.
