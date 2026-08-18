# Documentation Drift Remediation Plan — Phase 3A (+3B execution, +3C deployment-context alignment)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../references/database/flyway-migration-baseline.md) §5.

> Derived from the Phase-3 truth audit. Implementation code is the source of truth for every item. Findings are grouped
> P1 → P4. Each carries exact file(s), issue summary, the authoritative code, estimated effort, and risk.
> Sources: [documentation-truth-audit.md](documentation-truth-audit.md) · [authorization-doc-drift.md](authorization-doc-drift.md) · [database-doc-drift.md](database-doc-drift.md) · [api-contract-drift.md](api-contract-drift.md).
>
> **Status:** Phase **3B** executed the P1 doc corrections (P1-1 + the P1-2 version edits). Phase **3C** (2026-06-23)
> re-verified P1-1, **reclassified P1-2** under the project's real deployment model, and added the navigation-seed
> follow-up. See [§Phase-3C verification results](#phase-3c-verification-results-2026-06-23) at the end.
>
> **Scope note.** Items whose *fix* lands in the code tree (Javadoc edits, security-filter wiring) are tagged
> **[CODE-TREE]** — documentation-truth liabilities outside the docs workstream, needing dev/PO ownership.

---

## Deployment-context premise (read first — governs DB-related classifications)

This refactor **reuses the existing legacy database**. The deployment model is:

- UAT and PROD **already contain** the legacy schema, legacy functions, and legacy seed data.
- Refactor work **primarily ports application code**; existing table DDL is generally **not** recreated through Flyway,
  and existing DB functions are generally **reused** from the legacy system.
- **Flyway is *not* the source of truth for all legacy database objects.** The source of truth is the existing legacy /
  production **schema, functions, and seed data**.
- Therefore **a missing file under `db/migration` is *not*
  automatically a defect.** Developers must **not** assume a missing Flyway file has to be created; legacy objects are
  reused as-is.

Any finding below that is purely "a version number / a Flyway file doesn't match" is therefore a **documentation-clarity**
issue, **not** a runtime or deployment risk. This premise is why **P1-2 was reclassified in Phase 3C** (see below).

---

## P1 — Critical documentation defects

*Actively wrong content that will misdirect an implementer or operator.*

> **P1 status (2026-06-23):** **P1-1 — ✅ DONE & VERIFIED** (Phase 3B executed; Phase 3C re-verified — see end).
> **P1-2 — RECLASSIFIED** to *APPROVED DEVIATION / DEPLOYMENT CONTEXT NOTE* (no longer a critical defect; the
> version-number edits were still applied for clarity). Its full entry now lives in
> [§Approved deviations / deployment-context notes](#approved-deviations--deployment-context-notes).

### P1-1 — Banking Daily Financial (CMC) docs describe a retired read path — ✅ DONE & VERIFIED
- **Files:**
  - `wecms-api/docs/features/banking/reports/daily-financial/database-mapping.md` (:135, :150)
  - `wecms-api/docs/features/banking/reports/daily-financial/migration-guide.md` (:6-13, :77-122 — esp. :121-122)
  - `wecms-api/docs/features/banking/reports/daily-financial/legacy-spec.md` (:10, :38, :43)
  - `wecms-api/docs/features/banking/reports/daily-financial/fix-plan-2026-06-04.md` (:15-43)
  - `wecms-api/docs/features/banking/reports/daily-financial/parity-verification-report.md` (:20, :92-115, :138)
  - `wecms-web/docs/features/banking/reports/daily-financial/README.md` (:16), `…/legacy-spec.md` (:8, :21)
- **Issue:** All describe the CMC report as a "Strategy B" Java live-compute over `ACCT_MSTR`/`ACCT_TRNS_HSTR`, and state `rept_0015_001_r001_rec1` is "not called." `migration-guide.md:121-122` explicitly instructs *"Do not add `rept_0015_001_r001_rec1` functions"* — the opposite of current behaviour.
- **Source of truth:** `ReportService.dailyFinancial` calls `rept_0015_001_r001_rec1` directly via `DailyFinancialCmcFunctionRepository` (`wecms-api/src/main/java/com/kosign/wecms/domain/banking/service/ReportService.java:147-225`) — 2026-06-14 pivot.
- **Effort:** ~0.5 day (rewrite to "calls DB function directly"; reframe Strategy-B prose as superseded, don't delete history).
- **Risk if unchanged:** **HIGH** — a developer porting/maintaining the report would follow the migration guide and break it, or re-implement a path the code abandoned.
- **✅ Executed (Phase 3B) & verified (Phase 3C):** 11 files corrected (9 backend + 2 web). All active docs now state the
  report calls `rept_0015_001_r001_rec1` directly via `DailyFinancialCmcFunctionRepository`; the harmful
  "Do not add … `rept_0015_001_r001_rec1`" instruction was replaced with "**IS relied upon — do not re-express in Java**";
  Strategy-A/B + the 2026-06-04 P0 records were reframed as superseded (audit history preserved). Verification grep
  confirms **0 active "Not called / do not add" instructions** remain. See [§Phase-3C verification results](#phase-3c-verification-results-2026-06-23).

> ### P1-2 — moved
> **P1-2 (common migration-version numbers) was reclassified in Phase 3C** from *Critical defect* to
> *APPROVED DEVIATION / DEPLOYMENT CONTEXT NOTE*. The full, updated entry is in
> [§Approved deviations / deployment-context notes](#approved-deviations--deployment-context-notes) below.

---

## P2 — High-value corrections

### P2-1 — Missing referenced audit file (dangling canonical reference) — ✅ RESOLVED 2026-06-23 (Option B)
- **Files (were dangling links to a missing target):** `wecms-api/docs/features/settings/login-credentials/README.md:147`, `…/migration-guide.md:5`, `…/source-code-index.md`; plus `…/login-credentials.md` and the web `login-credentials/README.md` + `login-credentials.md`.
- **Issue (was):** `wecms-api/docs/audits/login-credentials-legacy-vs-refactor-audit.md` did not exist.
- **Decision: Option B (create the file).** Investigation confirmed `login-legacy-compatibility-2026-06-10.md` is **NOT** the successor — it audits *auth login + password (SHA-256) coexistence* (USER_INFM, bootstrap removal), whereas the missing file is the *Settings → Login Credentials feature* audit (bank/GDT certs, pgcrypto cipher §7, `ACCT_AUTH_INFO`, GDT verify-before-save). The login-credentials README links **both** as distinct (`:147` = this audit, `:149` = the password-coexistence audit), proving they are separate documents.
- **Action taken:** authored [login-credentials-legacy-vs-refactor-audit.md](../legacy-parity/login-credentials-legacy-vs-refactor-audit.md) in the existing audit format (header block → executive summary → numbered §1–§11 incl. §7 cert cipher + §11 DB-object inventory → gaps table → bottom line), consolidating the per-row parity matrix from `login-credentials/refactor-gap-analysis.md` and cross-linking the feature folder + the password-coexistence audit. All 5 dangling references now resolve. No application code changed; no restructuring; the per-row gap-analysis preserved as the authoritative matrix.

### P2-2 — Undocumented unauthenticated endpoints
- **Files:** no leaf doc exists; document under `wecms-api/docs/features/payment/` (e.g. a dispatch-callbacks interface note) **and** the authz allow-list.
- **Issue:** `POST /api/v1/payment/dispatch/callbacks/*` (EFS→WeCMS bulk exec/reception) have no `@PreAuthorize` and no doc; `CallbackIpWhitelistFilter` exists but is not wired.
- **Source of truth:** `BulkExecutionResultController.java:23-34`, `BulkReceptionResultController`, `TODO(B7)` at `:20-21`.
- **Effort:** doc note ~0.25 day; **[CODE-TREE]** filter wiring is a separate dev task.
- **Risk if unchanged:** **MEDIUM-HIGH** — an unauthenticated POST surface invisible in docs; a real security item before any non-local deploy.

### P2-3 — Stale source-code Javadoc asserting false security/crypto posture **[CODE-TREE]**
- **Files / claims:**
  - `wecms-api/.../config/SecurityConfig.java:35-38` — "always writes BCrypt … transparent upgrade" → actually SHA-256, `upgradeEncoding()` returns false.
  - `wecms-api/.../common/security/JwtAuthenticationFilter.java:53-55` — "advisory, no `@EnableMethodSecurity`" → it is enabled (`SecurityConfig.java:24`).
  - `wecms-api/.../domain/setting/logincredentials/.../GdtCertInfo.java:26` (+ `BankCertInfo`) — "AES-GCM" → legacy pgcrypto AES-256-CBC (`BankCertCryptoService.java:20-27`).
- **Source of truth:** `WecmsPasswordEncoder`, `SecurityConfig.java:24`, `BankCertCryptoService.java:20-27`.
- **Effort:** ~0.25 day (code-tree Javadoc edits).
- **Risk if unchanged:** **MEDIUM** — a code reader believes the system uses BCrypt/AES-GCM/advisory-only authz; all three are false and security-relevant.

---

## P3 — Medium-value corrections

### P3-1 — Settings: `AcctAuthInfo` PK column ordering
- **File:** `wecms-api/docs/features/settings/bank-account/database-mapping.md` (:23-26, :34)
- **Issue:** documents PK as `(BIZ_KEY, BANK_CD, ACCT_NO, USER_ID, AUTH_TYPE)`; code orders `(BIZ_KEY, USER_ID, BANK_CD, ACCT_NO, AUTH_TYPE)`.
- **Source of truth:** `AcctAuthInfoId.java:22-26`. **Effort:** <15 min. **Risk:** LOW (same key set; cosmetic but factually wrong).

### P3-2 — Settings: contacts wrong error-code citation
- **Files:** `wecms-api/docs/features/settings/contacts/interface-specification.md:175`; **[CODE-TREE]** `wecms-web/lib/api/contacts.ts:34` Javadoc.
- **Issue:** cites `WBE2040`; code throws `WBE_CONTACTS_NOT_FOUND`.
- **Source of truth:** contacts service/controller. **Effort:** <15 min. **Risk:** LOW-MEDIUM (wrong error contract in a shipped interface-spec).

### P3-3 — Banking: HS "mock fallback" mislabel
- **Files:** `wecms-web/docs/features/banking/reports/daily-financial/README.md` (:17, :71), `…/component-index.md` (:28), `…/api-consumption.md` (:17)
- **Issue:** claim the HS report has a mock fallback; the HS client has none.
- **Source of truth:** `wecms-web/components/banking/reports/.../DailyFinancialHsClient.tsx:20, :31-42` ("No mock fallback"). **Effort:** ~15 min. **Risk:** LOW.

### P3-4 — Daily-cash residual "live recompute" prose (contradicts sibling docs)
- **Files:** `wecms-api/docs/features/banking/reports/daily-cash/functional-specification.md` (:5-10), `…/source-code-index.md` (:73-75), `…/test-scenarios.md` (:43, :81, :95), `…/refactor-gap-analysis.md` (:68-257)
- **Issue:** still document `DailyCashRecomputeRepository`/`CurrencyAccumulator` as the active source; those are `@Deprecated`/bypassed. The sibling `legacy-spec.md`/`refactor-parity-verification.md` already say so — the docs contradict each other.
- **Source of truth:** `DailyCashReportService` calls `rept_0900_001_rec1..4` via `DailyCashReportFunctionRepository` (`service/DailyCashReportService.java:66-153`). **Effort:** ~0.25 day. **Risk:** MEDIUM (internal contradiction).

### P3-5 — Stale CLAUDE.md endpoint counts & behavioural claims **[CODE-TREE / CLAUDE.md]**
- **Files / claims:**
  - `wecms-api/CLAUDE.md` — chart-of-accounts "6 endpoints" (actual 9), contacts "six" (actual 17), "4-level hierarchy cap" (write cap is `MAX_HIERARCHY_DEPTH=3`); also `ChartOfAccountsController.java:31` ("six"), `ChartAcctInfm.java:19` ("5"), `wecms-web/lib/api/chartOfAccounts.ts:88-89` ("levels 1..4").
  - Contacts "global audit aspect handles `AUDIT_LOGS_INFM`" (`ContactsService.java:84-86` + CLAUDE.md) — **no such aspect exists**; contacts audit is not wired (gap-analysis correctly says PARTIAL).
- **Source of truth:** the controllers; `gap-analysis.md` for contacts audit status. **Effort:** ~0.5 day. **Risk:** MEDIUM (counts + a claimed audit subsystem that doesn't exist).

### P3-6 — Stale `@Subselect`/key-gen/masking Javadoc **[CODE-TREE]**
- **Files / claims:**
  - `PurposeInUseRepository.java:12` ("v_purpose_in_use view (V81)") and `FirmBankingSettingViewRepository.java:9-10` ("V_FIRM_BANKING_SETTING/V59/V64") — actually `@Subselect`, no real view.
  - `BusiVasAcctInfm.java:54-57` + `VasAccountService.java:55-58` — "Java MAX+1 replaces wb_get_next_val" → actually funnels via `wb_get_next_val` (`VasAcctSeqNoGenerator.java:30`, `LegacyFunctionRepository.java:274-276`).
  - `FirmBankingSettingRow.java:13-15` — "service-layer masked `wdrlActNo`" → ships cleartext (FE-only masking).
  - `BusiUsagSetInfm.java:17, :80` — "V80" → actual V35.
- **Effort:** ~0.5 day (code-tree). **Risk:** MEDIUM (funnel-rule + view-vs-subselect confusion misleads future DB work).

---

## P4 — Low-value cleanup

| ID | File(s) | Issue | Source of truth | Effort | Risk |
|---|---|---|---|---|---|
| P4-1 | `wecms-web/docs/features/auth/login/api-consumption.md:101-102` | login `toStoredUser` table lists `all_menus`/`allowed_menu_cds` (login response has no menu fields) | `LoginResponse.java`; `client.ts:120,130-131` | <15 min | LOW (self-qualified in Notes col) |
| P4-2 **[CLAUDE.md]** | `wecms-web/CLAUDE.md` §Route protection | describes client `tokenStorage` guard + `?preview=1`; actual is `proxy.ts` edge middleware | `wecms-web/proxy.ts`, `app/(dashboard)/layout.tsx:23-27`; `login-auth.md:39-40` already correct | ~15 min | LOW |
| P4-3 **[CLAUDE.md]** | `wecms-api/CLAUDE.md` Auth-Rules | "JWT filter → WBE1009" for unauthenticated path | `AuthService.java:101` (`WBE1401`); `jwt-authentication/functional-specification.md:145-148` already correct | <15 min | LOW |
| P4-4 **[CODE-TREE]** | `CardTransactionsController.java:90-96`, `PurposeSettingsController.java:60-63`, `VasAccountController.java:47-52` | Javadoc says authz "deferred/none" directly next to a live `@PreAuthorize` | the live annotations in the same files | ~15 min | LOW-MEDIUM (misreads as unguarded) |
| P4-5 | `wecms-api/docs/features/payment/README.md:107` (+ no interface-spec) | `BlockedCharsRuleController` lookup undocumented as allow-list exception | `BlockedCharsRuleController.java:40-43` | ~10 min | LOW |
| P4-6 | ~~`wecms-api/docs/archive/features/sales-and-expense-einvoices.md`~~ | malformed link artifact | **MOOT** — the `archive/` docs folder was removed 2026-06-23 (project decision); the file no longer exists | — | resolved-by-deletion |

---

## Recommended execution order

1. ~~**P1-1 Banking Daily Financial CMC rewrite**~~ — ✅ **done (Phase 3B)**, verified (Phase 3C).
2. ~~**P1-2 Common migration-version sweep + root `wecms-api/CLAUDE.md`**~~ — ✅ version edits **done (Phase 3B)**; **reclassified (Phase 3C)** to a deployment-context note (DC-1) — not a defect.
3. **Quick-win batch** (P3-1, P3-2, P3-3, P4-1, P4-3, P4-6) — bundle all <30-min doc nits into one MR.
4. **P3-4 daily-cash residual prose** + **P3-5 CLAUDE.md counts/contacts-aspect** — medium content edits.
5. **P2-1 missing login-credentials audit** — raise the PO decision in parallel (decision-gated, not code-blocked).
6. **Code-tree batch** (P2-3, P3-6, P4-2, P4-4) — single dev MR scrubbing stale Javadoc + `wecms-web/CLAUDE.md`.
7. **P2-2 dispatch-callback** — doc note now; security wiring tracked as a dev/PO task.

## Quick wins (<30 min each)
- P3-1 bank-account PK ordering (1 line)
- P3-2 contacts `WBE2040` → `WBE_CONTACTS_NOT_FOUND`
- P3-3 HS "mock fallback" label (3 short edits)
- P4-1 auth login `all_menus` table rows
- P4-3 CLAUDE.md `WBE1009` → `WBE1401` note
- P4-6 archive malformed link
- (code-tree quick wins) P4-2, P4-4, P3-6 view-Javadoc one-liners

## High-risk inaccuracies (do not defer)
- **P1-1** — ✅ *Resolved (Phase 3B).* `migration-guide.md` no longer instructs avoiding the function the code depends on.
- ~~**P1-2** — wrong migration versions.~~ **Reclassified (Phase 3C):** documentation-clarity only, **not** a runtime/deployment risk under the legacy-DB-reuse model (Flyway is not the source of truth for legacy objects). See [§Approved deviations](#approved-deviations--deployment-context-notes) — **DC-1**.
- **P2-2 / authz AZ-6** — unauthenticated `/payment/dispatch/callbacks/*` undocumented; security blind spot. *(Still open.)*
- **P2-3** — Javadoc claims BCrypt + AES-GCM; real system is SHA-256 + AES-256-CBC (false crypto posture in the code). *(Still open.)*

## Items that should remain intentionally unchanged
- **addon-service IR-4 client-side invoice-status filter** — honestly documented as a deferred gap (`sales-e-invoices/refactor-gap-analysis.md`, `lib/api/addon.ts:64-77`). Not drift.
- **Banking HS wire type bridged by `projectHsRows`** — acknowledged pending in `daily-financial/api-consumption.md:17`. Leave until the wire-type flip MR.
- **Phase-2 redirect stubs** (`features/banking/banking-*.md`, `features/payment/payment-*.md`) — intentional redirects to `docs/audits/`; keep.
- **Legacy WSVC ids in JEX file:line citations, migration-traceability prose, and `EXT_TRAN_CALL_HIS.WEB_SVC_ID = "aosv_0001_001_r001"`** — coexistence/traceability, governed by the slug rule's two preserved buckets. Not drift.
- **Settings flat-summary asymmetry** (`change-password.md` / `environment-settings.md` absent on the web tree) — both are non-Settings-domain screens; leave unless the PO wants symmetric stubs.
- **`transaction-history` naming triad** (pkg `erptransactionhistory` / route `erp-transaction-history` / doc folder `transaction-history`) — disclosed and deliberate. Not drift.
- **`contacts` audit "PARTIAL" status in `gap-analysis.md`** — correct; the drift is the *overstatement* in `ContactsService.java:84-86` + CLAUDE.md, not the gap-analysis. Fix the overstatement (P3-5), not the gap-analysis.
- **`db/manual/` and `db/archive/` (the archived migration history) were removed 2026-06-23 (approved decision); only `db/migration/` remains.** A missing file under `db/migration` is **not** a defect — legacy schema/functions/seed are reused from the existing legacy/PROD DB (deployment-context premise above). The project does not use manual-seed or archived-migration SQL; do not create Flyway migrations or DDL to "fill" them.

---

## Approved deviations / deployment-context notes

*These are NOT defects. They are documentation-clarity or deployment-architecture items. None is a runtime risk or a deployment blocker.*

### DC-1 — Common module migration-version citations *(reclassified from P1-2, 2026-06-23)*

- **Classification:** **APPROVED DEVIATION / DEPLOYMENT CONTEXT NOTE** (was: "Critical defect").
- **Files (version edits applied Phase 3B):**
  - Navigation: `features/common/navigation/{database-mapping.md, migration-guide.md, README.md, source-code-index.md, refactor-gap-analysis.md}` + `features/common/README.md`
  - Audit-log: `features/common/audit-log/{database-mapping.md, migration-guide.md, README.md, refactor-gap-analysis.md, source-code-index.md}` + `features/common/audit-log.md`
  - File-management: `features/common/file-management/{database-mapping.md, migration-guide.md}`
  - Root cause: `wecms-api/CLAUDE.md` (ledger cells annotated, not rewritten)
- **What it is:** Docs cited `V242`/`V243` (navigation) and `V155` (`wb_get_next_val` / audit seq). On the **current disk** the live files are `V137`/`V138`/`V139` (nav catalogue) and `V140__wb_get_next_val.sql`; the `V242`/`V243`/old-`V140` files were under the archived migration history (`db/archive/`, the 2026-06-09 renumber), **removed 2026-06-23 (approved decision)** — no longer in the repo. The live `V137`–`V140` files remain under `db/migration/`; `V155` on disk was `mrec_pic_store`.
- **Why this is NOT critical (corrected risk):**
  - Risk is **documentation confusion only** — a reader mapping a doc citation to a Flyway filename.
  - **Not a runtime risk.** The application calls `wb_get_next_val` by name through `LegacyFunctionRepository`; it does not depend on the migration *version number*. The function already exists in the legacy/PROD DB.
  - **Not a deployment blocker.** UAT/PROD already contain the schema, functions, and seed; nothing is "missing" at runtime.
  - **Developers should NOT assume a missing/renumbered Flyway file must be created.** Legacy schema and functions are **reused**, not recreated. Flyway is not the source of truth for legacy objects.
- **Action taken (Phase 3B, for clarity only):** corrected the cited numbers to the live `V137`–`V140` and noted that `V242/V243/V155` are the pre-renumber numbers (their files were in the archived history, since **removed 2026-06-23**). This is a clarity improvement, not a defect remediation.
- **Effort:** done. **Residual risk:** none (documentation clarity).

### DC-2 — Navigation seed source of truth *(NEW — DBA / Deployment Verification)*

- **Classification:** **DBA / DEPLOYMENT VERIFICATION** (not a migration defect).
- **Description:** `V137`–`V139` create the navigation catalogue tables (`product_menu` / `menu_item` / `menu_item_translation`). The seed data (42 menu items, 115 product-menu rows, 345 translations) shipped as the pre-renumber `V243__navigation_menu_seed.sql`, which lived in the archived migration history (`db/archive/`) — **removed 2026-06-23 (approved decision)**, no longer in the repo. The live `V137`–`V139` files contain **no `INSERT`**; UAT/PROD already hold the navigation data in the existing legacy database, so this is **expected, not missing functionality**. *(historical — NOT IN THE CURRENT SET; `V137`-`V139` create the nav tables but contain zero INSERTs, so the seed must be applied separately)*
- **This is NOT a defect.** The repository is **not** intended to be a complete historical archive of legacy DB objects; the absent in-repo seed `INSERT` is by design under the legacy-DB-reuse model. Do **not** recreate the `V243` seed.
- **Clean-environment provisioning:** *Clean-environment database provisioning is outside the scope of this repository and must follow DBA-approved procedures.* (Do **not** invent or document a repository-side provisioning mechanism.)
- **Explicitly do NOT:** create Flyway migrations · create DDL · recreate the removed `db/archive/` history or the `V243` seed.
- **Effort:** none — informational; no code, migration, or replacement-SQL action required.

---

## Phase-3C verification results (2026-06-23)

**§2 — Banking Daily Financial (CMC) — ✅ VERIFIED.**
- All active docs reflect that `ReportService.dailyFinancial` (`ReportService.java:147-225`) calls `rept_0015_001_r001_rec1` **directly**, and that `DailyFinancialCmcFunctionRepository` is the execution path (referenced in all 9 backend leaf docs).
- Strategy-B Java live-compute appears **only** under doc-level "superseded / HISTORICAL" banners (P0-fix-plan, parity-verification-report, refactor-gap-analysis) or inline "superseded 2026-06-14" framing — never as current behaviour.
- Grep confirms **0 active** "Not called" / "do not add `rept_0015_001_r001_rec1`" instructions remain. The one "Add Flyway migrations for the summary tables" line sits inside the `### Two viable read strategies (HISTORICAL — superseded)` / `§5 (HISTORICAL — Strategy A only; not shipped)` blocks.

**§4 — Documentation consistency — ✅ VERIFIED.**
- **No active documentation requires creating missing legacy DDL** (the only "Add Flyway migrations" mention is inside an explicitly HISTORICAL, not-shipped Strategy-A block).
- **No documentation requires porting all legacy DB functions into Flyway** — the active posture is "**not ported** — batch owns the writes" and "documented set-returning **exception** … not re-expressed in Java."
- Docs correctly describe **reuse** of legacy schema/functions ("copied verbatim from DEV", "documented set-returning exception", "batch owns writes").

**Deployment-model alignment — ✅.** The plan now classifies DB-version/seed items by the legacy-DB-reuse architecture (DC-1, DC-2), not a Flyway-first model.

## Suspected items — verify before acting (do not edit blind)
- **DB-S1** `UserPwChgHis.REG_DTM`/`MOD_DTM` type: doc says `OffsetDateTime`; sibling `USER_LOGIN_HIS.REG_DTM` was remapped to `String varchar(14)`. **Open the entity** before changing the doc.
- **DB-S2** firm-banking-history `SYS_SCR_OPTION_WECMS` seed: feature docs say not seeded; `CLAUDE.md` V153 row says seeded 7 cols. Confirm against the migration + provider.
- **AZ-S1** `TransferController.java:34-35` duplicate `GET /banking/transfers/own-accounts` vs `OwnAccountTransferController` — confirm whether redundant or intentional before documenting.

## Effort roll-up
- **Doc-only (P1, P2-1, P3-1..5 doc parts, P4 doc parts):** ~1.5 days.
- **Code-tree (P2-2 wiring, P2-3, P3-6, P4-2, P4-4):** ~1 day — separate dev/PO workstream, out of the docs scope.
- **Decision-gated:** P2-1 (PO: author vs repoint).
