# database/flyway/

Flyway migration design notes for the refactor database.

## Purpose

Document the migration strategy, version numbering, naming convention, baseline state, and per-version intent for files under `wecms-api/src/main/resources/db/migration/`.

## What goes here

- `version-log.md` — chronological log of `V<n>__<slug>.sql` migrations with one-line intent
- Migration policy (forward-only, no in-place edits, repair vs new version)
- Seed-data strategy (per-tenant seed migrations, e.g. View Setting column width seeds)

## Naming convention

- Flyway file: `V<n>__<kebab-case-slug>.sql` — slug matches the feature where applicable.
- Per-version notes here use the same `V<n>` prefix in markdown filenames.

## Related

- Per-feature schema: [`../`](../)
- Refactor DB host (R&D): see memory `reference_local_db_setup`
