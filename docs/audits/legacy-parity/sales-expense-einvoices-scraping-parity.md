# Sales / Expenses e-Invoice — Legacy ↔ Refactor Parity Audit


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../references/database/flyway-migration-baseline.md) §5.

> **Scope:** Sales e-Invoices + Expenses e-Invoice (legacy JEX module `einv`, refactor `domain/einvoice` + `wecms-web` `addon/{sales,expenses}-einvoices`).
> **System tags:** JEX (read-only reference) · Refactor (backend `wecms-api`, frontend `wecms-web`).
> **Date:** 2026-06-03.
> **Author:** Claude Code (audit-and-implement, legacy-first parity task).
> **Status:** Audit complete. List + Export = full parity (already shipped). **Scraping "Update" flow = PORTED full-stack on 2026-06-03** (lead chose "Full scrape flow (FE+BE)") — backend compile + unit tests green, frontend typecheck + lint + build green. The browser↔engine leg is **runtime-unverifiable in R&D** (engine unlicensed on `localhost`, OQ-S1). **Update 2026-07-14:** SEED-CBC decryption is now done **server-side** (full legacy parity — `POST /api/v1/addon/einvoices/decrypt`), fixing the "scrape success, no data" bug; OQ-S2 **resolved** (see §6). See §8.

This is a parity audit (refactor vs JEX), so it lives under `wecms-api/docs/audits/` per the docs rules. Frontend findings are included here too for a single source of truth; the frontend feature specs live under `wecms-web/docs/features/addon-service/`.

---

## 0. TL;DR

| Capability | Legacy JEX | Refactor | Parity |
|---|---|---|---|
| List Sales (`DOC_TYPE='I'`) / Expenses (`DOC_TYPE='B'`) | `einv_0001_001_r001` | `GET /api/v1/addon/einvoices?type=SALES\|EXPENSE` | ✅ shipped |
| Date-range + keyword search | `einv_0001_001_r001` | ✅ | ✅ shipped |
| Multi-currency totals footer | `BUSA_DOC_MSTR_R022_CNT` / `getTotInfo2` | `aggregateByCurrency` | ✅ shipped |
| Excel export (list) | `einv_0001_001_r002_excel` | `POST /api/v1/addon/einvoices/export` | ✅ shipped |
| **Invoice-status filter** (`einv_invoiceStatus`) | server-side `getDynamic0` | client-side regex only | ⚠️ **gap (IR-4)** |
| **Symbol-type filter** (`einv_testResults` C/K/POS) | server-side `getDynamic0` | not implemented | ⚠️ **gap** |
| **Currency filter** (`TRCO_TRSC_CCY_CD`) | server-side `getDynamic0` | not implemented | ⚠️ **gap** |
| **"Update" (scrape) flow** — local `.exe` engine | `nxisas_einvoice` + `einv_0001_001.ts` | ✅ ported (`UpdateEInvoiceModal` → `useEInvoiceScrape` → `useNxisasEngine`) | 🟢 ported (runtime-unverifiable, OQ-S1) |
| Save scraped rows → `BUSA_DOC_MSTR` | `nxisas_einvoice_c001_act.jsp` | ✅ `POST /api/v1/addon/einvoices/scrape-results` → `EInvoiceScrapeService` (upsert) | 🟢 ported (tested) |
| Scrape audit → `EXT_TRAN_CALL_HIS` | `nxisas_einvoice_c001_act.jsp` | ✅ `EInvoiceScrapeService.writeAudit` (MAX+1 SEQ_NO) | 🟢 ported (tested) |
| GDT credential capture/use | `gdtc_0001_001` | `domain/setting/logincredentials` (GDT CRUD exists) | 🟡 partial — credential storage present, not wired to e-invoice |
| Invoice detail popup | `einv_0002_001` | `EInvoiceHistoryDialog`/`UpdateEInvoiceModal` (stubs) | ❌ stub |

**Documented bug check:** the CLAUDE.md / earlier-audit note that `EInvoiceService.resolveDocType` maps `EXPENSE → 'E'` is **stale** — the current code (`EInvoiceService.java:205-221`) maps `SALES/I → 'I'` and `EXPENSE/BILL/B → 'B'`, which is correct legacy parity. No fix needed; do **not** "re-fix" correct code. The CLAUDE.md `domain/einvoice` row should be corrected to drop the bug claim.

