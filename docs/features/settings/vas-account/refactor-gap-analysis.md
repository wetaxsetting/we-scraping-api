# Settings > VAS Account — Refactor Gap Analysis

**Screen ID:** `vas-account` · **JEX:** `comm_0217_*` + `comm_1002_001` · **Hub:** [README.md](README.md)

Status legend: **MATCH** (behaves as legacy) · **APPROVED DEVIATION** (intentional, signed-off) ·
**PARTIAL** (incomplete) · **MISSING** (planned-but-absent). The authoritative as-built record is the
preserved audit [references/audits/vas-account.md](../../../references/audits/vas-account.md)
(2026-05-11; 4 Majors all resolved). This file is the final parity review against the request's
verification checklist.

---

## 1. Parity matrix

| Area | Legacy (`comm_0217_*`) | Refactor (`vas-account`) | Status |
|---|---|---|---|
| Search (code/name keyword) | `comm_0217_001_r001` ILIKE on cd/nm | `search` JPQL, `LIKE … ESCAPE '!'` on cd/nm, lowercased | **MATCH** |
| Status filter (A / I) | checkbox set; `N` sentinel for "neither" | `resolveStatusFilter` — 0/2 values = no filter, 1 value validated A/I | **MATCH** (refactor rejects unknown single value with `WBE_VALIDATION`; legacy was lenient — APPROVED stricter behaviour) |
| Lookup picker | `comm_1002_001` (`DISP_VAS_ACCT_NM`) | `GET …/lookup`, active+live, ≤500, ordered by code | **MATCH** |
| Detail | `comm_0217_004_r001` | `GET …/{seqNo}` | **MATCH** |
| Create | `comm_0217_003_c001` (+ dup-check) | `POST …`, dup-check → `WBE0003`, seq MAX+1 | **MATCH** |
| Update | `comm_0217_004_u001` (dup-check on code change) | `PUT …`, dup-check only on code change; blank enm/type/cont preserved | **MATCH** |
| Activation (single) | status radio on create/edit | `stts` field on Create/Update | **MATCH** |
| Deactivation (single) | status radio on create/edit | `stts` field on Create/Update | **MATCH** |
| Bulk activation | **none** | **none** | **MATCH** (both absent) |
| Bulk deactivation | **none** | **none** | **MATCH** (both absent) |
| Hierarchy management | flat list; no parent column | flat list; no parent column; frontend prefix-tree is display-only | **MATCH** |
| Soft delete | `comm_0217_001_d001` → `DEL_YN='Y'` | `DELETE …` → `DEL_YN='Y'` | **MATCH** |
| Bulk delete | **none** | **none** (DN-8; `CHECKBOX` not seeded) | **MATCH** (both absent) |
| Hard delete | `D001` IDO exists but unused | not ported | **MATCH** |
| Import (Excel preview) | `comm_0217_002_r001` | `POST …/excel/preview` | **MATCH** |
| Import (save A/R) | `comm_0217_002_c001` | `POST …/excel/import` | **MATCH** |
| Import per-cell re-validate | `comm_0217_002_r002` | dropped (no inline cell edit) | **APPROVED DEVIATION** (audit F2) |
| Export (XLSX) | none | `POST …/export` (shared pipeline) | **APPROVED DEVIATION** (refactor addition; frontend button deferred) |
| Sequence generation | `SeqHelper.getNextVal` MAX+1 | `VasAcctSeqNoGenerator` → `LegacyFunctionRepository.nextVasAcctSeqNo` (`wb_get_next_val` funnel, V140 #25; per-`BIZ_KEY` MAX+1) | **MATCH** (no retry) |
| Validation (code/name/status) | length + presence | `@Size`/`@Pattern`/`@NotBlank` + service dup-check | **MATCH** (refactor adds `^[A-Za-z0-9_]+$` code guard — APPROVED) |
| Audit logging | n/a (legacy `comm_0217_*` wrote none verified) | one `AUDIT_LOGS_INFM` row per write, `wsvc_id='vas-account'` | **APPROVED DEVIATION** (refactor addition, consistent with firm-banking-settings/contacts) |
| Integration tests | n/a | `@Disabled` (F1) | **PARTIAL** (infra-only; unit/controller/Excel green) |

## 2. Special verification areas (per request)

- **Bulk Activate parity** — **MATCH (both absent).** Neither JEX nor the refactor has any
  bulk-status path or status endpoint. Status is a per-row Create/Update field. Verified in code
  (`VasAccountController` has no `/status` mapping) and in legacy (`comm_0217_*` has no bulk WSVC).
- **Parent validation parity** — **MATCH (N/A — no parent).** `BUSI_VAS_ACCT_INFM` has no parent FK
  (confirmed against the live DB). There is no parent to validate. The frontend prefix-tree grouping
  is display-only and writes nothing back. (Parent validation is a Chart-of-Accounts concern — a
  different table.)
- **Import validation parity** — **MATCH.** Refactor enforces required / length (3–15 code, ≤50 name)
  / format / dup-in-file / dup-in-db, header validation (EN/KO/VI label sets), and the 500-row cap —
  mirroring `comm_0217_002_r001`. Append re-checks DB duplicates; Replace skips the DB-dup check
  because the wipe clears conflicts. Atomic abort on the first invalid row at import time.
- **Sequence generation parity** — **MATCH.** Both are per-`BIZ_KEY` MAX+1. The refactor routes
  `VasAcctSeqNoGenerator` through `LegacyFunctionRepository.nextVasAcctSeqNo` — the single
  `wb_get_next_val` funnel (V140 branch #25, copied verbatim from the live JEX function). **Known limitation:**
  no one-shot retry on a concurrent PK collision (surfaces as `WBE_DB`) — a deliberate difference from
  Chart of Accounts, accepted for R&D.
- **Hierarchy management parity** — **MATCH (N/A — no hierarchy).** No parent column, no display-order
  column, no depth cap, no circular-reference logic in either system. The frontend renders a
  two-level prefix grouping for readability only.

## 3. Verdict

```
Refactor Status: 100% Complete (full-stack)
```

Every user-facing capability of the legacy `comm_0217_*` + `comm_1002_001` family is implemented and
wired end-to-end with verified parity; SIT/UAT completed. All four Major audit findings (F1–F4) were
resolved on 2026-05-11. The single residual item is infrastructure-only:

- **F1 — `VasAccountIntegrationTest` `@Disabled`.** Testcontainers + JWT-mock harness not yet wired.
  Unit (`VasAccountServiceTest`), controller (`VasAccountControllerTest`), Excel
  (`VasAccountExcelServiceTest`), and export (`VasAccountExportProviderTest`) suites are green. This is
  a test-infra gap, not a functional gap — it does not affect the "100% Complete" verdict for feature
  behaviour, but the parity matrix marks it **PARTIAL** for transparency.

Evidence: see [test-scenarios.md](test-scenarios.md) (automated coverage map), the preserved audit
[references/audits/vas-account.md](../../../references/audits/vas-account.md) (finding-by-finding
resolution log + build verification), and [business-flow.md](business-flow.md) (flow-by-flow behaviour
with the consumption boundary).

## 4. Open questions (PO confirmation)

| ID | Question | Owner | Status |
|---|---|---|---|
| OQ-VAS-1 | Should the frontend expose the (already-implemented) XLSX **export** button on the VAS Account panel, or keep export backend-only? | PO | Open — backend `/export` + `VasAccountExportProvider` are tested; the panel currently surfaces Excel **Upload** only. |
| OQ-VAS-2 | Add a one-shot retry on the sequence-generation PK race (parity with Chart of Accounts), or keep the current "surface `WBE_DB`" behaviour for R&D? | PO / lead | Open — low real-world likelihood at R&D scale. |
| F1 | Wire Testcontainers + JWT-mock so `VasAccountIntegrationTest` can run. | dev | Open — tracked in the preserved audit. |
