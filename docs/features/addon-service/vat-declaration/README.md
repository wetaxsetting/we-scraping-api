# VAT Declaration & 8% Tax Reduction Annex — WeCMS Technical Specification

| | |
|---|---|
| **JEX counterpart** | None — **brand-new module** on WeCMS (no JEX WSVC to port) |
| **Refactor status** | 🟡 Design (this spec) — no code on disk yet; branch `f1_v1_vat_declaration` reserved |
| **Owner** | PO: Phan Tuyen · Backend spec normalized: 2026-07-13 |
| **Sprint** | TBD (Release Plan: Phase 1 MVP = Sprint 1–2) |
| **Source documents** | PO package at [`docs/vat-declaration/`](../../../vat-declaration/0.%20VAT_Declaration_Overview_VN_EN.md) (PRD, User Flow, Business Rules, Design Brief, prototype TechSpecs, Release Plan, HTKK templates) |
| **Open questions** | [planning/open-questions/vat-declaration-follow-up.md](../../../planning/open-questions/vat-declaration-follow-up.md) |

> **Why this document exists.** The PO's prototype TechSpecs
> ([`5. VAT_Declaration_TechSpecs_EN.md`](../../../vat-declaration/5.%20VAT_Declaration_TechSpecs_EN.md))
> describes a Next.js-API-route prototype: lowercase tables without `BIZ_KEY`, UUID PKs,
> unwrapped JSON arrays, and source tables (`tbl_pur_vat`, `tbl_sale_vat`) that **do not exist**
> in WeCMS. This spec is the **normative translation to real WeCMS conventions** — when the
> prototype TechSpecs and this document disagree on architecture (tables / API shape / tenancy),
> **this document wins**. For business formulas and HTKK XML structure the PO package
> (Business Rules doc + prescribed XLS/XML templates) remains the source of truth.

---

## 1. Prototype → WeCMS normalization map

| Aspect | Prototype (doc 5) | WeCMS standard (this spec) |
|---|---|---|
| API base | `GET /api/declarations` (Next.js route) | `GET /api/v1/addon/vat-declarations` (Spring, `@menuAuthz`) |
| Envelope | raw JSON array | `ApiResponse<T>` = `{ status, data }`, errors + `trace_id` |
| Tables | `vat_declarations`, `vat_declaration_indicators` (lowercase, UUID) | `VAT_DECL_INFM`, `VAT_DECL_INDC_DETL`, `VAT_DECL_ANNEX_DETL` (UPPER_SNAKE, `BIZ_KEY`-scoped) |
| Tenancy | none (single company) | Every table + every query scoped by `BIZ_KEY` from `TenantContext.currentBizKey()` |
| Invoice source | `tbl_pur_vat` / `tbl_sale_vat` / `tbl_pur_mst` / `tbl_sale_mst` (do not exist) | `BUSA_DOC_MSTR` + `EINV_TAX_DETL` (existing e-invoice tables, PROD Active List) |
| Company info | columns on the declaration row | `BUSI_INFM` (company name, tax code) resolved at create time and snapshotted |
| Pagination | none | `page` (0-based) + `size`, `total_count` on the list DTO |
| Calculation authority | frontend JS | Frontend recalcs for UX; **backend recomputes and persists** — backend values are what the XML export serializes |

---

## 2. Functional scope (from PRD / Release Plan)

**MUST (MVP):** declaration list (filter year/status) · create period (month/quarter, carry `[43]`→`[22]`) · real-time indicator calculation + manual override of white cells · sequential lock/unlock state machine · HTKK-compliant XML export · 8% reduction annex grid with add/delete rows.

**SHOULD:** auto-aggregation from raw e-invoices · re-sync (`Draft` only) · VI/EN/KO localization · accordion invoice-details modal · Excel export (annex invoices) + PDF print (main form).

**COULD:** defaults from Company Settings · Excel import into annex.

**WON'T (Phase 2):** digital signing (CA), direct T-VAN submission.

Out of scope on the backend: editing raw source invoices from this module (read-only aggregation over `BUSA_DOC_MSTR`).

---

## 3. Database design