---

## 1. Legacy files discovered & reviewed

### 1.1 Views / pages
| File | Role |
|---|---|
| `WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp` | Main list page. `einvType` default `"2"`; `1`=Sales (menu `sale_einv_001`), `2`=Expenses (`prch_einv_001`). Includes `inc_wabooks_nxisas_v2.jsp` (engine client) + `comm_0106_001` (date range). Title ML: `mle041` (expenses view) / `mle042` (sales `einv_0001_000` view). "Update" button `#btnUpBills` (`ml009`) opens the scraping layer popup. |
| `einv_0001_000_view.jsp` | Sales variant view (same JS, `einvType` switched). |
| `einv_0002_001_view.jsp` / `einv_0002_002_view.jsp` | Invoice **detail** popup (line items, fee/tax detail). |
| `nxisas_einvoice_view.jsp` | Scraping layer-popup view (progress bar, auto-close checkbox, GDT login gating). |
| `nxisas_einvhis_view.jsp` / `nxisas_einvhis_dt_view.jsp` | Scrape **call history** list + detail. |
| `gdtc_0001_001*` | GDT credential register/validate popup. |
| `inc_wabooks_nxisas_v2.jsp` (include) | Wires the local engine client (`nxisas_api.js` + `isasscaping.js`). |

### 1.2 TypeScript / JS
| File | Role |
|---|---|
| `WECMS_STATIC/ts-src/js/jex/wabooks/einv/einv_0001_001.ts` | Main page controller (~3850 lines). Owns: status filter (`INV_STTS`, `inv_filter_li`), symbol-type filter (`smy-type`, `inv_filter`), date presets (Last 30/90/180 + manual range via `comm_0106_001`), search, pagination, list render for Inv vs Bill, totals footer, credit/debit confirmation popups, Excel export, **`scrapingProcess()`** (the Update flow entry). |
| `WECMS_STATIC/web/js/jex/wabooks/einv/nxisas_einvoice.js` | The scrape orchestrator: builds `INVOICE_LIST` then `INVOICE_DETAIL` engine jobs, drives the progress bar, posts results to `nxisas_einvoice_c001`. (Compiled; reviewed for protocol.) |
| `WECMS_STATIC/web/nxisas/js/isasscaping.js` | Low-level engine transport — **WebSocket Secure** to `wss://ibasews.coocon.co.kr:14102/`, health check `https://…:14101/`. |
| `WECMS_STATIC/web/nxisas/js/nxisas_api.js` | `CooconiSASNX` engine facade: `init/open/execute`, 2FA (LOGIN/CAPTCHA/OTP/QNA), install detection (download `./nxisas/file/NXiSAS.exe`), OS check. |
| `nxisas_einvhis.ts` / `nxisas_einvhis_dt.ts` | Scrape call-history list/detail. |
| `einv_0001_001_r002_excel.js` | List Excel export. |

### 1.3 Action JSP APIs (`web/WEB-INF/action/jex/wabooks/einv/`)
| Action | Role |
|---|---|
| `einv_0001_001_r001_act.jsp` | **List read.** Validates `FROM_DT/TO_DT/DOC_TYPE`; builds `getDynamic0` (the full filter chain — see §3); reads `EXT_TRAN_CALL_HIS_R004` (last scrape end-date), `GDT_CERT_INFO` (cert presence), `BUSA_DOC_MSTR_R022` (rows, paged), `BUSA_DOC_MSTR_R022_CNT` (totals). |
| `nxisas_einvoice_c001_act.jsp` | **Save scraped list.** Decrypts engine `Result` via `ISASSeedCBC`, parses `invoiceList[]`, writes `BUSA_DOC_MSTR` (`BUSA_DOC_MSTR_C002`), writes one `EXT_TRAN_CALL_HIS` row (svc `GDT_INQUIRY_SALES`/`GDT_INQUIRY_EXPENSES`, web_svc `nxisas_einvoice_c001`, rslt code). |
| `nxisas_einvoice_c002_act.jsp` | Save scraped **detail** (line items / fee / tax). |
| `nxisas_einvhis_r001_act.jsp` / `nxisas_einvhis_dt_r001_act.jsp` | Scrape call-history read. |
| `einv_0001_001_u001_act.jsp` | Row update (memo/purpose/etc.). |
| `einv_0002_001_r001_act.jsp` / `einv_0002_001_u001_act.jsp` | Detail read/update. |
| `gdtc_0001_001_r001_act.jsp` | Read latest `GDT_CERT_INFO` for tenant (`seq_no = MAX(seq_no)`). |

