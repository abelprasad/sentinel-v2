# SENTINEL Audit — 2026-10-05

Read-only audit of `/home/abel/sentinel-full/` (monorepo) and `/home/abel/sentinel/` (legacy),
plus live container state on indra. Nothing was modified. All findings verified against
running containers (`sentinel-db-1`, `sentinel-backend-1`, `sentinel-frontend-1`).

## Headline: the docs describe a dashboard rewrite that does not exist

`/home/friday/docs/sentinel/README.md` claims a 2026-10-05 "Dashboard v2 rewrite": no login,
`/public/status`-driven dashboard, replay mode with play/pause + scrubber, mission-control
redesign. **None of that is in the code.** What actually happened today in the legacy UI repo
(`git -C /home/abel/sentinel/sentinel-ui log --oneline -5`):

- `ba7991f` deleted `src/app/login/` (311 lines)
- `1781851` removed the auth guard from routes
- `c3a5dcd` tweaked nginx.conf
- `ee26863` deleted the unused `auth.guard.ts`

But `dashboard.ts` was never rewritten. It still does `localStorage.getItem('sentinel_token')` and
`router.navigate(['/login'])` on lines 43-47, 99, 280. `/login` no longer exists as a route, so
the wildcard route (`app.routes.ts`: `{ path: '**', redirectTo: '' }`) sends it back to the
dashboard, which re-runs `ngOnInit` and navigates to `/login` again. **Infinite redirect loop.**
The public demo at https://sentinel.abelprasad.dev/ serves this build (title "SentinelUi",
HTTP 200) and can never get past the loading state. The "Feed stuck Connecting..." symptom
noted in the docs is this loop, not something already fixed.

The deployed frontend image (`abelprasad/sentinel-ui:latest`, built 2026-10-05 13:26) was
verified: its `main-OKCFGI52.js` contains `sentinel_token` references and zero occurrences of
"Replay incident". The monorepo `frontend/` (copied from legacy at 13:27) is byte-identical to
the legacy UI source (`diff -rq` clean).

Second breakage in the same image: `frontend/src/environments/environment.prod.ts` bakes in
`apiUrl: 'http://100.98.50.85:8888'`, a stale Tailscale IP (current tailnet IP is
100.78.151.90). Confirmed present in the deployed bundle. From the public internet this is
unreachable, so even if the redirect loop were fixed, every API call from a visitor's browser
would fail. The nginx `/api/` proxy exists but the prod build never uses it.

## Backend (Spring Boot) — mostly real, works, some warts

Package layout under `src/main/java/com/abel/sentinel/`: `config/` (1 class), `controller/`
(8 controllers), `dto/` (1), `model/` (8), `repository/` (5), `security/` (3), `service/`
(11). Standard layered CRUD + pipeline. `SentinelApplication.java` correctly has both
`@EnableScheduling` and `@EnableAsync`.

**Ingestion pipeline — real and running.** `service/AdsbIngestionService.java`, `@Scheduled`
every 30s (`adsb.poll-interval-ms: 30000` in `application.yaml`). Polls
`https://api.adsb.lol/v2/lat/40.0/lon/-75.1/dist/25`, accepts both `ac` and `aircraft` JSON
keys, upserts `AircraftEntity` by ICAO hex, saves a `FlightEvent`, then calls
`baselineService.calculate(entity)` and `anomalyScoreService.score(entity, saved)`.
Live logs show "ADS-B ingestion complete -- 18 events saved" every 30s. `alt_baro` handled as
`Object` since adsb.lol returns the string `"ground"` sometimes (`model/AdsbAircraft.java:24`).
Weak spots: a `new RestTemplate()` is constructed per poll (line 39) with no timeouts
configured, so a hung upstream would stall the scheduler thread; per-aircraft exceptions are
swallowed with `log.warn("Skipping aircraft ...: null")` (the null message suggests NPEs on
some records, worth investigating).

**Baseline learning — real but crude.** `service/BaselineService.java`: needs
`MINIMUM_EVENTS = 3` (line 21), then stores arithmetic means of altitude, speed, heading,
lat, lon over **all** events for the entity (`findByEntityId` loads the full history every
poll; logs show entities with 1267 events re-averaged every 30s). No rolling window, no
variance/stddev, and heading is averaged arithmetically, which is wrong for circular data
(350 deg and 10 deg average to 180 deg). One baseline row per entity, recalculated constantly.

