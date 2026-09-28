# Local development

## Working preview

The current working slice is the React app. It is safe to run without credentials:

```bash
cd SKILL-LINK
npm install
npm run dev
```

Vite binds to `0.0.0.0` for the Arena preview. The browser demo stores its workspace in local storage. Use **Reset demo workspace** in the sidebar to clear it.

## Backend prerequisites

The production-oriented API expects:

- Java 21
- Maven 3.9+
- PostgreSQL 16+
- Redis 7+ when queue/rate-limit features are enabled
- optional Python 3.11+ for the AI engine

The supplied sandbox used for this build has Node.js and Java 11 but does not include Maven or Docker, so the Java build and container boot were not run here. The frontend build was run successfully.

## Spring API

```bash
cd SKILL-LINK/backend
cp ../.env.example .env
mvn spring-boot:run
```

Flyway validates the schema on startup. The default security config is intentionally strict; configure a real JWT/OAuth issuer before connecting a frontend.

## Database

With PostgreSQL available:

```sql
CREATE USER skilllink PASSWORD 'change-me';
CREATE DATABASE skilllink OWNER skilllink;
```

Then set `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`.

## AI engine

```bash
cd SKILL-LINK/ai-engine
python -m venv .venv
. .venv/bin/activate
pip install -e '.[test]'
uvicorn app:app --host 0.0.0.0 --port 8000
```

The current app is a contract-safe local implementation. Provider credentials are not required and no live model calls occur.

## Docker Compose

`infrastructure/docker-compose.yml` starts PostgreSQL, Redis, and the optional AI engine. Docker was not available in the build sandbox, so run it in a local environment with Docker installed.

## Production wiring checklist

- configure GitHub OAuth callback and minimum scopes
- implement JWT issuer/refresh rotation
- add repository snapshot worker and secret redaction
- wire Spring jobs to a queue
- replace demo client adapter with `/api/v1` TanStack Query calls
- add object storage and retention jobs
- run backend migrations and integration tests
- enable SAST, secret scan, dependency audit, and container scanning
