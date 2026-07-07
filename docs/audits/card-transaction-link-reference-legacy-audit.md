# Card Transaction Link Reference Legacy Audit

- **System tag:** Refactor (`wecms-api` + `wecms-web`) vs Legacy JEX (source of truth)
- **Feature:** Payment > Corporate Card > Card Transactions → per-row **"Link"** ("Link Reference") → modal **"Link Card Payments with the Expenses"** (Expenses e-Invoice tab + Receipt tab + New Receipt sub-popup + GDT-login empty state)
- **Legacy WSVC family:** `card_0001_002` (link popup) · `card_0001_001` (list host) · `prch_0011_001` → `prch_0005_001_c001` (New Receipt) · `gdtc_0001_001_r001` + `comm_0218_007` (GDT) · IDO `BUSA_DOC_MSTR_R034`
- **Refactor route family:** `/payment/corporate-card/card-transactions` → `LinkCardPaymentsDialog` · API base `/api/v1/payment/card-transactions`
- **Date:** 2026-07-03 · **Status:** AUDIT ONLY — no code changed, no schema changed
- **Method:** legacy source-of-truth read first (JSP `_act`/`_view`, TS, IDO/WSVC XML, enums, DAO) → live JEX DEV schema spot-verified via `postgres_dev_server_jexframe` (read-only) → refactor backend (controller/service/repo/entity/DTO) + frontend (components/api client/types) read → compared.

---

## 1. Scope

Audited surfaces (as requested):

- Card Transactions list + the per-row "Link Reference" trigger and the params it passes.
- The "Link Card Payments with the Expenses" modal and its two tabs.
- Expenses e-Invoice tab (list load, search, date range, row selection, status handling, item dropdown).
- Receipt tab (list load, attachment list/count, New Receipt launcher).
- New Receipt popup (fields, Contacts/Manual toggle, save).
- Link e-Invoice / Link Receipt save (both share one action/endpoint).
- GDT login-info requirement flow (empty state + navigation).
- Backend/data: action/endpoint ids, SQL/tables, insert/update/delete, link columns, card↔doc matching, duplicate blocking, status eligibility, validation, error handling, refresh/close behavior.

Out of scope (mentioned only where it intersects): unlink flow (`card_0001_002_u001`/`DELETE /links/{card_seq_no}`), the main-list "Linked/Unlinked" grid filter (covered by the prior [card-transactions-legacy-vs-refactor-audit.md](card-transactions-legacy-vs-refactor-audit.md) D1), NXiSAS card scrape (covered by [corporate-card-transaction-scrape-legacy-vs-refactor-audit.md](corporate-card-transaction-scrape-legacy-vs-refactor-audit.md)).

---

## 2. Legacy Entry Points

| Concern | Legacy file / id |
|---|---|
| List host view | `WECMS DEV/WECMS/web/view/jex/wabooks/card/card_0001_001_view.jsp` |
| List host TS (Link trigger) | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/card/card_0001_001.ts` — `.link_card_payment` render `:1158-1166`, click handler `:533-542` |
| Link popup view | `WECMS DEV/WECMS/web/view/jex/wabooks/card/card_0001_002_view.jsp` (title ML `card_0001_002_m001`; tabs `m002`/`m003`; buttons `#btnNewReceipt` m016, `#linkReceipt` m017, `#linkEInvoice` m018) |
| Link popup TS | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/card/card_0001_002.ts` |
| e-Invoice list action | `.../action/jex/wabooks/card/card_0001_002_r001_act.jsp` (WSVC `card_0001_002_r001`) |
| Receipt list action | `.../card/card_0001_002_r002_act.jsp` (WSVC `card_0001_002_r002`) |
| Link save action | `.../card/card_0001_002_c001_act.jsp` (WSVC `card_0001_002_c001`) |
| Unlink/update/delete | `.../card/card_0001_002_u001_act.jsp`, `card_0001_002_d001_act.jsp` |
| Shared read IDO | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.BUSA_DOC_MSTR_R034.xml` (JSON `REC_1`=files from `BUSA_DOC_FILE`, `REC_2`=items from `BUSA_DOC_DETL`; status via `wb_get_invoice_status`) |
| New Receipt view/TS | `.../view/jex/wabooks/prch/prch_0011_001_view.jsp` · `.../ts-src/.../prch/prch_0011_001.ts` (Korean: 간이영수증 등록 = simple-receipt) |
| New Receipt save action | `.../action/jex/wabooks/prch/prch_0005_001_c001_act.jsp` (WSVC `prch_0005_001_c001` — **no** `prch_0011_001_c001` exists) |
| GDT config check | `.../action/jex/wabooks/gdtc/gdtc_0001_001_r001_act.jsp` (reads latest `GDT_CERT_INFO`) |
| GDT setting popup | `.../view/jex/wabooks/comm/comm_0218_007_view.jsp` (+ `comm_0218_007_c001_act.jsp`), opened via `openLayerPopup("comm_0218_007.act")` |

---

## 3. Legacy UI Flow

1. **Open modal.** Card list renders a `.link_card_payment` "Link" anchor in the `DISP_DOC_ID` column **only when the row has no linked docs** (`REC_1` null/empty, `card_0001_001.ts:1158-1166`). Click reads the row JSON (base64) and `openLayerPopup("card_0001_002.act", { JSON_DATA })` (`:533-542`). The popup decodes `CARD_SEQ_NO` from `JSON_DATA` (`card_0001_002.ts:378,405`).
2. **Params passed:** the whole selected card-transaction row JSON; the only key the save uses is `CARD_SEQ_NO`. `TRSC_AMT` from the row is the amount the linked docs must sum to.
3. **e-Invoice tab load** (`fetchEInvoiceData` `:514-743`) → WSVC `card_0001_002_r001` with `{ SRCH_WD, FROM_DT, TO_DT }`. Default range **Last 30 Days** (today−30 … today; `:41`, `:88-99`); presets 30/90/180/Manual.
4. **Receipt tab load** (`fetchReceiptData` `:745-963`) → WSVC `card_0001_002_r002`, same input shape; date filter uses `USE_DT` (not `ISSU_DT`).
5. **Search:** single `SRCH_WD` box; server ILIKE on `TRCO_CMNM`/`TRCO_CMENM`/`DISP_DOC_ID`.
6. **Row selection:** multi-select checkboxes; the tab's Link button (`#linkEInvoice`/`#linkReceipt`) enables only when ≥1 row is checked (`:138-141`).
7. **Item dropdown (e-Invoice):** from `REC_2` (`BUSA_DOC_DETL`); 1 item → text, >1 → first item + chevron `.dropdown-toggle-itemlist` expanding all `EINV_PRODUCTNAME` (`:590-645`).
8. **Attachment list (Receipt):** from `REC_1` (`BUSA_DOC_FILE`); paperclip + `(count)`, dropdown per file, download `prch_0006_001_r002.act?DOC_ID=&FILE_ID=` (`:802-869`).
9. **New Receipt:** `#btnNewReceipt` → `openLayerPopup("prch_0011_001.act")`; on save reloads `fetchReceiptData()` (`prch_0011_001.ts:555`).
10. **Link save** (`fn_linkEInvoice` `:376-422`, both tabs): `{ CARD_SEQ_NO, REC:[{DOC_ID, DEDU_AMT}] }`; success `WBI0200` then popup closes.
11. **GDT gate (e-Invoice tab only):** `checkwithGdtAPI` (`:424-456`) calls `gdtc_0001_001_r001`; if `CERT_INFO_CON`+`CERT_INFO_DETL_CON` not both non-blank → `renderLoginGdtNoData()` empty state (`:458-512`) with "Go to GDT Login Info Setting" → `comm_0218_007.act`.
12. **Close/cancel:** layer-popup close (Esc/X).

