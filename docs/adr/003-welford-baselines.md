# ADR-003: Rolling Welford baselines for anomaly detection

**Status:** Accepted
**Date:** 2026-10-05

## Context

v1 computed anomaly scores against all-time per-aircraft means with a
0.7 threshold on max-normalized-deviation. Result: ~142 flags/hour across
41 active tracks — overwhelmingly noise. Heading used a naive circular
average, so 359 -> 1 degree transitions scored as massive deviations.

## Decision

Per-dimension rolling baselines using Welford's online algorithm over a
6-hour sliding window, scored with z-scores (3.0-sigma threshold),
absolute deviation floors, proper circular heading math, and per-aircraft
cooldowns.

## Rationale

1. **Online, O(1) memory.** Welford's algorithm updates mean and variance
   incrementally — no window of raw samples to store. Correct for a
   poller that runs every 30 seconds indefinitely.
2. **Per-dimension sample counts.** A null field (e.g. missing speed)
   must not increment that dimension's n. Caught during implementation:
   dividing by global event count silently biases every z-score.
3. **Circular heading.** Heading accumulates sin/cos sums, not degrees.
   Angular distance replaces linear difference. This kills the entire
   class of wraparound false positives.
4. **Absolute floors.** A tight baseline makes even tiny deviations
   statistically significant (single-outlier z-score is capped at ~sqrt(n)).
   Floors (500 ft, 25 kts, 15 deg, 10 nm) keep sensor jitter from flagging.
5. **Cooldowns + escalation.** A 15-minute per-aircraft cooldown suppresses
   repeats; a 1.5x worse score during cooldown links as an escalation
   thread instead of being dropped. The UI renders threads, not spam.

## Consequences

- Baselines need ~10 samples before scoring (readiness gate) — new
  aircraft are unscored for ~5 minutes. Acceptable: better than false
  positives on every first sighting.
- Baselines learn *through* anomalies (observe-before-score), so a
  sustained deviation gradually becomes the new normal. This is correct
  for behavioral baselining but means very slow drifts may never flag.
  Documented as a known limitation, not a bug.
- Target: under 5 flags/track/day (v1: ~83).
