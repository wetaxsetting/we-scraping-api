# CLAUDE.md Reconciliation Audit — 2026-06-23

> _**Documentation-governance artifact — not a feature specification.** This audit reconciles the `CLAUDE.md` governance/rule files against the current codebase. See the [audits index](../README.md) for where this fits._

**Auditor:** Claude (code-as-source-of-truth reconciliation)
**Targets:** `wecms-api/CLAUDE.md` · `wecms-web/CLAUDE.md`
**Compared against:** current implementation code + the six reconciled feature-doc trees (Settings, Add-on Service, Payment, Auth, Common, Banking — all audited 2026-06-23).
**Companions:** [claude-source-index.md](claude-source-index.md) (claim → verified code ref) · [claude-gap-analysis.md](claude-gap-analysis.md) (gap matrix + owners).
**Gap vocabulary:** MATCH · APPROVED DEVIATION · DEAD LEGACY · PARTIAL · MISSING.

---

## Executive summary

The two CLAUDE.md files are **structurally sound and mostly accurate on rules** (naming, envelope, View Setting,
sort, security, JWT, product-type, Excel-export *contract*), but they carry a consistent class of **stale status
claims**: features that have since **shipped** are still marked *stub / mock / not-implemented / open bug*. This is
the laggard tail of six feature-doc audits — each of those passes verified its own docs against code and explicitly
deferred the matching CLAUDE.md row as "project-instruction config, fix in the next CLAUDE.md pass." This is that
pass; it records the corrections without applying them (CLAUDE.md is not edited — see Scope).

**Highest-value findings (all verified against code):**

1. **Notification is documented as "Not implemented" but is live** — `domain/payment/notification` (4 endpoints on `BLTB_*`). CLAUDE.md has no accurate notification row at all (CA-3).
2. **`EXPENSE→'E'` is called an "open code bug"; the code maps `'B'`** and rejects `'E'` with `WBE2002` (CA-1).
3. **Operation-Status "Save is a stub"; the `create()` endpoint exists** (`OperationStatusController:100`) (CA-2).
4. **Migration table says "V155 is the highest live migration"; the real max is V248** (158 files) (CA-6).
5. **wecms-web "What works today" lists ~4 routes; 43 dashboard routes ship**, and Account Inquiry is wired to the real API, not mock (CW-1, CW-2).
6. **Change Password is "Not implemented" on both sides; it is implemented** (`AuthController:72` + `ChangePasswordDialog.tsx`) (CA-8, CW-5).
7. **Excel-export "wired" table lists 3 screens; ~20 providers are registered** (CA-4).
8. **Smart-Organizer note flags the feature docs as stale "scaffold" — those docs were already corrected**; the CLAUDE.md note is now the stale one (CA-5).

**Correctly stated (no change):** Dashboard, refresh tokens, 5-failure auto-lock, password-change-history reset,
and the absent frontend test baseline are genuinely MISSING and accurately marked. The wecms-web UPPER_SNAKE
"holdovers cleared 2026-06-07" section matches code. The two CLAUDE.md rule sets for envelope / naming / sort /
View Setting / security are accurate.

**No false positives:** every stale claim corresponds to a feature confirmed present in code; no genuine gap was
mistaken for staleness.

---

## Files requiring updates

| File | Sections needing correction | Severity |
|---|---|---|
| `wecms-api/CLAUDE.md` | Feature Status table (einvoice CA-1, operation CA-2, notification CA-3, smartorganizer CA-5, "Frontend not verified" caveats CA-7); "Endpoints with Excel export wired" table (CA-4); migration table "V155 highest" + V156–V248 (CA-6); Project Overview change-password mention (CA-8) | High (status accuracy) |
| `wecms-web/CLAUDE.md` | Project Overview "What works today" / "Implemented pages" (CW-1); Feature Status table (account-inquiry CW-2, approval-box CW-3, Placeholders CW-4, change-password CW-5, print/export CW-6, DateRangePicker CW-7); Known Issues "Mock data" (CW-9); Notes-for-future menu-resolution (CW-8) | High |
| `wecms-api/src/.../TransferHistoryController.java` (code, NOT in scope) | Javadoc line 32 vs `@PreAuthorize` line 54 (CC-1) | Med — dev fix |

---

## Recommended corrections (for a maintainer to apply in a dedicated CLAUDE.md edit)