---

## 4. Legacy Backend Flow

**e-Invoice read** — `card_0001_002_r001_act.jsp` (IDO `BUSA_DOC_MSTR_R034`), `getDynamic0` `:59-105`:
- `BDM.BIZ_KEY = session bizKey`
- `BDM.DOC_TYPE = 'B'` (`BusaDocType.BILL` — GDT-scraped e-invoices)
- `BDM.HAND_REG_YN = 'N'`
- `(BIZ_KEY, DOC_ID) NOT IN (SELECT BIZ_KEY, DOC_ID FROM BUSA_DOC_RCV_PAY_HIS)` — **already-linked excluded**
- `PROV_STTS NOT IN ('EE','VD')` (draft / cancelled excluded)
- `BDM.ISSU_DT BETWEEN FROM_DT AND TO_DT`
- optional `SRCH_WD` ILIKE on `TRCO_CMNM`/`TRCO_CMENM`/`DISP_DOC_ID`

**Receipt read** — `card_0001_002_r002_act.jsp`, same IDO, `getDynamic0` `:55-101`: identical **except** `DOC_TYPE='E'` (`EXPENSE`), `HAND_REG_YN='Y'`, date on `USE_DT`.

**Link save** — `card_0001_002_c001_act.jsp`:
- Match card by `CARD_SEQ_NO` (+ bizKey) via `ValidationHelper.validateCardTrnsHstr` (`:83-87`).
- Match each doc by `DOC_ID` via `validateBusaDocMstr` (`:147-151`); re-check doc type must be EXPENSE or BILL, reject hand-registered BILL (`:155-165`).
- **Amount validation** (`:90-110`): `Σ DEDU_AMT` (each = invoice `TOTL_AMT`) must **exactly equal** card `TRSC_AMT` (scaled 6dp) → else `WbRollbackException(WBE0001, "DEDU_AMT")`.
- **Duplicate block** (`:118-123`): a `CARD_TRNS_RCV_PAY_INFM` row already for `(biz_key, card_seq_no)` → `WSE0409`. One link per card txn.
- Writes in one txn (`:128-194`): INSERT `CARD_TRNS_RCV_PAY_INFM` (master) + INSERT `BUSA_DOC_RCV_PAY_HIS` per doc (`RCV_PAY_WAY_CD='R'`, `REF_NO=CARD_SEQ_NO`, `DEDU_AMT`) + `BusaDocMstrDAO.updateProvStts(...)` (`:190`) to mark the invoice reconciled/paid.

**New Receipt save** — `prch_0011_001.ts` → WSVC `prch_0005_001_c001` (via file-uploader plugin); `prch_0005_001_c001_act.jsp` INSERTs `BUSA_DOC_MSTR` (`DOC_TYPE='E'`, `HAND_REG_YN='Y'`), `DOC_ID = SeqHelper.getNextVal(...,"doc_id")` (`:59-66`), plus `BUSA_DOC_FILE` attachments and payment-way children.

**GDT check** — `gdtc_0001_001_r001_act.jsp:31-38`: latest `GDT_CERT_INFO` for tenant (`seq_no = MAX(seq_no)`), returns `CERT_INFO_CON` + `CERT_INFO_DETL_CON`.

---

## 5. Legacy Database / SQL Behavior

| Table | Role in this flow | Ops |
|---|---|---|
| `BUSA_DOC_MSTR` | e-invoice (`B`) / manual receipt (`E`) master | SELECT (both tabs), INSERT (New Receipt), UPDATE `PROV_STTS` (link) |
| `BUSA_DOC_RCV_PAY_HIS` | **the doc↔card link row** — `REF_NO=CARD_SEQ_NO`, `RCV_PAY_WAY_CD='R'` | INSERT (link), also the "already-linked" `NOT IN` exclusion source |
| `CARD_TRNS_RCV_PAY_INFM` | link master, PK-scoped `(BIZ_KEY, CARD_SEQ_NO[, RCV_PAY_SEQ_NO])` | INSERT (link), existence check for duplicate block |
| `CARD_TRNS_HSTR` | card transaction, keyed `(BIZ_KEY, CARD_SEQ_NO)`; amount `TRSC_AMT` | SELECT (match target) |
| `BUSA_DOC_DETL` | e-invoice line items → `REC_2` item dropdown | SELECT (aggregate) |
| `BUSA_DOC_FILE` | attachments → `REC_1` paperclip/count | SELECT (aggregate) |
| `GDT_CERT_INFO` | GDT credential (config gate) | SELECT (latest) |
| `USAG_INFM(_LANG_DETL)` | Purpose dropdown (grp 6) | SELECT |

Key SQL facts:
- Card↔doc match key: **`BUSA_DOC_RCV_PAY_HIS.REF_NO = CARD_TRNS_HSTR.CARD_SEQ_NO` with `RCV_PAY_WAY_CD='R'`.**
- Duplicate links blocked: yes (`CARD_TRNS_RCV_PAY_INFM` existence → `WSE0409`).
- Cancelled/Replaced/Adjustment e-invoices: **linkable** — the query excludes only `PROV_STTS ∈ {EE,VD}` and already-linked docs; it does **not** filter on `EINV_INVOICESTATUS`.
- Validated on link: **amount sum only** (exact). No currency / date / merchant / tax-id cross-check.
- Legacy receipt `DOC_ID` format (verified on JEX DEV): `R-yyyyMMddNNN` (e.g. `R-20260629003`) — `R-` + date8 + 3-digit per-day sequence via `wb_get_next_val('busa_doc_mstr','doc_id',…)`. (Scraped bills use a bank composite id, e.g. `1K25TCA-162810-0100686174-108`.)

---

## 6. Refactor Entry Points

**Frontend** (`wecms-web`):
| Concern | File |
|---|---|
| List host / Link trigger | `components/payment/corporate-card/CardTransactionsPage.tsx` (`linkRow` state `:200`; set `:905`; render `:1018-1029`) + `CardTransactionsTable.tsx` (`onLinkRefClick` `:149-153`, cell `:570-575`) |
| Modal | `components/payment/corporate-card/LinkCardPaymentsDialog.tsx` |
| New Receipt | `components/payment/corporate-card/NewReceiptDialog.tsx` |
| GDT sub-popup | `components/shared/popups/GdtAccountManagementDialog.tsx` |
| API client | `lib/api/cardTransactions.ts` (BASE `/api/v1/payment/card-transactions`) |
| Wire types | `lib/types/api.ts` (`ApiLinkable*`, `ApiLink*`, `ApiCreateReceipt*`) |
| Fetch hook | `lib/api/useApiData.ts` (no react-query) |

