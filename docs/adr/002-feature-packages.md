# ADR-002: Feature-based package organization

**Status:** Accepted
**Date:** 2026-10-05

## Context

Spring projects conventionally organize by layer: `controller/`,
`service/`, `repository/`, `entity/`, `dto/`. As the codebase grows,
a single feature's code scatters across five packages.

## Decision

Organize `com.sentinel` by feature: `ingestion/`, `baseline/`,
`anomaly/`, `llm/`. Shared cross-cutting code lives in `config/`,
`web/`, `security/`. Each feature package owns its entities,
repositories, services, and logic.

## Rationale

1. **Cohesion.** Everything about anomaly scoring — the entity, the
   Welford updater, the z-score calculator, the engine — sits in one
   place. Reading or changing a feature means opening one directory.
2. **Clear boundaries.** The anomaly engine consumes baselines through
   `BaselineService`, not by reaching into another package's internals.
   Package-private visibility enforces this for free.
3. **Scales with team size.** In a real team, features map to owners.
   Layer-based packages map to merge conflicts.

## Consequences

- Slightly unfamiliar to developers who only know the layered convention.
  The README's layout diagram orients them in seconds.
- Cross-feature DTOs (e.g. `AnomalyDto`) live in `web/` since they serve
  the API, not a single feature. This is the one deliberate exception.