### 1.4 IDO / WSVC (read-only reference)
- `BUSA_DOC_MSTR_R022` (paged list, `LANG_CD`, `DYNAMIC_1`), `BUSA_DOC_MSTR_R022_CNT` (totals), `BUSA_DOC_MSTR_C002` (insert), `EXT_TRAN_CALL_HIS_R004` (last end-date), `GDT_CERT_INFO_R001`.

---

## 2. Local `.exe` scraping engine — confirmed protocol

E-Invoice does **NOT** use a bank/REST integration. The browser drives a **local Windows engine (NXiSAS.exe)** that talks to Vietnam's GDT e-invoice portal and returns encrypted payloads.

- **Transport:** WebSocket Secure — `wss://ibasews.coocon.co.kr:14102/` (health: `https://ibasews.coocon.co.kr:14101/`). Event-driven (`onopen/onmessage/onerror/onclose`), no polling. (`isasscaping.js:18-28, 95-160`.)
- **Engine facade:** `CooconiSASNX` (`nxisas_api.js`) — `init()` → `open()` → `execute()`. Header built by `_v2nxisas.makeHeader(bankCd, seqno, jobType)`.
- **Commands (jobs) used by e-invoice:**
  - `LOGIN` (+ 2FA: `CAPTCHA` / `OTP` / `QNA`) — authenticate to GDT with `bankCd='VIETNAM_GDT'` + `loginID/loginPWD` from `GDT_CERT_INFO`.
  - `INVOICE_LIST` — `Input: { invoiceType: '1'(Sales)|'2'(Purchase), startDate, endDate, testResult }`. (`nxisas_einvoice.js:308-326`.)
  - `INVOICE_DETAIL` — `Input: { invoiceType, numericSymbol, invoiceSymbol, invoiceNo, startDate, endDate, sellerTaxCode }`. (`nxisas_einvoice.js:586-597`.)
  - **Note on names:** the question's `nxisas_einvoice` / `nxisas_einvhis` are the **action/web-service ids** (save list / call-history), not engine job strings. The engine job strings are `LOGIN`/`INVOICE_LIST`/`INVOICE_DETAIL`.
- **Engine response:** `{ Msg:"ONCOMPLETED", Progress, Output:{ ErrorCode, ErrorMessage, Result } }`. `Result` is `ENC=…` SEED-CBC ciphertext; success = `ErrorCode === "00000000"`. Decryption is **server-side** in `nxisas_einvoice_c001_act.jsp` via `ISASSeedCBC.decrypt(enc, Uid, Action)` → JSON `{ invoiceList: [ {checkResult, invoiceDate(YYYYMMDD), totalAmt, totalAmtWithoutTax, totalTaxAmt, invoiceNo, invoiceSymbol, numericSymbol, sellerTaxCode, invoiceStatus, invoiceInfo, totalDiscount} ] }`.
- **Install / OS check:** WSS connect failure ⇒ show "download & install" prompt → `location.href="./nxisas/file/NXiSAS.exe"`; macOS ⇒ unsupported message. (`nxisas_api.js:382, 565-621`.)
- **Progress UI:** `.process-bar > progress` stepped `25 → 35 → 50 → 100`; step label `nxisas_einvoice_j004` "In Progress (#{CMP_CNT}/#{ALL_CNT})"; completion `WBI1131`; optional auto-close `#_AUTO_CLOSE_YN`.
- **Licensing constraint (critical for refactor):** per memory `reference_nxisas_local_engine_origin.md`, the engine is **licensed per page origin** via Coocon's WSS handshake. `wecms.kosign.dev` is licensed; **`localhost` is NOT** → `ErrorCode 80000015` (unlicensed origin). The refactor is **local-only R&D, no bank/external integration** (memory `project_refactoring_scope.md` + both CLAUDE.md scope guards). ⇒ the scrape leg of this flow **cannot be runtime-verified in the refactor environment**.

---

## 3. Legacy list filter chain (`getDynamic0`, `einv_0001_001_r001_act.jsp:75-168`)

