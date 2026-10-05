# SENTINEL — AI Airspace Anomaly Detection

Full-stack airspace anomaly detection: ingests live ADS-B aircraft telemetry,
learns per-aircraft behavioral baselines, scores deviations, and uses an LLM
to explain anomalies in plain English.

## Architecture

ADS-B feed (adsb.lol) -> Ingestion -> Postgres -> Baselines -> Anomaly scoring -> LLM -> Dashboard

## Monorepo Layout

- backend/     Spring Boot 3, Java 21 — ingestion, scoring, API
- frontend/    Angular 21 + Leaflet — public dashboard + admin
- deploy/      Docker, CI/CD, infrastructure
- docs/        Architecture docs, ADRs, runbooks

## Quick Start

cp .env.example .env
# Fill in secrets (see .env.example)
docker compose up -d

## Status

Rebuild in progress. See AUDIT.md for what was wrong with v1.

## License

MIT
