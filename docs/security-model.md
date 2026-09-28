# Security model

## Threat priorities

1. source-code privacy
2. OAuth token theft
3. identity/session compromise
4. unauthorized recruiter/institution access
5. prompt injection and evidence hallucination
6. arbitrary code execution
7. public passport leakage
8. abusive analysis/API usage

## Identity and sessions

- Spring Security owns server-side authentication and authorization.
- Passwords use Argon2id or BCrypt with current cost guidance; plaintext passwords are never stored.
- Access tokens are short-lived; refresh tokens rotate and are revoked on reuse.
- OAuth state and PKCE are required for GitHub.
- GitHub tokens are encrypted at rest, scoped to the minimum required permissions, and removed when disconnected or no longer required.
- Account recovery and email verification are audited.

## Authorization

Use server-side RBAC plus resource ownership checks. Roles are not trusted from the frontend. Recruiters can see only a candidate's explicitly shared proof. Institution access is scoped to its membership and student relationship. Platform admins require audited elevated access.

## Repository and AI privacy

Before external processing:

- allowlist relevant files and paths
- exclude `.env`, keys, credentials, binaries, and unnecessary large files
- run secret detection for API keys, JWT secrets, passwords, private keys, and cloud credentials
- redact or hash detected values
- record what was redacted without persisting the secret
- send only selected context to a provider
- document provider retention and opt-out policy

Prompt injection in README or comments is treated as untrusted data. It cannot change policy, permissions, or the source-reference requirement.

## Web/API controls

- TLS at the edge
- strict CORS allowlist
- secure headers and content security policy
- payload and upload size limits
- input validation and output encoding
- parameterized queries/JPA
- rate limiting by identity/IP/job type
- SSRF protection for repository URLs/webhooks
- no stack traces, tokens, raw code, or provider responses in errors
- request IDs and security event audit

## Code execution

Candidate code never runs on the production API host. Practical execution uses an isolated, ephemeral worker with no host mounts, no production network, quotas, and a minimal artifact channel. Text/patch-only verification is the default until the sandbox exists.

## Privacy operations

Support disconnect GitHub, delete snapshots, delete account, export data, revoke passport, revoke credentials, and configurable retention. Public URLs must stop serving revoked passports.

## Accessibility and fairness

Accessibility is a security/trust concern: status is never communicated by color alone. Do not infer personality, mental health, protected traits, or unrelated sensitive attributes from code. Recruiter UI must present proof as decision support, not automatic rejection.

## Security validation

CI should run dependency audit, SAST, secret scanning, container scanning, and API authorization tests. Production should add DAST, threat modeling, key rotation checks, and incident runbooks.