**Backend** (`wecms-api`, package `domain/payment/cardtransactions`):
| Concern | File |
|---|---|
| Controller | `controller/CardTransactionsController.java` (class-gated `@PreAuthorize("@menuAuthz.canAccess('payment-corporate-card-transactions')")`) |
| Service | `service/CardTransactionsService.java` (`linkExpenses` `:1131-1235`, `createReceipt` `:1551-1602`, `unlinkExpenses` `:1619-1645`) |
| Link repos | `repository/CardTrnsRcvPayInfmRepository.java`, `repository/BusaDocRcvPayHisRepository.java` (`findLinkableEInvoices` `:147-186`, `findLinkableReceipts` `:205-246`) |
| e-invoice entity/repo | `domain/einvoice/entity/BusaDocMstr.java`, `domain/einvoice/repository/BusaDocMstrRepository.java` |
| DTOs | `dto/LinkExpensesRequest.java`, `dto/CreateReceiptRequest.java`, `dto/Linkable*Row/…ListResponse`, `dto/PurposeOption.java` |
| Key funnel | `common/legacy/LegacyFunctionRepository.nextBusaDocRcvPayHisSeqNo` |
| GDT config source | `domain/setting/logincredentials` (`GdtCertInfoRepository`, surfaced by `GET /api/v1/login-credentials`) |

---

## 7. Refactor Flow

1. **Open modal:** row click sets `linkRow`; `<LinkCardPaymentsDialog row … />` renders; `onLinked` deliberately does **not** close the popup (refreshes card list); modal reads `row.cardSeqNo`, `row.trscAmtRaw`, `row.ccyCd`.
2. **e-Invoice list:** `GET /links/e-invoices?from_date&to_date&search&page&size(50)` → `findLinkableEInvoices` (`DOC_TYPE='B'`, `HAND_REG_YN='N'`, `PROV_STTS NOT IN ('EE','VD')`, `ISSU_DT BETWEEN`, `NOT EXISTS` on `BUSA_DOC_RCV_PAY_HIS`, search on `TRCO_CMNM/TRCO_CMENM/DISP_DOC_ID`).
3. **Receipt list:** `GET /links/receipts` → `findLinkableReceipts` (`DOC_TYPE='E'`, `HAND_REG_YN='Y'`, dates on `COALESCE(USE_DT,ISSU_DT)`, search also matches `BUSA_USAG_CD`).
4. **Selection:** multi-select `selectedDocIds`; Link button enabled when `selectedCount>0 && !submitting`.
5. **Link save:** client sums selected `totl_amt` and pre-checks against `row.trscAmtRaw` (`WBE_LINK_AMOUNT_MISMATCH` dialog) → `POST /links { card_seq_no, items:[{doc_id, dedu_amt}] }` → `linkExpenses`: exact `TRSC_AMT == Σ deduAmt` (`:1152`), `WBE_LINK_ALREADY_LINKED` if master exists (`:1158`), `findLinkableByDocIds` re-validates each doc (`WBE_LINK_DOCS_UNAVAILABLE`), then INSERT `CARD_TRNS_RCV_PAY_INFM` master + `BUSA_DOC_RCV_PAY_HIS` per doc (`RCV_PAY_WAY_CD='R'`, `REF_NO=cardSeqNo`, `RCV_PAY_HIS_SEQ_NO` via `wb_get_next_val`). **No `updateProvStts`.**
6. **Refresh after save:** success dialog; on OK the popup closes; card list refreshed via `onLinked`. New Receipt success bumps `refreshKey` so the receipt list re-fetches (new row immediately linkable).
7. **New Receipt:** `POST /receipts { use_dt, trco_key?, contact_name, tax_id?, amount, ccy_cd?("VND"), purpose_cd?, description? }` → `createReceipt` INSERT `BUSA_DOC_MSTR` (`DOC_TYPE='E'`, `HAND_REG_YN='Y'`, `PROV_STTS='OP'`); `DOC_ID = "R"+yyyyMMddHHmmssSSS+random(3)` (`:1560-1561`); manual mode → `TRCO_KEY` sentinel `"99999999999999999999"`.
8. **GDT gate:** `GET /api/v1/login-credentials`; empty state shown when no group has `type==="GDT"`. "Go to GDT Login Info Setting" opens `GdtAccountManagementDialog` in place (not a route).

---

## 8. Legacy vs Refactor Comparison Table

