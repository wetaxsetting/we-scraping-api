# CLAUDE.md Reconciliation — Gap Analysis

> _**Documentation-governance artifact — not a feature specification.** This audit reconciles the `CLAUDE.md` governance/rule files against the current codebase. See the [audits index](README.md) for where this fits._

**Audit date:** 2026-06-23
**Companions:** [claude-docs-audit.md](claude-docs-audit.md) · [claude-source-index.md](claude-source-index.md)
**Convention:** every row verified against the working tree. Gap vocabulary is fixed:

| Term | Meaning |
|---|---|
| **MATCH** | CLAUDE.md, feature docs, and code agree. |
| **APPROVED DEVIATION** | Intentional divergence by a recorded decision. |
| **DEAD LEGACY** | References a removed/superseded artifact; must not be stated as live. |
| **PARTIAL** | Partly true — some sub-claims hold, others stale. |
| **MISSING** | Correctly states something is not implemented (no gap). |

The task asks to separate three categories. They are tagged in the **Kind** column:
**stale-doc** (CLAUDE.md prose) · **stale-comment** (code-tree javadoc) · **impl-gap** (genuine missing feature).

---

## A. wecms-api/CLAUDE.md

| ID | Gap | Class | Kind | Owner |
|---|---|---|---|---|
| CA-1 | Feature Status `domain/einvoice` calls `EXPENSE→'E'` an "open code bug to file"; code maps `EXPENSE`/`BILL`→`'B'`, rejects `'E'`→`WBE2002` (`EInvoiceService.java:205-217`). | DEAD LEGACY → should read MATCH | stale-doc | maintainer (CLAUDE.md) |
| CA-2 | Feature Status `domain/operation` says inquire-dialog Save is "a stub (no backend upsert endpoint yet)"; `OperationStatusController.java:100-103 create()` persists. | PARTIAL → MATCH | stale-doc | maintainer |
| CA-3 | Feature Status "Not implemented" line includes **notifications**; `domain/payment/notification` is live (4 endpoints, 5 `BLTB_*` tables). No accurate notification row exists in the table. | DEAD LEGACY (contradiction) | stale-doc | maintainer |
| CA-4 | "Endpoints with Excel export wired" table lists **3**; ~20 providers are registered (26 provider files). | PARTIAL (understated) | stale-doc | maintainer |
| CA-5 | `smartorganizer` row flags the *feature docs* as "still label discovery scaffold — stale"; those docs were corrected in the 2026-06-23 Banking audit. The CLAUDE.md note is now the stale party. | DEAD LEGACY (self-referential) | stale-doc | maintainer |
| CA-6 | Migration table calls **V155** "the highest live migration" and stops at V243; actual max is **V248** (158 files). | PARTIAL (numbering drift) | stale-doc | maintainer |
| CA-7 | Many Feature Status rows say "Frontend not verified this pass"; later passes verified `login-credentials`, `einvoice`, `operation`, `vas-account`, `own-account-transfer`, `transfer-history` as frontend-wired. | PARTIAL | stale-doc | maintainer |
| CA-8 | Project Overview "What works today" omits `change-password`, which is implemented (`AuthController.java:72`). | PARTIAL (understated) | stale-doc | maintainer |
| CA-9 | "Deferred inside auth: password-change-history reset" — still accurate (the *reset* sub-feature is not built). | MISSING | impl-gap | dev |
| CA-10 | Dashboard / refresh-token / 5-failure auto-lock listed Not implemented — accurate. | MISSING | impl-gap | dev |

## B. wecms-web/CLAUDE.md

