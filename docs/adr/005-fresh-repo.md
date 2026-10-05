# ADR-005: Fresh repository — no salvaged git history

**Status:** Accepted
**Date:** 2026-10-05

## Context

v1's git history contained committed secrets: a hardcoded JWT signing
key, admin credentials in `body.json`/`login.json`, and a 5-character
placeholder LLM key. Rewriting history with filter-branch removes them
from the tip but they remain in the object store and in every clone.

## Decision

Start `sentinel-v2` as a brand-new repository. No history imported from
v1. The old repos (`abelprasad/sentinel`, `abelprasad/sentinel-ui`) are
left untouched until v2 is live, then archived.

## Rationale

1. **Secrets can't be un-committed.** A fresh repo is the only way to
   guarantee the credential material is gone, not just hidden.
2. **Clean narrative.** Focused commits telling the rebuild story is
   a better portfolio artifact than prototype archaeology with secrets
   scrubbed out.
3. **v1 stays as a reference.** AUDIT.md documents what was wrong, and
   the old code remains available for comparison — just not in the new
   history.

## Consequences

- v1 commit history (authorship dates, early experiments) is not
  preserved in v2. The audit doc captures the lessons; the commits
  weren't the valuable part.
- The old `sentinel` repo must be archived/renamed once v2 is the
  canonical project, to avoid confusion.