| Area | Legacy behavior | Refactor behavior | Status | Action needed |
|---|---|---|---|---|
| Modal open / params | List `.link_card_payment` → popup with row JSON; uses `CARD_SEQ_NO` | `linkRow` → `LinkCardPaymentsDialog`; uses `cardSeqNo` | Legacy parity | none |
| Link shown only when unlinked | Yes (`REC_1` empty) | Yes (unlinked rows show "Link"; linked show doc id + unlink) | Legacy parity | none |
| e-Invoice filter | `DOC_TYPE='B'`, `HAND_REG_YN='N'`, `PROV_STTS∉{EE,VD}`, `ISSU_DT`, not-already-linked, search 3 cols | Same predicates (native query `:147-186`) | Legacy parity | none |
| Receipt filter | `DOC_TYPE='E'`, `HAND_REG_YN='Y'`, `USE_DT`, same exclusions | Same (+ search also matches `BUSA_USAG_CD`; dates `COALESCE(USE_DT,ISSU_DT)`) | Legacy parity (minor superset) | none |
| Default date range | Last 30 Days (today−30…today) | Last 30 Days preset (walks back 30d) | Legacy parity | verify off-by-one only if strict |
| Search | single box, ILIKE server-side | single box, server-side (ESCAPE `!`) | Legacy parity | none |
| Row selection | multi-select checkbox | multi-select checkbox | Legacy parity | none |
| Link button enable | ≥1 checked | `selectedCount>0 && !submitting` | Legacy parity | none |
| Amount validation | exact `Σ DEDU_AMT == TRSC_AMT` (`WBE0001`) | exact `compareTo` (`WBE_LINK_AMOUNT_MISMATCH`) + client pre-check | Legacy parity | none |
| Duplicate link block | `WSE0409` (one per card) | `WBE_LINK_ALREADY_LINKED` | Legacy parity | none |
| Link write tables | `CARD_TRNS_RCV_PAY_INFM` + `BUSA_DOC_RCV_PAY_HIS` (`REF_NO`, `WAY_CD='R'`) | same two tables, same columns | Legacy parity | none |
| Cancelled/Replaced e-invoice selectable | Yes (only `PROV_STTS` EE/VD excluded) | Yes (same) | Needs business confirmation | see §9 L1 |
| Currency check on link | none | none | Needs business confirmation | see §9 L2 |
| `PROV_STTS` update on link | `updateProvStts` marks invoice reconciled | **Not performed** (deferred) | Refactor gap / Needs confirmation | see §10 G1 |
| Item dropdown (e-Invoice) | `REC_2` from `BUSA_DOC_DETL`, chevron list | Backend returns **no item data**; FE `items?` never populated | Refactor bug (gap) | see §10 G2 |
| Attachment paperclip/count (Receipt) | `REC_1` from `BUSA_DOC_FILE`, count + download | Backend returns none; FE column blank (deferred) | Refactor gap | see §10 G3 |
| New Receipt: Project/Site | `PRJT_NO` captured | Field absent (FE + BE) | Refactor gap / Needs confirmation | see §10 G4 |
| New Receipt: Payment Method | `PAY_WAY_CD` captured | Dropdown "None" only; not persisted | Refactor gap / Needs confirmation | see §10 G4 |
| New Receipt: attachments | saved to `BUSA_DOC_FILE` | Client-side only; not sent/persisted | Refactor gap | see §10 G5 |
| New Receipt save path | `prch_0005_001_c001` (full expense + payment-way children) | `POST /receipts` writes **only** `BUSA_DOC_MSTR` | Safe difference (simplified) / confirm | see §10 G6 |
| Receipt `DOC_ID` | `R-yyyyMMddNNN` via `wb_get_next_val` (daily seq) | `R`+17-digit timestamp+random(3), bypasses funnel | Needs business confirmation (migration + funnel rule) | see §10 G7 |
| Contacts/Manual toggle | toggle; sentinel `TRCO_KEY="9…9"`; name→`TRCO_CMNM` | same (sentinel + name→`TRCO_CMNM`) | Legacy parity | none |
| Purpose dropdown | USAG grp 6 | `GET /purpose-options?grp_cd=6` | Legacy parity | none |
| GDT config source | `GDT_CERT_INFO` (both cert fields non-blank) | `GDT_CERT_INFO` row present (login-credentials list) | Safe difference (mechanism) | see §9 L3 |
| GDT "Go to Setting" nav | opens `comm_0218_007` popup | opens `GdtAccountManagementDialog` (same GDT flow) | UI-only difference | none |
| Refresh after link | success msg → close popup | success dialog → OK closes; list refreshed, popup stays until OK | UI-only difference | none |
| Close/cancel | layer-popup Esc/X | Esc/X | Legacy parity | none |
| Tenant scope | `BIZ_KEY` from session on every query/write | `TenantContext.currentBizKey()` everywhere | Legacy parity | none |

---

## 9. Bugs / Incorrect Legacy Logic

Real issues found in **legacy** (do not blindly copy):

- **L1 — Cancelled / Replaced e-invoices are linkable. [Needs business confirmation — possible legacy bug]** The e-invoice query excludes only `PROV_STTS ∈ {EE,VD}` and already-linked docs; it does **not** exclude by `EINV_INVOICESTATUS`. A GDT invoice whose status is *Cancelled*, *Replaced*, or *Adjusted* can still be selected and linked as the justification for a card payment (`card_0001_002_r001_act.jsp:59-105`; refactor mirrors it at `BusaDocMstrRepository.findLinkableEInvoices:147-186`). This may be intentional (status is display-only; `PROV_STTS` is the real lifecycle gate) or a genuine oversight. The refactor reproduces it faithfully — **confirm with PO before treating it as correct.** If wrong, fix **both** sides together; do not silently diverge.

- **L2 — No currency guard on the amount match. [Needs business confirmation — possible legacy bug]** The link validates `Σ DEDU_AMT == TRSC_AMT` numerically with no currency comparison between the card transaction and the linked docs (`card_0001_002_c001_act.jsp:90-110`; refactor `CardTransactionsService:1146-1154`). A VND card amount and a differently-denominated doc that happen to share the same numeric value would pass. Collision is unlikely in practice, but the design has no currency assertion. The refactor inherits this. Confirm whether a currency-equality check should be added on both sides.

- **L3 — GDT "configured" definition differs subtly (mechanism, not a bug per se). ✅ RESOLVED 2026-07-04 (see §11a).** Legacy treats GDT as configured only when the latest `GDT_CERT_INFO` row has **both** `CERT_INFO_CON` and `CERT_INFO_DETL_CON` non-blank; the refactor treats GDT as configured when **any** `GDT_CERT_INFO` row exists for the biz_key. **PO-confirmed the refactor's presence-by-biz_key check is the intended behavior** — do not require the both-cert-fields-non-blank test. Business-confirmed safe difference; no change.

No other incorrect legacy logic found in this flow. The link/duplicate/amount/tenant behavior is internally consistent and correct.

---

## 10. Refactor Gaps

Behavior present in legacy but missing/changed in the refactor:

- **G1 — `PROV_STTS` not advanced on link. [Refactor gap — deferred by design; confirm]** Legacy calls `BusaDocMstrDAO.updateProvStts(...)` on link to mark the invoice reconciled/paid (`card_0001_002_c001_act.jsp:190`); the refactor deliberately skips it and treats link-state as authoritative via `BUSA_DOC_RCV_PAY_HIS` presence (`CardTransactionsService:1126`, unlink `:1614`). Self-consistent within the refactor, but any **other** screen/report that reads `BUSA_DOC_MSTR.PROV_STTS` to infer reconciliation, and any JEX↔refactor coexistence, will disagree. Migration-safety impact: confirm no consumer depends on `PROV_STTS` being advanced.

- **G2 — e-Invoice item list is not returned. [Refactor bug]** Legacy renders line items (`REC_2` from `BUSA_DOC_DETL`, `EINV_PRODUCTNAME`) with the expand chevron; the screenshots show real item names. The refactor `LinkableEInvoiceRow` has no items field and `findLinkableEInvoices` does not join `BUSA_DOC_DETL`, so the FE `ItemsCell` (which renders `row.items`) always shows blank. The Items column is effectively non-functional. **Backend must project item names (`BUSA_DOC_DETL`) to restore parity.**

- **G3 — Receipt attachment paperclip + count not shown. [Refactor gap — deferred]** Legacy shows `REC_1` (`BUSA_DOC_FILE`) count + per-file download. The refactor FE leaves the column empty (comment: no file metadata on the wire) and the backend returns none. Restore requires a file projection (`BUSA_DOC_FILE`) on the linkable-receipt (and e-invoice) rows.

- **G4 — New Receipt Project/Site + Payment Method. ✅ RESOLVED 2026-07-04 (see §11a).** **Project/Site:** WeCMS has no Project/Site concept — PO-confirmed it is **intentionally not implemented**; the refactor `NewReceiptDialog` correctly omits it. **Payment Method:** resolved by the card link/payment layer (`BUSA_DOC_RCV_PAY_HIS.RCV_PAY_WAY_CD='R'` at link), not persisted at receipt-create — PO-confirmed. No code change.

