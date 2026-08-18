# Documentation Folder Rationalization — Deletion Impact Audit

> **⚠️ SUPERSEDED BY PROJECT DECISION (2026-06-23).** After this audit, the `archive/`, `planning/`, and
> top-level `migration/` folders under `wecms-api/docs` **and** `wecms-web/docs/archive/` were **all removed**
> by project decision — going further than this report's "KEEP planning / KEEP-as-archive" recommendations.
> The decision stands; this report is retained as the **pre-deletion impact analysis** (it correctly predicted
> the link impact — e.g. the ~92 inbound links that were then reconciled). Remaining docs structure (API):
> `architecture/ · audits/ · features/ · references/ · testing/`; (WEB): `architecture/ · bug-reports/ ·
> change-log/ · features/ · references/ · testing/`. The reconciliation is recorded in
> [documentation-remediation-closure-report.md](documentation-remediation-closure-report.md).
>
> **Read-only audit. No files were deleted, moved, or modified** (this report is the only new file).
> Scope: the `archive/`, `planning/`, and `migration/` folders under `wecms-api/docs` and `wecms-web/docs`.
> Method: a link-graph pass over **both** docs trees — inbound = links from *outside* a folder that resolve to a
> file *inside* it (these break if the file is deleted); outbound = links from inside the folder pointing out.
> Companions: [documentation-governance-roadmap.md](documentation-governance-roadmap.md) · [documentation-remediation-plan.md](documentation-remediation-plan.md) · [documentation-remediation-closure-report.md](documentation-remediation-closure-report.md).

## Folder presence & counts

| Folder | `wecms-api/docs` | `wecms-web/docs` |
|---|---|---|
| `archive/` | **33** md | **1** md (README-only shell) |
| `planning/` | **15** md | absent |
| `migration/` | **1** md (top-level stray) | absent |

*(Note: the substantive JEX→Spring migration docs live in `wecms-api/docs/references/migration/` — a **different** folder, NOT under review here. The top-level `docs/migration/` holds a single stray file.)*

---

## Folder verdicts

| Folder | Files | Inbound | Outbound | Verdict | Rationale |
|---|---|---|---|---|---|
| `wecms-api/docs/planning/` | 15 | **57** | 175 | **KEEP** | Active + heavily referenced (feature:9, audit:3, README:16). Open-questions, add-on plans, and the phase-1 plan are live working docs. |
| `wecms-api/docs/archive/` | 33 | 32 | 381 | **KEEP (as archive)** — trim optional | The designated read-only history store (banner README). Preserves the decision/audit trail (governance requirement). Most files have **0 inbound**; a superseded zero-inbound subset is an optional trim candidate (below). |
| `wecms-api/docs/migration/` | 1 | 3 | 6 | **ARCHIVE / RELOCATE (merge)** | Single stray file in a top-level folder that duplicates the `references/migration/` convention. Substantive (212 lines) + referenced 3×; relocate into `references/migration/` and drop the stray folder. |
| `wecms-web/docs/archive/` | 1 | **0** | 4 | **SAFE TO DELETE** | README-only empty shell; nothing archived; 0 inbound — deleting breaks nothing. |

---

## Per-document classification

### `wecms-api/docs/planning/` — 15 docs · KEEP (Active)

| Doc | Inbound | Class |
|---|---|---|
| `addon/sales-e-invoices-plan.md` | 16 | **Active** (feature + README + cross-refs) |
| `addon/operation-status-inquiry-plan.md` | 8 | **Active** |
| `addon/expenses-e-invoices-plan.md` | 6 | **Active** |
| `open-questions/documentation-follow-up.md` | 8 | **Active** (open items) |
| `phase-1/account-inquiry-and-transaction-history-plan.md` | 5 | **Active** |
| `open-questions/banking-reports-remaining-scope.md` | 4 | **Active** |
| `open-questions/database-follow-up.md` | 3 | **Active** |
| `open-questions/README.md` | 3 | **Active** (index) |
| `open-questions/wb-get-next-val-open-decisions.md` | 1 | **Active** (audit-referenced) |
| `roadmap/task-plan-common-backend-functional-seahorse.md` | 1 | **Active** |
| `README.md`, `phase-1/README.md` | 0 | **Active** (index, in-folder use) |
| `open-questions/c3-payment-result-rerun-deferred.md` | 0 | **Historical** (deferred decision record) |
| `phase-2/README.md`, `roadmap/README.md` | 0 | **Orphaned placeholder** (empty scaffold; low-value, optional delete) |