> All three tables are **new refactor-owned tables** — they have **no JEX counterpart** and are
> **not in the PROD Active Table List**. Per the active-list rule this requires an explicit
> scope widening approved by the lead/PO before the Flyway migration lands
> (**OQ-VAT-1**, precedent: `TRCO_INFO_FILE` 2026-05-13, `MENU_ITEM` V137–V139).
> Because they are brand-new (like `MENU_ITEM`), standard PostgreSQL `IDENTITY` keys are
> allowed — **no new `wb_get_next_val` branch** (DB Function Policy: Java/identity preferred;
> branches only mirror existing DEV branches).

### 3.1 `VAT_DECL_INFM` — declaration header / lifecycle

```sql
CREATE TABLE IF NOT EXISTS VAT_DECL_INFM (
    BIZ_KEY        VARCHAR(20)  NOT NULL,
    DECL_ID        BIGINT       GENERATED ALWAYS AS IDENTITY,
    PERIOD_TYPE    VARCHAR(1)   NOT NULL,              -- 'M' month | 'Q' quarter
    PERIOD_VALUE   INTEGER      NOT NULL,              -- 1..12 | 1..4
    DECL_YEAR      INTEGER      NOT NULL,
    STTS           VARCHAR(1)   NOT NULL DEFAULT 'D',  -- 'D' Draft | 'C' Confirmed
    CMPN_NM        VARCHAR(200) NOT NULL,              -- snapshot from BUSI_INFM at create
    TAX_ID         VARCHAR(40)  NOT NULL,              -- snapshot from BUSI_INFM at create
    ANNEX_8PCT_YN  VARCHAR(1)   NOT NULL DEFAULT 'Y',  -- 8% reduction annex attached
    REVISION_NO    INTEGER      NOT NULL DEFAULT 0,    -- 0 = official (L00); 1..n = supplement (L01..)
    LAST_SYNC_DTM  TIMESTAMPTZ,                        -- last invoice re-aggregation
    LOCK_DTM       TIMESTAMPTZ,                        -- when status flipped to 'C'
    LOCK_USER_ID   VARCHAR(100),
    REG_ID         VARCHAR(100) NOT NULL,
    REG_DTM        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    MOD_ID         VARCHAR(100) NOT NULL,
    MOD_DTM        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT vat_decl_infm_pk  PRIMARY KEY (BIZ_KEY, DECL_ID),
    CONSTRAINT vat_decl_infm_uk01 UNIQUE (BIZ_KEY, PERIOD_TYPE, PERIOD_VALUE, DECL_YEAR, REVISION_NO),
    CONSTRAINT vat_decl_infm_chk01 CHECK (PERIOD_TYPE IN ('M','Q')),
    CONSTRAINT vat_decl_infm_chk02 CHECK (STTS IN ('D','C'))
);
CREATE INDEX IF NOT EXISTS ix_vat_decl_infm_biz_year ON VAT_DECL_INFM (BIZ_KEY, DECL_YEAR, PERIOD_TYPE, PERIOD_VALUE);
```

Design notes:
- `vat_decl_infm_uk01` enforces the PRD's "period already exists" duplication rule at the DB level (US-01 AC).
- `REVISION_NO` reserves supplementary-declaration support (HTKK `L01` files with `01-KHBS` sheets). MVP creates only `REVISION_NO = 0`; the supplement flow is **OQ-VAT-2**.
- Company name / tax code are **snapshotted** (not joined live) so a later `BUSI_INFM` edit cannot silently change an already-filed declaration.

### 3.2 `VAT_DECL_INDC_DETL` — indicator values (one row per declaration)