- **G5 — New Receipt attachments not persisted. [Refactor gap]** Legacy saves attached files to `BUSA_DOC_FILE`. The refactor collects attachments client-side only; `POST /receipts` accepts no files and writes none. (Pairs with G3 — without persistence there is nothing to count/download later.)

- **G6 — New Receipt writes a thinner record. [Safe difference / confirm]** Legacy routes New Receipt through the full expense-creation service `prch_0005_001_c001` (writes `BUSA_DOC_MSTR` + payment-way children). The refactor `createReceipt` writes only a `BUSA_DOC_MSTR` row (`DOC_TYPE='E'`, `PROV_STTS='OP'`) meant to be linked afterward. Likely acceptable given the link step creates the payment-way row, but confirm no downstream expectation of the legacy child rows at receipt-create time.

- **G7 — Receipt `DOC_ID` diverges from legacy and bypasses the key funnel. [Needs business confirmation — migration safety + funnel-rule deviation]** Legacy `busa_doc_mstr.doc_id` for manual receipts is `R-yyyyMMddNNN` (verified on JEX DEV: `R-20260629003`) generated by `wb_get_next_val('busa_doc_mstr','doc_id',…)`. The refactor generates `"R"+yyyyMMddHHmmssSSS+random(3)` (`CardTransactionsService:1560-1561`), which (a) has a **different format** from legacy, and (b) **bypasses the `wb_get_next_val` funnel** that the project's key-generation rule mandates (`busa_doc_mstr.doc_id` has a live DEV branch; Sprint-1 audit lists it). Both start with `R` so legacy and refactor rows coexist, but this is structural drift + a low-probability collision on the random suffix. Recommend routing receipt `DOC_ID` through `LegacyFunctionRepository`/`wb_get_next_val` to match legacy format — pending PO/architecture decision (the only sanctioned Java-side `doc_id` generator exception today is `EXPT_RESOL_MSTR.doc_no`, not `busa_doc_mstr.doc_id`).

---

## 11. Recommended Fix Plan

Audit only — no implementation performed. Suggested ordering once approved:

**Confirm with PO (blocking questions) — ALL RESOLVED (see §11a):**
1. L1 — Exclude Cancelled/Replaced/Adjusted e-invoices? → **YES, implemented.**
2. L2 — Add a currency-equality guard? → **YES, implemented.**
3. G4 — Project/Site & Payment Method on card-linked receipts? → **Project/Site intentionally NOT implemented; Payment Method handled at the card-link layer (2026-07-04).**
4. G7 — Route receipt `DOC_ID` through the funnel? → **YES — via `sys_doc_id_gen` (2026-07-04).**
5. G1 — Advance `PROV_STTS` on link? → **YES, implemented (reset to OP on unlink).**
6. L3 — GDT "configured" = presence-by-biz_key (not both-cert-fields)? → **Confirmed acceptable (2026-07-04).**

**Refactor fixes (once confirmed):**
- **G2 (bug, high):** project e-invoice line items (`BUSA_DOC_DETL.EINV_PRODUCTNAME`) into `LinkableEInvoiceRow.items` so the Items column populates. API + FE in one MR (full-stack ownership).
- **G3 + G5 (attachments):** implement against **`BUSA_DOC_FILE`** (multi-file, legacy source of truth) — **PO decision 2026-07-04 (final; supersedes the 2026-07-03 single-file note).** `busa_doc_file` is now in the inventory. Full plan in **§13**; stays blocked until the schema DDL + `file_id` funnel branch are applied to the env DBs.
- **G7:** switch `createReceipt` `DOC_ID` to the funnel to restore format + funnel-rule compliance.
- **G1/G6:** decide `PROV_STTS` handling; if required, add `updateProvStts` on link and its reset on unlink.

**Verify only — now resolved:** L3 (GDT presence-by-biz_key confirmed) and G4 (Project/Site not-implemented; Payment Method at card-link layer) were PO-confirmed 2026-07-04 — no code change needed, current behavior matches.

---

## 11a. Remediation status (2026-07-03)

Implementation pass on PO direction (team lead Sonny) to fix the "correct-first" items. Audit-only status above superseded for these rows.