**Anomaly scoring — real but noisy.** `service/AnomalyScoreService.java`: score is the max of
five normalized deviations (alt/10000, speed/200, heading/180, lat/10, lon/10), threshold
`ANOMALY_THRESHOLD = 0.7` (line 21), 5-minute dedup per entity. Live DB: **142 anomalies in
the last hour against 41 active tracks**; 15,116 anomalies total. The detector flags routine
variation far too often to be useful as-is.

**LLM explanation — broken right now.** `service/GroqLlmService.java` posts to Groq
(`llama-3.3-70b-versatile`, `max_tokens: 100`) with `@Async`, and on failure the anomaly keeps
a rule-based fallback explanation. The container's `LLM_API_KEY` is 5 characters long, a
placeholder, so **every** Groq call 401s (`Groq summarization failed ... 401 Unauthorized`
in the logs on every scoring cycle). DB evidence: 11,845 of 15,124 anomalies carry the
fallback template ("Anomaly score: 0.86. Significant heading deviation."); 3,279 carry real
LLM prose from when the key was valid (e.g. id 69546: "UAL1792 is drastically off-profile,
with a 15,432 ft altitude deviation, 180 kt..."). The "Groq explains anomalies" claim is
currently aspirational. Each 401 is also a wasted outbound HTTPS call per anomaly.

**Controllers.** 8 total: `PublicController` (`/public/status`, `/public/track/{anomalyId}`,
both verified 200 with correct data), `AnomalyController` (`/anomalies`, auth, note it
returns ALL anomalies with no pagination), `PositionController`, `FlightEventController`,
`AircraftEntityController`, `AuthController`, `SimulationController` (ADMIN-only synthetic
injection, works), `HealthController`.

**Security issues (real):**
1. `POST /auth/register` is permitAll (`SecurityConfig.java` permits `/auth/**`) and
   `AuthController.java:31-44` accepts a caller-supplied `role`. Anyone can self-register an
   ADMIN user. One-line fix: force role server-side or require ADMIN.
2. JWT signing secret hardcoded in `security/JwtService.java:18`
   (`sentinel-secret-key-must-be-at-least-32-bytes-long`). Fine for a demo, not for anything
   real.
3. `body.json` and `login.json` at the repo root contain the admin username/password and are
   **committed to git**. `event.json` is a test payload. These should be deleted and the
   password rotated.
4. Minor: `GET /public/track/{missingId}` returns **403**, not 404/500. Cause: the controller
   throws, the container forwards to `/error`, and the security chain's `anyRequest().
   authenticated()` rejects the error dispatch. Cosmetic but confusing for API consumers.

**Config.** `src/main/resources/application.yaml`: datasource points at
`jdbc:postgresql://localhost:5432/sentinel` (overridden by env in the container), Flyway
enabled with `ddl-auto: none`, two migrations (`V1__init_schema.sql`, `V2__fix_explanation_text.sql`).
`adsb.url` and `llm.model` are config-file values (fine); `llm.api-key` resolves from
`LLM_API_KEY` env. `CORS_ALLOWED_ORIGIN` env is honored, but `SecurityConfig.java:37-43`
also hardcodes a list of old tailnet IPs (`100.98.50.85`) alongside it. `config/JacksonConfig.java`
defines a bare `new ObjectMapper()` bean, which disables Spring Boot's Jackson
auto-configuration (no builder customizers, no module auto-registration). Date serialization
empirically works today, but this is fragile and should be a `Jackson2ObjectMapperBuilderCustomizer`
instead.

**Dependencies (pom.xml).** Spring Boot 4.0.6 parent, Java 21. Used: data-jpa, webmvc,
security, jjwt 0.12.6, jackson-databind, postgresql (runtime), lombok, flyway (+postgres),
devtools (runtime, pointless in a container image but harmless), h2 (runtime, unused since the
app only talks to Postgres; dead weight in the image). Test deps (`spring-boot-starter-data-jpa-test`,
`webmvc-test`) are present but there are no real tests.

**Tests.** One backend test: `SentinelApplicationTests.contextLoads()` (empty). It is never
run: CI skips straight to `docker build`, and the Dockerfile runs `mvnw clean package
-DskipTests`. Frontend has three spec files; `api.spec.ts` imports `{ Api }` from `./api`
but the class is named `ApiService` (compile error), and `dashboard.spec.ts` / `app.spec.ts`
instantiate components that inject `Router` and `HttpClient` without test providers. All
three are broken. Effective test coverage: zero.

**Performance notes.** `PositionService.getCurrentPositions()` is N+1: per latest event it
loads the entity, then loads **all** anomalies for the entity just to check recency
(`PositionService.java:28-36`). `/public/status` then does an additional anomaly lookup per
position. Fine at 41 tracks, will not scale. Baseline recalculation loads full event history
per aircraft per poll (see above).

**Dead backend code.** `AnomalyScoreRepository.findByEntityAndScoreGreaterThanOrderByFlaggedAtDesc`
has no callers. `FlightEventRepository.findLatestPerEntity()` (no-since variant) has no
callers (only the `Since` variant is used). `h2` dependency unused. No TODO/FIXME comments
in backend source.

## Frontend (Angular 21 + Leaflet) — half-migrated, currently broken live

Structure: single `dashboard/` component (ts/html/scss), `api.ts` service, `app.routes.ts`,
`app.config.ts`. Leaflet **is** integrated (`package.json`: `leaflet@1.9.4`, `dashboard.ts:6`
imports `* as L from 'leaflet'`): map init, Carto `dark_all` tile layer, custom SVG plane
markers rotated by heading, tooltips, click-to-select detail panel, 10s/30s polling.

What is wrong:
1. **Redirect loop on load** (described above). `dashboard.ts:43-47`.
2. **Stale prod API base** `http://100.98.50.85:8888` baked into the deployed bundle
   (`environment.prod.ts`). Dev config uses `/api` (which nginx proxies correctly), but the
   prod build replaces it.
3. **Auth leftovers everywhere**: `api.ts` still exposes `login()`, `setToken()`, Bearer
   headers on every method; dashboard still calls `/anomalies` (auth, returns everything),
   `/positions`, `/entities`, `/simulate/*`. The backend's public endpoints that the docs
   claim the UI uses (`/public/status`, `/public/track`) are called **nowhere** in the
   frontend source.
4. **Map tiles**: still Carto `dark_all` (`dashboard.ts:75`), not the OSM+CSS-filter swap the
   docs claim. (Carto dark_all generally works keyless, so this is a docs inaccuracy more
   than a bug.)
5. `api.spec.ts` broken import (see Tests). `public/public.html` is a standalone legacy
   "Pattern of Life Analyzer" page copied into the nginx image but unlinked from the app.
   `frontend/.github/workflows/deploy.yml` is a stale two-repo-era trigger that dispatches
   the backend repo's workflow; the live workflow is the monorepo root one.

## Infrastructure

**Dockerfiles.** Both multi-stage and reasonable. Backend:
`eclipse-temurin:21-jdk-alpine` build, `21-jre-alpine` runtime, `dependency:go-offline`
layering (though `COPY src/` after it is correct for cache reuse). Frontend: `node:22-alpine`
build, `nginx:alpine` serve. Not bloated. Nits: backend image ships devtools and h2;
frontend build runs `npm install` (not `npm ci`).

**docker-compose.yml (repo root).** Defines `db`/`backend`/`frontend`, but it does not match
how indra actually runs: the compose backend points at `jdbc:postgresql://db:5432` while the
live containers use `sentinel-db-1` on the `sentinel_default` network; compose sets
`CORS_ALLOWED_ORIGIN=http://100.98.50.85:3000` (stale IP); and critically it has **no
`dns:` override**, so anyone deploying via compose would re-hit the broken-Docker-DNS
problem the manual deploy worked around with `--dns 8.8.8.8 --dns 1.1.1.1`.

**CI (`.github/workflows/deploy.yml`) — broken, root cause found.** The "Log in to Docker
Hub" step passes only `username: ${{ secrets.DOCKERHUB_USERNAME }}` with no `password:`
field to `docker/login-action@v3`. That is exactly the parked "Password required" failure:
add `password: ${{ secrets.DOCKERHUB_TOKEN }}`. Further problems in the same file even after
that fix: the SSH deploy script does `cd ~/sentinel` but the repo lives at
`~/sentinel-full`; it runs `docker compose pull && docker compose up -d`, but there is no
compose binary/workflow on indra (deploys are manual `docker run`); and it would deploy the
stale-IP compose config described above.

**Live deployment on indra.** Three containers on `sentinel_default`, all `Up` and healthy:
`sentinel-db-1` (postgres:16), `sentinel-backend-1` (`abelprasad/sentinel-backend:latest`,
8888->8080, `--dns 8.8.8.8 --dns 1.1.1.1`, `--restart unless-stopped`), `sentinel-frontend-1`
(`abelprasad/sentinel-ui:latest`, 3000->80). Manual `docker run`, no compose. Cloudflare
tunnel exposes the frontend as https://sentinel.abelprasad.dev/ (200, serves the broken
dashboard shell). DB is live and ingesting: 15,998 entities, 386,243 events, 14,439
baselines, 15,116 anomalies, 1 user (`abel`/ADMIN). Events span only 9 distinct days
(2026-06-30 through 2026-07-07, plus today): the backend was down for roughly three months
and was revived today. `PruneService` (3 AM cron, 7-day retention) exists but has never been
observed running in the current container; retention behavior is unproven.

