# Settings > Firm Banking History — Refactor Feature Spec

**Module:** Settings
**Menu:** Firm Banking History
**Refactor screen ID / slug:** `firm-banking-history`
**Frontend route (proposed):** `/settings/firm-banking-history`
**JEX service ID:** `comm_0221_001` (View) + `comm_0221_001_r001` (Read API)
**JEX counterpart doc:** `docs/jex/features/firm-banking-history.md` *(to be created — JEX side gap; cite WSVC + IDO + `_act.jsp` until written)*
**Status:** **Backend + frontend wired** (Phase 2 + Phase 3, 2026-05-08).
Frontend ships as the existing **Settings modal panel** (Figma 9198:11718)
wired to the Phase 2 backend — NOT a standalone `/settings/firm-banking-history`
route as the Phase-1 sketch proposed. The Settings modal is the canonical
placement (per [wecms-web/CLAUDE.md § Approved reference set](../../../../wecms-web/CLAUDE.md) — "Settings > Purpose modal" is the fourth approved baseline).

### Implementation references (Phase 2)

| Layer | File | Notes |
|---|---|---|
| Flyway — source tables | [V59__firm_banking_history_source_tables.sql](../../../src/main/resources/db/migration/V59__firm_banking_history_source_tables.sql) | TB_FIRM_TRAN, NXSIAS_MODULE_TRAN_LOG_HIS, TB_FIRM_BANK_CODE |
| Flyway — view + functions | [V60__firm_banking_history_view_and_fns.sql](../../../src/main/resources/db/migration/V60__firm_banking_history_view_and_fns.sql) | `kr_to_vn_time`, `wb_get_napas_bank_nm` (1-arg), `V_FIRM_BANKING_HISTORY` view |
| Flyway — demo seed | [V61__firm_banking_history_demo_seed.sql](../../../src/main/resources/db/migration/V61__firm_banking_history_demo_seed.sql) | 8 TB_FIRM_TRAN + 8 NXSIAS rows for tenant + 2 TB_FIRM_BANK_CODE rows |
| Entity | [FirmBankingHistoryView.java](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/entity/FirmBankingHistoryView.java) | `@Immutable @Table(name="V_FIRM_BANKING_HISTORY")` + composite `@IdClass` |
| Repository | [FirmBankingHistoryRepository.java](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/repository/FirmBankingHistoryRepository.java) | Single JPQL `search()` covers every filter combination |
| Service | [FirmBankingHistoryService.java](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/service/FirmBankingHistoryService.java) | Validation, sort whitelist, keyword escaping, tenant scoping |
| Sort whitelist | [FirmBankingHistorySortField.java](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/service/FirmBankingHistorySortField.java) | `SortableField` enum — TARN_DTM, SVC_TYPE, SVC_NM, SEQ_NO, DEP_BANK_CD, RSLT_CD |
| Controller | [FirmBankingHistoryController.java](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/controller/FirmBankingHistoryController.java) | `GET /api/v1/settings/firm-banking-history`, snake_case `@RequestParam` names |
| DTO — row | [FirmBankingHistoryRow.java](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/dto/FirmBankingHistoryRow.java) | Java record; 10 fields |
| DTO — response | [FirmBankingHistoryListResponse.java](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/dto/FirmBankingHistoryListResponse.java) | `list`, `total_count`, `total_pages`, `page_number`, `page_size` |
| Service tests | [FirmBankingHistoryServiceTest.java](../../../src/test/java/com/kosign/wecms/domain/setting/firmbankinghistory/service/FirmBankingHistoryServiceTest.java) | Date validation, svc_type guard, keyword escaping, sort whitelist, pagination clamp, tenant scoping |
| Controller tests | [FirmBankingHistoryControllerTest.java](../../../src/test/java/com/kosign/wecms/domain/setting/firmbankinghistory/controller/FirmBankingHistoryControllerTest.java) | Standalone MockMvc; verifies snake_case wire shape end-to-end + WBE_VALIDATION envelope on missing required params |

### Frontend implementation references (Phase 3)

| Layer | File | Notes |
|---|---|---|
| Wire types | [lib/types/api.ts](../../../../wecms-web/lib/types/api.ts) | `ApiFirmBankingHistoryRow` + `ApiFirmBankingHistoryListResponse` (snake_case) |
| API client | [lib/api/firmBankingHistory.ts](../../../../wecms-web/lib/api/firmBankingHistory.ts) | `fetchFirmBankingHistory(...)` + `FetchFirmBankingHistoryParams` — translates camelCase request params to snake_case wire form, errors propagate as `ApiCallError` |
| Mapper | [lib/api/mappers.ts](../../../../wecms-web/lib/api/mappers.ts) | `mapFirmBankingHistoryRow` — wire snake_case → camelCase `FirmBankingHistoryRow`; SVC_TYPE → 4-state enum; rslt_cd → 3-state result; tarn_dtm → date+time |
| Settings modal panel | [components/settings/firm-banking-history/FirmBankingHistoryPanel.tsx](../../../../wecms-web/components/settings/firm-banking-history/FirmBankingHistoryPanel.tsx) | useApiData + debounced keyword + latched stable data + server-side pagination + global error dialog + last-30-days default range |
| Filters | [components/settings/firm-banking-history/FirmBankingHistoryFilters.tsx](../../../../wecms-web/components/settings/firm-banking-history/FirmBankingHistoryFilters.tsx) | Existing static UI — no changes |
| Table | [components/settings/firm-banking-history/FirmBankingHistoryTable.tsx](../../../../wecms-web/components/settings/firm-banking-history/FirmBankingHistoryTable.tsx) | Refactored to use the shared `<TableHeaderCell>` so sort glyphs + ARIA semantics + click-to-sort match the canonical pattern; FIELD_IDs match the backend `FirmBankingHistorySortField` whitelist (`TARN_DTM` / `SVC_TYPE` / `SVC_NM` / `SEQ_NO` / `DEP_BANK_CD` / `RSLT_CD`) |
| Detail popover | [components/settings/firm-banking-history/FirmBankingHistoryDetailPopover.tsx](../../../../wecms-web/components/settings/firm-banking-history/FirmBankingHistoryDetailPopover.tsx) | Refactored to be **row-driven**: prop signature flipped from `detailType` to `row`; per-leg fields read from `row.rsltCd / rsltMsg / job / reqData / vanOrgCd` (mirrors legacy `comm_0221_001.ts:429-638` `fnDetails`) — G-leg parses `numericSymbol/invoiceSymbol/invoiceNo` from `req_data`, B-leg parses `search` (Tax ID), F-leg shows VAN code, S-leg uses common subset |
| Types | [components/settings/firm-banking-history/types.ts](../../../../wecms-web/components/settings/firm-banking-history/types.ts) | Extended `FirmBankingHistoryRow` with `rsltCd`, `rsltMsg`, `job`, `reqData`, `vanOrgCd` |
| Mock data | `mockData.ts` (removed Sprint 1 — Needs Confirmation if still referenced) | Extended for type compatibility — kept as offline-dev affordance only; the panel uses the live API path now |

**Wire-up notes**

- **Date range**: defaults to last-30-days at panel mount (mirrors legacy `comm_0221_001.ts:108` `loadinitializeDate(-30, today)`). **Phase 3a** wires the shared [`<InteractiveDateRangePicker>`](../../../../wecms-web/components/shared/InteractiveDateRangePicker.tsx) — promoted from Sales e-Invoices' `<EInvoiceDateRangeControl>` so both pages now share one implementation. Click the start chip → compact single-date popover commits `start`; click the end chip → same popover commits `end`; click the calendar icon → dual-month range popover with preset chips (Today / This week / Q1-4 / monthly / etc.) and Cancel / Apply commits both ends. ESC + outside-click close any open popover. Open question 1 (real DateRangePicker wire-up) is now resolved.

### DICTIONARY_VAN port (Phase 3b)

**Goal.** Localize the F-leg row's `rslt_msg` shown in the View Details popover, mirroring legacy `jexjs.getCode("DICTIONARY_VAN", RSLT_CD)` at [`WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0221_001.ts:440-442`](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0221_001.ts).

**Source.** All 69 XML files under [`WECMS DEV/WECMS_ETC/xml/code/DICTIONARY_VAN/CD_*.xml`](../../../../WECMS%20DEV/WECMS_ETC/xml/code/DICTIONARY_VAN/) (read-only legacy reference). The legacy lookup is keyed **only by `RSLT_CD`** — `VAN_ORG_CD` is not a dictionary key (verified: `comm_0221_001.ts` never passes `VAN_ORG_CD` to `getCode`). XML schema: each file holds one `<C_<code> seq="N">` element with `<EN>`, `<KO>`, `<VI>` children, each carrying `<name><![CDATA[...]]></name>` — matching the workspace's three supported locales 1:1.

**Refactor implementation.**

