# Settings Gap Analysis — 2026-06-23

> Consolidated parity / gap matrix for the **Settings** menu group.
> Companion files: [settings-docs-audit.md](settings-docs-audit.md) · [settings-source-index.md](settings-source-index.md).
> Gap vocabulary: **MATCH · APPROVED DEVIATION · DEAD LEGACY · PARTIAL · MISSING**.
> Per-module detail lives in each `features/settings/<module>/refactor-gap-analysis.md`; this file is the cross-module roll-up.

## Cross-module gaps

| ID | Module | Kind | Status | Notes |
|---|---|---|---|---|
| S-1 | Login Credentials | doc-missing | ✅ **RESOLVED 2026-06-23** | `docs/audits/login-credentials-legacy-vs-refactor-audit.md` was referenced by the feature docs + `CLAUDE.md` + memory but absent. Verified `login-legacy-compatibility-2026-06-10.md` is a **different** doc (auth password coexistence, not the cert feature) → **Option B: created** the dedicated audit (cert cipher §7, `ACCT_AUTH_INFO`, GDT verify-before-save, §11 DB-object inventory). All dangling references now resolve. |
| S-2 | User Management | naming | **MATCH (by design)** | `users.md` flat summary ↔ `user-management/` folder slug — intentional; banner points at folder. |
| S-3 | Chart of Accounts | PO-decision | **PARTIAL** | OQ-1 (USER-role gating) + OQ-3 (legacy `u001:237-256` parent-cleanup branch) pending Mr. Jung. Default ships without `@PreAuthorize`; OQ-3 dropped pending confirmation. |
| S-4 | Approval Line | APPROVED DEVIATION | **MATCH** | No `AUDIT_LOGS_INFM` writes (legacy `comm_0222_*` writes none either) and no View Setting (legacy panel has no toolbar list-icon) — deliberate parity, not a gap. |
| S-5 | Transaction History (ERP) | APPROVED DEVIATION | **MATCH** | Upload write-path intentionally not ported (each Payment feature writes its own audit row); View Setting not adopted. |

## Per-module gap status (roll-up)

| Module | Backend | Frontend | Notable open items |
|---|---|---|---|
| Purpose | ✅ MATCH (+tests) | present | — |
| VAS Account | ✅ MATCH (+tests) | ✅ present | — |
| Contacts | ✅ MATCH | not re-verified this pass | OD-1 `trco_acct_key` create-vs-update key contract (recorded, not actioned) |
| Approval Line | ✅ MATCH (+tests) | not re-verified | S-4 (no audit-log / no View Setting — approved) |
| Chart of Accounts | ✅ MATCH | not re-verified | S-3 (OQ-1 / OQ-3 PO-pending) |
| Bank Account | ✅ MATCH | ✅ present | — |
| Login Credentials | ✅ MATCH | ✅ present | S-1 (missing audit doc) |
| Preferences | ✅ MATCH | ✅ present | — |
| Transaction History (ERP) | ✅ MATCH | not re-verified | S-5 (upload not ported — approved) |
| Firm Banking Settings | ✅ MATCH (+tests) | not re-verified | — |
| Firm Banking History | ✅ MATCH (+tests) | not re-verified | — |
| User Management | ✅ MATCH (full-stack) | ✅ present | OQ-DOC-UM-1..5 (audit-log on mutation, action-level entitlement, status edit, pw-history, phone CC round-trip) |

## Notes

- "not re-verified this pass" mirrors the conservative `CLAUDE.md` Feature-Status legend — backend + tests present,
  frontend wiring not re-checked in this docs pass; do not read as incomplete.
- The only **actionable** new gap surfaced here is **S-1** (missing Login Credentials audit file). Everything else is
  either MATCH, an approved deviation, or a previously-recorded PO open question.
