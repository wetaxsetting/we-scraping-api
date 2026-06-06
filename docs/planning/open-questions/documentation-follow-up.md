# Documentation Follow-Up Checklist

**Raised-on:** 2026-06-01 · **Owner:** Sonny / Arthur · **Status:** OQ-DOC-1 & OQ-DOC-2 **RESOLVED 2026-06-01**; **11 decisions open** — OQ-DOC-3 (docs-migration), OQ-DOC-4–6 (Banking), OQ-DOC-7–13 (added by the 2026-06-01 implementation-vs-docs audit, see "Documentation Audit Round 2" below)

Tracks the residual decisions left after the docs migration (Settings + Add-on Service reorg,
source-code path cleanup, service-mapping repair). All **link/reference** work is done; the items
under "Needs Confirmation" are **content decisions**, not broken links.

---

## Documentation Migration Status — ✅ Complete

| Item | Status |
|---|---|
| Settings documentation | ✅ done |
| Add-on Service documentation | ✅ done |
| Source-code references validated | ✅ 0 invalid |
| API ↔ Web cross-links validated | ✅ |
| Service mapping validated (both `migration/service-mapping.md` + `api-mapping/service-mapping.md`) | ✅ 0 dangling |
| README navigation updated | ✅ |
| No dangling documentation links | ✅ |
| No invalid source-code references | ✅ |

---

## Needs Confirmation

### OQ-DOC-1 — Login Authentication feature doc — ✅ RESOLVED 2026-06-01
- **Decision (2026-06-01):** **Yes — create dedicated feature documentation.** Added under a new
  `auth/` system-feature domain on both sides (the Auth & Common documentation audit,
  [`../../archive/reports/auth-common-domain-audit-2026-06-01.md`](../../archive/reports/auth-common-domain-audit-2026-06-01.md)).
- **Docs created:** [`../../features/auth/login-auth.md`](../../features/auth/login-auth.md) (backend) +
  [`../../features/auth/README.md`](../../features/auth/README.md); frontend
  [`../../../../wecms-web/docs/features/auth/login-auth.md`](../../../../wecms-web/docs/features/auth/login-auth.md) + `README.md`.
  Both `service-mapping.md` (`migration/`) and `api-mapping.md` (`api-spec/`) updated to link them.
- **Historical record (as raised):** `login-auth` was plain-text *Needs Confirmation* in both mapping files; no refactor-side doc existed.
- **Related existing docs (JEX, read-only reference):** [`docs/jex/features/login-spring-boot-migration.md`](../../../../docs/jex/features/login-spring-boot-migration.md), [`docs/jex/features/login-wecms.md`](../../../../docs/jex/features/login-wecms.md), [`docs/jex/api-spec/auth.md`](../../../../docs/jex/api-spec/auth.md); refactor plan: [`../../archive/planning/phase-1/task-sprint-0-login-refactor.md`](../../archive/planning/phase-1/task-sprint-0-login-refactor.md).
- **Suggested files (as proposed):** `wecms-api/docs/features/login-auth.md`, `wecms-web/docs/features/login-auth.md`. **Final placement:** under `features/auth/` (system-feature domain), slug `login-auth` preserved.
- **API surface:** `POST /api/v1/auth/login`, `GET /api/v1/auth/me` (JEX `logn_0001_001`).

### OQ-DOC-2 — View Setting feature doc — ✅ RESOLVED 2026-06-01
- **Decision (2026-06-01):** **Yes — create dedicated feature documentation.** Added under a new
  `common/` system-feature domain on both sides (same audit as OQ-DOC-1).
- **Docs created:** [`../../features/common/view-setting.md`](../../features/common/view-setting.md) (backend) +
  [`../../features/common/README.md`](../../features/common/README.md); frontend
  [`../../../../wecms-web/docs/features/common/view-setting.md`](../../../../wecms-web/docs/features/common/view-setting.md) + `README.md`.
  The web architecture doc stays in place and is cross-linked, not moved. Both mapping files updated.
- **Historical record (as raised):** `view-setting` was plain-text *Needs Confirmation* in both mapping files.
- **Related existing docs:** [`docs/jex/features/view-setting.md`](../../../../docs/jex/features/view-setting.md) (JEX); [`wecms-web/docs/architecture/view-setting/README.md`](../../../../wecms-web/docs/architecture/view-setting/README.md) (web architecture). View Setting is a global/common feature.
- **Suggested files (as proposed):** `wecms-api/docs/features/view-setting.md`, `wecms-web/docs/features/view-setting.md`. **Final placement:** under `features/common/`, slug `view-setting` preserved (matches JEX).
- **API surface:** `/api/v1/screen-options/{webSvcId}/{dymAreaId}` GET/PUT/DELETE (JEX `comm_0108_001`).

