# Card Transactions — Legacy (JEX) vs Refactor Parity Audit


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../references/database/flyway-migration-baseline.md) §5.

**Feature:** Payment > Corporate Card > Card Transactions
**Route (refactor):** `/payment/corporate-card/card-transactions`
**Legacy WSVC family:** `card_0001_001` (list) · `card_0001_002` (link popup) · `card_0001_003` (Card Management) · `card_0001_004` (Save Card) · `card_0001_005` (Request Approval) · `nxisas_cardTranList` (Update/scrape)
**Date:** 2026-06-19 · **System tag:** Refactor · **Status:** audit-only (no code changed)
**Method:** legacy source-of-truth read first (JSP/TS/IDO/WSVC) → live JEX DB schema verified via `postgres_dev_server_jexframe` (read-only) → refactor backend + frontend read → compared.

---

## 1. Executive Summary

The refactor reproduces the **core Card Transactions flow faithfully** — same tables (`CARD_MSTR`, `CARD_TRNS_HSTR`, `CARD_TRNS_RCV_PAY_INFM`, `BUSA_DOC_RCV_PAY_HIS`), same key generation funnel (`wb_get_next_val`), correct `BIZ_KEY` tenant scoping on every read and write, legacy-correct scrape persistence shape, and the same per-bank status mapping. No cross-company leakage and no plaintext PAN beyond what legacy already stores (the DB stores card numbers **already masked** — confirmed: all 268 DEV rows are 16 chars containing `*`).

However, **four behavioral parity defects** and **one data-correctness divergence** were confirmed against the legacy source. Two are HIGH and break the list's core filtering semantics:

1. **Linked/Unlinked filter reads the wrong column** — legacy keys on linked-document presence (`REC_1` over `BUSA_DOC_RCV_PAY_HIS`); refactor keys on `APPR_SEQ_NO`. The "Linked / Unlinked" tab returns the wrong rows.
2. **Card-company and Card filters are silently ignored** — accepted on the wire, never applied to either the grid query or the dashboard totals.
3. **Search is narrower** — legacy searches cardholder + nickname + card name + merchant; refactor searches only merchant + card name.
4. **Footer transaction count** shows the current-page row count, not the full filtered count.
5. **Scrape de-duplication key differs** from legacy — a migration-safety / re-scrape risk.

Plus one refactor-internal bug (Purpose-column sort maps to a non-existent entity property) and several minor/accepted differences.

**Verdict: NOT READY for SIT/UAT** until defects D1, D2, D5 are fixed (and ideally D3, D4, D6). D1/D2 change which rows and which totals the user sees; D5 affects whether re-scraping after migration double-inserts or wrongly skips.

---

## 2. Legacy File Map

| Concern | File | Notes |
|---|---|---|
| Main list view | `WECMS/web/view/jex/wabooks/card/card_0001_001_view.jsp` | empty colgroup; columns injected via View Setting `dymAreaId` `table1`/`table2` (H product) |
| Main list TS | `WECMS_STATIC/ts-src/js/jex/wabooks/card/card_0001_001.ts` | filters, render, dashboard, Excel, print, Update-popup launch |
| Main list read action | `WECMS/web/WEB-INF/action/jex/wabooks/card/card_0001_001_r001_act.jsp` | grid (`CARD_MSTR_R002`) + count (`CARD_TRNS_COUNT_R001`); **link filter `:134-151`** |
| Card/company multiselect read | `card_0001_001_r002_act.jsp` (IDO `CARD_MSTR_R003`) | card filter source |
| Inline edit (desc/purpose) | `card_0001_001_u001_act.jsp` | patches `CONT` / `CARD_TRNS_USAGE_CODE` only |
| Reporting account update | `card_0001_001_u002_act.jsp` | patches `CHART_ACCT_CD` + `CHART_ACCT_SEQ_NO` |
| Card Management list/TS | `card_0001_003_view.jsp` / `card_0001_003.ts` | Manage Cards popup |
| Card Management read | `card_0001_003_r001_act.jsp` (IDO `CARD_MSTR_R004`) | incl. `CAN_DEL_YN` subselect |
| Enable/disable | `card_0001_003_u001_act.jsp` | `CARD_MSTR.ACTV_YN` |
| Rename | `card_0001_003_u002_act.jsp` | `CARD_HLDR_NM` + `CARD_NICK_NM` |
| Delete (guarded) | `card_0001_003_d001_act.jsp` | hard delete; WBE1153 if txns exist |
| Save scraped cards | `card_0001_004_c001_act.jsp` | upsert `CARD_MSTR`; `BANK_CERT_INFO` lookup/create |
| Request Approval save | `card_0001_005_c001_act.jsp` | `EXPT_RESOL_*`; `APPR_DV='33'` |
| Update/scrape popup TS | `WECMS_STATIC/ts-src/js/jex/wabooks/nxisas/nxisas_cardTranList.ts` | Auto/date logic, NXiSAS calls, period-limit |
| Scrape period-limit read | `nxisas_cardTranList_r001_act.jsp` (IDO `BANK_INFM_DETL_R007`) | `BANK_DETL_INFO_CD IN ('26','27')` |
| Scrape persistence | `WECMS/web/WEB-INF/action/jex/wabooks/fnsh/nxisas_fnsh_r001_act.jsp` → `fnsh_0001_001_c001_act.jsp:1308-1435` | `IsasJob.CARD_USING` branch: dedup + INSERT into `CARD_TRNS_HSTR` |
| NXiSAS engine include | `WECMS/web/nxisas/nxisas.jsp`, `inc_wabooks_nxisas_v2.jsp` | Coocon client engine `_v2nxisas` |
| IDOs | `WECMS_ETC/xml/service/IDO/IDO.CARD_MSTR_R002/R003/R004.xml`, `IDO.CARD_TRNS_COUNT_R001.xml`, `IDO.CARD_TRNS_HSTR_*.xml`, `IDO.CARD_TRNS_RCV_PAY_INFM_*.xml` | read/aggregate/CRUD |
| WSVCs | `WECMS_ETC/xml/service/WSVC/WSVC.card_0001_*.xml`, `WSVC.nxisas_cardTranList*.xml` | `<login>Y</login>`, `NORM_USER_ONLY_YN=Y` |

