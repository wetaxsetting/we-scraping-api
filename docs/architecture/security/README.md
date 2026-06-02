# Security (shared)

Security policies (auth, API, audit logging, checklist, baseline) are
cross-system and live once at the workspace root:

- ➡️ [../../../docs/security/](../../../../docs/security/)
- ➡️ [../../../docs/_shared/security-baseline.md](../../../../docs/_shared/security-baseline.md)

Backend-specific security *design* notes live under
[../business-rules/security/](../../references/business-rules/security/).

## What goes here

- Pointers to the shared, cross-system security policies (auth, API, audit logging, baseline) at the workspace root.
- Backend security **architecture** notes that are genuinely system-design level (filter-chain topology, JWT/tenant model boundaries) and not feature-specific.

## What does NOT go here

- Cross-system security policy → [workspace docs/security/](../../../../docs/security/) (single source of truth).
- Feature / service-level security design → [references/business-rules/security/](../../references/business-rules/security/).
- Auth feature spec (login / me / logout) → [features/auth/](../../features/auth/).