### OQ-DOC-3 — Historical source references (renamed / replaced / removed?)
Each was referenced by docs but no longer exists at the cited path; references have been **converted to plain text `Needs Confirmation — source file not found`** (no active link errors). Confirm the disposition of each:

| Reference | Note from repo scan |
|---|---|
| `OtpChallengeDialog.tsx` | absent; only `components/banking/own-account-transfer/OtpAuthDialog.tsx` exists (possible rename — confirm) |
| `lib/api/settings.ts` | absent; no equivalent found |
| `V86__bank_cert_info.sql` | absent under that name — RESOLVED (pending confirmation): exists on disk as V100__bank_cert_info.sql / V101__nxsias_module_tran_log_his.sql respectively (draft-number drift). |
| `V87__nxsias_module_tran_log_his.sql` | absent under that name — RESOLVED (pending confirmation): exists on disk as V100__bank_cert_info.sql / V101__nxsias_module_tran_log_his.sql respectively (draft-number drift). |
| `V181__transfer_history_helper_index.sql` | `V181` is now a different migration (`…unlock_trsc_dtm_in_user_table.sql`) |
| `Claude Code (AI)/sql/firm_banking_dispatch_remaining_tables_20260518.sql` | absent (generated SQL artifact — confirm if renamed/dropped) |

> Already resolved by relocation (no action needed): `TranTp.java` → `common/enums/TranTp.java`;
> `AuditLogsService.java` → `common/auditlog/service/AuditLogService.java`.

---

## Needs Confirmation — Banking (raised 2026-06-01, from the Banking docs restructure)

### OQ-DOC-4 — Smart Organizer scope — RESOLVED (verified 2026-06-02)
- **Resolution:** Smart Organizer **is** in scope and **is implemented**. Backend ships `SmartOrganizerController` + `OrganizerRuleController` + services with migrations V172 (`smart_organizer_rmrk_org_infm_schema`), V173 (`wb_get_next_val_rmrk_org_infm`), V174 (`smart_organizer_view_setting_seed`); the Next.js route `app/(dashboard)/banking/smart-organizer/` exists (VND; an FCY-specific page is not yet split out — the shared route serves it). The feature docs were corrected from their original "discovery scaffold" framing; residual `Needs Confirmation` detail in those docs reconciles against the shipped code in the next stabilisation pass. Not in the legacy `docs/jex/screen-flows/banking.md` 9-screen list (newer feature).
- **References:**
  - JEX source: `fnsh_0200_001` (`WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/fnsh/fnsh_0200_001.ts`), `fnsh_0200_002`
  - [`../../features/banking/smart-organizer-vnd.md`](../../features/banking/current-savings-vnd/smart-organizer.md) · [`../../features/banking/smart-organizer-fcy.md`](../../features/banking/current-savings-fcy/smart-organizer.md)
  - [`../../../../wecms-web/docs/features/banking/smart-organizer-vnd.md`](../../../../wecms-web/docs/features/banking/current-savings-vnd/smart-organizer.md) · [`../../../../wecms-web/docs/features/banking/smart-organizer-fcy.md`](../../../../wecms-web/docs/features/banking/current-savings-fcy/smart-organizer.md)

### OQ-DOC-5 — Transaction History API/DB contract
- **Decision needed:**
  - Create dedicated `api-spec/banking/` + `database/banking/` docs for Transaction History (VND / FCY)?
  - Confirm the endpoint contract and the **exact** transaction-history table (e.g. `ACCT_TRNS_HSTR` or another).
- **Current state:** the two hubs cite `GET /api/v1/banking/accounts/{acctSeqNo}/transactions` and reuse the account-inquiry DB docs; the dedicated api-spec/db are left as plain-text "to be authored".
- **References:**
  - [`../../features/banking/transaction-history-vnd.md`](../../features/banking/current-savings-vnd/transaction-history.md) · [`../../features/banking/transaction-history-fcy.md`](../../features/banking/current-savings-fcy/transaction-history.md)
  - [`../../../../wecms-web/docs/features/banking/transaction-history-vnd.md`](../../../../wecms-web/docs/features/banking/current-savings-vnd/transaction-history.md) · [`../../../../wecms-web/docs/features/banking/transaction-history-fcy.md`](../../../../wecms-web/docs/features/banking/current-savings-fcy/transaction-history.md)