| File | Role |
|---|---|
| [lib/i18n/dictionaryVan.data.ts](../../../../wecms-web/lib/i18n/dictionaryVan.data.ts) | Static reference data — 69 entries × {EN/KO/VI} ported verbatim from the legacy XML (whitespace + EN-as-VI duplications preserved). Frozen `Readonly<Record<string, DictionaryVanEntry>>`; `localizedMessage(entry, locale)` projects to the active locale. |
| [lib/i18n/dictionaryVan.ts](../../../../wecms-web/lib/i18n/dictionaryVan.ts) | Shared lookup utility. `lookupVanMessage(code, locale)` → `string \| null` for raw access; `resolveVanMessage(code, rsltMsg, locale, emptyFallback)` is the canonical resolver every caller should use (mirrors the legacy fallback chain). `getVanEntry(code)` for diagnostics. |
| [components/settings/firm-banking-history/FirmBankingHistoryDetailPopover.tsx](../../../../wecms-web/components/settings/firm-banking-history/FirmBankingHistoryDetailPopover.tsx) | F-leg branch in `buildDetailRows` calls `resolveVanMessage(row.rsltCd, row.rsltMsg, locale, failedLabel)` for the response-message slot. Other legs render `rsltMsg` verbatim (no VAN dictionary applies). |

**Mapping rule (F-leg only).**

```
DICTIONARY_VAN = read-only legacy XML at WECMS_ETC/xml/code/DICTIONARY_VAN/
                 → bundled as the typed TS table dictionaryVan.data.ts (69 entries)

Lookup key  : RS_CD ∈ TB_FIRM_TRAN          (4 chars, e.g. "0000", "1030", "FB01", "G999")
Filename map: CD_<RS_CD>.xml  → entry "<C_<RS_CD>>" inside the XML
Locale pick : EN | KO | VI  via the active <LocaleProvider> (the three workspace-supported locales)

F-leg fallback chain (matches comm_0221_001.ts:440-442):
  1. DICTIONARY_VAN[RS_CD][locale]                 (dictionary hit)
  2. row.rsltMsg                                   (gateway free-text — NULL for F leg in practice)
  3. t("settings.firm_banking_history.result.failed")  (localized "Failed" — legacy j004)
  4. ""                                            (empty string — popover renders "—" placeholder)
```

**Unresolved dictionary gaps (from DEV `TB_FIRM_TRAN.RS_CD` audit, 2026-05-08).**

The following codes appear in the live tenant's `TB_FIRM_TRAN` rows but have **no matching `CD_<code>.xml`** in the legacy ETC directory — they are content gaps on the legacy side that the refactor cannot fill:

| RS_CD | Tenant rows (DEV) | Status |
|---|---|---|
| `0091` | 229 | **GAP** — no `CD_0091.xml`. Lookup returns `null`; popover falls back to `rsltMsg` then "Failed". |
| `0011` | 124 | **GAP** — no `CD_0011.xml`. |
| `1006` | 50 | **GAP** — no `CD_1006.xml`. |
| `1000` | 25 | **GAP** — no `CD_1000.xml`. |

These gaps are the legacy team's responsibility to author into the ETC dictionary; until then the fallback chain above renders gracefully without crashing.

**Reuse contract.** Any future banking / payment / VAN-gateway feature that surfaces an `RS_CD` from `TB_FIRM_TRAN`, `BAT_CALL_HIS`, or any other VAN-fronted table MUST consume `lookupVanMessage` / `resolveVanMessage` from `lib/i18n/dictionaryVan.ts`. Inlining the resolver per-page is the bug pattern this module exists to prevent.

**Open question 2 (DICTIONARY_VAN port) — resolved.** F-leg result-message localization is now wired.

### Result-code i18n bundle (Phase 3c — S / G / B legs)

**Goal.** Localize the response-message slot in the View Details popover for S (scraping), G (GDT e-Invoice), and B (Vietnam Business) leg rows. Mirrors the legacy inline `resultMap` at [`comm_0221_001.ts:459-481`](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0221_001.ts) which maps 18 raw result codes onto 16 legacy ML keys (`comm_0221_001_j027` … `j046`). Distinct from the F-leg's DICTIONARY_VAN integration — different code shape (8-char vs 4-char), different gateway (scraping/NXISAS vs Korean firm-banking), different legacy source (inline JS map vs ETC XML).

**Source of truth.**