Applied filters (legacy source of truth for "search conditions"):
1. `issu_dt BETWEEN ? AND ?` — required.
2. `doc_type = ?` — `I` (Sales) / `B` (Expenses).
3. `einv_invoiceStatus = ?` — **optional invoice-status filter.**
4. `biz_key = ?` — tenant (or wire `BIZ_KEY` for `ChnlType.TYPE_T`).
5. `hand_reg_yn = 'N'`.
6. `(doc_id,biz_key) IN (SELECT … FROM busa_doc_detl …)` — only docs with line items.
7. `PROV_STTS NOT IN ('EE','VD')`.
8. `einv_testResults = ?` unless `'all'` — **symbol-type filter** (`''`=C / `'1'`=K / `'2'`=POS).
9. `TRCO_TRSC_CCY_CD = ?` — **optional currency filter.**
10. `PROV_STTS IN (…)` when a dashboard status is passed (`OP`→`OP/PP/OD`).
11. `SRCH_WD` → `SearchHelper.getSearchColumnDynamic(...)` (View-Setting-driven multi-column search; web_svc differs by `PrdtType`).

**Refactor coverage:** 1, 2, 4, 5, 7, and a fixed-5-column subset of 11 are implemented (`BusaDocMstrRepository.findFiltered`). **Gaps:** filter 3 (status), 8 (symbol-type), 9 (currency), 10 (dashboard status), and filter 6 (`busa_doc_detl` existence — explicitly deferred because `BUSA_DOC_DETL` has no local entity/schema yet).

---

## 4. Refactor implementation inventory

### 4.1 Backend (`wecms-api/.../domain/einvoice/`)
- `controller/EInvoiceController.java` — `GET /api/v1/addon/einvoices`, `POST /api/v1/addon/einvoices/export`.
- `service/EInvoiceService.java` — `list(...)`, `resolveDocType` (SALES→I, EXPENSE/BILL→B), `validateDateRange`, keyword pattern, row map. `service/EInvoiceSortField.java`.
- `repository/BusaDocMstrRepository.java` — `findFiltered`, `aggregateByCurrency` (+ card-link queries for a different feature).
- `entity/BusaDocMstr.java` (+`BusaDocMstrId`) — read subset of the 100-col table.
- `dto/EInvoiceRow.java`, `dto/EInvoiceListResponse.java`, `repository/EInvoiceCcyAggregate.java`.
- `export/{SalesEInvoiceExportProvider,BillEInvoiceExportProvider,EInvoiceExportProvider}.java`.
- Flyway: V15 (schema subset), V23 (+80 cols → 100), V28 (parity), V45/V49/V50/V52/V53/V54 (View Setting + seed), V51 (real seed).
- Tests: `EInvoiceServiceTest`, `EInvoiceExportProviderTest`.

**Reusable NXiSAS infra already present (banking):** `domain/banking/nxisas/` (`NxisasGatewayClient`, gateway job DTOs incl. `NxisasLoginJobInput`, session registry), `domain/banking/ownaccounttransfer/service/OatNxisasService.java` (ports `nxisas_decrypt`/`nxisas_logging`/`cert-acct-list`; **decrypt is a documented pass-through — Coocon SEED-CBC library is NOT bundled**), `ScrapingResultIntakeRequest` (the canonical "browser scraped, POST verbatim, server re-validates + persists" pattern), `bankcredentials/service/BankCertCryptoService`. GDT credential CRUD lives in `domain/setting/logincredentials` (per backend CLAUDE.md).

### 4.2 Frontend (`wecms-web/`)
- Routes `app/(dashboard)/addon/{sales,expenses}-einvoices/page.tsx`; shared `components/addon/.../EInvoicesPage.tsx` + table/footer/columns + `SalesEInvoiceActionBar` (Update/Excel/Print) + `InvoiceStatusFilter` (client-side) + stubs `EInvoiceHistoryDialog`, `UpdateEInvoiceModal`.
- API client `lib/api/addon.ts` (`fetchEInvoices`), wire types in `lib/types/api.ts`, status regex `lib/api/einvoiceStatus.ts` (documented IR-4 BLOCKER).
- Reusable local-engine infra: `lib/nxisas/nxisas_api.ts`, `public/nxisas/js/nxisas_v2.js`, `public/nxisas/file/NXiSAS.exe`, `components/banking/own-account-transfer/hooks/use-nxisas-engine.ts`.