| Item | Status | What shipped |
|---|---|---|
| **L1** — invalid e-invoice status linkable | ✅ **Implemented** | `EInvoiceLinkStatuses` exclude-list (5 superseded VN statuses; Original + null stay linkable). Applied to the list query (`findLinkableEInvoices`), the save-path gate (`findLinkableByDocIds`), and an explicit save-time check → `WBE_LINK_INVALID_STATUS`. **Filters the real stored `EINV_INVOICESTATUS` DB values directly — no `wb_get_invoice_status(...)` DB-function dependency** (PO direction 2026-07-03: prefer stored-value filtering). If a future need forces localisation-based filtering, `wb_get_invoice_status` would have to be added to the DEV refactor DB and documented as a dependency — not required today. |
| **L2** — no currency guard | ✅ **Implemented** | Save-time guard: card `CCY_CD` vs doc `TRCO_TRSC_CCY_CD` (normalized); concrete mismatch → `WBE_LINK_CURRENCY_MISMATCH` (blank-either-side skipped, preserves legacy data). FE pre-check mirrors it. |
| **G1** — PROV_STTS not updated | ✅ **Implemented** | Link advances each doc to `PA` (full) / `PP` (partial) mirroring legacy `updateProvStts`; unlink resets to `OP`. Already-linked gate still keys on `BUSA_DOC_RCV_PAY_HIS` presence. |
| **G2** — e-invoice items missing | ✅ **Implemented** | Backend batch-projects `BUSA_DOC_DETL` (`EINV_PRODUCTNAME` ⁄ `ITEM_NM`) into `LinkableEInvoiceRow.items`; FE already renders 1-item / multi-item + chevron. |
| **G7** — receipt DOC_ID off-format + funnel bypass | ✅ **Implemented (no DBA branch needed)** — **corrected 2026-07-04 to `SYS_DOC_ID_GEN`** | `createReceipt` calls `LegacyFunctionRepository.nextSysDocIdGenDocId(bizKey,'E')` → `wb_get_next_val('sys_doc_id_gen','doc_id',biz_key,doc_type)` (V140 **#48**, already present — **no DBA branch script required**), yielding legacy **`R-yyyyMMddNNN`** (dash after R, e.g. `R-20240523005`). **Not** a `busa_doc_mstr` max — that approach + its DBA script (`BUSA_DOC_MSTR_20260703.sql`) are **RETRACTED**. Because the #48 branch is a read-only `max+1` over the single daily `sys_doc_id_gen` row (PK `(biz_key,doc_type,req_date)`), `createReceipt` also **upserts** that row's `doc_id` with the minted id (`SysDocIdGenRepository.upsertDailyDocId`; `req_date` = the id's own embedded UTC `yyyyMMdd`) so same-day receipts increment instead of colliding on `001` — matching legacy population (334 `'E'` rows on JEX DEV). Fails fast (`WBE_RECEIPT_DOCID_UNAVAILABLE`) if the branch returns null/short. Test pins `matches("R-\\d{8}\\d{3}")` + the upsert call. `sys_doc_id_gen` is in the inventory (line 122) + created by `V90`. |
| **L3** — GDT "configured" definition | ✅ **RESOLVED — business-confirmed safe difference (PO 2026-07-04)** | Decision: the "Please set up your GDT login information…" empty state shows **only when the biz_key has NO GDT Login Credentials data**; when GDT data exists, show the Expenses e-Invoice list. "Go to GDT Login Info Setting" opens the existing Login Credentials / GDT account-management flow (local scraping-exe engine). The refactor's `items.some(g => g.type === 'GDT')` presence check by biz_key is **acceptable** — it deliberately does **not** require the legacy `CERT_INFO_CON` + `CERT_INFO_DETL_CON` both-non-blank test. **No code change** — current behavior already matches. |
| **G3 / G5** — receipt attachments | ✅ **IMPLEMENTED 2026-07-04** (`BUSA_DOC_FILE`, multi-file) | DB prereq confirmed applied to the DEV refactor DB (table + `busa_doc_file.file_id` branch returns `1`, §13.0a). Full-stack shipped mirroring the Contacts template + shared `SecureFileDownload` + `lib/file.ts`: `BusaDocFile` entity/id/repo, `nextBusaDocFileFileId` funnel, `CardTransactionsFileStorageProperties`/`Resolver`/`Config` (`wecms.card-transactions.storage-root`), `ReceiptFileRow` DTO + `file_count`/`files[]` on both link rows, upload (one-shot retry) / list / download / delete endpoints, FE paperclip+count+dropdown on the Receipt tab + New-Receipt multipart upload. Multi-file preserved (not collapsed to `BUSA_DOC_MSTR` columns). Tests green. See §13. |
| **G4** — New Receipt Project/Site + Payment Method | ✅ **RESOLVED (PO 2026-07-04)** | **Project/Site — intentionally NOT implemented:** WeCMS does not have Project/Site; do not add it to New Receipt. Current code has no Project/Site field — matches. **Payment Method — handled by the card link/payment layer, not New Receipt:** the payment method resolves to Card at link time (`BUSA_DOC_RCV_PAY_HIS.RCV_PAY_WAY_CD='R'`, written by the link save), so there is no create-time payment-method persistence and no `pay_way_cd` column is needed on `busa_doc_mstr`. Do not add unrelated Project/Site logic. **No code change** — current behavior already matches. |

---

## 12. Evidence

**Legacy (`WECMS DEV/`):**
- List Link trigger: `WECMS_STATIC/ts-src/js/jex/wabooks/card/card_0001_001.ts:1158-1166` (render), `:533-542` (open popup).
- Popup TS: `WECMS_STATIC/ts-src/js/jex/wabooks/card/card_0001_002.ts` — `CARD_SEQ_NO` decode `:378,405`; e-invoice load `:514-743`; receipt load `:745-963`; date default `:41,88-99`; selection→enable `:138-141`; item dropdown `:590-645`; attachment list `:802-869`; link save `fn_linkEInvoice:376-422`; GDT check `:424-456`, empty state `:458-512`; New Receipt launch `:148-156`; GDT nav `:158-160`.
- e-Invoice read: `web/WEB-INF/action/jex/wabooks/card/card_0001_002_r001_act.jsp:52` (IDO), `:59-105` (`getDynamic0`); Receipt read: `card_0001_002_r002_act.jsp:55-101`.
- Link save: `card_0001_002_c001_act.jsp:83-87` (card match), `:90-110` (amount), `:118-123` (dup `WSE0409`), `:128-194` (writes), `:190` (`updateProvStts`).
- New Receipt: `WECMS_STATIC/ts-src/js/jex/wabooks/prch/prch_0011_001.ts` — fields `createExpense():471-541`, Contacts/Manual `:386-425`; save via `prch_0005_001_c001`; `prch_0005_001_c001_act.jsp:58-66` (`DOC_ID` via `SeqHelper.getNextVal`).
- GDT: `web/WEB-INF/action/jex/wabooks/gdtc/gdtc_0001_001_r001_act.jsp:31-38`; setting popup `comm_0218_007_view.jsp`.
- IDO: `WECMS_ETC/xml/service/IDO/IDO.BUSA_DOC_MSTR_R034.xml` (`REC_1`=`BUSA_DOC_FILE`, `REC_2`=`BUSA_DOC_DETL`, `wb_get_invoice_status`).
- Enums: `src/wabooks/enums/BusaDocType.java` (`I/B/E/S`), `RcvPayWayCd.java` (`CARD='R'`), `ProvStts.java` (`VD` cancelled, `EE` draft).
- Live DEV DOC_ID format: `busa_doc_mstr` sample — `R-20260629003` (manual E), `1K25TCA-…` (scraped B) [`postgres_dev_server_jexframe`, read-only].

**Refactor backend (`wecms-api`):**
- Controller: `domain/payment/cardtransactions/controller/CardTransactionsController.java` — e-invoice list `:637-645`, receipt list `:657-665`, link `:676-680`, unlink `:687-693`, create receipt `:702-706`, receipt detail `:715-720`, purpose `:731-735`.
- Service: `.../service/CardTransactionsService.java` — link `:1131-1235` (amount `:1152`, dup `:1158`, docs `:1168`, seq `:1204`), `updateProvStts` deferred `:1126`, create receipt `:1551-1602` (`DOC_ID` `:1560-1561`, `PROV_STTS='OP'` `:1585`), unlink `:1619-1645`.
- Repos: `.../repository/BusaDocRcvPayHisRepository.java` (`findLinkableEInvoices:147-186`, `findLinkableReceipts:205-246`), `CardTrnsRcvPayInfmRepository.java` (`existsByBizKeyAndCardSeqNo`).
- Entities: `.../entity/CardTrnsRcvPayInfm.java`, `.../entity/BusaDocRcvPayHis.java` (`REF_NO`, `RCV_PAY_WAY_CD`), `domain/einvoice/entity/BusaDocMstr.java`.
- DTOs: `.../dto/LinkExpensesRequest.java`, `CreateReceiptRequest.java`, `LinkableEInvoiceRow/…ListResponse`, `LinkableReceiptRow`, `PurposeOption`.
- Error codes: `messages.properties` — `WBE_LINK_AMOUNT_MISMATCH`, `WBE_LINK_ALREADY_LINKED`, `WBE_LINK_DOCS_UNAVAILABLE`, `WBE_NOT_FOUND`; audit `WSVC_ID="card-transactions"`.
- GDT: `domain/setting/logincredentials` (`GdtCertInfoRepository`, `LoginCredentialsService:207-208` GDT group).
- Inventory: all tables present in `docs/references/database/legacy-table-inventory.md` (`BUSA_DOC_MSTR`, `BUSA_DOC_RCV_PAY_HIS`, `CARD_TRNS_RCV_PAY_INFM`, `CARD_TRNS_HSTR`, `GDT_CERT_INFO`, `USAG_INFM*`). `BUSA_DOC_DETL`/`BUSA_DOC_FILE` (needed for G2/G3) must be re-checked against the inventory before an entity is added.

**Refactor frontend (`wecms-web`):**
- `components/payment/corporate-card/CardTransactionsPage.tsx:200,905,1018-1029`; `CardTransactionsTable.tsx:149-153,570-575`.
- `components/payment/corporate-card/LinkCardPaymentsDialog.tsx` — tabs `:316-329`; e-invoice fetch `:127-133`; link handler `:179-234` (amount pre-check `:183-195`, payload `:199-205`); status color/localize `:830-850`; item cell `:636-708`; receipt fetch `:135-141`; attachment/payment-method cols blank `:931-942`; GDT gate `:151-154`, empty state `:600-609,760-787`, nav `:250-252,440-446`.
- `components/payment/corporate-card/NewReceiptDialog.tsx` — required fields `:176,197,280`; Contacts/Manual `:221-252`; Project/Site omitted `:40-42`; save `:117-157` (`ccy_cd` hardcoded VND `:135`); attachments client-only `:328-329`.
- API client: `lib/api/cardTransactions.ts:504-599`; wire types `lib/types/api.ts:4145-4229`.

---

## 12a. UAT legacy `wb_get_next_val` evidence (source of truth for key generation)

Supplied from the **UAT legacy `wb_get_next_val`** (relayed by PO/dev 2026-07-04) and cross-checked against JEX DEV. These are the authoritative branch bodies the refactor mirrors — do not derive receipt ids from a `busa_doc_mstr` max.

1. **`busa_doc_file.file_id`** — `$1='busa_doc_file'`, `$2='file_id'`, `$3=biz_key`, `$4=doc_id`:
   ```sql
   select coalesce(max(file_id::numeric), 0) + 1
   from busa_doc_file
   where biz_key = $3 and doc_id = $4;
   ```
   Per-document sequence 1, 2, 3… → refactor DBA script `Claude Code (AI)/sql/BUSA_DOC_FILE_20260704.sql` (Step 2). Consumed (when G3/G5 ships) by `LegacyFunctionRepository.nextBusaDocFileFileId(bizKey, docId)`.

2. **`busa_doc_detl.disp_ord`** — `$1='busa_doc_detl'`, `$2='disp_ord'`, `$3=biz_key`, `$4=doc_id`:
   ```sql
   select coalesce(count(*), 0) + 1
   from busa_doc_detl
   where biz_key = $3 and doc_id = $4;
   ```
   Already present in the refactor function (**V140 #43**) — no DBA action.

3. **Receipt `DOC_ID` → `sys_doc_id_gen`** — `$1='sys_doc_id_gen'`, `$2='doc_id'`, `$3=biz_key`, `$4=doc_type`. For `doc_type='E'`:
   ```sql
   select 'R' || '-' || to_char(timezone('utc', now()), 'YYYYMMDD')
        || lpad(cast(coalesce(max(right(doc_id,3)::numeric),0)+1 as text), 3, '0')
   from sys_doc_id_gen
   where biz_key = $3 and doc_type = $4
     and req_date = to_char(timezone('utc', now()), 'YYYYMMDD');
   ```
   → `R-YYYYMMDDNNN` (UTC date; example `R-20240523005`). Already present in the refactor function (**V140 #48**) — no DBA action. **Do NOT generate `DOC_ID` from `busa_doc_mstr` max.** The branch is read-only over the single daily `sys_doc_id_gen` row (PK `(biz_key,doc_type,req_date)`), so the refactor `createReceipt` upserts that row with the minted id (see §11a G7).

---

## 13. G3/G5 Attachment Implementation — `BUSA_DOC_FILE` (multi-file)

**Status: ✅ IMPLEMENTED 2026-07-04.** DB prereq verified applied (§13.0a). Full-stack shipped per the plan below (mirrors the Contacts template + shared `SecureFileDownload` + `lib/file.ts`); backend tests green + FE typecheck/lint clean. **PO decision 2026-07-04:** `BUSA_DOC_FILE` is the source of truth; multi-file preserved; the single-file `BUSA_DOC_MSTR.ORG_FILE_NM`/`SAVE_FILE_NM`/`FILE_SIZE` approach was NOT used.

### 13.0 Legacy source of truth (verified 2026-07-04, JEX DEV)

- Table `BUSA_DOC_FILE` — PK `(BIZ_KEY, DOC_ID, FILE_ID)`; columns: `BIZ_KEY varchar(20) NN`, `DOC_ID varchar(50) NN`, `FILE_ID varchar(100) NN`, `ORG_FILE_NM varchar(500)`, `SAVE_FILE_NM varchar(500)`, `FILE_SIZE varchar(100)`, `REG_ID varchar(100)`, `REG_DTM varchar(14)`. 598 rows on DEV.
- Read: IDO `BUSA_DOC_MSTR_R034` `REC_1` = `json_agg` of `(FILE_ID, ORG_FILE_NM, FILE_SIZE)` from `BUSA_DOC_FILE` per `(BIZ_KEY, DOC_ID)`.
- Write: `prch_0005_001_c001_act.jsp` inserts one `BUSA_DOC_FILE` row per uploaded file; `FILE_ID` via `SeqHelper.getNextVal(BusaDocFile, "file_id")`.
- Key gen: legacy `wb_get_next_val('busa_doc_file','file_id', biz_key, doc_id)` = `coalesce(max(file_id::numeric),0)+1` per `(biz_key, doc_id)`.
- Download: legacy `prch_0006_001_r002.act?DOC_ID=&FILE_ID=` streams the stored file.

### 13.0a Re-audit of DEV Refactor DB (2026-07-04, post-DBA)

Verified directly against the **DEV Refactor DB** (`172.168.5.172/wecmsdevdb`, `postgres_dev_server_refactor`) — not assumed:

| Object | Result |
|---|---|
| `busa_doc_file` table | ✅ **EXISTS**, matches legacy exactly — 8 cols (`biz_key20 NN`, `doc_id50 NN`, `file_id100 NN`, `org_file_nm500`, `save_file_nm500`, `file_size100`, `reg_id100`, `reg_dtm14`), PK `(biz_key, doc_id, file_id)`, unique PK index. 0 rows (fresh). No legacy difference. (My proposed optional helper index `ix_busa_doc_file_biz_doc` was not applied — perf-only, legacy DEV has only the PK index too, so not a gap.) |
| `sys_doc_id_gen` table | ✅ EXISTS, matches legacy (4 cols, PK `(biz_key, doc_type, req_date)`). 0 rows (legacy DEV has 614 — data-only diff, expected; the createReceipt upsert populates it). |
| `wb_get_next_val` → `sys_doc_id_gen.doc_id` ('E') | ✅ PRESENT + correct — functional call returns `R-20260703001` (dash, UTC date, `lpad(...,3)`). Matches UAT §12a.3. |
| `wb_get_next_val` → `busa_doc_detl.disp_ord` | ✅ PRESENT + correct — `count(*)+1` per `(biz_key, doc_id)`; functional call returns `1`. Matches UAT §12a.2. |
| `wb_get_next_val` → **`busa_doc_file.file_id`** | ✅ **APPLIED (2026-07-04, re-verified)** — branch now present (`busa_doc_file` occurs 3× in the function body); functional call returns `1` for a doc with no files; body = `coalesce(max(file_id::numeric),0)+1 where biz_key=$3 and doc_id=$4` (per-doc sequence, matches UAT §12a.1). **DB prerequisite COMPLETE — G3/G5 unblocked.** |

### 13.1 DBA prerequisites (unblock gate — manual, Flyway disabled)

1. **Schema + key branch** — `Claude Code (AI)/sql/BUSA_DOC_FILE_20260704.sql` (DBA-run, idempotent). **Re-audit 2026-07-04: Step 1 (table) is APPLIED ✅; Step 2 (the `busa_doc_file.file_id` branch) is NOT YET APPLIED ❌** — apply it now (splice the branch into `wb_get_next_val`: `coalesce(max(file_id::numeric),0)+1 where biz_key=$3 and doc_id=$4`, verbatim UAT §12a.1). Verify with `SELECT wb_get_next_val('busa_doc_file','file_id','VN_VNM0004_000001','<doc>',NULL,NULL,NULL,NULL)` → must return `1`, not `NULL`.
2. **Already present — no action:** `busa_doc_detl.disp_ord` (V140 #43) and the receipt-id `sys_doc_id_gen.doc_id` (V140 #48) branches already exist in the refactor function.
3. Confirm the inventory entry (done 2026-07-04) and the storage root config slot (§13.4).

### 13.2 Backend

- **Entity** `BusaDocFile` + `BusaDocFileId` (`@IdClass`, PK `(bizKey, docId, fileId)`) mapping `BUSA_DOC_FILE` column-for-column; `@Table(name="BUSA_DOC_FILE")`.
- **Repository** `BusaDocFileRepository` — `findByBizKeyAndDocIdInOrderByDocIdAscFileIdAsc(bizKey, docIds)` (batch, for the Receipt-tab count + dropdown), `findByBizKeyAndDocId(bizKey, docId)` (single-doc), `countByBizKeyAndDocId`.
- **Funnel** `LegacyFunctionRepository.nextBusaDocFileFileId(bizKey, docId)` → `nextVal("busa_doc_file","file_id", bizKey, docId, …)`.
- **New Receipt save** (`createReceipt`) — accept multipart files; after the `BUSA_DOC_MSTR` insert, for each uploaded file: allocate `FILE_ID` via the funnel, write bytes under the tenant-scoped storage root as `SAVE_FILE_NM`, insert a `BUSA_DOC_FILE` row (`ORG_FILE_NM`, `SAVE_FILE_NM`, `FILE_SIZE`, `REG_ID`, `REG_DTM`). Multi-file: loop, do not cap at 1.
- **`FILE_ID` concurrency — keep legacy `max+1`, add a one-shot retry (decision 2026-07-04):** the funnel branch stays the legacy per-`(biz_key, doc_id)` `max(file_id::numeric)+1` (the sequence rule is unchanged — no stored-value change). To handle the race where two files for the **same `DOC_ID`** are uploaded simultaneously and both read the same `max+1`, wrap the per-file `FILE_ID` allocate-then-insert in a **one-shot retry**: on `DataIntegrityViolationException` (PK `(biz_key, doc_id, file_id)` conflict), re-allocate `FILE_ID` via the funnel once and re-insert; surface the error only if the retry also conflicts. This is the same one-shot-retry pattern already used for other `wb_get_next_val` keys (`ChartOfAccountsService.create`, `ContactsService.create`). The retry handles only the race — it does not change normal behavior or stored values. Without it, a simultaneous multi-file upload for one `DOC_ID` could fail unnecessarily.
- **Receipt list** (`listLinkableReceipts`) + **e-invoice list** (`listLinkableEInvoices`) — batch-fetch files by the page's `doc_id`s (one query, group in memory, mirroring the G2 items projection), attach a `files: [{ file_id, org_file_nm, file_size }]` list + `file_count` to each row DTO.
- **Download endpoint** — `GET /api/v1/payment/card-transactions/receipts/{doc_id}/files/{file_id}` returning `ResponseEntity<Resource>` (binary, per REST §3). MUST use the project's secure file-resolver convention: normalize + `resolved.startsWith(tenantRoot)` traversal guard (reference `ErpTrnsHistoryFileResolver.resolve`), tenant scope via `TenantContext.currentBizKey()`, RFC 5987 `Content-Disposition` for the `ORG_FILE_NM`. Do not build a path from the client filename without the guard.
- **DTOs** — extend `LinkableReceiptRow` (and `LinkableEInvoiceRow`) with `fileCount` + `files[]`; `CreateReceiptRequest` moves to multipart (files part) or a companion upload endpoint.

### 13.3 Frontend

- Receipt tab: render the paperclip + `(count)` from `row.file_count`; the chevron dropdown lists `row.files[]` (each a download link to the new endpoint via `lib/file.ts`). This reverses the current "attachment column intentionally blank" note (`LinkCardPaymentsDialog.tsx:931-942`).
- e-Invoice tab: same file list on `REC_1` parity if the design shows it.
- New Receipt dialog: wire the existing `AttachmentCountDropdown` to actually upload (multipart) instead of client-only; on save the created receipt is immediately linkable with its files.
- Download goes through the shared `lib/file.ts` blob convention — never `unwrap()`.

### 13.4 Config / storage

- Reuse the ERP-history storage-root pattern: a `wecms.card-receipt.storage-root` (or a shared `wecms.attachments.storage-root`) config slot, tenant-scoped subpath. Files are stored on disk as `SAVE_FILE_NM`; the DB row holds the reference (matches legacy).

### 13.5 Tests to add (when implemented)

- New Receipt with N files → N `BUSA_DOC_FILE` rows, `FILE_ID` per-doc `1..N` via funnel; multi-file preserved (not capped).
- **`FILE_ID` one-shot retry:** first `save` throws `DataIntegrityViolationException` (simulated PK conflict) → service re-allocates `FILE_ID` via the funnel and re-inserts successfully; funnel called twice, row persisted. A second consecutive conflict surfaces the error.
- Receipt list returns `file_count` + `files[]`; count matches inserted rows.
- Download: happy path streams the file; traversal attempt (`../`) rejected; cross-tenant `doc_id`/`file_id` → not found.
- `file_id` funnel branch returns per-`(biz_key, doc_id)` MAX+1.

---

> **Status 2026-07-04 — ALL ITEMS CLOSED.** Implemented + verified (source changed, tests green): **L1, L2, G1, G2, G7, G3/G5**. Business-confirmed with no code change (current behavior already matches): **L3** (GDT configured = GDT-credential presence by biz_key; not the legacy both-cert-fields test) and **G4** (Project/Site intentionally not implemented — WeCMS has none; Payment Method resolved at the card-link layer, not persisted at receipt-create). No open items remain in this audit.