```sql
CREATE TABLE IF NOT EXISTS VAT_DECL_INDC_DETL (
    BIZ_KEY          VARCHAR(20) NOT NULL,
    DECL_ID          BIGINT      NOT NULL,
    BSNS_LINE_CD     VARCHAR(10)  NOT NULL DEFAULT '00',   -- ma_nganhnghe
    BSNS_LINE_NM     VARCHAR(255) NOT NULL DEFAULT 'Hoạt động sản xuất kinh doanh thông thường',
    ACCT_SUB_ITEM_CD VARCHAR(10)  NOT NULL DEFAULT '1701', -- tieumuchachtoan
    -- Indicators [21]..[43] — BIGINT (VND integer), floor-rounded, never negative
    CT21  BIGINT NOT NULL DEFAULT 0,
    CT22  BIGINT NOT NULL DEFAULT 0,   -- carry-forward from previous period's CT43
    CT23  BIGINT NOT NULL DEFAULT 0,   -- purchase value
    CT24  BIGINT NOT NULL DEFAULT 0,   -- purchase VAT
    CT25  BIGINT NOT NULL DEFAULT 0,   -- deductible input VAT
    CT26  BIGINT NOT NULL DEFAULT 0,   -- non-taxable revenue
    CT27  BIGINT NOT NULL DEFAULT 0,
    CT28  BIGINT NOT NULL DEFAULT 0,
    CT29  BIGINT NOT NULL DEFAULT 0,   -- 0% revenue
    CT30  BIGINT NOT NULL DEFAULT 0,   -- 5% revenue
    CT31  BIGINT NOT NULL DEFAULT 0,   -- 5% VAT
    CT32  BIGINT NOT NULL DEFAULT 0,   -- 10% (and 8%) revenue
    CT32A BIGINT NOT NULL DEFAULT 0,
    CT33  BIGINT NOT NULL DEFAULT 0,   -- 10%/8% VAT
    CT33A BIGINT NOT NULL DEFAULT 0,
    CT34  BIGINT NOT NULL DEFAULT 0,
    CT35  BIGINT NOT NULL DEFAULT 0,
    CT36  BIGINT NOT NULL DEFAULT 0,
    CT37  BIGINT NOT NULL DEFAULT 0,
    CT38  BIGINT NOT NULL DEFAULT 0,
    CT39A BIGINT NOT NULL DEFAULT 0,
    CT40A BIGINT NOT NULL DEFAULT 0,
    CT40B BIGINT NOT NULL DEFAULT 0,
    CT40  BIGINT NOT NULL DEFAULT 0,
    CT41  BIGINT NOT NULL DEFAULT 0,
    CT42  BIGINT NOT NULL DEFAULT 0,
    CT43  BIGINT NOT NULL DEFAULT 0,   -- carried forward → next period's CT22
    -- Signature / agent block
    SIGNATORY_NM   VARCHAR(100),
    SIGNING_DT     VARCHAR(8),          -- YYYYMMDD (legacy date convention)
    AGENT_NM       VARCHAR(255),
    AGENT_CERT_NO  VARCHAR(100),
    AGENT_TAX_ID   VARCHAR(13),
    PROVINCE  VARCHAR(100),
    DISTRICT  VARCHAR(100),
    WARD      VARCHAR(100),
    REG_ID  VARCHAR(100) NOT NULL,
    REG_DTM TIMESTAMPTZ  NOT NULL DEFAULT now(),
    MOD_ID  VARCHAR(100) NOT NULL,
    MOD_DTM TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT vat_decl_indc_detl_pk PRIMARY KEY (BIZ_KEY, DECL_ID),
    CONSTRAINT vat_decl_indc_detl_fk01 FOREIGN KEY (BIZ_KEY, DECL_ID)
        REFERENCES VAT_DECL_INFM (BIZ_KEY, DECL_ID) ON DELETE CASCADE
);
```

Design notes:
- The prototype's `ct33a` was **missing from the prototype DDL** but present in the formula
  matrix (`[28] = [31] + [33] + [33a]`) — added here.
- All indicator math is **integer** (`Math.floor`, BIGINT). No floats anywhere — HTKK parity
  (KPI: 100% import success).
- Note `SIGNING_DT` uses legacy `VARCHAR(8) YYYYMMDD` (prototype had free-text `VARCHAR(20)`).

### 3.3 `VAT_DECL_ANNEX_DETL` — 8% reduction annex rows

