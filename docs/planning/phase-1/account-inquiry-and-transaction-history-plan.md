# Plan — Account Inquiry & Transaction History (Refactor Completion)

> **Status (2026-06-02): partially complete — kept active.** Account Inquiry (VND/FCY) has shipped; the residual scope is the **Transaction-History frontend wiring**. This is the only Phase-1 plan still in active `planning/` — its shipped siblings were archived to [../../archive/planning/phase-1/](../../archive/planning/phase-1/). Archive this file once Transaction-History frontend lands.

**Date:** 2026-05-14 (revised 2026-05-15 after code audit)
**Author:** Arthur (dev) — drafted via Claude
**Scope:** Close remaining JEX → Spring/Next.js parity gaps for the two menus.
**System:** Refactor (`wecms-api/` + `wecms-web/`)

---

## 1. Current State Snapshot (revised after 2026-05-15 code audit)

The original 2026-05-14 draft under-counted backend progress on Transaction
History. A code walk on 2026-05-15 of [AccountController.java](../../../src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java),
[TransactionHistoryClient.tsx](../../../../wecms-web/components/banking/transaction-history/TransactionHistoryClient.tsx),
and [lib/api/banking.ts](../../../../wecms-web/lib/api/banking.ts) shows the
backend has already shipped detail / confirm / undo / summary / export — but
the frontend never wired any of them. The work shape for Sprints A–C
changes from "build endpoints" to "consume existing endpoints".

| Menu | Legacy JEX | Refactor Backend | Refactor Frontend | E2E |
|---|---|---|---|---|
| Account Inquiry — VND | `fnsh_0001_001_r001` | `GET /api/v1/banking/accounts?currency=VND` | `/banking/account-inquiry` | ~90% |
| Account Inquiry — FCY | `fnsh_0005_001_r001` (USD only) | `GET /api/v1/banking/accounts?currency=FCY\|USD\|KRW\|…` | `/banking/account-inquiry-fcy` | ~80% (FCY client still uncontrolled) |
| Transaction History — read | `trns_0001_001` | list ✅ + summary ✅ + detail ✅ | list only, `cnfm_yn` + `keyword` + `from/to` not forwarded; date range hardcoded | **~25%** |
| Transaction History — confirmation | `trns_0001_001_u001` | confirm ✅ + undo ✅; bulk-confirm ❌ | 0% — no modal, no undo, no bulk bar | **~0%** |
| Transaction History — export | (new) | export ✅ (`POST /accounts/transactions/export`) | 0% — no fetcher, no toolbar wiring | **~0%** |
| Auto-processor settings | `trns_0003_001` | — | — | **0%** |
| Inline add-contact from txn | (new) | — | — | **0%** |

**Bottom line:** Account Inquiry is essentially complete. **Transaction
History's backend is ~80% landed, but the frontend is still a read-only
skeleton pointing at one endpoint** — the confirmation workflow, summary
strip, detail view, Excel export, and the real filter wiring all exist on
the API and have zero consumers in the page. Sprint A–C scope flips from
backend build-out to **frontend wiring of already-shipped endpoints**.

### Frontend wiring gap — concrete

[`TransactionHistoryClient.tsx`](../../../../wecms-web/components/banking/transaction-history/TransactionHistoryClient.tsx)
+ [`TransactionHistoryPage.tsx`](../../../../wecms-web/components/banking/transaction-history/TransactionHistoryPage.tsx)
issues caught in the audit:

- `fetchTransactions(...)` in [`lib/api/banking.ts:146`](../../../../wecms-web/lib/api/banking.ts#L146)
  only forwards `from / to / type`. Backend already accepts `cnfm_yn` and
  `keyword`; the page's status tabs (All / Confirmed / Unconfirmed) and
  search input set local state but never reach the API.
- Date range is **hardcoded** at `TransactionHistoryPage.tsx:65-68`
  (`25-04-2024` → `25-12-2024`). The `DateRangePicker` is decorative.
- No fetcher exists for `GET .../summary`, `GET .../{trnsSeqNo}`,
  `PUT/DELETE .../confirm`, or `POST /accounts/transactions/export`.
- Sidebar uses static account slugs (`bidv-0569`) and bridges to the real
  `ACCT_SEQ_NO` via bank-name prefix matching — documented as a known
  Sprint 2 cleanup at lines 20-38 of the client.

---

## 2. Goals & Non-Goals

**Goals**
- Bring Transaction History to feature parity with the JEX live system.
- Close residual gaps on Account Inquiry (VAS column, account-type filter, print, formatter drift).
- Land everything as kebab-case slug identifiers (`transaction-history`, `auto-processor-settings`) per the cross-tree rule.

**Non-Goals**
- No live bank-scraping integration changes (NXISAS stays as-is in refactor scope).
- No PROD/UAT deploy — local R&D only.
- No mobile (`trns_1001_001`) port in this phase.

---

## 3. API List (target endpoints — all under `/api/v1/banking`)

Legend: ✅ exists · 🆕 to build · 🔧 to extend

### 3.1 Account Inquiry
| # | Method | Path | Purpose | Status |
|---|---|---|---|---|
| 1 | GET  | `/accounts` | List accounts (currency=VND\|FCY\|ALL\|<CCY>, bankCd, keyword, sort, page, size) | ✅ |
| 2 | POST | `/accounts/export` | XLSX export (`screenId=banking-account-inquiry-vnd\|-fcy`) | ✅ |
| 3 | GET  | `/accounts/banks` | Bank dropdown options (scoped by tenant + currency) | ✅ (via `BANKS_AVAILABLE` payload) |
| 4 | POST | `/accounts/{acctSeqNo}/refresh-balance` | On-demand balance refresh (triggers NXISAS) | 🆕 |
| 5 | GET  | `/accounts/print` | Print-friendly payload (or rely on browser print CSS) | 🔧 decision needed |

### 3.2 Transaction History (revised 2026-05-15)
| # | Method | Path | Purpose | Backend | Frontend |
|---|---|---|---|---|---|
| 6 | GET  | `/accounts/{acctSeqNo}/transactions` | List txns (`from`, `to`, `type`, `cnfm_yn`, `keyword`) | ✅ shipped — all params accepted | 🔧 fetcher forwards `from/to/type` only; `cnfm_yn` + `keyword` not wired |
| 7 | GET  | `/accounts/{acctSeqNo}/transactions/summary` | Aggregates: total in/out, count, confirmed vs unconfirmed | ✅ shipped | ❌ no fetcher |
| 8 | GET  | `/accounts/{acctSeqNo}/transactions/{trnsSeqNo}` | Transaction detail (VAS, contact, confirmation meta) | ✅ shipped | ❌ no fetcher, no detail view |
| 9 | PUT  | `/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/confirm` | Confirm → write `ACCT_TRNS_RCV_PAY_INFM` | ✅ shipped | ❌ no fetcher, no modal |
| 10 | DELETE | `/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/confirm` | Undo-confirm | ✅ shipped | ❌ no fetcher, no row action |
| 11 | POST | `/accounts/transactions/export` | XLSX export for current filter | ✅ shipped | ❌ no fetcher, toolbar Excel inert |
| 12 | POST | `/accounts/{acctSeqNo}/transactions/bulk-confirm` | Bulk confirm selected rows | 🆕 not built | ❌ |
| 13 | POST | `/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/contact` | Add contact (`TRCO_INFM`) inline from a transaction | 🆕 not built | ❌ |

### 3.3 Auto-Processor (Classification rules)
| # | Method | Path | Purpose | Status |
|---|---|---|---|---|
| 14 | GET    | `/auto-processor/rules` | List rules (filter: actv, bankCd, ccyCd) | 🆕 |
| 15 | POST   | `/auto-processor/rules` | Create rule (matchKeyword, matchType, vasAcctCd, rcvPayUsagCd, priority) | 🆕 |
| 16 | PUT    | `/auto-processor/rules/{id}` | Update rule | 🆕 |
| 17 | DELETE | `/auto-processor/rules/{id}` | Soft-delete (`ACTV_YN='N'`) | 🆕 |
| 18 | POST   | `/auto-processor/run` | Dry-run / batch-apply rules to a date range | 🆕 |

### 3.4 Supporting lookups (likely already exist; verify)
| # | Method | Path | Purpose |
|---|---|---|---|
| 19 | GET | `/vas-accounts` | VAS account dropdown for confirmation modal |
| 20 | GET | `/contacts` | Contact (`TRCO_INFM`) lookup for "Counterparty" field |
| 21 | GET | `/code/rcv-pay-usage` | Usage codes (deposit/withdrawal purpose) |

---

## 4. Screen Flow

### 4.1 Account Inquiry (VND & FCY) — already shipped
```
Sidebar → Banking → Account Inquiry (VND | FCY)
   │
   ▼
┌───────────────────────────────────────────────────────────┐
│ Summary card: Total accounts · Total balance · Available   │
│ (per-currency tiles in FCY)                                │
├───────────────────────────────────────────────────────────┤
│ Filter row: [Bank ▾] [Search keyword]  [Refresh] [Excel]  │
│             [View Setting ⚙] [Print]                      │
├───────────────────────────────────────────────────────────┤
│ Table — server-paginated                                  │
│  Bank · Account # · Nickname · Type · CCY · Balance ·     │
│  Available · Last Update · Status                         │
│  (row click → Transaction History for that account)       │
└───────────────────────────────────────────────────────────┘
```
Remaining tasks: VAS column (blocked on VAS Account feature), FCY client → controlled mode, formatter parity (EUR/KRW/JPY), print decision.

### 4.2 Transaction History (VND & FCY) — primary build
```
Sidebar → Banking → Transaction History (VND | FCY)
   │
   ▼  (no account in URL)
┌───────────────────────────────────────────────────────────┐
│ Account picker — reuses Account Inquiry grid, row=link    │
│ (or auto-redirect to last-used account)                    │
└───────────────────────────────────────────────────────────┘
   │  click row → /banking/transaction-history/[accountId]
   ▼
┌───────────────────────────────────────────────────────────┐
│ Header: Bank · Acct # · Nickname · Balance · CCY          │
├───────────────────────────────────────────────────────────┤
│ Filters:                                                  │
│  [From date] [To date] [Direction ▾ All/In/Out]           │
│  [Confirm ▾ All/Confirmed/Unconfirmed] [Keyword] [Search] │
│  [Excel] [Print] [View Setting ⚙] [Auto-Processor ⚙]      │
├───────────────────────────────────────────────────────────┤
│ Summary strip: in-total · out-total · #confirmed/#total   │
├───────────────────────────────────────────────────────────┤
│ Table (server paged):                                     │
│  ☐ Date · Description · Deposit · Withdrawal · Balance ·  │
│    VAS Acct · Purpose · Counterparty · Confirmed · By     │
│  Row actions: [Confirm] [Undo] [Add contact]              │
│  Bulk bar (when selected): [Bulk Confirm]                 │
└───────────────────────────────────────────────────────────┘
   │  click [Confirm]
   ▼
┌─ Confirm modal ────────────────────────────────────────────┐
│ VAS Account [▾]  Usage [▾]  Counterparty [▾/+new]  Memo   │
│                          [Cancel] [Save]                  │
└────────────────────────────────────────────────────────────┘
   │  click [Auto-Processor ⚙]
   ▼  /banking/auto-processor-settings
┌───────────────────────────────────────────────────────────┐
│ Rules list (priority, match keyword, match type, VAS,     │
│  usage, active). [Add] [Edit] [Delete] [Dry-run]          │
└───────────────────────────────────────────────────────────┘
```

---

## 5. Work Breakdown (revised 2026-05-15 — frontend-led, backend mostly done)

The original A–E grouping assumed the backend was the bottleneck. The
audit flipped that: the backend has shipped read + confirm + export.
The revised sprints front-load **frontend wiring of existing endpoints**
and push net-new backend work (bulk-confirm, inline contact, auto-processor,
refresh-balance) to the back of the queue.

**Sprint A — Transaction History read wiring (frontend-only)**

Goal: the page actually sends what the user types/clicks to the API.

- `lib/api/banking.ts` — extend `fetchTransactions(...)` to accept `cnfm_yn` + `keyword`, plus a real `from / to` (drop the hardcoded `25-04-2024 → 25-12-2024` constants in `TransactionHistoryPage.tsx:65-68`).
- Wire the `DateRangePicker`, `TransactionStatusTabs`, `TransactionTypeFilter`, and `SearchInput` state into the fetcher (debounce search; reset page on filter change per the shared `usePagination` rule).
- Add `fetchTransactionsSummary(acctSeqNo, filters)` → render the summary strip (in-total · out-total · #confirmed/#total) above the table.
- Add `fetchTransactionDetail(acctSeqNo, trnsSeqNo)` → row click opens a read-only detail panel (drawer or right-rail) showing VAS / counterparty / confirmation meta.
- No backend changes.

**Sprint B — Confirmation workflow wiring (frontend-only for single-row)**

Goal: the user can confirm/undo a transaction from the list.

- `lib/api/banking.ts` — add `confirmTransaction(acctSeqNo, trnsSeqNo, payload)` + `undoConfirmTransaction(acctSeqNo, trnsSeqNo)`.
- Confirm modal (VAS account ▾ · usage ▾ · counterparty ▾ · memo) — uses existing shared `<Dialog>` primitive per `wecms-web/CLAUDE.md` §3a.
- Row action chips (Confirm / Undo) on `TransactionTable`.
- Lookups for the modal dropdowns: confirm whether `/vas-accounts`, `/contacts`, `/code/rcv-pay-usage` already exist; add wrappers in `lib/api/` (Sprint B-side audit task).
- **Backend ✅** (`PUT/DELETE .../confirm` already wired).

**Sprint C — Transaction History Excel export wiring (frontend-only)**

- `lib/api/banking.ts` — add `exportTransactions(...)` going through the shared `useExcelExport()` hook per `wecms-web/CLAUDE.md` §13b.
- Register the column registry next to `TransactionTableColumns.tsx` (FIELD_ID → header / format / align), matching the View Setting seed for the page once it lands.
- Wire `PageActionButtons showExcel onExport={...}` on the page header.
- **Backend ✅** (`POST /accounts/transactions/export` already wired).

**Sprint D — Bulk-confirm + inline add-contact (full-stack)**

- Backend: `POST /accounts/{id}/transactions/bulk-confirm`, `POST .../{trnsSeqNo}/contact`.
- Frontend: row-selection checkboxes wired to a bulk bar; inline "+ new contact" affordance in the confirm modal's counterparty picker.

**Sprint E — Auto-Processor (full-stack, net new)**

- Backend: new module `domain/autoprocessor/` (entity, repo, service, controller). Rules CRUD + `POST /run` dry-run/batch.
- Frontend: `/banking/auto-processor-settings` page; "Auto-Processor ⚙" entry on the Transaction History toolbar.
- Batch runner that classifies on new scraped transactions (defer to a sprint where the scrape integration is in scope).

**Sprint F — Account Inquiry residuals (small)**

- FCY client → controlled (server paging).
- Currency formatter parity (EUR/KRW/JPY).
- `ACCT_TYPE_CD` filter for FCY.
- VAS column wire-up once VAS Account feature lands.
- `POST /accounts/{id}/refresh-balance` on-demand refresh (backend + button).
- Sidebar slug → real `ACCT_SEQ_NO` cutover (drops the bridge in `TransactionHistoryClient.tsx`).
- Print decision (browser CSS vs server payload) for both menus.

---

## 6. Open Decisions for PO (Mr. Jung)

1. **Print** — port server-rendered print or rely on browser CSS only?
2. **Additional legacy fields** on Account Inquiry response (`NXISAS_ACCT_TYPE`, `CORC_BF_BAL`, `CERT_INFO_*`, `BANK_NM2`, `LST_LGN_*`, …) — keep, drop, or defer per field?
3. **`ACCT_CUST_TYPE` / `HAND_REG_YN` / date-range filters** on Account Inquiry — required for parity?
4. **Bulk-confirm** — required for v1 of refactor, or single-row only?
5. **Auto-processor scope** — port full rule engine, or MVP (keyword equals/contains only)?
6. **VAS Account column** — confirm dependency landing order before Sprint A finishes.

---

## 7. References

- Legacy JSPs: `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/fnsh/`, `…/trns/`
- Legacy IDO/WSVC XML: `WECMS_ETC/xml/service/IDO/ACCT_TRNS_HSTR_*`, `WSVC/trns_0001_001*`, `WSVC/fnsh_0001_001_r001*`, `WSVC/fnsh_0005_001_r001*`
- Refactor backend: `wecms-api/src/main/java/com/kosign/wecms/domain/banking/`
- Refactor frontend: `wecms-web/app/(dashboard)/banking/`
- Existing docs: [account-inquiry-vnd.md](../../features/banking/current-savings-vnd/account-inquiry.md), [account-inquiry-fcy.md](../../features/banking/current-savings-fcy/account-inquiry.md), [service-mapping.md](../../references/migration/service-mapping.md)
- Parity audit: [db-parity-audit-2026-04-30.md](../../references/audits/db-parity-audit-2026-04-30.md)
