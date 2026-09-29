# Local development

## Prerequisites

- Node.js 20+
- Java 21
- PostgreSQL 16+
- Maven wrapper included for the backend
- optional Python 3.11+ for the AI engine
- Docker only when using Compose; Docker was unavailable in the build sandbox

## Start PostgreSQL

```sql
CREATE USER skilllink PASSWORD 'change-me';
CREATE DATABASE skilllink OWNER skilllink;
```

Or run `infrastructure/docker-compose.yml` in an environment with Docker. The backend applies Flyway V1–V3 automatically.

## Start the backend

```bash
cd SKILL-LINK/backend
export JAVA_HOME=/path/to/jdk-21
export DATABASE_URL=jdbc:postgresql://localhost:5432/skilllink
export DATABASE_USERNAME=skilllink
export DATABASE_PASSWORD=change-me
export JWT_SECRET=<base64-encoded-long-random-secret>
export TOKEN_ENCRYPTION_KEY=$(openssl rand -base64 32)
./mvnw -B spring-boot:run
```

Useful local URLs:

- `GET http://localhost:8080/api/v1/health`
- `GET http://localhost:8080/actuator/health`

GitHub is intentionally not required to boot. Configure `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, and a callback matching `GITHUB_REDIRECT_URI` before selecting a real repository. Missing configuration returns a structured error; there is no fallback to demo repositories.

## Start the frontend

The Vite dev server proxies `/api` to `VITE_BACKEND_URL` and binds to `0.0.0.0` for preview environments.

```bash
cd SKILL-LINK/frontend
npm install
cat > .env.local <<'EOF'
VITE_DEMO_MODE=false
VITE_API_BASE_URL=/api/v1
VITE_BACKEND_URL=http://localhost:8080
EOF
npm run dev -- --host 0.0.0.0
```

Use `VITE_DEMO_MODE=true` (or omit the file) for the explicitly labeled offline fixture. Demo state is local browser state. Server-backed state is never synthesized by the real pages.

## Optional AI engine

```bash
cd SKILL-LINK/ai-engine
python -m venv .venv
. .venv/bin/activate
pip install -e '.[test]'
uvicorn app:app --host 0.0.0.0 --port 8000
```

The FastAPI service exposes schema-validated `/v1/analyze` and `/v1/examiner/questions` contracts. It accepts deterministic, redacted context and returns references; it cannot mark a skill verified. The Spring application defaults to the disabled provider boundary.

## Verification commands

```bash
cd frontend
npm run build
npm test -- --run

cd ../backend
JAVA_HOME=/path/to/jdk-21 ./mvnw -B test
```

Backend tests include mocked GitHub commit/tree/blob snapshot fetching, limits/redaction, deterministic analyzer signals, worker orchestration, health, and auth refresh/logout error contracts. `AuthenticationFlowIntegrationTest` starts its own PostgreSQL container (Testcontainers) and verifies the full authentication loop — registration, login, JWT access, `/me`, refresh rotation, RBAC, and logout/revocation — over real HTTP. It is skipped automatically when Docker is not available; test-only JWT/encryption fixtures come from `backend/src/test/resources/application-test.yml`, so `./mvnw -B test` needs no extra environment variables.

## Preview environment notes

- bind servers to `0.0.0.0`
- use relative browser API paths; do not call `localhost` from browser code
- set `SKILLLINK_WEB_ORIGIN` and GitHub redirect URLs to the preview origin for OAuth
- the file viewer iframe has no network access; use the live Vite preview for API-backed behavior