Missing entirely from the prototype DDL, but required by PRD 2.1 ("add/delete rows directly on
the grid") and the Excel-import COULD-HAVE. One row = one invoice line eligible for the 8%
reduction (Resolution 204/2025/QH15).

```sql
CREATE TABLE IF NOT EXISTS VAT_DECL_ANNEX_DETL (
    BIZ_KEY      VARCHAR(20) NOT NULL,
    DECL_ID      BIGINT      NOT NULL,
    ANNEX_SEQ_NO INTEGER     NOT NULL,               -- per-declaration ordinal, Java-side MAX+1
    IO_DV        VARCHAR(1)  NOT NULL,               -- 'I' input (purchase) | 'O' output (sale)
    SRC_DOC_ID   VARCHAR(50),                        -- FK-by-value to BUSA_DOC_MSTR.DOC_ID; NULL = manual row
    INV_NO       VARCHAR(50),
    INV_SYMBOL   VARCHAR(20),
    INV_DT       VARCHAR(8),                         -- YYYYMMDD
    PROD_NM      VARCHAR(300),                       -- grouping key for the accordion modal
    SPLY_AMT     BIGINT NOT NULL DEFAULT 0,          -- tax-exclusive value
    VAT_RATE     NUMERIC(4,2) NOT NULL DEFAULT 8,    -- effective reduced rate
    VAT_AMT      BIGINT NOT NULL DEFAULT 0,          -- VAT at reduced rate
    REDUCED_AMT  BIGINT NOT NULL DEFAULT 0,          -- 2% reduction amount
    HAND_REG_YN  VARCHAR(1) NOT NULL DEFAULT 'N',    -- 'Y' = manually added row
    REG_ID  VARCHAR(100) NOT NULL,
    REG_DTM TIMESTAMPTZ  NOT NULL DEFAULT now(),
    MOD_ID  VARCHAR(100) NOT NULL,
    MOD_DTM TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT vat_decl_annex_detl_pk PRIMARY KEY (BIZ_KEY, DECL_ID, ANNEX_SEQ_NO),
    CONSTRAINT vat_decl_annex_detl_fk01 FOREIGN KEY (BIZ_KEY, DECL_ID)
        REFERENCES VAT_DECL_INFM (BIZ_KEY, DECL_ID) ON DELETE CASCADE,
    CONSTRAINT vat_decl_annex_detl_chk01 CHECK (IO_DV IN ('I','O'))
);
CREATE INDEX IF NOT EXISTS ix_vat_decl_annex_biz_decl_prod ON VAT_DECL_ANNEX_DETL (BIZ_KEY, DECL_ID, PROD_NM);
```

### 3.4 Source tables (existing — read-only for this module)

| Prototype said | Real WeCMS table | Columns used |
|---|---|---|
| `tbl_sale_mst` / `tbl_pur_mst` | [`BUSA_DOC_MSTR`](../../../references/database/erd/addon-service-erd.md) | `BIZ_KEY`, `DOC_ID`, `DOC_TYPE` (`'I'` sales / `'B'` expense — same mapping as `EInvoiceService.resolveDocType`), `ISSU_DT` (`YYYYMMDD` — period filter), `SPLY_AMT_SUM`, `TAX_AMT_SUM`, `TOTL_AMT`, `EINV_INVOICESTATUS` (active-status filter), `EINV_INVOICESYMBOL`, `DISP_DOC_ID`, `TRCO_TAX_ID`, `TRCO_CMNM` |
| `tbl_sale_vat` / `tbl_pur_vat` | `EINV_TAX_DETL` | `BIZ_KEY`, `DOC_ID`, `DISP_ORD`, `EINV_TAXRATE` (string), `AMT` (tax-exclusive), `TAX_AMT` |
| previous period's `[43]` | `VAT_DECL_INDC_DETL` (row of period K-1, `STTS='C'`) | `CT43` |
| company header | `BUSI_INFM` | `CMPN_NM`, tax code (snapshot at create) |

Aggregation predicate (translating the prototype's `ctuktthue <> 'N'` / `kkkthue` period match):
- **Period**: `ISSU_DT BETWEEN <period-start> AND <period-end>` (`YYYYMMDD` string range — month = calendar month, quarter = 3 months).
- **Active status**: exclude cancelled/replaced invoices via `EINV_INVOICESTATUS`. The stored
  value is a **Vietnamese phrase** (verified always-Vietnamese on JEX DEV — see the e-invoice
  IR-4 note in `wecms-api/CLAUDE.md`). The exact include/exclude phrase set for tax-declaration
  purposes is **OQ-VAT-3** (PO must confirm which of the 7 statuses count toward the declaration).
- **Rate distribution**: `EINV_TAX_DETL.EINV_TAXRATE` (string) → indicator bucket. The exact
  stored rate-string vocabulary (`"10%"`? `"8%"`? `"KCT"`? `"5"`?) must be inventoried from DEV
  data before coding — **OQ-VAT-4**. Target mapping:

| `EINV_TAXRATE` bucket | Output (DOC_TYPE `'I'`) | Input (DOC_TYPE `'B'`) |
|---|---|---|
| not-subject KKKNT (không kê khai nộp thuế) | `CT26` += AMT | — |
| not-tax-calculated KCT (không chịu thuế) | `CT32A` += AMT | — |
| 0% | `CT29` += AMT | — |
| 5% | `CT30` += AMT, `CT31` += TAX_AMT | — |
| 10% | `CT32` += AMT, `CT33` += TAX_AMT | — |
| 8% (reduced 10%) | `CT32` += AMT, `CT33` += TAX_AMT, + annex row | annex row (input side) |
| all rates (input side) | — | `CT23` += AMT, `CT24` += TAX_AMT |
| out-of-scope revenue | `CT32A` / `CT33A` — allocation rule **OQ-VAT-4** | — |

---

## 4. Entity / package design

```
com.kosign.wecms.domain.vatdeclaration
├── controller/ VatDeclarationController        @RequestMapping("/api/v1/addon/vat-declarations")
├── dto/        VatDeclarationRow, VatDeclarationListResponse,
│               VatDeclarationDetail, VatDeclarationCreateRequest,
│               VatDeclarationUpdateRequest, VatIndicatorSet,
│               AnnexRow, AnnexRowCreateRequest, AnnexInvoiceGroup, AnnexInvoiceListResponse
├── entity/     VatDeclInfm, VatDeclIndcDetl, VatDeclAnnexDetl (+ @IdClass keys)
├── repository/ VatDeclInfmRepository, VatDeclIndcDetlRepository, VatDeclAnnexDetlRepository
└── service/    VatDeclarationService           (list / detail / create / update / delete)
                VatDeclarationLockService       (lock / unlock state machine)
                VatIndicatorCalculator          (pure function: formula matrix [22]..[43])
                VatInvoiceAggregationService    (BUSA_DOC_MSTR + EINV_TAX_DETL scan → indicator set + annex rows)
                VatDeclarationXmlExporter       (HTKK 01/GTGT TT80 XML)
                VatAnnexExportProvider          (implements ExportRowProvider — Excel via common/export)
```

Rules applied: entities extend `BaseEntity` where audit columns align; composite PKs via
`@IdClass`; **every repository method constrains on `BIZ_KEY`**; `VatIndicatorCalculator` is a
stateless pure class (unit-test target #1); DTOs are records, camelCase → snake_case via the
global Jackson strategy; no `@JsonProperty` unless the wire name genuinely diverges.

---

## 5. REST API contract

> **Full request/response payloads, DTO list, and per-endpoint error mapping:**
> [interface-specification.md](interface-specification.md) — the table below is the summary index.

Base path `/api/v1/addon/vat-declarations`. All JSON endpoints return `ApiResponse<T>`;
binary exports are the documented envelope exception (§3 of the REST conventions).
Every endpoint gated `@PreAuthorize("@menuAuthz.canAccess('addon-vat-declaration')")`
(permission code — **OQ-VAT-5**, needs `MENU_ITEM`/`PRODUCT_MENU` seed; product gating F/M
only, H omits Add-on per V243 precedent).

| # | Method | Path | Purpose | Notes |
|---|---|---|---|---|
| 1 | GET | `/` | List declarations | Query: `year`, `status` (`D`/`C`), `page` (0-based), `size`. Returns `…ListResponse { rows, total_count }` |
| 2 | POST | `/` | Create period | Body: `{ period_type, period_value, decl_year, annex_8pct_yn }`. Flow: dup-check → preceding-period-locked check → aggregate invoices → compute indicators → persist header + indicator row + annex rows. Errors: `WBE_VAT_DECL_DUPLICATE_PERIOD`, `WBE_VAT_DECL_PREV_NOT_LOCKED` |
| 3 | GET | `/{decl_id}` | Detail | Header + full indicator set + annex summary |
| 4 | PUT | `/{decl_id}` | Save draft | Body carries the white-cell (editable) values only; backend recomputes all grey cells via `VatIndicatorCalculator` and persists the full set. `Draft` only → else `WBE_VAT_DECL_LOCKED` |
| 5 | POST | `/{decl_id}/sync` | Re-aggregate | Re-scan invoices, overwrite indicators + regenerate non-manual annex rows. `Draft` only. (Prototype used `PUT ?sync=true`; normalized to an action sub-resource per HTTP conventions §6) |
| 6 | DELETE | `/{decl_id}` | Delete declaration | `Draft` only; blocked if a later period exists (**OQ-VAT-6**) |
| 7 | PUT | `/{decl_id}/lock` | Lock (Draft→Confirmed) | Guard: preceding period `'C'` or first period → else `WBE_VAT_DECL_PREV_NOT_LOCKED` |
| 8 | DELETE | `/{decl_id}/lock` | Unlock (Confirmed→Draft) | Guard: subsequent period absent or `'D'` → else `WBE_VAT_DECL_NEXT_LOCKED`. Paired PUT/DELETE-on-sub-resource idiom (like `.../confirm`, REST conventions §6) |
| 9 | GET | `/{decl_id}/annex/invoices` | Annex invoice modal | Grouped by `PROD_NM` (accordion); query: `search`, `io_dv`, `page`, `size` |
| 10 | POST | `/{decl_id}/annex/rows` | Add manual annex row | `HAND_REG_YN='Y'`; `Draft` only |
| 11 | DELETE | `/{decl_id}/annex/rows/{annex_seq_no}` | Delete annex row | `Draft` only |
| 12 | GET | `/{decl_id}/export/xml` | HTKK XML download | Binary (`ResponseEntity<Resource>`), RFC 5987 filename. Available regardless of status? MVP: **Confirmed only** (**OQ-VAT-7**) |
| 13 | POST | `/{decl_id}/annex/export` | Annex Excel | Via `common/export` pipeline + `VatAnnexExportProvider` (`screenId = addon-vat-declaration-annex`) — requires `SYS_SCR_OPTION_WECMS` seed in the same migration |
| 14 | GET | `/{decl_id}/export/pdf` | Main form PDF (A4) | Binary. Library decision pending (**OQ-VAT-8** — no PDF producer exists in `build.gradle` today) |

### New error codes (add to `messages.properties` before throwing)

| Code | Meaning |
|---|---|
| `WBE_VAT_DECL_NOT_FOUND` | Declaration not found in tenant (merges not-found + wrong-tenant) |
| `WBE_VAT_DECL_DUPLICATE_PERIOD` | A declaration for this period/year already exists |
| `WBE_VAT_DECL_PREV_NOT_LOCKED` | Preceding period must be locked first (create & lock guards) |
| `WBE_VAT_DECL_NEXT_LOCKED` | Subsequent period is locked — unlock it first |
| `WBE_VAT_DECL_LOCKED` | Declaration is Confirmed — edit/sync/delete blocked |
| `WBE_VAT_DECL_INVALID_INDICATOR` | Indicator value negative / non-integer |
| `WBE_VAT_DECL_ANNEX_ROW_NOT_FOUND` | Annex row not found in declaration |
| `WBE_VAT_DECL_BSNS_LINE_REQUIRED` | Business industry missing on create (`WEB_VAT_CRE_MSG_004`) — added 2026-08-04 |

`BusinessException` → HTTP 200 with `status.error=true` per the global error contract.

> This table is incomplete against the implementation — see the note under
> [interface-specification.md §4](interface-specification.md#4-error-codes-mới-thêm-vào-messagesproperties-trước-khi-throw)
> for the four codes the code throws but neither doc lists.

---

## 6. Business rules (normative summary — full matrix in PO doc 3)

1. **Formula matrix** — implement exactly as
   [`3. VAT_Declaration_BusinessRules_EN.md`](../../../vat-declaration/3.%20VAT_Declaration_BusinessRules_EN.md) §1:
   propagation on `[23]→[24]→[25]`, `[30]→[31]`, `[32]→[33]`; derived `[27] [28] [34] [35] [36]`;
   `hieu_so = [36]-[22]+[37]-[38]-[39a]`; split into `[40a]`/`[41]`; `[40] = max(0, [40a]-[40b])`;
   `[43] = max(0, [41]-[42])`. **Integer math only** (`Math.floorDiv`-style flooring on the
   rate multiplications: `[24] = floor([23] × 0.1)` implemented as `ct23 / 10` integer division
   — never `double`).
2. **Carry-forward**: at create, `CT22 = CT43` of the **preceding locked period**; absent
   preceding period → `CT22 = 0` (first period).
3. **Lock state machine** (doc 3 §2): Lock K requires K-1 `Confirmed` (or K is first);
   Unlock K requires K+1 `Draft`/absent. Both checks + status flip happen atomically in one
   `@Transactional` method with a pessimistic/optimistic guard so two users cannot race
   lock/unlock (refactor addition — the prototype has no concurrency story).
4. **Backend is the calculation authority.** The PUT payload carries only editable inputs;
   the server recomputes every derived indicator before persisting. The XML export serializes
   only persisted server-side values. Frontend mirrors the same formulas for UX only.
5. **Validation** (doc 3 §3): signing date `YYYYMMDD` required at lock time; signatory ≤ 100
   chars; all indicators ≥ 0; agent tax code 10 or 13 digits when present.

---

## 7. HTKK XML export

- **Filename**: `{MST13}-01_GTGT_TT80-{PERIOD}-L{REV}.xml` — 10-digit tax code padded with
  `000` to 13; period `M052026` / `Q22026`; `L00` official, `L01+` supplement.
- **Structure**: per the prescribed template
  [`1201496252000-01_GTGT_TT80-Q22026-L00.xml`](../../../vat-declaration/) — indicators map
  1:1 to `<ct21>..<ct43>` under `<CTieuTKhaiChinh>`; `<loaiTKhai>` = `C`/`B`;
  `<soLan>` = revision. Grouped nodes (e.g. `<GiaTriVaThueGTGTHHDVMuaVao>` wrapping
  `ct23`/`ct24`) must match the template verbatim.
- Serialize with an explicit, deterministic writer (StAX/JAXB — decision at implementation);
  UTF-8, no BOM surprises; validate the output against the sample files in the PO package as a
  **unit-test fixture** (golden-file test).
- Signed-XML blocks (`CKyDTu`) are **out of scope** (Phase 2 — digital signing WON'T-HAVE).

## 8. Excel / PDF export

- **Annex Excel** goes through the shared `common/export` pipeline: new
  `VatAnnexExportProvider implements ExportRowProvider`, screenId
  `addon-vat-declaration-annex`, `SYS_SCR_OPTION_WECMS` seed rows in the module's own
  migration. Never hand-rolled POI.
- **Main-form Excel** (HTKK-shaped `01_GTGT_TT80_*.xls` parity) and **PDF print**: the shared
  pipeline produces flat list sheets, not a government form layout — these need a dedicated
  template-based writer (POI template fill for XLS; PDF library TBD, **OQ-VAT-8**). Do not
  bend `ExcelWorkbookWriter` into a form renderer.

---

## 9. Cross-references

- **Field-level data dictionary**: [references/database/addon/vat-declaration-db.md](../../../references/database/addon/vat-declaration-db.md) — per-field descriptions (EN/VN) for all three new tables + source-table usage
- PO source package: [`docs/vat-declaration/`](../../../vat-declaration/0.%20VAT_Declaration_Overview_VN_EN.md)
- e-Invoice source tables & ERD: [addon-service ERD](../../../references/database/erd/addon-service-erd.md) · [database-coverage-matrix](../../../references/database/database-coverage-matrix.md)
- Sibling feature docs (source-table owners): [sales-e-invoices/](../sales-e-invoices/README.md) · [expenses-e-invoices/](../expenses-e-invoices/README.md)
- REST conventions / envelope / error funnel: `wecms-api/CLAUDE.md` §"REST API Design Conventions"
- Open questions: [vat-declaration-follow-up.md](../../../planning/open-questions/vat-declaration-follow-up.md)
