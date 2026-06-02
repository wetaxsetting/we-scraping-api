# testing/integration/

Integration tests at the codebase level (Spring Boot integration tests + Next.js E2E).

## Purpose

Document the integration-test layer: which classes / specs exist, what they cover, how they connect to the local DB (per memory `reference_local_db_setup`), and how they handle test-data setup / teardown.

## What goes here

- Per-feature integration test design
- Test-data seed strategy
- DB cleanup / transactional rollback policy
- E2E test plan (Playwright / Cypress / etc. — choose once, document here)
