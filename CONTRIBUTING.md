# Contributing to SENTINEL

## Prerequisites

- **Java 21** (JDK, not just JRE — `./mvnw` needs `javac`)
- **Node 20+** and npm
- **Docker** + docker compose (for the full stack)
- No local Postgres needed — tests use Testcontainers (Docker)

## Backend development

```bash
cd backend

# Compile
./mvnw compile

# Run all tests (spins up Postgres 16 via Testcontainers)
./mvnw test

# Run one test class
./mvnw test -Dtest=AnomalyScoringServiceTest

# Run the app locally (needs Postgres running + env vars)
./mvnw spring-boot:run
```

### Backend conventions

- **Feature packages, not layers.** New ingestion logic goes in
  `com.sentinel.ingestion/`, not in a generic `service/` package.
- **Records for DTOs.** All API DTOs are Java records. Mapping lives in
  `DtoMapper` — don't scatter conversion logic across controllers.
- **Fail closed.** Missing secrets refuse to boot. Unknown paths are denied.
  Never add a default that weakens security for convenience.
- **No H2.** Tests run against real Postgres via Testcontainers.
  If a test needs a database, use `@ServiceConnection`.
- **Scheduled jobs must be test-safe.** If your `@Scheduled` bean touches
  the network or DB, make sure tests can `@MockBean` it — the ingestion
  poller once hit the real adsb.lol API mid-test.

### Config

All config flows through `SentinelProperties` (`@ConfigurationProperties`,
prefix `sentinel`). Add new settings there with validation annotations —
don't sprinkle `@Value` across the codebase.

## Frontend development

```bash
cd frontend

# Install (use ci for reproducible builds)
npm ci

# Dev server (proxies /api to localhost:8888)
npm start

# Tests
npm test

# Production build
npm run build
```

### Frontend conventions

- **Zoneless + signals.** The app runs without Zone.js. Use signals for
  state; don't fight the framework with manual change detection.
- **Relative API base.** All API calls go through `ApiService` with a
  relative `/api` base (proxied by nginx in prod, `proxy.conf.json` in dev).
  Never hardcode hosts or IPs.
- **Auth lives only under `/admin`.** Public routes never touch tokens.
  The JWT interceptor attaches tokens to `/api/admin/*` only. The wildcard
  route falls back to the public map — redirect loops are impossible by
  construction.
- **Strict TypeScript.** `strict: true` is on. Type-narrow before you use.

## Docker

```bash
# Full stack
docker compose up -d --build

# Rebuild one service
docker compose build backend && docker compose up -d backend
```

Both images are multi-stage, run as non-root, and have healthchecks.
Backend runs unit tests during the Docker build — a failing test stops
the image build.

## CI/CD

Three workflows in `.github/workflows/`:

| Workflow | Trigger | Does |
|----------|---------|------|
| `ci.yml` | push / PR | Backend + frontend build and test |
| `docker.yml` | push to main | Build, tag with SHA, push to Docker Hub |
| `deploy.yml` | after docker.yml | SSH to indra, pull, restart, smoke-test |

Failing tests block the pipeline. The deploy workflow gates on a
smoke test against the public demo URL.

## Commit style

Small, focused commits with a scope prefix:

```
ingest: dedup reports that add no information within the dedup window
anomaly: cooldown to prevent alert spam
frontend: admin login page — form, error handling, redirect on success
security: JWT auth filter, role-based admin access
docs: add architecture decision records
```

Scopes: `chore`, `build`, `config`, `ingest`, `anomaly`, `api`,
`security`, `frontend`, `docker`, `ci`, `docs`.