## 3. Refactor File Map

### Backend (`wecms-api/src/main/java/com/kosign/wecms`)
| Concern | File |
|---|---|
| Controller (all endpoints) | `domain/payment/cardtransactions/controller/CardTransactionsController.java` |
| List/CRUD/link/memo/approval service | `domain/payment/cardtransactions/service/CardTransactionsService.java` |
| Scrape sync service | `domain/payment/cardtransactions/service/CardTransactionSyncService.java` |
| Period-limit service | `domain/payment/cardtransactions/service/CardTranListR001Service.java` |
| Sort whitelists | `service/CardTrnsHstrSortField.java`, `service/CardMstrSortField.java` |
| Ledger query + totals | `repository/CardTrnsHstrRepository.java` (`findLedgerPage` `:73-90`, `findLedgerTotals` `:109-132`) |
| Card master repo | `repository/CardMstrRepository.java` |
| Link/doc repos | `repository/BusaDocRcvPayHisRepository.java`, `repository/CardTrnsRcvPayInfmRepository.java`, `repository/MemoInfmRepository.java`, `repository/UsagInfmRepository.java` |
| Entities | `entity/CardMstr.java`(+`Id`), `entity/CardTrnsHstr.java`(+`Id`), `entity/CardTrnsRcvPayInfm.java`(+`Id`), `entity/BusaDocRcvPayHis.java`(+`Id`), `entity/MemoInfm.java`, `entity/UsagInfm.java` |
| Export | `export/CardTransactionsExportProvider.java` |
| Key-gen funnel | `common/legacy/LegacyFunctionRepository.java` (`nextCardSeqNo`, `nextApprSeqNo`, `nextBusaDocRcvPayHisSeqNo`) |
| Scrape gateway | `domain/banking/nxisas/**` (`NxisasGatewayClient`, `NxisasCardBanks`, `gateway/dto/NxisasCard*`, `BankScrapingController/Service`) |
| Migrations | `resources/db/migration/V38__card_mstr.sql`, `V39__card_trns_hstr.sql`, `V40__card_trns_rcv_pay_infm.sql` |