**wecms-api/CLAUDE.md**
1. **einvoice row** → remove the "open code bug to file"; state `resolveDocType`: `SALES/I→'I'`, `EXPENSE/BILL→'B'`, else `WBE2002` (`EInvoiceService.java:205-217`). Keep the IR-4 client-side status-filter caveat (still true).
2. **operation row** → drop "Save is a stub"; `POST /api/v1/addon/operation-status` (`create`) persists with a `(BIZ_KEY,TAXCODE)` dup guard.
3. **"Not implemented" line** → remove **notifications**; add a Feature-Status row: `domain/payment/notification` — `GET /notifications`, `GET /unread-count`, `PUT /{id}/read`, `PUT /read-all` (BLTB-backed; ports `comm_0192_007`; General `TYPE='G'` not ported). Keep dashboard / refresh-token / auto-lock as Not implemented.
4. **Excel-export "wired" table** → replace the 3-row list with the ~20 registered screenIds (or point to [common-source-index.md](common-source-index.md) §3 / the `ExportableScreenRegistry`), and reword "First wired screen" → "first; ~20 now wired".
5. **smartorganizer row** → drop "feature docs still label scaffold — stale"; state implemented full-stack (2 controllers + 2 services + V172–V174); the "scaffold" wording survives only in the preserved `legacy-spec-*.md`.
6. **migration table** → stop calling V155 "the highest live migration"; either extend the curated table past V243 or replace the "highest" line with "current max = **V248** (158 files; see `db/migration/`)".
7. **"Frontend not verified this pass" caveats** → drop for `login-credentials`, `einvoice`, `operation`, `vas-account`, `own-account-transfer`, `transfer-history` (verified frontend-wired); keep only where genuinely unverified.
8. **Project Overview** → add `POST /api/v1/auth/change-password` to "What works today".

**wecms-web/CLAUDE.md**
9. **Project Overview** → rewrite "What works today" / "Implemented pages" to reflect the 43 shipped routes (banking full, payment list+detail ×5, addon ×3, reports ×4); note Settings is a modal, not a route.
10. **Feature Status table** → account-inquiry = live API (not mock); approval-box = wired (confirm depth); delete/rewrite the "Placeholders" row; change-password = implemented (`ChangePasswordDialog.tsx`); print/export/list = wired on adopted pages via `PageActionButtons`/`printReport`/`useExcelExport`; DateRangePicker = `InteractiveDateRangePicker` exists.
11. **Known Issues** → scope "Mock data in feature pages" to the specific unmigrated pages, not a blanket claim.
12. **Notes for future** → mark menu/permission resolution **done** (driven by `useNavigation` + `/api/v1/navigation`).

**Code tree (NOT a CLAUDE.md edit — separate dev change)**
13. **CC-1** → fix `TransferHistoryController` javadoc (line 32) to match the `@PreAuthorize` menu-authz gate (line 54).

---

## Remaining PO decisions

1. **Migration-table policy** (CA-6) — verbose V1–V248 table vs curated-notable list + "max = V248" pointer.
2. **Frontend-verification caveats** (CA-7 / CW-1..6) — accept the prior passes' "frontend-wired" verdicts so the caveats drop wholesale, or commission a per-module re-verification sweep.
3. **Notification Feature-Status placement** (CA-3) — confirm a `domain/payment/notification` row is added (physically under payment, conceptually Common — Common audit G1).

## Remaining developer follow-ups

1. **CC-1** — `TransferHistoryController` javadoc correction (code-tree).
2. Genuine MISSING items remain on the backlog and are correctly marked: dashboard, refresh-token, 5-failure auto-lock, password-change-history reset, frontend `.test.*` baseline. No doc change required.

---

## Scope & method

- **Documentation only.** No CLAUDE.md, no feature doc, and no application code was modified. The three audit
  artifacts are the entire change set. CLAUDE.md edits are deferred to a maintainer per the recommendations above.
- **Code is the source of truth.** Every CA/CW verdict carries a `file:line` (or grep-confirmed) counter-reference
  in [claude-source-index.md](claude-source-index.md). Feature docs were used as the corrected cross-check baseline,
  not as primary evidence.
- **Three categories separated** per the request: *stale documentation* (CLAUDE.md prose — CA/CW), *stale comments*
  (code-tree javadoc — CC-1), *genuine implementation gaps* (correctly-stated MISSING — kept, not flagged).

---

## Files created this audit

1. `wecms-api/docs/audits/claude-docs-audit.md` *(this file)*
2. `wecms-api/docs/audits/claude-gap-analysis.md`
3. `wecms-api/docs/audits/claude-source-index.md`

*Last verified: 2026-06-23.*