### OQ-DOC-6 — Banking report JEX mapping inconsistency
- **Decision needed:** confirm the correct JEX IDs for Daily Cash, Daily Financial, Cash Flow **against source** before changing any doc.
- **Conflict:**
  - Feature docs cite `rept_0009_001` (daily-cash) / `rept_0015_001` (daily-financial) / `cash_0001_001` (cash-flow).
  - The screen-flow index [`../../../../docs/jex/screen-flows/banking.md`](../../../../docs/jex/screen-flows/banking.md) lists `rept_0001_001` / `rept_0002_001` / `rept_0003_001`.
- **Action:** verify against the JEX WSVC/view sources; do **not** edit the feature docs until confirmed.
- **References:** [`../../features/banking/daily-cash.md`](../../features/banking/reports/daily-cash.md) · [`../../features/banking/daily-financial.md`](../../features/banking/reports/daily-financial.md) · [`../../features/banking/cash-flow.md`](../../features/banking/reports/cash-flow.md)

---

## Documentation Audit Round 2 (2026-06-01) — implementation-vs-docs sweep

Source-code audit of `wecms-api/` + `wecms-web/` against the feature docs. Four implemented-but-undocumented
features were **documented in this pass** (no OQ needed): `common/audit-log.md`, `banking/bank-credentials.md`
(API + Web), `settings/environment-settings.md`. The items below are the residual **decisions** the audit surfaced.

### OQ-DOC-7 — Bank Credentials ↔ Settings Login Credentials boundary
- **Decision needed:** what is the canonical relationship between the **Banking → Credentials** surface
  (`/banking/credentials`, `domain/banking/bankcredentials` + `domain/banking/nxisas`) and the
  **Settings → Login Credentials** panel? Both share the `BANK_CERT_INFO` table and the NXISAS/Coocon
  scraping flow (the scraping `/verify` endpoint Javadoc says it is "used by the Login Credentials Login button").
- **Current state:** both implemented; both now documented ([`../../features/banking/bank-credentials.md`](../../features/banking/bank-credentials.md), [`../../features/settings/login-credentials.md`](../../features/settings/login-credentials.md)). The new bank-credentials doc explicitly defers ownership to this OQ.
- **Action:** confirm whether they are two views of one feature, two distinct features, or one supersedes the other; then reconcile the two docs.

### OQ-DOC-8 — Daily Financial (HAENGSUNG) report scope
- **Decision needed:** is the HAENGSUNG-variant daily-financial report (`PRDT_TYPE='H'`) in scope, and at what depth?
- **Current state:** backend is a **STUB** (`ReportController#dailyFinancialHs` → `ReportService#dailyFinancialHs`, `/api/v1/banking/reports/daily-financial-hs`, `DailyFinancialHsReport` DTO) and a frontend page exists (`app/(dashboard)/banking/reports/daily-financial-hs/page.tsx`). No feature doc — parallels the Smart Organizer discovery-scaffold treatment (OQ-DOC-4).
- **Action:** confirm scope before authoring a full doc; if approved, add `features/banking/reports/daily-financial-hs.md` (both trees) once the stub is implemented.

### OQ-DOC-9 — Settings → Users doc accuracy (avatar + menu-entitlement + status) — ✅ ANSWERED, pending confirmation (2026-06-05)
- **Decision needed:** update [`../../features/settings/users.md`](../../features/settings/users.md) to match implementation, or split sub-features into their own docs?
- **Current state:** `users.md` marks **user-image/avatar upload** and **menu-entitlement** as "deferred / out of scope" and the screen status as "Plan", but the code is implemented: `domain/setting/users/service/UserImageService.java` (+ `POST/DELETE/GET /{userId}/image`), `MenuEntitlementService.java` (+ `GET/PUT .../menu-entitlement`), and full `UserManagementController` CRUD.
- **Resolution (2026-06-05, pending PO confirmation):** rather than edit the stale plan, a full multi-file backend feature folder was authored at [`../../features/settings/user-management/`](../../features/settings/user-management/README.md) (README, business-flow, functional-specification, database-mapping, interface-specification, source-code-index, migration-guide, test-scenarios, refactor-gap-analysis) documenting the **shipped** behaviour. Avatar upload + menu-entitlement are confirmed **functional** (not stubs). The flat `users.md` was given a SUPERSEDED banner and kept for history; the settings README index now points at the folder. Status flipped Plan → ✅ Implemented (parity verified 2026-06-05). **Confirm and close.**

### User Management — content open questions (raised 2026-06-05 with the user-management folder)