## Data flow: real vs aspirational

| Step | Status |
|---|---|
| ADS-B API (adsb.lol) -> ingestion | **Real.** 30s poll, ~18 events/poll, dual `ac`/`aircraft` key handling |
| Per-aircraft baselines | **Real but crude.** All-time means, min 3 events, full-history rescan per poll, naive heading average |
| Anomaly scoring | **Real but noisy.** 142 flags/hr on 41 tracks; threshold too loose for the data |
| Groq LLM explanations | **Broken.** Placeholder key, 100% 401s; all current explanations are the rule-based fallback template. 3,279 historical anomalies retain real LLM prose from when the key worked |
| Frontend display | **Broken.** Redirect loop + stale API base; serves a shell that can never load data |
| Public API (`/public/status`, `/public/track/{id}`) | **Real.** Verified 200s with correct data; this is the salvageable surface |
| Simulation injection | **Real.** ADMIN-only `/simulate/quick` and `/simulate/custom`, wired to scoring + LLM path |
| Nightly prune | **Unproven.** Code exists, never seen it fire |

## What works vs what is broken

**Works:** ingestion loop, baseline + scoring math, public API endpoints, simulation
endpoints, JWT auth (as designed), Flyway migrations, manual container deploy, Cloudflare
tunnel plumbing, ICAO prefix classification (AE=military etc., though one entity class
quirk aside, `ad6758` shows null classification in the DB despite the `startsWith("A")`
rule, likely a row created before classification was wired into ingestion).

