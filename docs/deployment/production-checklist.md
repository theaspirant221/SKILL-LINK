# Production deployment checklist

## Build

- [ ] frontend lint, typecheck, test, and build in CI
- [ ] backend unit/integration/API tests with PostgreSQL
- [ ] AI schema/grounding/regression tests
- [ ] lock dependency versions and run audit
- [ ] build immutable image with non-root runtime

## Secrets

- [ ] managed secret store
- [ ] GitHub OAuth client secret rotation
- [ ] JWT signing key rotation and key IDs
- [ ] database TLS and least-privilege user
- [ ] provider keys scoped and budget limited

## Runtime

- [ ] frontend deployed with API relative URL
- [ ] API behind TLS and trusted proxy headers
- [ ] worker queue has retry/dead-letter policy
- [ ] object storage encryption and retention
- [ ] analysis concurrency and cost budgets
- [ ] isolated code execution network policy

## Trust

- [ ] public passport revocation works
- [ ] candidate export/delete/disconnect works
- [ ] audit logs are immutable/retained
- [ ] recruiter access is tested for cross-tenant leakage
- [ ] AI outputs have source-reference validation
- [ ] dispute workflow is available before public launch

## Observability

- [ ] health/readiness endpoints
- [ ] request/job correlation IDs
- [ ] analysis/provider latency and failure metrics
- [ ] queue depth and retry metrics
- [ ] cost/token telemetry without source leakage
- [ ] alerting and incident runbooks
