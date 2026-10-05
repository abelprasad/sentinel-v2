# SENTINEL v2 — Deployment

## Local (docker compose)

```bash
cp .env.example .env
# fill in secrets in .env
docker compose up -d --build
```

Services:
- **db** — Postgres 16, internal only, health-gated
- **backend** — Spring Boot on :8888, waits for db healthy
- **frontend** — nginx on :3000, proxies /api to backend, waits for backend healthy

All services: `restart: unless-stopped`, DNS override (8.8.8.8/1.1.1.1) codified
in compose (Docker embedded DNS is broken on indra).

## Images

| Image | Base | Size | Notes |
|-------|------|------|-------|
| backend | eclipse-temurin:21-jre | ~388MB | Multi-stage; unit tests run during build |
| frontend | nginx:alpine | ~64MB | Multi-stage; non-root on :8080 |

Both run as non-root users. Secrets come from env vars only — never baked in.

## Health checks

- Backend: `GET /actuator/health` (wget spider)
- Frontend: `GET /` (wget spider)
- DB: `pg_isready`

Compose uses `service_healthy` conditions so startup order is guaranteed:
db -> backend -> frontend.