### Frontend (`wecms-web`)
| Concern | File |
|---|---|
| Route | `app/(dashboard)/payment/corporate-card/card-transactions/page.tsx` |
| Container | `components/payment/corporate-card/CardTransactionsPage.tsx` |
| Grid + columns | `components/payment/corporate-card/CardTransactionsTable.tsx`, `CardTransactionsTableColumns.tsx`, `CardTransactionsTableFooter.tsx` |
| Dashboard tiles | `components/payment/corporate-card/CardSummaryCards.tsx` |
| Filter bar | `components/payment/corporate-card/CardTransactionsFilterBar.tsx` |
| Card Management popup | `components/payment/corporate-card/CardManagementDialog.tsx` |
| Add Card flow | `components/payment/corporate-card/AddCardFlowDialog.tsx`, `SelectCardsDialog.tsx`, `SelectBankDialog.tsx` (+`lib/.../selectBankBanks.ts`) |
| Update/scrape popup | `components/payment/corporate-card/UpdateCardsDialog.tsx` |
| Link/receipt/memo/approval popups | `LinkCardPaymentsDialog.tsx`, `ReceiptDetailDialog.tsx`, `MemoPanel.tsx`, `ApprovalRefDetailDialog.tsx`, `RequestApprovalDialog.tsx` |
| API client | `lib/api/cardTransactions.ts` (+ `bankCredentials.ts`, `loginCredentials.ts`) |
| Mappers / wire types | `lib/.../cardTransactionMappers.ts`, `lib/types/api.ts` |

## 4. Table & Function Map (legacy DB-verified)

All card tables/columns confirmed on JEX DEV (`postgres_dev_server_jexframe`). **All tables are in the PROD Active Table List** (`CARD_MSTR`, `CARD_TRNS_HSTR`, `CARD_TRNS_RCV_PAY_INFM`, `BUSA_DOC_RCV_PAY_HIS`, `BANK_CERT_INFO`, `EXPT_RESOL_*`, `CHART_ACCT_INFM`).

Key verified facts (entity types must match):
- `CARD_TRNS_HSTR` PK = `(BIZ_KEY, CARD_SEQ_NO)`. `CARD_SEQ_NO`, `APPR_SEQ_NO`, `CHART_ACCT_SEQ_NO`, `CARD_TRNS_USAGE_CODE` all **varchar** (String).
- `PRCH_STTS` is **varchar** (not numeric); `wb_get_prch_stts_nm(varchar, varchar)` resolves the label.
- `TRSC_DT`/`TRSC_TM` are **varchar** strings; `TRSC_AMT` is unconstrained `numeric` (BigDecimal, no fixed scale).
- `CARD_NO` is stored **masked** (BIN + `*` + tail, 16 chars) in the legacy DB.
- `CARD_MSTR` PK = `(BIZ_KEY, BANK_CD, CARD_NO)`; `ACTV_YN char NOT NULL default 'Y'`; `BANK_CERT_SEQ numeric`; `TRN_LST_SUC_DTM`/`MOD_DTM` are **varchar(14)** (not timestamps).
- DB functions exist and are correct: `wb_get_prch_stts_nm`, `wb_get_usag_nm`/`wb_get_usag_nm_new`, `wb_get_chart_acct_nm`/`_lv_nm`, `wb_get_bank_nm` (**overloaded** 1-arg & 2-arg), `wb_get_memo_cont`/`_color`, `wb_get_next_val`.

| Table | Legacy ops | Refactor mapping | Parity |
|---|---|---|---|
| `CARD_TRNS_HSTR` | SELECT(grid/count), INSERT(scrape), PATCH(desc/purpose/chart-acct/appr), DELETE | `CardTrnsHstr` + repo; INSERT only via sync; patches via service | ✅ entity & column types match DB |
| `CARD_MSTR` | SELECT, upsert, PATCH(actv/rename/trn_lst), DELETE | `CardMstr` + repo | ✅ |
| `CARD_TRNS_RCV_PAY_INFM` | INSERT/DELETE/SELECT (link) | `CardTrnsRcvPayInfm` + repo | ✅ |
| `BUSA_DOC_RCV_PAY_HIS` | INSERT/DELETE (link), SELECT (REC_1 grid) | `BusaDocRcvPayHis` + repo (`resolveLinkedDocs`) | ⚠️ ported but **not wired to link_yn filter** (D1) |
| `BANK_CERT_INFO` | SELECT/INSERT (add card) | resolved in `createCards`/sync | ✅ |
| `wb_get_next_val` | CARD_SEQ_NO (DAO), APPR_SEQ_NO (SeqHelper) | `LegacyFunctionRepository.nextCardSeqNo`/`nextApprSeqNo` | ✅ funnel rule honored |

---

## 5. Flow Comparison

### 5.1 Main list