### `wecms-api/docs/archive/` — 33 docs · KEEP-as-archive (mostly Historical/Superseded)

| Group (files) | Inbound | Class | Note |
|---|---|---|---|
| `README.md` | 0 | **Active** (archive index) | The archive's own banner/index — keep if archive kept. |
| `settings-menu-hubs/*` (10) | 2 total (login-credentials 1, vas-account 1) | **Superseded** | Old per-screen menu hubs; content folded into `features/settings/` + its README. 8 of 10 have 0 inbound. Strongest trim candidates. |
| `dissolved-feature-indexes/*` (4) | 0 | **Superseded** | README stubs of dissolved `features/{accounting,approval,contacts,admin}/`. |
| `shared-pointers/*` (2) | 0 | **Superseded / Orphaned** | Old workspace-redirect stubs (bug-reports, change-log); live folders are at workspace root. |
| `features/sales-and-expense-einvoices.md` (1) | 1 (README) | **Superseded** | Combined spec; split into per-screen `features/addon-service/`. |
| `planning/phase-1/*` (6) | 11 total (chart-of-accounts-plan 4, transfer-history-plan 4 incl audit:2, others 0–1) | **Historical** | Completed sprint/task plans for shipped features; some still consulted by audits. **Retain.** |
| `planning/settings/*` (2) | 7 total (purpose-settings-task-plan 5 incl audit:1, erp 2) | **Historical** | Completed settings task plans; audit-referenced. **Retain.** |
| `reports/*` (7) | 1 (auth-common-domain-audit) | **Historical** | 2026-05/06 housekeeping/reorg/validation reports; 6 of 7 have 0 inbound. Audit-trail records. **Retain** (per "preserve historical audit records"). |

### `wecms-api/docs/migration/` — 1 doc

| Doc | Inbound | Class | Note |
|---|---|---|---|
| `operation-status-inquiry.md` | 3 (audit:1, feature:1, README:1) | **Historical (misplaced)** | 212-line migration narrative; self-labels "remains the narrative migration history" and points to `features/addon-service/operation-status-inquiry/migration-guide.md` (the canonical feature doc). Not a true duplicate, but **belongs in `references/migration/`**. |

### `wecms-web/docs/archive/` — 1 doc

| Doc | Inbound | Class | Note |
|---|---|---|---|
| `README.md` | 0 | **Orphaned placeholder** | Empty archive shell — no archived content. |

---

## True duplicates

**None (exact).** No byte-or-purpose duplicate pair found. The closest overlaps are **supersessions**, not duplicates:
- `archive/settings-menu-hubs/<x>.md` ↔ `features/settings/<x>.md` — the menu-hub is the *superseded* predecessor; content folded into the live feature docs. (Same topic, different/older content — supersession.)
- `docs/migration/operation-status-inquiry.md` ↔ `features/addon-service/operation-status-inquiry/migration-guide.md` — the stray is the *narrative history*; the feature doc is canonical. (Complementary, not duplicate.)
- `archive/features/sales-and-expense-einvoices.md` ↔ `features/addon-service/{sales,expenses}-e-invoices/` — superseded combined spec vs. per-screen split.

## Historical records that should remain
- `archive/planning/phase-1/*` + `archive/planning/settings/*` — completed plans for shipped features, still audit-referenced (transfer-history-plan, chart-of-accounts-plan, purpose-settings-task-plan). **Retain.**
- `archive/reports/*` — the structure/reorg/validation report trail (2026-05/06). **Retain** as audit history.
- `planning/open-questions/c3-payment-result-rerun-deferred.md` — deferred-decision record. **Retain.**
- `archive/README.md` — archive index/banner. **Retain** while the archive exists.