---

## 5. Database tables touched
| Table | Use | Local status |
|---|---|---|
| `BUSA_DOC_MSTR` | invoice master (list read; scrape write) | ✅ schema (100 cols, V15+V23+V28), entity = read subset |
| `BUSA_DOC_DETL` | line items (legacy list filter #6, detail) | ❌ not in local schema (filter #6 deferred) |
| `EINV_FEE_DETL` / `EINV_TAX_DETL` | detail fee/tax | ✅ schema (V15), no entity |
| `EXT_TRAN_CALL_HIS` | scrape call audit | ✅ schema (V16), seed (V29); **no entity/repository** |
| `GDT_CERT_INFO` | GDT credentials | ✅ schema; consumed by `logincredentials` |
| `BUSA_DOC_MSTR_HIS` / `*_DETL_HIS` | invoice history (detail dialog) | ❌ not in local schema |

---

## 6. Open questions / risks

- **OQ-S1 (scope — RESOLVED 2026-06-03):** the lead chose **path A — full FE+BE scrape port**. Implemented (§8). The browser↔engine leg remains **runtime-unverifiable in R&D** because the engine is unlicensed on `localhost` (`ErrorCode 80000015`); end-to-end verification requires a licensed origin (e.g. `wecms.kosign.dev`). Compile/lint/typecheck/unit-test all green.
- **OQ-S2 (SEED-CBC) — RESOLVED 2026-07-14 (server-side decrypt, full legacy parity):** legacy decrypts engine output server-side via Coocon `ISASSeedCBC`. Diagnosis (PROD console, 2026-07-14): the GDT engine **always** returns SEED-CBC-encrypted `Output.Result`; the earlier refactor stance (`nonDecrypt:false` + a client-side pass-through `nxisas_decrypt` + a backend intake that expected already-plaintext rows) left every payload encrypted, so the frontend `parseResultArray` JSON-parsed ciphertext, threw, and yielded **zero rows** — the "scrape success, no data" bug. Fix: the Coocon jar **was already bundled** (`wecms-api/libs/isas1.0.jar`) and ported as `SeedCbcDecryptService` (promoted to `common/nxisas/` as the 2nd consumer). The e-Invoice flow now mirrors legacy exactly — the browser sets `nonDecrypt:true` and POSTs each raw engine callback to `POST /api/v1/addon/einvoices/decrypt` (`EInvoiceScrapeDecryptService`, decrypt unconditional), then the existing `extractScrapedInvoices` / `extractScrapedDetails` parse the returned plaintext. `parseResultArray` was extended to accept the server-decrypted object `Result` (in addition to the plaintext-string fallback). Still runtime-unverifiable end-to-end in R&D (engine unlicensed on `localhost`, OQ-S1) — but the decrypt boundary is no longer a deviation. **Note:** the OAT path keeps the flag-gated `wecms.oat.seed-decrypt.enabled` (VND payloads mostly arrive plaintext there); only the e-Invoice path decrypts unconditionally.
- **OQ-S3 (status localization, IR-4):** legacy `wb_get_invoice_status(value, LANG_CD)` is not ported; refactor returns raw `EINV_INVOICESTATUS` and the FE regex (`einvoiceStatus.ts`) derives the badge. Server-side status filtering (legacy filter #3) can't be cleanly added without resolving the status value vocabulary first — entangled with IR-4.
- **OQ-S4 (`BUSA_DOC_DETL`):** legacy list filter #6 and the detail popup both need `BUSA_DOC_DETL` (+ `*_HIS`), which are not in the local schema. Porting them is a prerequisite for full list parity + the detail dialog.
- **CLAUDE.md correction:** the `domain/einvoice` Feature Status row claims a `resolveDocType EXPENSE→'E'` bug — **stale**; current code maps EXPENSE→'B'. Update the row.

---

## 7. Engine commands used (quick reference for any future scrape port)
`LOGIN` · `CAPTCHA` · `OTP` · `QNA` · `INVOICE_LIST` · `INVOICE_DETAIL` (engine jobs over `wss://ibasews.coocon.co.kr:14102/`, `bankCd='VIETNAM_GDT'`). Save actions (not engine jobs): `nxisas_einvoice_c001` (list), `nxisas_einvoice_c002` (detail). The refactor implements `LOGIN` + `INVOICE_LIST` (the `c001` list path); 2FA (`CAPTCHA`/`OTP`/`QNA`) is handled by the shared `useNxisasEngine` bundle; `INVOICE_DETAIL` (`c002`) is not yet ported (gap, §8).

---

## 8. Implemented this pass (2026-06-03) — full FE+BE scrape port

### Backend (`wecms-api`) — verified: `./gradlew compileJava compileTestJava` ✅, `test --tests einvoice.*` ✅ (no regressions)
- **New:** `dto/ScrapedEInvoiceRow.java`, `dto/EInvoiceScrapeRequest.java`, `dto/EInvoiceScrapeResult.java`; `service/EInvoiceScrapeService.java`; `test/.../EInvoiceScrapeServiceTest.java` (13 cases).
- **Reused (not duplicated):** `EXT_TRAN_CALL_HIS` is already mapped by `domain/banking/synchistory` (`ExtTranCallHis` entity + `ExtTranCallHisRepository.findMaxSeqNoForDay`) — the scrape audit writes through that existing shared entity/repo. (An initial duplicate einvoice-side entity/repo caused a Spring bean-name + duplicate-`@Entity` collision and was removed.)
- **Edited:** `entity/BusaDocMstr.java` (+7 write columns: `HAND_DOC_ID`, `EINV_INVOICEINFO`, `TOTL_DIS_AMT`, `EINV_CHECKRESULTS`, `EINV_NUMERICSYMBOL`, `EINV_TESTRESULTS`, `USER_ID` — all DDL-confirmed in V23); `controller/EInvoiceController.java` (+`POST /api/v1/addon/einvoices/scrape-results`).
- **Behaviour ported from `nxisas_einvoice_c001_act.jsp`:** branch-A vs branch-B `checkResult` accept sets; `DOC_TYPE` (SALES→I / EXPENSE→B / branch-B forces B); `DOC_ID` = `numericSymbol+invoiceSymbol-invoiceNo` (+`-sellerTax` for expense / branch-B); `DISP_DOC_ID` = base; `ISSU_TAX_ID` = company `BUSI_INFM.TAX_ID` (SALES) / `99999999999999999999` (expense); `PROV_STTS` OP/VD per the two void-status sets; `HAND_REG_YN='N'`; one `EXT_TRAN_CALL_HIS` audit row (`GDT_INQUIRY_SALES`/`_EXPENSES`, slug `web_svc_id='addon-einvoices-scrape'`).
- **Deliberate refactor deviations (documented):** (1) **upsert** by `(BIZ_KEY, DOC_ID)` instead of legacy blind INSERT → idempotent re-scrape; (2) `SEQ_NO` via Java MAX+1 (no `wb_get_next_val` branch — backend DB-function policy); (3) ~~no Coocon SEED-CBC server-side — client posts decrypted structured rows~~ **superseded 2026-07-14** — SEED-CBC now runs server-side via `POST /api/v1/addon/einvoices/decrypt` → `common/nxisas/SeedCbcDecryptService` (full legacy parity; OQ-S2 resolved); (4) audit `web_svc_id` uses the refactor slug, not legacy `nxisas_einvoice_c001`.

### Frontend (`wecms-web`) — verified: `tsc --noEmit` ✅, `eslint` ✅, `npm run build` ✅
- **New:** `lib/nxisas/einvoiceScrape.ts` (`buildInvoiceListInput` + `extractScrapedInvoices` — port of `nxisas_einvoice.js`); `components/addon/sales-einvoices/useEInvoiceScrape.ts` (composes the shared `useNxisasEngine` local-engine loader/probe).
- **Edited:** `lib/types/api.ts` (+`ApiScrapedEInvoiceRow` / `ApiEInvoiceScrapeRequest` / `ApiEInvoiceScrapeResult`); `lib/api/addon.ts` (+`scrapeEInvoices`); `UpdateEInvoiceModal.tsx` (CTA now runs engine→reveal→scrape→POST with live progress bar + result count/amount; gates on engine-installed + GDT credential; re-auth via shared `PasswordConfirmationDialog`); `EInvoicesClient.tsx` (`onScraped` → refetch); `locales/{en,ko,vi}.json` (+6 keys each).
- **Flow:** Update button → `UpdateEInvoiceModal` (Figma scrape popup, already built) → "Real-Time Online Inquire" → engine-ready gate → `fetchLoginCredentials` GDT gate → WeCMS re-auth → `revealLoginCredential('gdt',…)` → `useNxisasEngine.runScraping([INVOICE_LIST])` → `extractScrapedInvoices` → `POST scrape-results` → success/partial/error dialog + list refetch.

### Remaining gaps (not in this pass)
- **`INVOICE_DETAIL` (`nxisas_einvoice_c002`)** — line-item / fee / tax detail scrape + `EINV_FEE_DETL`/`EINV_TAX_DETL` persistence; not ported (`BUSA_DOC_DETL` also absent locally, OQ-S4).
- **Symbol-type (C/K/POS) selector in the Update popup** — the legacy page passes `einv_testResults`; the refactor popup defaults to `''` (C). Surface the selector when needed.
- **Server-side list filters** (invoice-status / symbol / currency, §3) — still client-side / unported (OQ-S3 / IR-4).
- **End-to-end runtime verification** — blocked on a licensed engine origin (OQ-S1).
- **`SEQ_NO` race** — `EXT_TRAN_CALL_HIS` MAX+1 is in-transaction without retry; acceptable for single-user R&D scraping, revisit if concurrent scrapes per `(SVC_ID, REF_DATE)` become real.

---

## 9. Fix 2026-07-15 — counterparty (TRCO) address + issuer tax id stamped on the invoice master

**Symptom.** The "View e-Invoice Details" popup/PDF showed an empty seller `Địa chỉ:` (and a `99999999999999999999` buyer MST) on Expenses/Bill invoices.

**Root cause (refactor divergence from `nxisas_einvoice_c002`).** For a Bill the supplier is the counterparty → `TRCO_*`, own company is the buyer → `ISSU_*` (roles swapped by type at write time — this part matched legacy). But phase-2 (`EInvoiceDetailScrapeService`) wrote **only** the issuer address (`applyIssuerAddress` → `ISSU_BSUN_*`); it never stamped the counterparty address onto `BUSA_DOC_MSTR.TRCO_BSUN_*`. `EInvoiceTrcoAutoRegistrar` (D5.1) parsed the counterparty address into the `TRCO_INFM` *contact*, but the master was never populated — and the detail popup reads the **master** columns. Legacy copies the resolved contact's `BsunAddr` + `telNo` onto the master via `busaDocMstr.setTrcoTaxInfo(...)` (`c002:625-634`). Separately, the phase-1 `ISSU_TAX_ID='99999999999999999999'` Bill placeholder was never overwritten with the buyer's own-company tax id at detail time (legacy `c002:270`).

**Fix (`EInvoiceDetailScrapeService`).** After the issuer block, phase-2 now also:
1. stamps `TRCO_BSUN_ST/CITY/POST_ADRS/DTL_ADRS` from the counterparty address (`buyerAddr` for Sale / `sellerAddr` for Bill), reusing `EInvoiceTrcoAutoRegistrar.parseBusinessAddress` so master + contact share one `[st, city, postAdrs, dtlAdrs]` decomposition that recomposes cleanly in `EInvoiceDetailService.getDetail`'s `joinAddr`;
2. sets `TRCO_RPRS_TLPH` from `sellerPhone` (legacy `c002:632-634`);
3. overwrites `ISSU_TAX_ID` with the own-company tax id (`buyerTaxCode` Bill / `sellerTaxCode` Sale) when the detail provides it, guarded so a blank never wipes the placeholder (legacy `c002:270`).

New `applyCounterpartyAddress` is package-private `static` (same pattern as `applyDueDate`). Test: `EInvoiceCounterpartyAddressTest` (4 cases — full-address reconstruction, short address, blank-noop, null-noop). Verified `./gradlew test --tests einvoice.*` ✅.

**Scope caveats.** Fixes **future** scrapes only — existing rows keep NULL `TRCO_BSUN_*` until re-scraped (the raw GDT payload isn't stored). End-to-end re-scrape needs a licensed engine origin (OQ-S1; not localhost). A legitimately-NULL `TRCO_BSUN` can still occur — like legacy — when the supplier already existed as a contact with no stored address.
