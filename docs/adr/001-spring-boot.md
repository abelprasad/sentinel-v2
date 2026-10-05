# ADR-001: Spring Boot for the backend

**Status:** Accepted
**Date:** 2026-10-05

## Context

SENTINEL needed a backend for: scheduled ADS-B polling, JPA persistence,
a REST API with JWT auth, and Flyway migrations. The main alternatives
were Spring Boot (Java), FastAPI (Python), and Express (Node).

## Decision

Spring Boot 3.4 on Java 21.

## Rationale

1. **Hiring signal.** SENTINEL is a portfolio project aimed at defense
   contractors (RTX, Lockheed Martin, Northrop Grumman). These are Java
   shops for backend enterprise systems. Spring Boot is what their teams
   use daily — "boring proven tech" is a feature when you're selling
   reliability.
2. **Ecosystem fit.** Spring Scheduling, Spring Data JPA, Spring Security,
   and Flyway auto-configuration cover every backend need with no glue
   code. The alternative would be assembling equivalents by hand.
3. **Type safety at the boundary.** ADS-B JSON is messy (`alt_baro` can be
   a number or the string `"ground"`). Jackson + Java records make the
   parsing contract explicit and testable.

## Consequences

- Heavier than FastAPI for the same endpoints (~388MB image vs ~150MB).
  Accepted: the hiring signal outweighs image size for this project.
- Java 21 required for builds. The Maven wrapper pins the build; CI and
  Docker both use it.
