# testing/performance/

Performance test plans and reports.

## Purpose

Capture the load / soak / spike test plan for the refactor: which endpoints are targeted, what the SLOs are, how the test environment is provisioned, and the reports produced.

## What goes here

- Per-feature or per-endpoint load test plan
- Test report archive
- SLO definitions (latency, throughput, error rate)

## Refactor scope

Per memory `project_refactoring_scope`: this is **R&D-local** today. Performance tests run against the local stack — no DEV/UAT/PROD load is generated. Once the refactor leaves R&D, this folder becomes the home for production performance verification.
