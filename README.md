# SENTINEL — AI Airspace Anomaly Detection

SENTINEL ingests live ADS-B aircraft telemetry, learns per-aircraft behavioral
baselines, scores deviations from those baselines in real time, and explains
flagged anomalies in plain English using an LLM. Think of it as a behavioral
intrusion detection system — for the sky.

**Live demo:** https://sentinel.abelprasad.dev

---

## How it works

```
ADS-B feed (adsb.lol) -> Ingestion -> Postgres -> Baselines -> Anomaly scoring -> LLM -> Dashboard
```

1. **Ingest** — A scheduled poller fetches aircraft positions near Philadelphia
   (40.0N, 75.1W) every 30 seconds from the adsb.lol API. Reports are
   deduplicated: identical positions inside a 5-minute window are dropped so
   the database stores signal, not noise.
2. **Baseline** — For each aircraft (keyed by ICAO hex), rolling statistics
   are maintained per telemetry dimension (altitude, speed, heading, position)
   using Welford's online algorithm. Baselines adapt over a 6-hour sliding
   window, so "normal" follows each aircraft's actual behavior.
3. **Score** — Every new report is scored against its baseline with per-dimension
   z-scores. Absolute deviation floors (500 ft, 25 kts, 15 deg, 10 nm) suppress
   sensor noise. Proper circular math handles heading wraparound (359 -> 1 deg).
   A 3.0-sigma threshold flags genuine deviations. A 15-minute cooldown per aircraft
   prevents alert spam; escalating deviations link into threads.
4. **Explain** — Flagged anomalies get a rule-based explanation immediately,
   and an LLM (Groq) generates a plain-English summary when a key is
   configured. The system degrades gracefully without one.
5. **Display** — A public Leaflet map shows live aircraft and anomaly feeds.
   A replay mode plays back scripted scenarios deterministically (works
   offline — ideal for demos). An admin panel manages aircraft, anomalies,
   and baselines.

## The anomaly math

For each telemetry dimension *d* (altitude, ground speed, heading, position):

- **Baseline**: Welford's algorithm maintains incremental mean and variance
  over a 6-hour sliding window. Sample counts are tracked per-dimension (not
  per-event) so null fields don't poison the statistics. Heading uses sin/cos
  accumulation for correct circular averaging.
- **Z-score**: z = |x - mean| / stddev, gated by absolute floors. A report
  is flagged when any dimension exceeds **3.0 sigma** and at least **10 baseline
  samples** exist (readiness gate).
- **Cooldown**: after flagging, an aircraft is silenced for 15 minutes unless
  the new score is 1.5x worse than the suppressed anomaly (escalation).
- **Track lifecycle**: tracks transition NEW -> ACTIVE -> STALE -> LOST based
  on telemetry freshness (10 min -> STALE, 60 min -> LOST).

This replaced v1's approach (all-time means, naive heading averages, no
cooldowns), which produced ~142 flags/hour of mostly noise. The target is
under 5 flags/track/day.

## Tech stack

| Layer    | Technology                                      |
|----------|-------------------------------------------------|
| Backend  | Java 21, Spring Boot 3.4, Spring Security (JWT) |
| Database | PostgreSQL 16, Flyway migrations               |
| Frontend | Angular 21 (zoneless, signals), Leaflet        |
| LLM      | Groq API (optional, graceful degradation)       |
| Infra    | Docker multi-stage builds, docker compose       |
| CI/CD    | GitHub Actions -> Docker Hub -> indra           |
| Tests    | JUnit 5 + Testcontainers (backend), Angular specs |

No H2, no devtools, no magic — production and test both run against real
Postgres 16 via Testcontainers.

## Monorepo layout

```
sentinel-v2/
├── backend/          Spring Boot — ingestion, scoring, REST API
├── frontend/         Angular — public map, replay, admin panel
├── deploy/           Dockerfiles, compose docs, runbooks
├── docs/             Architecture decision records (ADRs)
├── docker-compose.yml  db + backend + frontend, health-gated
└── .env.example      every secret, documented
```

Backend packages are organized **by feature**, not by layer:
`ingestion/`, `baseline/`, `anomaly/`, `llm/` each own their entities,
repositories, services, and logic.

## Quick start

```bash
cp .env.example .env
# Fill in secrets (JWT_SECRET, ADMIN_PASSWORD, optionally LLM_API_KEY)
docker compose up -d --build
```

- Frontend: http://localhost:3000 (public map, no login needed)
- Backend API: http://localhost:8888
- Admin: http://localhost:3000/admin/login (seeded from ADMIN_USERNAME/ADMIN_PASSWORD)

## API reference

### Public (no auth)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/health` | Liveness probe |
| GET | `/api/public/status` | Aggregate counters (aircraft, events, anomalies) |
| GET | `/api/public/tracks/{icaoHex}` | Aircraft + up to 200 track points, oldest-first |
| GET | `/api/public/anomalies` | Paginated anomalies, optional `?icaoHex=` filter |

### Admin (JWT required)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/login` | Login -> JWT token |
| GET/DELETE | `/api/admin/aircraft` | List / delete aircraft |
| GET/PATCH | `/api/admin/aircraft/{id}` | Get / update aircraft |
| GET | `/api/admin/anomalies` | List anomalies |
| POST | `/api/admin/anomalies/{id}/acknowledge` | Acknowledge |
| POST | `/api/admin/anomalies/{id}/escalate` | Escalate / de-escalate |
| GET | `/api/admin/baselines` | View baseline stats |
| POST | `/api/admin/baselines/{icaoHex}/reset` | Reset an aircraft's baseline |

Missing resources return **404** (not 403). Errors return a uniform
`{ "message": "..." }` body — internal details never leak.

## Development

See [CONTRIBUTING.md](CONTRIBUTING.md) for the full dev setup, test
commands, and code style.

```bash
# Backend (needs Java 21)
cd backend && ./mvnw test

# Frontend (needs Node 20+)
cd frontend && npm ci && npm test
```

## Screenshots

> Screenshots will be added after the v2 deployment goes live.
> The public map, anomaly detail view, replay mode, and admin panel
> are the planned shots.

## Why a v2?

The original SENTINEL (v1) worked as a prototype but had real problems,
documented in [AUDIT.md](AUDIT.md): a broken public demo (auth redirect
loop), a dead LLM integration (placeholder key), anomaly scoring that
produced ~142 flags/hour of noise, an open registration endpoint that let
anyone self-register as admin, committed credentials, and broken CI.

v2 is a from-scratch rebuild: fresh repo (no salvaged history), fixed
anomaly math, real security, working CI/CD, and replay mode for
deterministic demos.

## License

MIT