## Files that can be merged
- **`docs/migration/operation-status-inquiry.md` → `references/migration/operation-status-inquiry.md`** — relocate the lone stray so all migration narratives live under `references/migration/`; update its 3 referrers (`audits/operation-status-inquiry-parity.md`, the feature `migration-guide.md` + `README.md`). Eliminates the redundant top-level `migration/` folder. *(Merge/relocate — not delete.)*

## Files that can be deleted (candidates)
- **`wecms-web/docs/archive/README.md`** — 0 inbound, empty shell → delete the file and the empty `wecms-web/docs/archive/` folder. **Zero link impact.**
- **Optional archive trim** (only if the project chooses to stop retaining superseded scaffolding — consistent with the recent `db/archive` + `db/manual` removal and the "repository is not a complete historical archive" stance): `archive/settings-menu-hubs/*` (10), `archive/dissolved-feature-indexes/*` (4), `archive/shared-pointers/*` (2), `archive/features/sales-and-expense-einvoices.md` (1) = **17 superseded docs**. Requires repointing **3 inbound links first** (the 2 menu-hub README links + the 1 features-spec README link). **Do not** delete `archive/planning/*` or `archive/reports/*` — those are the audit-history trail to retain.

---

## Recommendations

### Recommended deletions
| Item | Files | Inbound to repoint | Link impact |
|---|---|---|---|
| `wecms-web/docs/archive/` (README shell) | 1 | 0 | **none** |
| *(optional)* `archive/settings-menu-hubs/` + `dissolved-feature-indexes/` + `shared-pointers/` + `archive/features/sales-and-expense-einvoices.md` | 17 | 3 | repoint 3 README links, then 0 broken |

### Recommended archival (keep, as-is, in archive)
- `wecms-api/docs/archive/planning/*` (8) and `archive/reports/*` (7) — historical/audit-trail; retain.
- `wecms-api/docs/archive/README.md` — retain as the archive index.

### Recommended retention (KEEP, active)
- **All of `wecms-api/docs/planning/`** (15) — active, 57 inbound (feature + audit + README). Deleting any breaks live references. *(The 2 empty phase-2/roadmap README scaffolds are the only optional trims, ~0 inbound.)*

### Recommended relocate/merge
- `wecms-api/docs/migration/operation-status-inquiry.md` → `references/migration/` (update 3 referrers); retire the stray top-level `migration/` folder.

---

## Expected link impact

| Action | Inbound links broken (if done naively) | After repointing |
|---|---|---|
| Delete `web/docs/archive/` | 0 | 0 |
| Relocate `docs/migration/` stray | 3 (audit/feature/README) | 0 (update the 3 referrers in the same change) |
| Optional archive-trim (17 superseded) | 3 | 0 (repoint 3 README links first) |
| **Delete `planning/`** ❌ | **57** | — **do not** |
| **Delete `archive/planning/` or `archive/reports/`** ❌ | 12 + audit-trail loss | — **do not** |

All "after-repointing" outcomes preserve the current **100% doc-to-doc link health** — provided referrers are updated in the same change. (Outbound links from deleted files simply disappear and break nothing else.)

## Estimated reduction in document count

- **Conservative (safe, near-zero risk):** **−1 doc** (`web/docs/archive/README.md`) **+ −1 folder** (consolidate the `docs/migration/` stray into `references/migration/`; net doc count unchanged, one fewer top-level folder). → ~**1 doc, 2 folders** removed.
- **Moderate (trim superseded archive scaffolding, repoint 3 links):** additional **−17 docs** (settings-menu-hubs 10 + dissolved-feature-indexes 4 + shared-pointers 2 + combined einvoice spec 1). → **~18 docs** total.
- **Not recommended:** deleting `planning/` (−15, breaks 57 links) or `archive/planning`+`archive/reports` (−15, destroys audit trail).

**In scope:** 50 docs (api archive 33 + planning 15 + migration 1 + web archive 1). **Recommended net reduction: 1 (conservative) to ~18 (moderate trim) docs**, with `planning/` and the archive's planning/reports history retained.