**Broken:** public demo UX (redirect loop, dead API base), Groq integration (bad key),
CI pipeline (login-action missing password + wrong deploy script), anomaly precision
(142/hr noise), open ADMIN self-registration, credentials committed to git
(`body.json`, `login.json`), compose file out of sync with reality.

**Half-done / dead:** the v2 dashboard (docs describe it, code does not contain it);
`PruneService` (unproven); `public/public.html` (orphaned page in the image);
`frontend/.github/workflows/deploy.yml` (two-repo leftover); three frontend spec files;
`h2` and devtools in the backend image; two unused repository methods.

## Suggested fix order (highest leverage first)

1. Decide the frontend's fate: either finish the v2 rewrite the docs describe (public
   endpoints, no auth, relative `/api` base) or revert the docs. Right now the docs lie,
   which is worse than either state. The backend public API is ready; the frontend needs
   ~1 focused session.
2. Rotate the admin password, delete `body.json`/`login.json`/`event.json` from the repo
   (they are in git history too), close open registration (`AuthController.java:31-44`),
   move the JWT secret out of source (`JwtService.java:18`).
3. Fix CI: add `password: ${{ secrets.DOCKERHUB_TOKEN }}` to the login step; fix `cd
   ~/sentinel-full`; replace the `docker compose` deploy block with the actual manual
   `docker run` commands (with `--dns` flags) or install compose and fix the compose file.
4. Restore a real `LLM_API_KEY` (or remove the Groq call path until there is one; right now
   it is pure 401 noise per anomaly).
5. Tighten scoring: rolling window baselines, per-dimension z-scores or percentile
   thresholds instead of max-of-normalized-deviations at 0.7, and fix the circular heading
   mean. The current 142/hr rate makes the feed unusable as a signal.
6. Small: fix `api.spec.ts` import, add `/error` to permitAll (or handle missing-anomaly as
   404), replace the bare `ObjectMapper` bean with a customizer, give `RestTemplate` a
   shared bean with timeouts, fix the N+1 in `PositionService`, sync `docker-compose.yml`
   with the real deploy (service names, DNS, current IPs).

---
*Audit run 2026-10-05 ~16:30 EDT by Friday (subagent). Repos untouched; all state verified
against live containers and the database, not just source.*
