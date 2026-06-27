# CLAUDE.md Reconciliation — Validated Source-Code Index

> _**Documentation-governance artifact — not a feature specification.** This audit reconciles the `CLAUDE.md` governance/rule files against the current codebase. See the [audits index](README.md) for where this fits._

**Audit date:** 2026-06-23
**Scope:** every suspect claim in `wecms-api/CLAUDE.md` + `wecms-web/CLAUDE.md`, mapped to the current implementation.
**Rule:** code is the source of truth. References marked ✔ were opened/grepped and verified against the working tree on 2026-06-23.
**Companions:** [claude-docs-audit.md](claude-docs-audit.md) · [claude-gap-analysis.md](claude-gap-analysis.md)

> This audit reconciles the two **CLAUDE.md project-instruction files** against code + the already-reconciled
> feature docs (Settings / Add-on / Payment / Auth / Common / Banking passes, all dated 2026-06-23). Where a
> feature doc and CLAUDE.md disagree, the feature doc is the corrected baseline (it was verified against code in
> its own pass); CLAUDE.md is the laggard. **No CLAUDE.md, feature doc, or application code was modified** —
> this produces the three audit artifacts only.

---

## A. wecms-api/CLAUDE.md claims → code

| ID | CLAUDE.md claim (verbatim gist) | Verified code reference | Verdict |
|---|---|---|---|
| CA-1 | Feature Status `domain/einvoice`: "`EInvoiceService.resolveDocType` maps EXPENSE→`'E'` where docs expect `'B'` — **open code bug to file**" | ✔ [`EInvoiceService.java:205-217`](../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceService.java#L205-L217): `SALES`/`I`→`'I'`; `EXPENSE`/`BILL`/`B`→`'B'`; else `WBE2002` ([:76](../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceService.java#L76)). Javadoc :36-39 documents `'E'` is rejected. | **STALE** — no bug; maps `'B'`. |
| CA-2 | Feature Status `domain/operation`: "the inquire-dialog **Save/persist** action is a stub (no backend upsert endpoint yet)" | ✔ [`OperationStatusController.java:100-103`](../../src/main/java/com/kosign/wecms/domain/operation/controller/OperationStatusController.java#L100-L103) `@PostMapping("/operation-status") create(...)` → `service.create(request)` (persist + `(BIZ_KEY,TAXCODE)` dup guard). | **STALE** — endpoint exists. |
| CA-3 | Feature Status: "`domain/member`; dashboard; **notifications**; refresh-token …; 5-failure auto-lock — **Not implemented. No controller/service on disk.**" | ✔ `domain/payment/notification/controller/NotificationController.java` (`@RequestMapping("/api/v1/notifications")`) + `NotificationService` + `ApprovalNotificationService` + 5 `BLTB_*` entities. **5-failure auto-lock is also now implemented** (F-10, `LoginLockoutService`); dashboard / refresh-token remain genuinely absent. | **CONTRADICTION** — notifications AND auto-lock are implemented; only dashboard + refresh-token still MATCH "Not implemented". |
| CA-4 | "Excel export — `common/export/`" → **Endpoints with Excel export wired** lists **3** (`banking/accounts`, `firm-banking-history`, `erp-transaction-history`) | ✔ 26 `*Provider`/`ExportRowProvider` files under `domain/**/export` (~20 registered screenIds incl. `cash-flow-report`, `daily-cash-report`, `addon-sales-einvoices`, `addon-expenses-einvoices`, `addon-operation-status`, `approval-box`, `card-transactions`, `vas-account`, `settings-contacts-local/-overseas`, 5 payment-result screens, account-inquiry VND+FCY, transaction-history). | **STALE / understated** — ~20 wired, not 3. |
| CA-5 | Feature Status `domain/banking/smartorganizer`: "⚠️ Feature docs still label Smart Organizer a 'discovery scaffold' — **stale, flag for correction**" | ✔ Banking audit (2026-06-23) corrected the feature-folder docs; the "scaffold" label now survives only in the preserved `smart-organizer/legacy-spec-*.md` (banner-marked superseded). | **STALE (self-referential)** — the flagged feature docs were already fixed; the CLAUDE.md note is the laggard. |
| CA-6 | Migration table: "**V155 (live)** … **The highest live migration.**" (table ends at V243) | ✔ `db/migration/` max = **V248** (158 `V*.sql` files); V156–V159 … V248 exist. The same table also lists V242/V243, which already contradicts "V155 highest". | **STALE** — migration numbering drift; highest is V248. |
| CA-7 | Feature Status: many Settings/Banking rows carry "**Frontend not verified this pass**" | ✔ Later passes verified frontend-wired: `login-credentials`, `einvoice` (sales/expenses), `operation`, `vas-account`, `own-account-transfer`, `transfer-history` (per Add-on + Banking + Settings audits). | **PARTIAL** — caveat stale for the verified modules; still true for bank-account/preferences/users/firm-banking unless re-checked. |
| CA-8 | "Deferred inside auth … password-change-history reset"; Project Overview "What works today" omits change-password | ✔ [`AuthController.java:72-76`](../../src/main/java/com/kosign/wecms/domain/auth/controller/AuthController.java#L72-L76) `@PostMapping("/change-password")` + `ChangePasswordService.java:55`. `AuthService.java:82` still lists "password-change-history reset" as deferred. | **MIXED** — change-password IS implemented (understated in Overview); password-change-history *reset* remains deferred (MATCH). |

## B. wecms-web/CLAUDE.md claims → code

| ID | CLAUDE.md claim | Verified reference | Verdict |
|---|---|---|---|
| CW-1 | Project Overview "What works today" / "Implemented pages" = `/login`, `/banking/account-inquiry` (mock), `/payment/approval-box` (mock); "every other route … placeholder without an `href`" | ✔ **43** `app/(dashboard)/**/page.tsx` routes exist — full banking (account-inquiry VND/FCY, transaction-history VND/FCY + `[accountId]`, transfer-own-accounts, transfer-history, smart-organizer, 4 reports incl. daily-financial-hs), payment (approval-box, card-transactions, 5×{scheduled,result}+`[id]`, exchange-rate-inquiry), addon (3). | **MASSIVELY STALE.** |
| CW-2 | Feature Status `/banking/account-inquiry` = "Shell + mock data … table rows are hardcoded in `AccountInquiryPage.tsx`" | ✔ Banking audit: Account Inquiry consumes live `/api/v1/banking/accounts` via `useApiData`; View Setting + sort + export wired. | **STALE.** |
| CW-3 | Feature Status `/payment/approval-box` = "Shell + mock data" | ✔ Route + page exist; backend `ApprovalBoxController` implemented. (frontend wiring depth: confirm per Payment docs.) | **PARTIAL/STALE.** |
| CW-4 | Feature Status "Placeholders" row: `/banking/transaction-history`, FCY variants, `/internal-transfer/**`, `/reports/**`, `/addon`, `/settings` = "disabled until the route exists" | ✔ All those routes exist (§CW-1 list) — transaction-history, reports, internal-transfer, addon all shipped; Settings is a modal (`SettingsModal`) not a route. | **STALE.** |
| CW-5 | "Change password dialog — **Not implemented**; ProfileMenu row exists, handler is empty" (Feature Status + Notes) | ✔ `components/layout/ChangePasswordDialog.tsx` exists; backend `POST /api/v1/auth/change-password` (CA-8). | **STALE.** |
| CW-6 | Feature Status "Print / Export / List view — **Not implemented**; `PageActionButtons` buttons exist, no handlers wired" | ✔ Account Inquiry wires all three: List→View Setting popover, Print→`lib/print.ts printReport`, Export→`hooks/useExcelExport`. `PageActionButtons` `showList/showPrint/showExcel` opt-in. | **STALE (for adopted pages)** — accurate only for not-yet-migrated pages. |
| CW-7 | Feature Status "DateRangePicker (real picker) — Not implemented; UI-only static display" | ✔ `components/shared/InteractiveDateRangePicker.tsx` exists (interactive); basic `DateRangePicker.tsx` may remain display-only on some pages. | **PARTIAL.** |
| CW-8 | "Notes for future implementation — Menu/permission resolution: when the backend returns a resolved menu tree … drive these from that payload instead of hardcoding" | ✔ `hooks/useNavigation.ts` + `lib/api/navigation.ts` drive Sidebar/Header from `GET /api/v1/navigation`; hardcoded arrays removed. | **STALE note** — already done. |

## C. Stale CODE COMMENTS (not CLAUDE.md, not feature docs — flagged for a developer)

| ID | Comment | Reference | Note |
|---|---|---|---|
| CC-1 | `TransferHistoryController` javadoc "`@PreAuthorize` — JWT authentication is the only gate" | ✔ [`:32`](../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/controller/TransferHistoryController.java#L32) contradicts [`:54`](../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/controller/TransferHistoryController.java#L54) `@PreAuthorize("@menuAuthz.canAccess('banking-internal-transfer-history')")` | Banking gap TXH-1. Code-tree; out of doc scope. |
| CC-2 | `AuthService.java:82` "Deferred: … password-change-history reset" | ✔ accurate for the *reset* sub-feature; change-password itself shipped | Keep — not stale, just narrow. |

## D. Genuine implementation gaps (CLAUDE.md correctly states these — keep)

| Item | State | Reference |
|---|---|---|
| Dashboard (`SYS_DSHBRD_INFM`/`USER_DSHBRD_INFM`) | MISSING — no controller/service/route | both CLAUDE.md + Common audit C5 |
| Refresh tokens | MISSING | `SecurityConfig` stateless |
| Server-side logout revocation | **IMPLEMENTED (F-03)** — in-memory; durable/multi-instance store still open | `TokenRevocationService` / `JwtAuthenticationFilter` / `AuthService.logout` |
| 5-failure auto-lock counter | **IMPLEMENTED (F-10)** | `LoginLockoutService` / `AuthService` / `USER_LOGIN_HIS` |
| Distributed rate limiting | MISSING — in-memory `RateLimiter` only (login + reveal) | `common/web/RateLimiter` |
| password-change-history reset | MISSING (deferred) | `AuthService.java:82` |
| Frontend `.test.*` baseline | MISSING (all screens) | Banking/Payment audits |

## E. Confirmed-accurate CLAUDE.md sections (spot-checked — no change)

- wecms-web "Holdovers (UPPER_SNAKE) — cleared 2026-06-07": ✔ matches (no UPPER_SNAKE wire types remain).
- wecms-api Auth flow / JWT / product-type / `@menuAuthz` F-3 enforcement: ✔ matches `MenuAuthorizationGuard`.
- wecms-api View Setting / Common table sort / Excel export *rules* (as opposed to the wired-list): ✔ match `common/` code.
- Banking data-source pivots: ✔ `CashFlowReportRepository.java:67` `get_cashflow_report`; `AccountSortField.java:35` `LAST_UPDATE("acctHisLstInqDt")`.

---

*Last verified: 2026-06-23. CLAUDE.md line numbers are not cited because both files are large living configs without stable line anchors; claims are cited by section heading + table row instead, and every code counter-reference is `file:line`.*