| Aspect | Legacy | Refactor | Verdict |
|---|---|---|---|
| Driving table | `CARD_TRNS_HSTR CTH LEFT JOIN CARD_MSTR CM` (`IDO.CARD_MSTR_R002.xml:151-155`) | single-table `CardTrnsHstr`; names/bank/doc-no resolved per-page in Java | ACCEPTED (equivalent result) |
| Date filter | `CTH.TRSC_DT BETWEEN ? AND ?`, default "Last 30 Days" = today−30 (`card_0001_001.ts:184-187`) | `trscDt BETWEEN`, default `last_30_days` = today−29 | ⚠️ D7 off-by-one (minor) |
| Card-company filter | `CTH.bank_cd IN (...)` (`r001_act.jsp:72-86`) | accepted as `bank_cd`, **ignored** in repo (`CardTrnsHstrRepository.java:53-65`) | ❌ **D2 HIGH** |
| Card filter | `CTH.card_no IN (...)` (`r001_act.jsp:88-102`) | accepted as `card_no`, **ignored** | ❌ **D2 HIGH** |
| All/Linked/Unlinked | `T.REC_1 IS [NOT] NULL` = linked-doc presence (`r001_act.jsp:140-148`) | `apprSeqNo IS [NOT] NULL` (`repo:80-82`) | ❌ **D1 HIGH** |
| Search columns | `CARD_HLDR_NM, CARD_NM, CARD_NICK_NM, MERCH_NM` ILIKE (`r001_act.jsp:104-116`) | `MERCH_NM, CARD_NM` only (`repo:77-79`) | ❌ **D3 MEDIUM** |
| Sort | SortHelper whitelist | `CardTrnsHstrSortField`; `CARD_TRNS_USAGE_NM→"cardTrnsUsageCd"` (entity prop is `cardTrnsUsageCode`) | ❌ **D6** (Purpose sort throws/falls back) |
| Pagination size | default 15 (`view #PAGE_SIZE`) | FE default 10 (`CardTransactionsPage.tsx:56`); BE default 15 | ⚠️ D8 (minor) |
| Footer count | `TOTL_CNT` from `CARD_TRNS_COUNT_R001` (full filtered) | `rows.length` (current page) (`CardTransactionsTableFooter.tsx:40-45`) | ❌ **D4 MEDIUM** |
| Total amount | `SUM(TRSC_AMT)` single value, rendered VND (legacy hardcodes VND) | `SUM(trscAmt)` single value, rendered VND | ACCEPTED (both single-currency) |
| Status | `wb_get_prch_stts_nm(PRCH_STTS, lang)` | raw `prchStts` + `stts_key` i18n key | ACCEPTED (FE resolves label) |
| Approval requested | `APPR_SEQ_NO IS NOT NULL` | `apprSeqNo IS NOT NULL` | ✅ |
| Request Approval enable | ≥1 row checked & none already has `appr_seq_no` (`ts:644-658`) | same (`CardTransactionsPage.tsx:558,574-577`) | ✅ |
| Reporting account | inline pick → `card_0001_001_u002` (`CHART_ACCT_CD`+`SEQ_NO`); clear via same | `PUT/DELETE .../reporting-account` | ✅ |
| Card number masking | stored masked in DB | stores raw scrape value (bank-masked); no encryption | ✅ matches legacy |

### 5.2 Dashboard cards (Total / Approval Not Requested / Approval Requested)