Surfaced while documenting the shipped feature; referenced from
[`../../features/settings/user-management/refactor-gap-analysis.md §4`](../../features/settings/user-management/refactor-gap-analysis.md). None block release.

| ID | Question | Current behaviour / proposed default |
|---|---|---|
| OQ-DOC-UM-1 | Should user create/update/delete write `AUDIT_LOGS_INFM` rows (slug `user-management`), matching firm-banking-settings / contacts / chart-of-accounts? | No audit write today. Proposed: add for consistency. |
| OQ-DOC-UM-2 | Is action-level (CRUD) entitlement (`USER_MENU_ACTION_AUTH_INFM`) in scope, or is menu-level sufficient? | Menu-level only. Proposed: defer until required. |
| OQ-DOC-UM-3 | Should admins toggle `ACTV_YN` / `LOCK_YN` from this screen (activate/deactivate/unlock)? | Not editable here; DBA/auth-driven. (Overlaps the "status" leg of OQ-DOC-9.) |
| OQ-DOC-UM-4 | Enforce password reuse history (`USER_PW_CHG_HIS`) + 5-failure auto-lock? | Deferred to the auth-stabilisation MR. |
| OQ-DOC-UM-5 | Frontend hard-codes phone country code `"84"` — should the wire carry a real code? | Cosmetic; revisit with a proper phone picker. |

### OQ-DOC-10 — Exchange Rate: inquiry-only vs management
- **Decision needed:** is exchange-rate **admin configuration/CRUD** a product requirement, or is inquiry-only the intended scope?
- **Current state:** `ExchangeRateController` / `ExchangeRateService` expose **read-only** inquiry (`GET /api/v1/banking/exchange-rates`); data is written by an external batch. Documented as read-only in [`../../features/payment/exchange-rate-inquiry.md`](../../features/payment/exchange-rate-inquiry.md). No admin/config endpoints exist.
- **Action:** if admin config is in scope, author `features/banking/exchange-rate-management.md`; if inquiry-only, existing doc is sufficient (close this OQ).

### OQ-DOC-11 — Web Change Password doc
- **Decision needed:** author a frontend change-password feature doc?
- **Current state:** backend fully implemented (`POST /api/v1/auth/change-password`, `ChangePasswordService`, `UserPwChgHis`) and documented at [`../../features/settings/change-password.md`](../../features/settings/change-password.md); the **frontend ProfileMenu handler is a stub** and there is no `wecms-web/docs/features/settings/change-password.md`.
- **Action:** create the web doc once the frontend handler is implemented (do not document a stub).

### OQ-DOC-12 — Cross-cutting common docs: approval-workflow + attachments
- **Decision needed:** do we want unifying `common/` docs, or keep these per-feature?
- **Current state:** **Approval** is implemented as two distinct concerns — approval-**LINE** (config master data, [`../../features/settings/approval-line.md`](../../features/settings/approval-line.md)) and approval-**BOX** (payment execution inbox, [`../../features/payment/approval-box/README.md`](../../features/payment/approval-box/README.md)); both documented, but no doc explains the engine/flow that links them. **File upload / attachments** is implemented per-feature (Contacts `ContactsFileService`; ERP / firm-banking downloads) with no generic spec.
- **Action:** decide whether to add `common/approval-workflow.md` and/or `common/attachments.md` as **cross-link-only** explainers (no content duplication), or leave per-feature. No doc created in this pass to avoid duplication.

### OQ-DOC-13 — Notifications & Dashboard (not implemented)
- **Status:** **confirmed NOT implemented** in the refactor trees (no `domain/*` package, no route, no page beyond the login redirect). Legacy `BLTB_*` / `MSG_SND_*` and `SYS_DSHBRD_INFM` / `USER_DSHBRD_INFM` exist in the PROD active list but have no refactor code.
- **Action:** docs are **intentionally absent**. Create `common/notifications.md` / `common/dashboard.md` only when the feature lands in a future sprint. Do not create stubs.

---

## Final Documentation Health
- Invalid source references: **0**
- Dangling documentation links: **0**
- Broken service-mapping links: **0**

**Round 1 (structure).** OQ-DOC-1 & OQ-DOC-2 resolved 2026-06-01 (auth/ + common/ feature domains created).
**Round 2 (implementation sweep, 2026-06-01).** Created `common/audit-log.md`, `banking/bank-credentials.md` (API+Web), `settings/environment-settings.md`. Open: **OQ-DOC-3** (docs-migration source refs), **OQ-DOC-4–6** (Banking), **OQ-DOC-7–13** (round-2 decisions). No OQ-DOC item closed without its doc created + linked.