| ID | Gap | Class | Kind | Owner |
|---|---|---|---|---|
| CW-1 | "What works today" / "Implemented pages" list ~4 routes; **43** dashboard routes ship (banking/payment/addon full). | PARTIAL (massively understated) | stale-doc | maintainer |
| CW-2 | `/banking/account-inquiry` = "Shell + mock data, rows hardcoded"; it consumes the live `/banking/accounts` API with View Setting + sort + export. | DEAD LEGACY → MATCH | stale-doc | maintainer |
| CW-3 | `/payment/approval-box` = "Shell + mock data"; route + backend exist (frontend wiring depth per Payment docs). | PARTIAL | stale-doc | maintainer |
| CW-4 | "Placeholders" row lists transaction-history / FCY / internal-transfer / reports / addon / settings as "disabled until the route exists"; all exist (Settings is a modal, not a route). | DEAD LEGACY → MATCH | stale-doc | maintainer |
| CW-5 | "Change password dialog — Not implemented; handler empty"; `ChangePasswordDialog.tsx` + backend exist. | DEAD LEGACY → MATCH | stale-doc | maintainer |
| CW-6 | "Print / Export / List view — Not implemented"; wired on Account Inquiry (`printReport`, `useExcelExport`, View Setting). | PARTIAL | stale-doc | maintainer |
| CW-7 | "DateRangePicker (real picker) — Not implemented"; `InteractiveDateRangePicker.tsx` exists (basic `DateRangePicker` still display-only on some pages). | PARTIAL | stale-doc | maintainer |
| CW-8 | "Notes for future: drive menu from backend payload when it returns a tree" — already done (`useNavigation` + `/api/v1/navigation`). | DEAD LEGACY (note) | stale-doc | maintainer |
| CW-9 | Known Issues "Mock data in feature pages" — true only for the few unmigrated pages; misleading as a blanket statement. | PARTIAL | stale-doc | maintainer |

## C. Stale code comments (NOT CLAUDE.md — flagged for a developer, out of doc scope)

| ID | Gap | Class | Kind | Owner |
|---|---|---|---|---|
| CC-1 | `TransferHistoryController.java:32` javadoc "JWT is the only gate" contradicts its own `@PreAuthorize` (`:54`). | PARTIAL | stale-comment | dev |
| CC-2 | (No action) `AuthService.java:82` "password-change-history reset" deferred — accurate/narrow. | MATCH | stale-comment (n/a) | — |

---

## Cross-cutting findings

### 1. CLAUDE.md is the last laggard after six feature-doc passes
The Settings / Add-on / Payment / Auth / Common / Banking audits each verified their feature docs against code
and **explicitly flagged** the matching CLAUDE.md rows as out-of-scope ("CLAUDE.md is project-instruction config,
fix in next CLAUDE.md pass"). This audit is that pass. Every CA/CW row above traces to a finding a prior pass
already recorded — none is new behavior, only un-applied corrections.

### 2. The dominant theme is "implemented-but-still-marked-TODO"
CA-1/2/3, CW-1..6 all describe features that **shipped** but CLAUDE.md still marks stub / mock / not-implemented.
A reader trusting CLAUDE.md would under-estimate the system's completeness and might re-build existing features.
This is the inverse of a normal doc risk and the highest-value correction set.

### 3. Migration numbering drift (CA-6) is a maintenance smell
The "V155 is highest" claim predates V156–V248. The migration table should either be trimmed to a curated
"notable migrations" list with an explicit "current max = V248 (see `db/migration/`)" footer, or stop claiming a
"highest" at all — the per-file headers are the real record.

### 4. Genuine gaps are correctly stated (no false-positives)
Dashboard, refresh-token, 5-failure auto-lock, password-change-history reset, and the frontend test baseline are
**accurately** marked not-implemented in CLAUDE.md. Do not "fix" these — they are real MISSING items, owned by dev.

---

## Remaining PO decisions

1. **Migration-table policy (CA-6)** — keep the verbose V1–V248 table, or switch to a curated-notable list + a
   "current max = V248" pointer? (affects how much CLAUDE.md the team maintains by hand).
2. **Frontend-verification caveats (CA-7, CW-1..6)** — confirm the modules a prior pass marked "frontend-wired"
   are accepted as such so the caveats can be dropped wholesale (vs. a per-module re-verification sweep).
3. **Notification placement (CA-3)** — confirm the Feature-Status table should carry a `domain/payment/notification`
   row (it physically lives under payment but is a Common header-bell feature — see Common audit G1).

## Remaining developer follow-ups (code-tree, outside this doc pass)

1. **CC-1** — fix `TransferHistoryController` javadoc (line 32) to match its `@PreAuthorize`.
2. **CA-9 / CA-10** — the genuine MISSING items remain on the backlog (dashboard, refresh-token, auto-lock,
   pw-change-history reset, FE test baseline). No doc change needed; tracked here for completeness.

## Note on remediation

This audit is **documentation-only** and does **not** edit CLAUDE.md (per the task + the wecms-api Scope Guard,
which treats CLAUDE.md as project-instruction config not touched during doc tasks). The recommended corrections
are enumerated in [claude-docs-audit.md](claude-docs-audit.md) §"Recommended corrections" for a maintainer to
apply in a dedicated CLAUDE.md edit with explicit go-ahead.