| Aspect | Legacy | Refactor | Verdict |
|---|---|---|---|
| Source | `CARD_TRNS_COUNT_R001` aggregate | `findLedgerTotals` (`repo:109-132`) | ✅ shape matches |
| Count + amount per tile | yes | yes | ✅ |
| Not-requested / requested split | `APPR_SEQ_NO` null check | `apprSeqNo` null check | ✅ |
| Honors date/search filters | yes (DYNAMIC_0) | yes | ✅ |
| Honors card-company/card filters | yes | **no** (totals also ignore bank_cd/card_no) | ❌ **D2 HIGH** (totals desync from grid when those filters set) |
| Honors Linked/Unlinked | legacy count IDO ignores LINK_YN (DYNAMIC_0 only) | totals **include** link_yn predicate | ⚠️ D9 (refactor totals shift with the tab; legacy totals don't — and the tab itself is on the wrong column per D1) |
| Currency grouping | none (VND hardcoded) | none (sums mixed) | ACCEPTED |

### 5.3 Card Management Setting popup

| Aspect | Legacy (`card_0001_003`) | Refactor (`CardManagementDialog`) | Verdict |
|---|---|---|---|
| List source | `CARD_MSTR` via `CARD_MSTR_R004` | `findManagementPage` on `CardMstr` | ✅ |
| Columns | Card Company(`wb_get_bank_nm`), Card Number, Card Holder, Card Nickname, Last update(`MOD_DTM`) | bank_nm, card_no, card_hldr_nm, card_nick_nm, mod_dtm | ✅ |
| `CAN_DEL_YN` | returned to disable delete button (`IDO.CARD_MSTR_R004.xml`) | **not surfaced**; relies on WBE1153 at delete | ⚠️ **D10** (UX parity gap) |
| Enable/disable | `ACTV_YN` patch (`card_0001_003_u001`) | `PATCH .../active {actv_yn}` | ✅ |
| Rename | `CARD_HLDR_NM`+`CARD_NICK_NM` (`u002`) | `PATCH .../{bankCd}/{cardNo}` | ✅ |
| Delete | hard delete; guard counts `CARD_TRNS_HSTR` → WBE1153 (`d001_act.jsp:31-38,83-97`) | hard delete; same guard → WBE1153 | ✅ |
| Add Card → persist | `card_0001_004_c001`: upsert `CARD_MSTR` on PK, `BANK_CERT_INFO` lookup/create | `POST /cards` (`createCards`): upsert on `(BIZ_KEY,BANK_CD,CARD_NO)`, cert auto-resolve | ✅ inserted/skipped counts returned |
| Duplicate handling | upsert by PK, no error | server-side dedupe on PK, `skipped_count` | ✅ |
| Select Bank source | hardcoded allow-list `["11704088","11704020"]` (Shinhan, Woori) from `comm_0140_001_r001` VND | `GET /login-credentials/banks` in Add-Card; only NXiSAS-supported (`NxisasCardBanks` Shinhan/Woori) | ✅ ACCEPTED (equivalent intent) |
| Bank→module mapping | bank-code branching (`IsasHelper.moduleToBankCd`) | `NxisasBankConfig.find` / `NxisasCardBanks` | ✅ |
| Tenant scope | `BIZ_KEY` on every query + PK | `TenantContext.currentBizKey()` on every query/PK | ✅ |
| Drag reorder | UI only, no persist (`ts:63-87`) | n/a | ✅ matches (no persist) |

### 5.4 Update Card Transactions popup + scrape persistence

| Aspect | Legacy (`nxisas_cardTranList` → `fnsh_0001_001_c001`) | Refactor (`UpdateCardsDialog` → `/sync`) | Verdict |
|---|---|---|---|
| Default range | from = min(`TRN_LST_SUC_DTM`) else today−limit; to = today | auto: from = min(`TRN_LST_SUC_DTM`[0:8]) else today−90d; to = today | ✅ |
| Auto checkbox | checked → disable datepickers, use latest dates | checked (default) → server derives window | ✅ |
| Manual validation | end<start → WBE1000; over limit → WBI1122 | from≤to else `WBE_CARD_SYNC_BAD_RANGE`; period cap warning | ✅ (codes differ — refactor-internal) |
| Period limit | `BANK_INFM_DETL_R007` `CD IN ('26','27')`, default 90d | `CardTranListR001Service` same table/codes, default 90d | ✅ |
| Engine | NXiSAS/Coocon client `_v2nxisas` (client-side login) | server-side `NxisasGatewayClient` (Coocon smart-gateway) | ACCEPTED (per `project_login_credentials_flow`: client-exe → server gateway) |
| Persist target | INSERT into `CARD_TRNS_HSTR` (no upsert) | INSERT (no upsert) | ✅ |
| **Dedup key** | `biz_key, card_no, trsc_dt, trsc_tm, ccy_cd, trsc_amt, merch_nm, coalesce(trim(auth_code),'')` (`fnsh_0001_001_c001_act.jsp:1332-1360`) | authCode present → `(biz_key,bank_cd,card_no,trsc_dt,auth_code)`; else `(biz_key,bank_cd,card_no,trsc_dt,trsc_tm,trsc_amt)` (`CardTrnsHstrRepository:170-178`) | ❌ **D5 MEDIUM** |
| PRCH_STTS mapping | Shinhan 11704088: Chấp nhận→1/Hoàn thành→2/Đã hủy→9; Woori 11704020: Giao dịch được duyệt→1 | same `mapPurchaseStatus` | ✅ |
| INOUT_DV / ERP_SND_YN | `'2'` / `'N'` | `'2'` / `'N'` | ✅ |
| Key gen | `CARD_SEQ_NO` via DAO seq | `wb_get_next_val('card_trns_hstr','card_seq_no',biz_key)` (saveAndFlush per row) | ✅ funnel |
| `TRN_LST_SUC_DTM` update | set to now per insert | set per card when scraped non-empty | ✅ (empty-result cursor-hold is deliberate) |
| Re-scrape idempotency | existing row → skip | existing row → skip | ✅ (but governed by D5 key) |
| Progress / messages | WBI1131 success, WBC1153/WBI1123 cancel | equivalent dialogs | ✅ |

---

## 6. Confirmed Defects

### D1 — Linked/Unlinked filter reads the wrong column **[HIGH]**
- **Legacy:** `LINK_YN='Y'` → `T.REC_1 IS NOT NULL`; `'N'` → `IS NULL`. `REC_1` is the JSON aggregate of linked docs from `BUSA_DOC_RCV_PAY_HIS` where `REF_NO=CARD_SEQ_NO AND RCV_PAY_WAY_CD='R'` (`card_0001_001_r001_act.jsp:134-151`, `IDO.CARD_MSTR_R002.xml:120-138`). "Linked" = transaction has a linked e-invoice/receipt.
- **Refactor:** `link_yn='Y'` → `apprSeqNo IS NOT NULL AND <> ''`; `'N'` → null/empty (`CardTrnsHstrRepository.java:80-82`, totals `:123-125`). "Linked" = approval requested. The in-code comment `:46-51` claims `BUSA_DOC_RCV_PAY_HIS` is "not yet ported" — **stale**: it is ported (`BusaDocRcvPayHisRepository`, `CardTransactionsService.resolveLinkedDocs:1941`, V40).
- **Affected:** `CardTrnsHstrRepository.findLedgerPage` + `findLedgerTotals`; `CardTransactionsService.list`.
- **Fix:** replace the `apprSeqNo` predicate with an EXISTS subquery on `BusaDocRcvPayHis` (`refNo = c.cardSeqNo AND rcvPayWayCd = 'R'`) for both the page and totals queries (or add a `linkedExists` flag and filter on it). Keep approval-status filtering separate from link filtering.
- **Risk:** HIGH — the Linked/Unlinked tab returns wrong rows; users reconciling linked vs unlinked card spend get incorrect lists.
- **Test:** create a card txn with a linked receipt but no approval, and another with approval but no link. Linked tab must show the first, not the second.

### D2 — Card-company & Card filters silently ignored **[HIGH]**
- **Legacy:** `CTH.bank_cd IN (...)` and `CTH.card_no IN (...)` applied to grid (`card_0001_001_r001_act.jsp:72-102`); count IDO honors them too.
- **Refactor:** `buildFilters` parses `bank_cd`/`card_no` into `CardTransactionListFilters` (`CardTransactionsService.java:1833-1836`) but `findLedgerPage`/`findLedgerTotals` do not accept or apply them (`repo:53-65`). The frontend filter bar sends them; results and dashboard totals are unfiltered.
- **Affected:** `CardTrnsHstrRepository` (page + totals), `CardTransactionsService.list`.
- **Fix:** add sibling repository methods with `AND c.bankCd IN :bankCds` / `AND c.cardNo IN :cardNos`, branch in the service when the lists are non-empty (the canonical Spring Data pattern the code comment itself describes). Apply to both page and totals so the dashboard stays consistent with the grid.
- **Risk:** HIGH — wrong grid rows AND wrong dashboard totals whenever a company/card filter is selected.
- **Test:** with ≥2 card companies present, filter to one; grid + all three dashboard tiles must reflect only that company.

### D3 — Search columns narrower than legacy **[MEDIUM]**
- **Legacy:** ILIKE across `CARD_HLDR_NM, CARD_NM, CARD_NICK_NM, MERCH_NM` (`card_0001_001_r001_act.jsp:104-116`).
- **Refactor:** ILIKE across `MERCH_NM, CARD_NM` only (`repo:77-79`). Searching by cardholder or nickname returns nothing.
- **Fix:** extend the LIKE to cover cardholder + nickname. Note these live on `CARD_MSTR` (the refactor ledger query has no join) — needs a join or an EXISTS/IN subquery on `CardMstr` by `(bizKey,bankCd,cardNo)`. Add `ESCAPE` handling while editing (see D11).
- **Risk:** MEDIUM — users searching by person/nickname get empty results.
- **Test:** search a known cardholder name → row appears.

### D4 — Footer transaction count uses page count, not total **[MEDIUM]**
- **Legacy:** footer count = `TOTL_CNT` (full filtered count) from `CARD_TRNS_COUNT_R001`.
- **Refactor:** footer = `rows.length` (current page) (`CardTransactionsTableFooter.tsx:40-45`). With paging on, the footer under-reports.
- **Fix:** bind footer count to `totals.total_count` (already available in the same response).
- **Risk:** MEDIUM — misleading transaction count.
- **Test:** load >1 page of data; footer count must equal the full filtered total, not 10.

### D5 — Scrape de-duplication key differs from legacy **[MEDIUM — data correctness / migration safety]**
- **Legacy:** dedup on the full composite `biz_key, card_no, trsc_dt, trsc_tm, ccy_cd, trsc_amt(::numeric), merch_nm, coalesce(trim(auth_code),'')` (`fnsh_0001_001_c001_act.jsp:1332-1360`).
- **Refactor:** if `auth_code` present → `(biz_key, bank_cd, card_no, trsc_dt, auth_code)`; else `(biz_key, bank_cd, card_no, trsc_dt, trsc_tm, trsc_amt)` (`CardTrnsHstrRepository:170-178`, `CardTransactionSyncService.persistScrapedRows`).
- **Consequence:** two genuinely different transactions sharing an `auth_code` on the same date (or banks that reuse/blank auth codes) are wrongly skipped by the refactor; conversely the narrower else-branch may admit rows legacy would treat as duplicates. After JEX→refactor data migration, the **first re-scrape** can diverge from what legacy would have produced (missed inserts or different dup handling).
- **Fix:** match the legacy composite key exactly (include `ccy_cd`, `trsc_amt`, `merch_nm`, and the trimmed `auth_code` in all cases). Keep it a single key, not auth-conditional.
- **Risk:** MEDIUM — silent data loss/skew in card history; undermines migration-safety goal.
- **Test:** scrape a batch twice → no duplicates AND no legitimate rows dropped; compare row set against legacy persistence for the same scrape payload.

### D6 — Purpose-column sort maps to a non-existent entity property **[MEDIUM — refactor-internal bug]**
- **Refactor:** `CardTrnsHstrSortField.CARD_TRNS_USAGE_NM` maps to property `"cardTrnsUsageCd"` (`CardTrnsHstrSortField.java:44`), but the entity property is `cardTrnsUsageCode` (`CardTrnsHstr.java:131`). Spring Data raises `PropertyReferenceException` (or silently falls back) when the user sorts the Purpose column.
- **Fix:** change the mapping to `"cardTrnsUsageCode"`.
- **Risk:** MEDIUM — clicking the Purpose header errors or no-ops.
- **Test:** sort the Purpose column ASC/DESC → rows reorder, no 500.

---

## 7. Accepted Differences (product / architecture reason)

| # | Difference | Why accepted |
|---|---|---|
| A1 | Refactor resolves card/bank names, doc-no, linked-docs per-page in Java vs legacy SQL JOIN/subselect | Same visible result; clean Spring rewrite is the sanctioned pattern (CLAUDE.md "never port JEX patterns directly"). Watch N+1 perf at scale. |
| A2 | Multi-currency totals not grouped (single VND figure) on both sides | Legacy hardcodes VND; refactor sums un-grouped ("Phase B"). Equivalent for single-currency tenants. Flag for PO if mixed-currency cards exist. |
| A3 | Scrape engine: legacy client `NXiSAS.exe` vs refactor server-side Coocon gateway | Established refactor direction (memory `project_login_credentials_flow`, `reference_nxisas_gateway_urls`). |
| A4 | Select Bank list: legacy hardcoded `[Shinhan, Woori]` vs refactor `/login-credentials/banks` ∩ NXiSAS-supported | Same effective set; refactor sources from data instead of a literal. |
| A5 | Error/message codes differ (WBE1000/WBI1122 → `WBE_CARD_SYNC_*`) | Refactor i18n code scheme (CLAUDE.md); behavior equivalent. |
| A6 | `CARD_SEQ_NO` via `wb_get_next_val` funnel vs legacy DAO seq | Required by the funnel rule; per-tenant MAX+1 matches legacy semantics. |
| A7 | `findActiveCards` dropdown returns inactive cards too (name misleading) | Deliberate legacy parity — the legacy JSP commented out the `actv_yn` filter. |

---

## 8. Items Needing PO / Business Decision

| # | Question | Default if unanswered |
|---|---|---|
| P1 | Confirm "Linked/Unlinked" must mean **linked-document presence** (legacy) — fixing D1 changes current refactor behavior. | Fix to legacy (linked-doc). |
| P2 | Is a single combined total acceptable for SIT, or is per-currency grouping required for card spend? (A2) | Single VND for SIT; grouping later. |
| P3 | Should Card Management surface `CAN_DEL_YN` to pre-disable the delete button (D10), or is the WBE1153-at-delete guard sufficient? | Keep server guard; optional UX add. |
| P4 | Default list page size 10 (refactor) vs 15 (legacy) (D8) and date window today−29 vs today−30 (D7). | Align to legacy (15 / 30) unless UX prefers 10. |

---

## 9. Minor / Watch Items (not blocking)

- **D7** date default off-by-one (today−29 vs today−30).
- **D8** page size 10 vs 15.
- **D9** refactor dashboard totals shift with the Linked/Unlinked tab; legacy count IDO ignores LINK_YN. (Resolves naturally once D1 defines the tab correctly; decide whether totals should follow the tab.)
- **D10** `CAN_DEL_YN` not surfaced (UX).
- **D11** ledger search LIKE has no `ESCAPE`; user-typed `%`/`_` act as wildcards (link/receipt queries already escape — inconsistent).
- **Legacy smell carried or not:** legacy `IDO.CARD_MSTR_R002.xml:64` hardcodes `wb_get_usag_nm_new(...,'EN')`; refactor resolves Purpose via `/purpose-options` + i18n — refactor is arguably better; confirm label parity.

## 10. Security & Scope Check

| Check | Result |
|---|---|
| BIZ_KEY / tenant isolation | ✅ every refactor query & write scoped via `TenantContext.currentBizKey()`; no wire `biz_key` accepted (matches legacy `loginUser.getBizKey()`). |
| User/company scope | ✅ cards are company-wide (BIZ_KEY only) on both sides — matches legacy (no USER/ORG filter). |
| Cross-company leakage | ✅ none found on the card path; link/receipt/period-limit lookups all carry bizKey. |
| Plaintext PAN | ✅ DB stores masked card numbers (verified); refactor stores the bank-masked scrape value; no encryption on either side (matches legacy). No new exposure. |
| Frontend-only validation | ✅ none security-critical: link amount-match, Request-Approval eligibility, holder-name-required are all re-checked server-side. |
| Direct DB write without backend validation | ✅ all writes go through the controller→service→repo; scrape persistence validates active card + dedup before insert. |

---

## 11. SIT/UAT Smoke Test Checklist

**Main list**
- [ ] Date presets (30/90/180/custom) filter correctly; default window matches agreed spec (P4).
- [ ] Card-company filter narrows grid **and** all 3 dashboard tiles (D2).
- [ ] Card filter narrows grid **and** tiles (D2).
- [ ] All / Linked / Unlinked returns rows by **linked-document** presence (D1): linked-but-not-approved row shows under Linked.
- [ ] Search by cardholder, nickname, card name, merchant each returns matches (D3).
- [ ] Sort every column incl. Purpose without error (D6).
- [ ] Footer count = full filtered total across pages (D4).
- [ ] Status + approval-status render correct labels; rows with `appr_seq_no` are read-only & unselectable.
- [ ] Request Approval enabled only when ≥1 non-approved row checked; opens approval dialog (`APPR_DV` 33 equivalent).
- [ ] Reporting account: set, change, clear; persists to `CHART_ACCT_CD`+`CHART_ACCT_SEQ_NO`.

**Dashboard** — Total / Not Requested / Requested counts+amounts reconcile with grid under each filter combination.

**Card Management** — list columns correct; enable/disable persists `ACTV_YN`; rename persists holder+nickname; delete blocked with WBE1153 when txns exist, succeeds otherwise; Add Card scrape → save inserts new, skips duplicates (counts shown); Select Bank shows supported banks; tenant isolation holds.

**Update / scrape** — Auto on derives from latest update; manual range validated + period-cap enforced; scrape inserts new rows, **re-scrape inserts no duplicates and drops no legitimate rows** (D5); `TRN_LST_SUC_DTM` advances; progress + success/cancel messages.

**Export/Print** — Excel columns honor View Setting; print shows current-page rows; both label "Card Transactions".

**Security** — second-tenant login never sees tenant-1 cards/transactions; no PAN beyond masked form in any response.

---

## 12. Final Verdict

**NOT READY** for SIT/UAT as-is.

Blockers to clear first: **D1** (Linked/Unlinked wrong column), **D2** (card-company/card filters ignored — grid + dashboard), **D5** (scrape dedup-key divergence / migration safety). Strongly recommended alongside: **D3** (search scope), **D4** (footer count), **D6** (Purpose sort bug).

Everything else (status mapping, scrape persistence shape, key generation, tenant scoping, card management CRUD, add-card upsert, period limits, masking) matches legacy and the verified DB model. Once D1/D2/D5 (+D3/D4/D6) are fixed and the smoke checklist passes, this is SIT-ready.

> Audit-only. No source files were modified. Awaiting approval before any code change.