| Asset | Path | Lines |
|---|---|---|
| Code → ML-key map | [`WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0221_001.ts`](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0221_001.ts) | 459-481 (`resultMap`) |
| Verified EN fallback per ML key | same file | 80-99 (`multiLanguagesObj` initializer's `_osmc.ui.ml.get(key, fallback)` 2nd-arg literals) |
| KO / VI translations | **NOT in source** — registered in Jex Studio (Multi Language → Common View → `comm_0221_001_jNNN`). Outside `WECMS DEV/` source tree. | — |

**18-code registry (verified).**

| Code | Legacy ML key | Verified EN message |
|---|---|---|
| `42110000` | j027 | No transaction results. (No transaction history found) |
| `80004113` | j028 | Login failed. Please verify with the institution and try again. |
| `80010011` | j029 | Some login information entered is incorrect. Please check and try again. |
| `80002216` | j030 | Customer number not entered. Please check and try again. |
| `80010141` | j031 | Invalid OTP. Please check and try again. |
| `80002411` | j032 | Incorrect account number entered (for transfer, withdrawal account). Please check on the institution's website and try again. |
| `8000E200` | j033 | No response from the institution, transaction canceled (HTTP call failed). Please try again later. |
| `80002F33` | j035 | Transaction failed due to changes on the institution's related page. Please try again later. |
| `8000FFFF` | j036 | Unknown error received from the institution, transaction canceled. Please check the error details on the institution's website and try again. |
| `LBS00001` | j037 | High user traffic is causing delays. Please try again later. |
| `80002121` | j038 | Please check the Job name. |
| `00000000` | j040 | Successfully processed. *(distinct from F-leg's "Process is completed." — same code, two leg-specific resolvers, both legacy-verified)* |
| `GWS06004` | j041 | Response time exceeded. (Please contact the administrator) |
| `80002E21` | j042 | Logged out due to duplicate login. Please try again later. |
| `80002214` | j043 | Incorrect ID or password. Please check and try again. |
| `80002F31` | j035 | Transaction failed due to changes on the institution's related page. Please try again later. *(shares text with `80002F33`)* |
| `80002421` | j043 | Incorrect ID or password. Please check and try again. *(shares text with `80002214`)* |
| `LOGIN_CAN` | j046 | You have canceled the operation |

**Refactor implementation.**

| File | Role |
|---|---|
| [`lib/i18n/scrapingResultCodes.data.ts`](../../../../wecms-web/lib/i18n/scrapingResultCodes.data.ts) | 18-entry static registry. Each entry carries `code`, `i18nKey` (`fb_history.result.<code>`), and `legacyMlKey` (for traceability against Jex Studio). Frozen `Readonly<Record>`. `KNOWN_SCRAPING_RESULT_CODES` set for fast existence checks. |
| [`lib/i18n/scrapingResultCodes.ts`](../../../../wecms-web/lib/i18n/scrapingResultCodes.ts) | Shared resolver. `lookupScrapingResultMessage(code, t)` → `string \| null` (raw lookup); `resolveScrapingResultMessage(code, rsltMsg, t, emptyFallback)` (canonical resolver, 4-step fallback chain). `getScrapingResultEntry(code)` for diagnostics; `SCRAPING_RESULT_CODES_SIZE` for tests. |
| [`locales/en.json`](../../../../wecms-web/locales/en.json) | 18 verified EN entries under the `fb_history.result.<code>` namespace. Trailing whitespace + curly apostrophes preserved verbatim from the legacy source. |
| [`components/settings/firm-banking-history/FirmBankingHistoryDetailPopover.tsx`](../../../../wecms-web/components/settings/firm-banking-history/FirmBankingHistoryDetailPopover.tsx) | `buildDetailRows` dispatches per leg: F → `resolveVanMessage`; S/G/B → `resolveScrapingResultMessage`. The `t` function is threaded into the helper so the resolver can call `t(entry.i18nKey)`. |

**Fallback chain (`resolveScrapingResultMessage`).**

```
1. localized i18n message    : t(`fb_history.result.${code}`) when code ∈ KNOWN_SCRAPING_RESULT_CODES
2. row.rsltMsg               : gateway free-text (NAPAS / NXISAS / EXT_TRAN emit this)
3. raw rsltCd                : the code string itself — never blank
4. emptyFallback             : caller-supplied (popover passes the localized "Failed" label)
5. ""                        : empty (popover renders "—" placeholder)
```

Step 3 is a refactor enhancement over the legacy `resultMap[RSLT_CD] || RSLT_MSG` chain — it satisfies the task rule "Never render blank UI" even when both the dictionary and `rsltMsg` are empty.

**Supported locales.**

| Locale | Status | Notes |
|---|---|---|
| EN | ✅ All 18 codes localized (verified strings from legacy source). | `locales/en.json` under the `fb_history.result.*` namespace. |
| KO | ⚠️ **UNRESOLVED** — Korean translations live in Jex Studio's ML configuration database (Multi Language → Common View → `comm_0221_001_jNNN`), not in `WECMS DEV/` source. Per the task rule "Do not invent translations" KO entries are intentionally NOT added to `locales/ko.json`. The workspace `LocaleProvider` falls back to EN automatically (`LocaleProvider.tsx:101-103`). |
| VI | ⚠️ **UNRESOLVED** — Same situation as KO. Vietnamese entries live in Jex Studio ML, not in source. EN fallback applies. |

**How to resolve KO / VI later.** Two paths, both outside the refactor R&D scope:
1. Export from Jex Studio's ML config DB (admin tooling — Sonny / DBA).
2. Have a translator render the 16 unique EN messages into KO + VI, then add them to `locales/{ko,vi}.json` under the same `fb_history.result.<code>` keys.

Until that lands, KO and VI users see the verified EN strings — visibly correct, no fabrication.

**Reuse contract.** Any future banking / scraping / GDT / business-inquiry feature that surfaces a result code from `NAPAS_REMIT_TRNS_HSTR.TRNS_STTS`, `EXT_TRAN_CALL_HIS.RSLT_CD`, `NXSIAS_MODULE_TRAN_LOG_HIS.NXISAS_ERR_CODE`, or any other scraping/NXISAS-fronted table MUST consume `lookupScrapingResultMessage` / `resolveScrapingResultMessage` from `lib/i18n/scrapingResultCodes.ts`. F-leg VAN codes go through `lib/i18n/dictionaryVan.ts`. Do NOT inline result-code maps per page. Do NOT duplicate the 18-code registry.

### Service-name mapping — Spring-owned (Phase 3d)

**Goal.** Move authoritative ownership of the `(svc_type, svc_nm) → service-name display` mapping from the legacy frontend `SERVICE_NAME_MAP` JS object to the Spring backend, with the `TB_FIRM_TRAN.TRAN_TP` value set typed as a Spring enum (`TranTp`) mirroring legacy `wabooks.enums.TranTp`. Frontend becomes a thin renderer driven by a server-emitted i18n key.

**Source of truth.**

| Asset | Path | Notes |
|---|---|---|
| Legacy F-leg enum | [`WECMS DEV/WECMS/src/wabooks/enums/TranTp.java:6-11`](../../../../WECMS%20DEV/WECMS/src/wabooks/enums/TranTp.java) | 4 codes: `201` / `202` / `203` / `204`. The legacy `getLabel()` returns Korean labels (lines 38-41); the legacy frontend renders English from `comm_0221_001.ts`. |
| Legacy frontend `SERVICE_NAME_MAP` | [`WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0221_001.ts:40-52`](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0221_001.ts) | Lookup table keyed by F-leg numeric codes (`201`/`202`/`203` only — no `204`) AND by S/G/B-leg English literals. The `j021`-`j046` ML keys carry the verified EN fallbacks. |
| V60 view CASE-WHEN literals | [`V60__firm_banking_history_view_and_fns.sql`](../../../src/main/resources/db/migration/V60__firm_banking_history_view_and_fns.sql) | The S/G/B legs' `SVC_NM` values come from this view's CASE expressions — they are stable English identifiers, not user-facing strings. |

**Backend implementation.**

| File | Role |
|---|---|
| [`wecms-api/.../firmbankinghistory/service/TranTp.java`](../../../src/main/java/com/kosign/wecms/common/enums/TranTp.java) | Spring enum mirroring legacy `wabooks.enums.TranTp`. 4 constants × `(code, i18nKey)`. **Not** a JPA `@Enumerated` mapping (workspace rule "Do not use `EnumType.ORDINAL`"); the DB column stays `VARCHAR(3)` mapped as `String` on `FirmBankingHistoryView`. `i18nKey` is `null` for code `204` because the legacy frontend never registered an English label for it — resolver propagates null, frontend renders the raw code (legacy parity). |
| [`wecms-api/.../firmbankinghistory/service/FirmBankingServiceNameResolver.java`](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/service/FirmBankingServiceNameResolver.java) | Stateless utility. `resolve(svcType, svcNm)` branches per leg: F → `TranTp.fromCode(...)`; S/G/B → switch on the V60 view's CASE-WHEN literals. Returns `null` for unknown pairs (frontend falls back to raw `svc_nm`). |
| [`wecms-api/.../common/enums/FirmBankingSvcType.java`](../../../src/main/java/com/kosign/wecms/common/enums/FirmBankingSvcType.java) | **Phase 3d.2.** 4-constant Spring enum mirroring the V60 view's `SVC_TYPE` letter set (F/S/G/B). Each constant carries `(code, i18nKey)`; `i18nKey()` is non-null for every constant (the four target keys are pre-registered in `locales/{en,ko,vi}.json`). `resolveKey(code)` is the convenience helper called by `FirmBankingHistoryService.toRow`. **Not** a JPA `@Enumerated` mapping — pure code → key resolver. |
| [`FirmBankingHistoryRow.java`](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/dto/FirmBankingHistoryRow.java) | DTO record gains components `svcTypeKey` (Phase 3d.2, wire `svc_type_key`) and `svcNameKey` (Phase 3d, wire `svc_name_key`). |
| [`FirmBankingHistoryService.java#toRow`](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/service/FirmBankingHistoryService.java) | Computes both keys: `svcTypeKey` via `FirmBankingSvcType.resolveKey(...)`, `svcNameKey` via `FirmBankingServiceNameResolver.resolve(...)`. Threads both into the DTO. |

**API wire shape.**

```
{
  "svc_type":     "F",                                                     // unchanged — raw leg discriminator
  "svc_type_key": "settings.firm_banking_history.service_type.firm_banking_transaction",  // Phase 3d.2 — server-resolved leg i18n key (or null)
  "svc_nm":       "201",                                                   // unchanged — raw value from V_FIRM_BANKING_HISTORY
  "svc_name_key": "fb_history.service.201",                                // Phase 3d — server-resolved service-name i18n key (or null)
  …
}
```

`svc_nm` is preserved verbatim — it carries the F-leg `TRAN_TP` code on F rows, and the V60 view's English literal on S/G/B rows. `svc_name_key` is the resolved i18n key under the `fb_history.service.*` namespace; `null` when the resolver doesn't recognise the `(svcType, svcNm)` pair.

**`svc_type_key` (Phase 3d.2).** Server-resolved frontend i18n key for the leg discriminator, derived from `svc_type` (the raw `F`/`S`/`G`/`B` letter from the V60 view) via the new Spring [`FirmBankingSvcType`](../../../src/main/java/com/kosign/wecms/common/enums/FirmBankingSvcType.java) enum. Resolves to one of four pre-registered keys under the `settings.firm_banking_history.service_type.*` namespace already present in `locales/{en,ko,vi}.json`:

| `svc_type` | `svc_type_key` |
|---|---|
| `F` | `settings.firm_banking_history.service_type.firm_banking_transaction` |
| `S` | `settings.firm_banking_history.service_type.scraping_transfer` |
| `G` | `settings.firm_banking_history.service_type.vietnam_gdt_inquiry` |
| `B` | `settings.firm_banking_history.service_type.vietnam_business_inquiry` |

Wire value is `null` only when `svc_type` is not one of the four known letters; the frontend then renders the raw character (never blank). The frontend ([`mapFirmBankingHistoryRow`](../../../../wecms-web/lib/api/mappers.ts) → `extractFirmBankingServiceType`) extracts the suffix from `svc_type_key` and validates it against the `FirmBankingServiceType` union, then renders via `t(...)`. **Replaces the legacy frontend-side `coerceFirmBankingServiceType` switch** — domain-code meaning is now owned server-side, mirroring the architectural move that produced `svc_name_key`.

**Code-set registry (10 verified entries in `locales/en.json`).**

| `svc_name_key` | Verified EN | Legacy ML | Source |
|---|---|---|---|
| `fb_history.service.201` | Inquiry Recipient | `j021` | F-leg / `TranTp.BANKING_INQUIRY_OF_ACCOUNT_HOLDER` |
| `fb_history.service.202` | Transfer Payment | `j022` | F-leg / `TranTp.BANKING_DEPOSIT_TRANSFER` |
| `fb_history.service.203` | Inquiry Transfer Result | `j023` | F-leg / `TranTp.BANKING_DEPOSIT_TRANSFER_RESULT_INQUIRY` |
| *(no key)* | *(unverified)* | — | F-leg / code `204` — legacy frontend SERVICE_NAME_MAP has no English entry. Resolver returns `null`; frontend renders the raw `"204"`. |
| `fb_history.service.funds_transfer_scraping` | Funds Transfer (Scraping) | `j039` | S-leg / NAPAS |
| `fb_history.service.scraping_balance` | Scraping Balance | `j034` | S-leg / EXT_TRAN `BALANCE_INQUIRY` |
| `fb_history.service.scraping_transaction` | Scraping Transaction | `j044` | S-leg / EXT_TRAN `REAL_TIME_ONLINE_INQUIRY` |
| `fb_history.service.scraping_card_transaction` | Scraping Card Transaction | `j045` | S-leg / EXT_TRAN `CARD_INQUIRY` |
| `fb_history.service.sale_einvoice_inquiry` | Sale E-Invoice Inquiry | `j025` | G-leg / NXISAS `E-INVOICE_SALES_INQUIRY` |
| `fb_history.service.expense_einvoice_inquiry` | Expense E-Invoice Inquiry | `j026` | G-leg / NXISAS `E-INVOICE_EXPENSE_INQUIRY` |
| `fb_history.service.vietnam_business_inquiry` | Vietnam Business Inquiry | `j009` | B-leg / NXISAS `BUSSINESS_REGISTRATION_*` |

**Frontend behaviour.**

| File | Role |
|---|---|
| [`lib/types/api.ts`](../../../../wecms-web/lib/types/api.ts) | `ApiFirmBankingHistoryRow.svc_name_key: string \| null` added. |
| [`lib/api/mappers.ts`](../../../../wecms-web/lib/api/mappers.ts) | `mapFirmBankingHistoryRow` passes `svc_name_key → serviceNameKey` verbatim. |
| [`components/settings/firm-banking-history/types.ts`](../../../../wecms-web/components/settings/firm-banking-history/types.ts) | `FirmBankingHistoryRow.serviceNameKey: string \| null` added. |
| [`FirmBankingHistoryTable.tsx`](../../../../wecms-web/components/settings/firm-banking-history/FirmBankingHistoryTable.tsx) | Service Name cell renders `t(row.serviceNameKey)` when the key is non-null; falls back to `row.serviceName` (raw `svc_nm`) when `null`. |
| [`FirmBankingHistoryDetailPopover.tsx`](../../../../wecms-web/components/settings/firm-banking-history/FirmBankingHistoryDetailPopover.tsx) | `buildDetailRows` resolves the popover's "Service Name" field through the same chain: i18n key → raw `svc_nm` → row's `job` (last resort). Never blank. |

**Frontend fallback chain.**

```
1. t(row.serviceNameKey)   when serviceNameKey is non-null and resolves
2. row.serviceName          (raw svc_nm — unknown legacy code path; mirrors legacy SERVICE_NAME_MAP fallback at comm_0221_001.ts:646)
3. row.job                  (popover only — last resort; never blank)
```

The legacy `SERVICE_NAME_MAP` JS object **is retired** — frontend holds zero hardcoded service-code → label mappings. The hardcoded enum/list of 4-leg English literals lives ONLY on the backend resolver.

**JPA / DB rules.**

- `TB_FIRM_TRAN.TRAN_TP` is `VARCHAR(3)` (legacy parity) and stays mapped as `String` through the V60 view's `SVC_NM` column on `FirmBankingHistoryView`. **No `@Enumerated` annotation, no `EnumType.ORDINAL`.** The `TranTp` enum exists purely to type-check the code set and own the i18n-key mapping; it is not a persistence type.
- DB values are unchanged — V61's seeded `tran_tp` rows still carry the raw `'201'` / `'202'` / `'203'` strings from DEV.
- No DDL / DML executed by this MR.

**Supported locales.**

| Locale | Status | Behaviour |
|---|---|---|
| EN | ✅ All 10 codes localised (verified strings from `comm_0221_001.ts:65-99`). | Renders verified EN messages. |
| KO | ⚠️ **UNRESOLVED** — legacy KO labels live in Jex Studio's ML config DB (`comm_0221_001_jNNN`), not in source. | EN fallback via `LocaleProvider`. |
| VI | ⚠️ **UNRESOLVED** — same as KO. | EN fallback. |

**Reuse contract.** Any future feature that surfaces an F-leg `TRAN_TP` value MUST consume `TranTp` from `wecms-api/.../firmbankinghistory/service/TranTp.java`. Any feature that surfaces the Firm Banking History row shape MUST consume `FirmBankingServiceNameResolver` rather than reimplementing the leg branches. Any feature that surfaces an `SVC_TYPE` discriminator letter from the unified history view MUST consume [`FirmBankingSvcType`](../../../src/main/java/com/kosign/wecms/common/enums/FirmBankingSvcType.java) rather than re-implementing the F/S/G/B → semantic mapping. Frontend pages MUST consume `svc_type_key` and `svc_name_key` from the wire and pass them through `t(...)` — no per-page `SERVICE_NAME_MAP` and no per-page `coerceFirmBankingServiceType` switch.

### Truncated-text tooltip (`<EllipsisTooltip>`)

**Goal.** Replace the browser-default `title=` tooltip on every truncating cell with the dark Figma-styled custom tooltip (Figma node `9198:8036`, file key `7lkk3Dovp5pFXp0JVsdxed`). Black background, white text, 8 px radius, 4 × 6 px padding, downward / upward notch, 14 px medium typography — all matched against the workspace tokens (`bg-wb-ink`, `text-white`, `rounded-lg`, `text-wb-14`, `font-medium`, `tracking-wb-tight`).

**Component.** [`components/shared/EllipsisTooltip.tsx`](../../../../wecms-web/components/shared/EllipsisTooltip.tsx) — shared, not feature-local. Renders the trigger as a `<span>` with `block truncate`; on hover or focus, when the trigger's `scrollWidth > clientWidth + 1` (sub-pixel safe), portal-mounts a tooltip card on `document.body` so parent overflow can't clip it. Auto-flips placement when there's no room below the trigger. Closes on Escape, scroll, or resize. Recomputes truncation via `ResizeObserver` so dynamic font-loading + container resizing don't strand a stale flag. `aria-label` carries the full text when truncated; `tabIndex={0}` only when truncated so keyboard users can focus elided cells without polluting tab order on fitting cells.

**Wired into Firm Banking History.**

| Site | Replaced |
|---|---|
| [`FirmBankingHistoryDetailPopover.tsx`](../../../../wecms-web/components/settings/firm-banking-history/FirmBankingHistoryDetailPopover.tsx) — every value cell in `detailRows.map(...)` | `<span title={r.value}>` (browser default) |
| [`FirmBankingHistoryTable.tsx`](../../../../wecms-web/components/settings/firm-banking-history/FirmBankingHistoryTable.tsx) — Service Name cell | `<span className="block truncate" title={...}>` (browser default) |

The popover's fixed `w-[388px]` width is unchanged; the value column still computes to 160 px and now surfaces overflow via the custom tooltip on hover. The table's Service Name column still respects its `<colgroup>` width (260 px) and surfaces overflow via the same custom tooltip. Modal / page / table widths unaffected.

**Migration path for other truncating call sites (out of scope for this MR).** The codebase has at least three other places that still use `<span className="...truncate" title={...}>` on browser default:

- [`components/settings/bank-account/BankAccountTable.tsx`](../../../../wecms-web/components/settings/bank-account/BankAccountTable.tsx) — Description cell
- [`components/settings/contacts/ContactsTable.tsx`](../../../../wecms-web/components/settings/contacts/ContactsTable.tsx) — multi-line address tooltip
- [`components/shared/excel-upload/ExcelUploadDialog.tsx`](../../../../wecms-web/components/shared/excel-upload/ExcelUploadDialog.tsx) — error tooltip on invalid cells

Each is a one-line swap (`<span className="...truncate" title={x}>{x}</span>` → `<EllipsisTooltip text={x} className="..." />`). They are not migrated by this MR — that's a follow-up sweep coordinated with each owning feature.
- **Service-type filter**: 4 enum values map 1:1 to the backend `svc_type` whitelist (`F`/`S`/`G`/`B`); "all" omits the param.
- **Keyword**: 300 ms debounce; routes to backend `keyword` param which case-insensitive-ILIKEs against `seq_no` (LIKE meta-chars escaped server-side).
- **Sort**: cycle `default → ASC → DESC → default` per `useTableSort`; FIELD_IDs forwarded to the backend whitelist; unknown values fall back silently to default ORDER BY.
- **Pagination**: server-side. Default page size 10 (frontend canonical), backend default 15. Backend clamps `size` to 100. Page is zero-based on the wire, one-based in the UI chrome.
- **Errors**: `ApiCallError` propagates from the fetcher; `useDialog().open({ type: "error", … })` surfaces non-validation codes via the global system dialog with the backend's `trace_id` rendered. `WBE_VALIDATION` is suppressed because the panel guarantees its own input shape.
- **Latched stable data**: prevents pagination/total-count flicker between refetches (mirrors `OperationStatusClient` / `AccountInquiryClient`).
- **i18n**: every column label + filter option + result label + popover label routes through `useTranslation()`. Keys are present in the three workspace-supported locales (`en` / `ko` / `vi`); pages and shared utilities target this set only.

**Memory compliance**

- ✅ `feedback_view_setting_global` — **not applicable to this page.** Firm Banking History uses a fixed table layout; no column-visibility customization, no saved layouts, no popover, no seed. See § 4.4 for the rationale and the future-phase guard.
- ✅ `feedback_desktop_first_ui` — panel inherits Settings modal sizing; min-width 1080 px on the table forces horizontal scroll on narrower laptops, matching Account Inquiry pattern.
- ✅ `feedback_typography_source` — uses only `text-wb-13` / `text-wb-14` / `text-wb-18` workspace tokens; no raw `text-[*px]` / `font-[*]` / inline `fontFamily`. Font stack inherited from `<html>` per `app/layout.tsx`.
- ⚠️ `feedback_figma_first` — no Figma URL provided this turn. The static UI mirrors Figma 9198:11718 (already approved, built before this MR). Wiring-only changes preserve the visual design.

JEX is **read-only reference**. This spec captures the refactor design; JEX behavior is the functional source of truth.

---

## 1. Overview

A unified, paginated, read-only audit history of every **outbound integration call** the WeCMS system has made on behalf of the current tenant for firm-banking-related services. Five distinct legacy source legs (Korean firm-banking gateway, NAPAS scraping transfers, generic scraping balance/transaction/card calls, Vietnam GDT e-Invoice inquiries, Vietnam Business Registration inquiries) are stitched together into one unified row shape and surfaced as a single list page with date-range filter, service-type filter, sequence-no keyword search, sortable columns, paginated results, and a per-row "View Details" popover that renders integration-leg-specific fields and a localized result message.

The page is a **diagnostic / audit tool**. It does not initiate transactions. It does not modify data.

---

## 2. Scope

**In scope:**

- Five-leg `UNION ALL` consolidation matching the legacy IDO `IDO.TB_FIRM_TRAN_R002`:
  | Leg | `SVC_TYPE` | Source table | Localized service name |
  |---|---|---|---|
  | Firm Banking Transaction | `F` | `TB_FIRM_TRAN` | `tran_tp` ∈ `{201, 202, 203}` → Inquiry Recipient / Transfer Payment / Inquiry Transfer Result |
  | Funds Transfer (Scraping) | `S` | `NAPAS_REMIT_TRNS_HSTR` | "Funds Transfer (Scraping)" |
  | Scraping Balance / Transaction / Card | `S` | `EXT_TRAN_CALL_HIS` (`svc_id` ∈ `BALANCE_INQUIRY`, `REAL_TIME_ONLINE_INQUIRY`, `CARD_INQUIRY`) | "Scraping Balance" / "Scraping Transaction" / "Scraping Card Transaction" |
  | Vietnam GDT Inquiry (e-Invoice) | `G` | `NXSIAS_MODULE_TRAN_LOG_HIS` (`nxisas_type` ∈ `E-INVOICE_SALES_INQUIRY`, `E-INVOICE_EXPENSE_INQUIRY`) | "Sale E-Invoice Inquiry" / "Expense E-Invoice Inquiry" |
  | Vietnam Business Inquiry | `B` | `NXSIAS_MODULE_TRAN_LOG_HIS` (`nxisas_job` ∈ `BUSSINESS_REGISTRATION_DETAIL`, `BUSSINESS_REGISTRATION_LIST`) | "Vietnam Business Inquiry" |
- Required date-range filter (`fromDate`, `toDate`, `YYYYMMDD`) over `tarnDtm[0:8]`.
- Optional service-type filter (single-value: `F` / `S` / `G` / `B`).
- Optional sequence-number keyword search (`SRCH_WD` → case-insensitive partial match on `seq_no`).
- Server-side pagination (`page`, `size`).
- Server-side sort over the View-Setting-driven whitelist (`tarnDtm`, `svcType`, `svcNm`, `seqNo`, `depBankCd`, `rsltCd`).
- Per-row "View Details" popover (no extra HTTP call — every detail field is in the list response, the popover renders client-side).
- Localized service names + localized result-message dictionary lookup.
- Tenant scoping via `BIZ_KEY = TenantContext.currentBizKey()` on every leg.

**Out of scope (deliberately excluded — handled by other features or the live system):**

- Triggering or replaying any integration. The page is read-only.
- Editing firm-banking calls, NAPAS remit history, or NXISAS log rows.
- Writing to any of the five source tables. They are populated by the live JEX system / external bank gateways / NXISAS bridge — none of which the refactor R&D operates today.
- Listing GDT-side e-Invoice rows from `BUSA_DOC_MSTR` — that is the **Add-on Service > Sales / Expenses e-Invoices** screens. This page only logs the *calls* WeCMS made to the GDT bridge.
- Excel export (Phase 2 / 3 — not Sprint baseline; defer until PO confirms).
- Print (legacy has none for this screen).
- A reverse-direction (browser-initiated) `inquire` button (none in legacy; never had one).
- VND/FCY currency split (this page is non-monetary).

**Refactor R&D constraints (workspace memory `project_refactoring_scope.md`):** local-only, GitLab-only, no DEV/UAT/PROD deploy, no live bank or NXISAS integration, no coexistence with the live JEX system. The five source tables are populated locally only by seeded rows or future demo data — there is no live integration path to generate new rows from the refactor stack.

---

## 3. Legacy reference (JEX)

| Asset | Path | Anchors |
|---|---|---|
| View JSP | `WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0221_001_view.jsp` | header line 94 ("Firm Banking History"); table cols 213–235; service-type filter list 145–183 |
| Action JSP (read) | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/api/comm/comm_0221_001_r001_act.jsp` | `validateInput` 29–38; `getDynamic` 40–64; `getRows` 66–77; `setResult` 127 |
| TypeScript | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0221_001.ts` | `loadData` 238–404; `fnSelect` 405–428; `fnDetails` 429–638; result-code map 459–478 |
| WSVC XML (API) | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0221_001_r001.xml` | `<in>` 32–39; `<out>` 40–43; `<subRule>` 44–57 |
| IDO XML (custom read) | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.TB_FIRM_TRAN_R002.xml` | full file — five-leg UNION 24–129 |
| Live DEV URL | `https://dev.wecms.vn/comm_0221_001.act` | (settings menu — bottom-left) |

> File-line citations follow `path:line` format so phase 2 can verify each behavior against the legacy source.

---

## 4. Target architecture

### 4.1 Backend (`wecms-api`)

```
domain/setting/firmbankinghistory/
├── controller/FirmBankingHistoryController.java
│      GET /api/v1/settings/firm-banking-history
├── service/FirmBankingHistoryService.java
│      list(query, pageable) → Page<FirmBankingHistoryRow>
├── repository/FirmBankingHistoryRepository.java
│      Spring Data JPA OR a single @Query against a Flyway-installed read-only view (preferred — see §6.4)
├── entity/FirmBankingHistoryView.java
│      @Entity @Immutable @Table(name = "V_FIRM_BANKING_HISTORY")
│      Read-only projection of the five-leg UNION
└── dto/
    ├── FirmBankingHistoryRow.java       — record (camelCase Java fields → snake_case wire)
    └── FirmBankingHistoryListResponse.java — record { list, totalCount, totalPages, pageNumber, pageSize }
```

Conventions enforced:

- Java DTO fields stay camelCase; global `JacksonConfig` converts to snake_case on serialize. No `@JsonProperty("UPPER_SNAKE")` (CTO directive 2026-05-08, memory `project_api_naming_lowercase`).
- Controller returns `ApiResponse<FirmBankingHistoryListResponse>` per [§ Response envelope](../../../CLAUDE.md#response-envelope--all-endpoints). Errors thrown as `BusinessException`.
- Tenant scope: `TenantContext.currentBizKey()` is read inside the service; **never** from request body or query string.
- No `JexData`, no `IDODynamic`, no `getInput()`/`getOutput()`. The JEX patterns are reference, not target.

### 4.2 Frontend (`wecms-web`)

```
app/(dashboard)/settings/firm-banking-history/
└── page.tsx                                 — thin server component composing <FirmBankingHistoryPage />

components/settings/firm-banking-history/
├── FirmBankingHistoryPage.tsx               — page shell (PageHeader + filter row + table + pagination + details modal)
├── FirmBankingHistoryClient.tsx             — fetcher + state (page/size/filter/sort/refreshKey)
├── FirmBankingHistoryTable.tsx              — shadcn <Table> render of mapped rows
├── ServiceTypeFilter.tsx                    — single-select dropdown (All / F / S / G / B)
├── ResultBadge.tsx                          — green tick / red ✗ / amber pending pill (success/failure-code mapping)
├── DetailsPopover.tsx                       — shadcn <Dialog>; receives the full mapped row (no extra fetch)
├── mockData.ts                              — static-UI mock (one row per leg)
└── types.ts                                 — FirmBankingHistoryRow (camelCase) + FilterState

lib/types/api.ts                             — adds ApiFirmBankingHistoryRow + ApiFirmBankingHistoryListResponse (snake_case wire)
lib/api/mappers.ts                           — adds mapFirmBankingHistoryRow(api → camelCase)
lib/api/firmBankingHistory.ts                — fetchFirmBankingHistory({fromDate, toDate, svcType?, srchWd?, page, size, sortField?, sortDirection?})
```

The page is **Standard menu page** baseline (per [wecms-web/CLAUDE.md § Approved reference set](../../../../wecms-web/CLAUDE.md)): same shell as Banking > Account Inquiry (VND), no tabs, no totals footer. The "View Details" affordance reuses the system `<Dialog>` modal pattern (NOT the small layer-popover from JEX, which is bound to jQuery dropdown and not portable).

### 4.3 Database (R&D PostgreSQL)

Two new Flyway migrations (no DDL executed in this phase — script intent only):

| Migration | Purpose |
|---|---|
| `V59__firm_banking_history_source_tables.sql` | Add the three source tables not yet migrated locally: `TB_FIRM_TRAN`, `NXSIAS_MODULE_TRAN_LOG_HIS`. (`NAPAS_REMIT_TRNS_HSTR` is in V13; `EXT_TRAN_CALL_HIS` is in V16.) Plus seed: real DEV rows for the configured tenant per the [Data Migration Rule](../../../CLAUDE.md). |
| `V60__firm_banking_history_view_and_fns.sql` | Install the five-leg `UNION ALL` as a read-only **view** (`V_FIRM_BANKING_HISTORY`) plus port the helper functions `kr_to_vn_time(varchar)` and the `wb_get_napas_bank_nm` / `wb_get_bank_nm` overloads from JEX DEV `pg_proc` if not already present. |

Final filename numbers will be assigned at build time (next free V-number after the latest); the order above is the dependency ordering. **Do not run DDL in this phase** — `postgres` MCP is DEV read-only. Phase 2 will author and apply.

### 4.4 View Setting integration — NOT APPLICABLE

**Firm Banking History does NOT use the View Setting system.** The page renders a fixed table layout — no column-visibility customization, no saved per-user layouts, no "Manage Columns" popover, no `SYS_SCR_OPTION_WECMS` / `USER_SCR_OPTION_WECMS` rows.

Rationale:
- The legacy `comm_0221_001` JEX view ships a **hardcoded** column set (`comm_0221_001_view.jsp:213-235`); there is no JEX `dymAreaId`, no `wecmsScrOption*` consumption on this screen, and no list-view toolbar icon to open a column popover. Sales e-Invoices' "consume View Setting data without rendering the popover" (IR-9) does not apply here either — that page reads column metadata for Excel export; this page has no Excel export and no column-metadata consumption.
- Memory `feedback_view_setting_global` ("View Setting is a global/common feature, not per-page or Sprint-1-only") is a contract for pages that *do* customize columns — Firm Banking History never appeared in the list of consumer pages and the legacy screen never offered the affordance.

**Future-phase guard.** Do not seed `SYS_SCR_OPTION_WECMS` rows for `WEB_SVC_ID = "firm-banking-history"`. Do not author a `V62__view_setting_seed_firm_banking_history.sql`. Do not import `useViewSetting`, `<ViewSettingPopover>`, `SCREEN_IDS.*`, or any of the `domain/setting/screenoption` surface from the panel, table, or filters. The table's column FIELD_ID set (`TARN_DTM` / `SVC_TYPE` / `SVC_NM` / `SEQ_NO` / `DEP_BANK_CD` / `RSLT_CD`) is the **sort whitelist only** — it matches the backend `FirmBankingHistorySortField` enum and exists purely so click-to-sort can drive `?sort_field=…&sort_direction=…` against the existing `<TableHeaderCell>` plumbing. It is not a View Setting registration.

What this page DOES use (kept):
- Click-to-sort on the 6 whitelisted FIELD_IDs (server-side via the Phase 2 backend).
- Server-side pagination (page / size, default 10, clamped to 100).
- Server-side filtering (date range, svc_type, keyword).
- Desktop-first responsive layout with a 1080 px min-width table inside the modal's scroll container.

---

## 5. API contract

### 5.1 Request

```
GET /api/v1/settings/firm-banking-history
  ?from_date=YYYYMMDD               (required)
  &to_date=YYYYMMDD                 (required, fromDate ≤ toDate)
  &svc_type=F|S|G|B                 (optional)
  &keyword=<seqNoLike>              (optional, trimmed; max 100 chars; %/_/! escaped server-side)
  &page=0                           (optional, zero-based; default 0)
  &size=15                          (optional; default 15; clamped to MAX_PAGE_SIZE = 100)
  &sort_field=tarn_dtm              (optional; whitelist below; default tarn_dtm)
  &sort_direction=desc              (optional; asc | desc; default desc)

Headers:
  Authorization: Bearer <jwt>
  Accept-Language: en | ko | vi | km
```

Sort whitelist: `tarn_dtm`, `svc_type`, `svc_nm`, `seq_no`, `dep_bank_cd`, `rslt_cd`. Anything else → `WBE_VALIDATION` (no silent fallback).

### 5.2 Success response

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "list": [
      {
        "tarn_dtm":     "20260507143015",
        "svc_type":     "F",
        "svc_nm":       "201",
        "seq_no":       "0000000123",
        "dep_bank_cd":  "Vietcombank - 970436",
        "rslt_cd":      "00000000",
        "rslt_msg":     "00000000",
        "job":          "201",
        "req_data":     null,
        "van_org_cd":   "FB"
      }
    ],
    "total_count":   42,
    "total_pages":   3,
    "page_number":   0,
    "page_size":     15
  }
}
```

Field semantics — see [§ 6 Database](#6-database) for the source-leg column-by-column origin and the wire ↔ Java ↔ legacy mapping table.

### 5.3 Error responses

Per [`GlobalExceptionHandler`](../../../src/main/java/com/kosign/wecms/common/exception/GlobalExceptionHandler.java) — every error envelope carries `status` + `timestamp` + `path` + `trace_id`.

| Cause | HTTP | Code | Notes |
|---|---|---|---|
| `from_date` / `to_date` missing | 200 | `WBE_VALIDATION` | mirrors JEX `ValidationHelper.validateRequired` (`comm_0221_001_r001_act.jsp:30-31`) |
| `from_date` or `to_date` not a valid `YYYYMMDD` | 200 | `WBE_VALIDATION` | mirrors `ValidationHelper.validateDate8` (`comm_0221_001_r001_act.jsp:33-34`) |
| `from_date > to_date` | 200 | `WBE_VALIDATION` | mirrors `ValidationHelper.validatePeriod` (`comm_0221_001_r001_act.jsp:36`); frontend ALSO checks (`comm_0221_001.ts:420` → `WBE1000`) — refactor keeps the server-side check authoritative |
| `svc_type ∉ {F,S,G,B}` | 200 | `WBE_VALIDATION` | new explicit validation; legacy silently accepts any 1-char string |
| `sort_field` not in whitelist | 200 | `WBE_VALIDATION` | new explicit validation |
| `size > 100` | 200 | `WBE_VALIDATION` | global page-size cap; mirrors backend rule, not legacy parity |
| Anything unexpected | 500 | `WBE9999` | catch-all, logged with `traceId` |

No new error codes are introduced. The existing framework codes (`WBE_VALIDATION`, `WBE9999`) cover this feature — no entries needed in [docs/_shared/error-code-registry.md](../../../../docs/_shared/error-code-registry.md). The legacy `WBE1000` ("end date earlier than start date") only fired client-side in JEX; the refactor consolidates it to `WBE_VALIDATION` server-side.

---

## 6. Database

### 6.1 Source tables (DEV — read-only reference, mirror locally)

Schema captured from `wecmsvndev` on 2026-05-08 via the read-only `postgres_dev` MCP. Full column lists in [../../database/settings/firm-banking-history-schema.md](../../references/database/settings/firm-banking-history-schema.md). High-level shape:

| Table | PK | Columns used by this screen | DEV row count (2026-05-08) | Local status |
|---|---|---|---|---|
| `TB_FIRM_TRAN` | `(TRAN_DT, BIZ_KEY, TRAN_SEQ)` | `TRAN_DT`, `TRAN_TM`, `BIZ_KEY`, `OUT_BANK_CD`, `TRAN_SEQ`, `TRAN_TP`, `RS_CD`, `VAN_ORG_CD` | 10 097 | **Missing — V59 adds** |
| `NAPAS_REMIT_TRNS_HSTR` | `(BIZ_KEY, SEQ_NO)` | `REG_DTM`, `BIZ_KEY`, `SEQ_NO`, `OUT_BANK_CODE`, `TRNS_STTS`, `TRNS_MSG` | 290 | Already present (V13) |
| `NXSIAS_MODULE_TRAN_LOG_HIS` | none in DEV (verify) | `REG_DTM`, `BIZ_KEY`, `UUID`, `NXISAS_TYPE`, `NXISAS_MODULE`, `NXISAS_JOB`, `NXISAS_ERR_CODE`, `NXISAS_ERR_CONT`, `REQ_DATA` | 77 250 | **Missing — V59 adds** |
| `EXT_TRAN_CALL_HIS` | `(SVC_ID, REF_DATE, SEQ_NO)` | `REG_DTM`, `BIZ_KEY`, `SEQ_NO`, `BANK_CD`, `SVC_ID`, `RSLT_CD`, `ERR_CONT` | 24 019 | Already present (V16) |

`BIZ_KEY` is **non-null on all four tables** (verified) so tenant scoping does not introduce null-row leakage.

### 6.2 Read-only view: `V_FIRM_BANKING_HISTORY`

The five-leg `UNION ALL` is identical in shape to the legacy `IDO.TB_FIRM_TRAN_R002.xml`. Migrating it as a Postgres **view** (rather than re-deriving in JPQL or Criteria) buys two things:

1. **Byte-for-byte parity** with JEX. Phase 2 can `EXPLAIN` and `EXPLAIN ANALYZE` against the same plan the live system uses.
2. **One JPA entity** (`FirmBankingHistoryView`), one repository, one query path. No five-leg fan-out in Java.

The view's columns mirror the legacy `<out>` of the IDO:

| View column | Type | Source |
|---|---|---|
| `TARN_DTM` | VARCHAR(14) | `kr_to_vn_time(TFT.TRAN_DT‖TRAN_TM)` for leg `F`; `REG_DTM` for legs `S`/`G`/`B` |
| `SVC_TYPE` | CHAR(1) | literal `'F'` / `'S'` / `'G'` / `'B'` per leg |
| `SVC_NM` | VARCHAR(50) | `TRAN_TP` for leg `F`; constant string for the others |
| `SEQ_NO` | VARCHAR(50) | `TRAN_SEQ` (firm) / `SEQ_NO::varchar` (napas, ext_tran) / `UUID` (nxisas) |
| `DEP_BANK_CD` | VARCHAR(200) | `wb_get_napas_bank_nm(OUT_BANK_CD) ‖ ' - ' ‖ OUT_BANK_CD` for leg `F`; `wb_get_bank_nm(OUT_BANK_CODE,'s') ‖ ' - ' ‖ OUT_BANK_CODE` for napas; `NXISAS_MODULE` for nxisas |
| `RSLT_CD` | VARCHAR(20) | `RS_CD` (firm) / `TRNS_STTS` (napas) / `NXISAS_ERR_CODE` (nxisas) / `RSLT_CD` (ext_tran) |
| `RSLT_MSG` | VARCHAR(2000) | `RS_CD` (firm — same value, frontend resolves via `DICTIONARY_VAN`) / `TRNS_MSG` (napas) / `NXISAS_ERR_CONT` (nxisas) / `ERR_CONT` (ext_tran) |
| `JOB` | VARCHAR(5000) | `TRAN_TP` (firm) / `'TRANSFER'` (napas) / `NXISAS_JOB` (nxisas) / `'BALANCE'`/`'TRANSACTION'`/`'CARD_USING'` (ext_tran, by `SVC_ID`) |
| `REQ_DATA` | VARCHAR(1000) | `NULL` (firm, napas, ext_tran) / `REQ_DATA` (nxisas) |
| `BIZ_KEY` | VARCHAR(20) | per-leg source row's `BIZ_KEY` |
| `VAN_ORG_CD` | VARCHAR(20) | `VAN_ORG_CD` (firm) / `NULL` (others) |

**Predicate ordering:** every leg filters on `BIZ_KEY = :bizKey` first so each per-leg branch hits its tenant-scoped index before the union materialises. Sort + paging are applied to the materialised set.

**Performance reading:** total of ~111K rows across all legs in DEV. Date-range + tenant filter typically narrows to <1K rows. No new index needed at R&D scale; **Phase 2 should profile against ≥30-day window before declaring "done"** and add `(BIZ_KEY, REG_DTM)` indexes per source table only if EXPLAIN warrants it.

### 6.3 DB function ports (helpers used by the view)

| Function | DEV signature | Purpose | Migration |
|---|---|---|---|
| `kr_to_vn_time(varchar)` | `kr_to_vn_time(kr_time_str varchar) → varchar` | Convert KR timezone `YYYYMMDDHHMMSS` to VN local | V60 — port verbatim from `wecmsvndev.kr_to_vn_time` |
| `wb_get_bank_nm(varchar, varchar)` | overload `(_bank_cd, _type_nm)` | Already ported in V44 ([account-inquiry-vnd-db.md § V44](../../references/database/banking/account-inquiry-vnd-db.md)) | reuse |
| `wb_get_napas_bank_nm(varchar)` | `(_bank_cd) → varchar` | NAPAS-specific bank-name resolution (lookup table different from regular bank list) | V60 — port verbatim |
| `wb_get_napas_bank_nm(varchar, varchar, varchar)` | `(_bank_cd, _type_nm, _land_cd) → varchar` | 3-arg overload (not used by this screen, but ship together for completeness) | V60 |

Strategy decision (consistent with V44): **parity-critical SQL helpers stay as DB functions, ported verbatim from JEX DEV `pg_proc`.** Do not re-implement in Java.

### 6.4 Why a view (not JPQL `UNION`)

JPA does not natively support `UNION` across heterogeneous entities. Three alternatives were considered:

1. **Five repositories + Java-side merge + sort + paginate.** Rejected: server-side sort by `tarnDtm` across legs requires materializing all legs in memory; breaks pagination semantics; loses index assistance. ~111K rows is small but the algorithm is wrong.
2. **One native `@Query` returning a projection.** Workable, but the SQL string is 90 lines long and lives in a Java string literal — hard to read, hard to diff against JEX. We hit the same EXPLAIN cost as a view, but lose the named DB object.
3. **One read-only Postgres `VIEW` + one immutable JPA entity + Spring Data derived methods.** Chosen. Mirrors JEX `IDO.TB_FIRM_TRAN_R002` semantics, indexable, EXPLAIN-friendly, repository code stays trivial (`findByBizKeyAndTarnDtmBetween…`).

Trade-off: a view is a schema object — **changes require a Flyway migration**, not a Java commit. That's the right friction level for parity-critical SQL.

---

## 7. Validation

| Field | Rule | Source citation |
|---|---|---|
| `from_date` | required | `comm_0221_001_r001_act.jsp:30` |
| `to_date` | required | `comm_0221_001_r001_act.jsp:31` |
| `from_date` | valid `YYYYMMDD` (length 8, parsable as `LocalDate`) | `comm_0221_001_r001_act.jsp:33` |
| `to_date` | valid `YYYYMMDD` | `comm_0221_001_r001_act.jsp:34` |
| `from_date ≤ to_date` | period check | `comm_0221_001_r001_act.jsp:36` (server) + `comm_0221_001.ts:420` (client) |
| `svc_type` | optional; if present must be one of `F`, `S`, `G`, `B` | NEW — derived from view JSP filter list (`comm_0221_001_view.jsp:148-181`); legacy back-end did not enforce |
| `keyword` | optional; trimmed; max 100 chars | length 100 declared in `WSVC.comm_0221_001_r001.xml:35` |
| `page` | ≥ 0 | new (refactor enhancement; legacy used `PAGE_NO` 1-based, refactor uses 0-based) |
| `size` | 1 ≤ size ≤ 100 | global cap from [`AccountService`](../../../CLAUDE.md) |
| `sort_field` | whitelist (see §5.1) | NEW — refactor explicit; legacy `SortHelper` accepts any `paramSort` string |

Validation lives on the controller's record `@RequestParam` binding (`@NotBlank`, `@Pattern(regexp="\\d{8}")`, `@Min`, `@Max`). Bean-validation failures funnel into the global handler as `WBE_VALIDATION` (HTTP 200, business-error envelope).

**No business-rule validation beyond date and enum.** The page never writes; there is no per-leg business gate (e.g. account-status check, approval-line check) — those are on the source-leg writer paths (live JEX), not on the read path.

---

## 8. Error handling

Every error funnels through the project's global `@RestControllerAdvice` ([`GlobalExceptionHandler`](../../../src/main/java/com/kosign/wecms/common/exception/GlobalExceptionHandler.java)). The error-envelope contract is documented in [wecms-api/CLAUDE.md § Error handling](../../../CLAUDE.md#error-handling--every-error-envelope-must-carry).

This feature does not introduce a new exception class. It does not add a new `WBExxxx` code. Operational errors map as:

| Failure | Handler | HTTP | Code |
|---|---|---|---|
| Bean-validation on the record | `MethodArgumentNotValidException` | 200 | `WBE_VALIDATION` |
| Sort field not in whitelist | thrown as `IllegalArgumentException` | 400 | `WBE_VALIDATION` |
| DB connection / function failure | `DataAccessException` | 500 | `WBE_DB` |
| Catch-all | `Exception` | 500 | `WBE9999` |

All errors carry `timestamp`, `path`, `trace_id`. The frontend's [`LoginError`/`ApiCallError`](../../../../wecms-web/CLAUDE.md) display path renders the `trace_id` in the system error dialog so the user can quote it back to support.

**Important — result-code translation is NOT error handling.** The `RSLT_CD` field in each row carries leg-specific result codes (e.g. `00000000` = success for firm-banking, `0000` = success for NAPAS, `42110000` = "no transaction results found" for scraping). The frontend's `DetailsPopover` translates these to localized messages via:

- For `SVC_TYPE='F'` (firm banking): lookup against `DICTIONARY_VAN` (existing JEX dictionary; ported with the dictionary tables — track as a separate scope item, not blocking Phase 2).
- For all others: fixed map of 16 codes → 16 i18n keys (`fb_history.result.42110000` etc.) reproducing `comm_0221_001.ts:459-478` verbatim.

These are **localization** concerns, not error envelope concerns. The HTTP call returns 200 with the row regardless of `RSLT_CD`.

---

## 9. Reuse map

Reuse-first is the rule (workspace [CLAUDE.md](../../../../CLAUDE.md)). Build only what is missing and shared.

### 9.1 Backend reuse — already shipped in `wecms-api`

| Component | Reused for | Reuse evidence |
|---|---|---|
| `common/exception/GlobalExceptionHandler` + `BusinessException` + `WBE_VALIDATION` | All errors | exists |
| `common/response/ApiResponse` + global `JacksonConfig` (snake_case) | Wire envelope | exists |
| `common/security/JwtAuthenticationFilter` + `TenantContext.currentBizKey()` | Tenant scoping | exists |
| `common/i18n/MessageResolver` + `Accept-Language` → `LangCd` | Status code labels (if backend resolves) | exists |
| `common/exception/TraceId` + `TraceIdFilter` (MDC) | `trace_id` echo in error envelope | exists |
| `BaseEntity` audit columns | N/A — view is read-only | exists |
| `wb_get_bank_nm(_, 's')` (V44) | NAPAS leg `DEP_BANK_CD` resolution | exists |

### 9.2 Backend — must be added (refactor R&D first use)

| Component | Why it's new | One-time cost |
|---|---|---|
| `kr_to_vn_time(varchar)` Postgres function (V60) | First feature in the refactor that needs the legacy KR↔VN time conversion. Port verbatim from JEX DEV. | low |
| `wb_get_napas_bank_nm(varchar)` + 3-arg overload (V60) | First feature needing NAPAS-specific bank name resolution. | low |
| `V_FIRM_BANKING_HISTORY` view (V60) | First feature in the refactor that consumes a multi-table union as a single read shape. | low |
| `TB_FIRM_TRAN` table + entity (V59 + Java) | First feature needing firm-banking-side row | medium — entity has 30 cols; only 8 used by this screen, but legacy schema parity rule says mirror full DEV column set. Map every column even if unused (consistent with `BUSI_INFM`'s 45-col entity). |
| `NXSIAS_MODULE_TRAN_LOG_HIS` table + entity (V59 + Java) | First feature needing NXISAS-side row | medium — 12 cols |
| Repository + service + controller for this screen | New endpoint | low |

### 9.3 Frontend reuse — already shipped in `wecms-web`

| Component | Reused for | Reuse evidence |
|---|---|---|
| `<AppShell>` + `<Header>` + `<Sidebar>` + `<ProfileMenu>` | Page chrome | `(dashboard)/layout.tsx` |
| `<PageHeader>` + `<PageActionButtons>` | Page title + Print/Excel placeholder buttons | `components/shared/` |
| `<SearchInput>` + debounce pattern | Keyword filter | reuse |
| `<DateRangePicker>` UI component (currently UI-only — see [feature status](../../../../wecms-web/CLAUDE.md#feature-status)) | Date filter UI shell | UI yes; **wire-up is missing in the global component** — implementing it for this page is a global enhancement. **Flag as gating**: if `DateRangePicker` real picker is still not wired by Phase 2, this page either ships with the same UI-only date display (OK for static-UI sprint) OR Phase 2 owns the global wire-up. PO/lead pick. |
| `<Pagination>` (global, existing) | Page-size + nav | `components/shared/Pagination.tsx` |
| `<DataTableShell>` / shadcn `<Table>` | List body | reuse the no-footer variant — same as Account Inquiry baseline |
| `useDialog()` system dialog (4 types, ≤500 px) | Validation + error display | reuse |
| `lib/axios` + `lib/types/api.ts` `ApiResponse<T>` envelope | HTTP boundary | reuse |
| `lib/api/mappers.ts` (snake → camel translation) | This page's row mapper | extend, don't replace |
| `LocaleProvider` + `useTranslation()` (in-house i18n, EN/KO/VI — the three workspace-supported locales) | Service-name + result-code labels | reuse |
| `formatDateForView` / `formatDateLang` helpers | `tarnDtm` → display | reuse |

### 9.4 Frontend — must be added (refactor R&D first use)

| Component | Why it's new |
|---|---|
| `<ServiceTypeFilter>` | Single-select dropdown specific to this page's enum (F/S/G/B). Could be generalized later if a second page needs it; build as feature-local for now. |
| `<ResultBadge>` | Per-leg result tint (success/failure). **Could** reuse `StatusBadge` from `payment/approval-box` if its tokens fit; verify in Phase 2. Default plan: small wrapper that delegates to `StatusBadge` once tokens align. |
| `<DetailsPopover>` | Modal showing the legacy "View Details" content. Uses the shadcn `<Dialog>` primitive directly. **Not** a clone of the JEX jQuery dropdown — that pattern doesn't fit the system dialog rules (see [§ Dialogs & modals](../../../../wecms-web/CLAUDE.md#dialogs--modals)). |
| `mapFirmBankingHistoryRow` mapper | Wire ↔ in-memory translation |
| `firmBankingHistory.ts` API client | One fetcher |
| `mockData.ts` | Static-UI fallback |

### 9.5 Anything missing (build-once-and-share)

- A reusable **DateRangePicker** with a real popover calendar + range model. Not built yet (currently UI-only). Required by this page and several future pages. Track as a global frontend enhancement; **owner: Arthur** (Sprint 2 candidate).
- An i18n bundle for the 16 legacy result-code messages (`fb_history.result.*`). Required by this page; might be reused by other diagnostic pages later. Add to `locales/en.json` + `ko.json` + `vi.json` (the three workspace-supported locales).
- A localized `DICTIONARY_VAN` lookup helper (for SVC_TYPE='F' result-message decoding). The legacy uses `jexjs.getCode("DICTIONARY_VAN", code)`. Refactor needs the same dictionary on the wire; **owner: Sonny — coordinate with the dictionary-port plan; out-of-scope for Phase 1 design**.

---

## 10. Out-of-scope

Listed for traceability:

- Any **write** flow against the source tables (no `c001` / `u001` / `d001` exists in JEX for this page either; the page is purely an audit reader).
- **Bank integration / NXISAS bridge.** Refactor R&D scope is local-only; the source rows are populated by the live JEX system / external gateways. The refactor has no live-row insertion path. **Demo seed migration** in V59 will copy a representative slice from DEV per the [Data Migration Rule](../../../CLAUDE.md).
- **GDT live-pull.** No "Update" / "Inquire" button in legacy; none in refactor.
- **Excel export.** Defer to Phase 2 PO confirmation. If approved, the implementation pattern is the same as `addon/sales-einvoices/export` — one new `ExportRowProvider`, one `@PostMapping("/api/v1/settings/firm-banking-history/export")`. `screenId="firm-banking-history"`.
- **Print.** Legacy has none.
- **Per-row drill-down to the source-leg native screen** (e.g. clicking a NAPAS row to open the NAPAS transfer detail). Not in legacy; potential UX enhancement for later.
- **Realtime / push refresh.** This is a manual refresh page.
- **Cross-tenant view (multi-`BIZ_KEY`).** Tenant scope is hard rule; admin / support tooling for multi-tenant inspection is a separate feature, not this one.

---

## 11. Open questions / blockers

| # | Topic | Decision needed | Owner | Status |
|---|---|---|---|---|
| 1 | Frontend date-range picker — wire it up globally before this page lands, or ship Phase 2 with the UI-only display? | Sonny + Arthur | **Resolved (Phase 3a)** — shared `<InteractiveDateRangePicker>` wired in. |
| 2 | DICTIONARY_VAN port plan — which sprint takes the dictionary tables / lookup helper? Required for SVC_TYPE='F' result-message localization. | Sonny | **Resolved (Phase 3b)** — `lib/i18n/dictionaryVan.{ts,data.ts}` shipped. |
| 3 | ~~Figma column widths for View Setting seed.~~ | ~~Designer + Arthur~~ | **Removed.** Firm Banking History does not use the View Setting system; see § 4.4 for the rationale. No Figma column-width extraction is required. |
| 4 | Demo seed scope — how many real DEV rows per leg do we copy locally? Mirror Account Inquiry's V22 sizing (≤30 rows per legacy table). | Arthur | Resolved (Phase 2) — V61 seeds 8 rows per leg. |
| 5 | NXSIAS_MODULE_TRAN_LOG_HIS PK — DEV table appears to have no PK; declare one in V59 or leave it as `oid`-only? Composite `(BIZ_KEY, REG_DTM, UUID)` is the natural choice. | Arthur | Resolved (Phase 2) — V59 declares the composite PK. |
| 6 | `tarnDtm` value for NXISAS / NAPAS / EXT_TRAN legs — legacy uses `REG_DTM` directly (already VN-localized at insert time). Confirm we don't need `kr_to_vn_time` on those legs (only the firm-banking leg uses it). | Arthur — verify in Phase 2 against DEV row sample | Resolved (Phase 2) — view definition applies `kr_to_vn_time` only to the F leg. |
| 7 | Phase 2 landing scope — does this feature ship in the existing **Settings** sprint or stand alone? | Mr. Jung (PO) | Open (planning question, not a code blocker). |
| 8 | ~~View Setting `FIELD_ID` set.~~ | ~~Designer + PO~~ | **Removed.** Firm Banking History does not register with View Setting; see § 4.4. |

All listed items are either resolved or planning-only — no remaining code blockers.

---

## 12. Cross-references

- Migration mapping: [../../migration/firm-banking-history-mapping.md](../../references/migration/firm-banking-history-mapping.md)
- Database design: [../../database/settings/firm-banking-history-schema.md](../../references/database/settings/firm-banking-history-schema.md)
- Parity audit checklist: [../../audits/firm-banking-history-parity-checklist.md](../../references/audits/firm-banking-history-parity-checklist.md)
- Frontend page design (Phase 2 deliverable): `../../frontend/settings/firm-banking-history-page.md` *(not authored in Phase 1)*
- Backend API design (Phase 2 deliverable): `../../backend/settings/firm-banking-history-api.md` *(not authored in Phase 1)*
- Cross-feature service mapping: [../../migration/service-mapping.md](../../references/migration/service-mapping.md) — **add a row** for `comm_0221_001_r001` when this design is approved
- View Setting feature: [../view-setting.md](../../../../docs/jex/features/view-setting.md) — global / common feature consumed by Account Inquiry, Sales / Expenses e-Invoices, and Operation Status. **Not consumed by this page** (see § 4.4).
- Workspace conventions: [/CLAUDE.md](../../../../CLAUDE.md), [wecms-api/CLAUDE.md](../../../CLAUDE.md), [wecms-web/CLAUDE.md](../../../../wecms-web/CLAUDE.md)
- Naming directive: memory `project_api_naming_lowercase`
- Refactor R&D scope: memory `project_refactoring_scope`
