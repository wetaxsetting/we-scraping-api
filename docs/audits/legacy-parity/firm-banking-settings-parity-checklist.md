# Firm Banking Settings — Refactor Parity Checklist


> ⚠️ **Historical migration numbering (note added 2026-08-17).** This is a dated, point-in-time parity checklist
> written on 2026-05-09, **before** the [2026-06-09 Flyway rebaseline](../../references/database/flyway-migration-baseline.md).
> Every `V<n>` below is a **pre-rebaseline number** and is preserved as written — the findings are only
> meaningful against the migration set as it stood that day. Those numbers do **not** identify the same
> migrations today (the set was renumbered to one-table-per-migration, and seeds / helper indexes / views
> were dropped entirely). To find the current migration for any table, see
> [flyway-migration-baseline.md](../../references/database/flyway-migration-baseline.md) §5 or run
> `ls src/main/resources/db/migration/ | grep <table>`.

**Companion to:** [docs/refactor-wecms/features/settings/firm-banking-settings.md](../../features/settings/firm-banking-settings.md), [docs/refactor-wecms/migration/firm-banking-settings-mapping.md](../../references/migration/firm-banking-settings-mapping.md), [docs/refactor-wecms/database/settings/firm-banking-settings-schema.md](../../references/database/settings/firm-banking-settings-schema.md)
**Phase:** Used by Phases 2 (backend impl), 3 (frontend wire-up), and 4 (QA / Mr. Youvita) to verify behavior parity vs JEX live.

**Phase 2 Path-A status (2026-05-09):** All five endpoints **IMPLEMENTED**. List / Detail / Audit-read shipped first (Flyway V62–V65). Update + Delete shipped after P-1 (`AuditLogService`) landed. **Create shipped 2026-05-09** under OQ-8a Option (c) — bounded-bank set + server-derived `file_type` (Shinhan / Vietcombank / Woori); `WBE0001` for unsupported codes; `WBE0002` for missing `TB_FIRM_BANK_CODE` row; `WBE2021` for duplicate PK. V9 (`NAPAS_BANK_INFO` existence) deferred — strictly weaker than V8 for the supported set, table not migrated locally. Tests passing modulo JAVA_HOME=JDK21.

**Phase 3 Path-A status (2026-05-09):** Frontend wired to **all five endpoints**. Settings modal `FirmBankingSettingsPanel` calls `fetchFirmBankingSettings()` via `useApiData` and bumps a refetch token after every Create / Update / Delete success. `FirmBankingDetailsDialog` reads detail + audit by composite PK; `EditFirmBankingDetailsDialog` issues PUT; `NewFirmBankingDialog` (rewritten 2026-05-09) holds a real form with bounded-bank dropdown (3 banks) + 3 text inputs, schema-gated submit, `WBE0001`/`WBE0002`/`WBE2021` surfaced through the shared error dialog. `mockData.ts` retained as offline / failed-fetch fallback. i18n keys seeded across en / ko / vi. Automation toggles remain static-UI no-ops (separate env-settings feature). Tests deferred — `wecms-web` has no test framework dependency and the brief forbids adding deps without confirmation.

**Delete slice — IMPLEMENTED 2026-05-09 (separate MR):** `DELETE /api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}` shipped end-to-end. Hard delete, mirrors `comm_0220_001_d001_act.jsp`. Per OQ-2 the JEX runtime writes no audit row on delete, so the slice has **no `AuditLogService` dependency** — the earlier "Delete pending P-1" entry was a documentation error, corrected in the same MR. Frontend `FirmBankingSettingsPanel.openDeleteConfirm` now passes a real `onConfirm` that calls `deleteFirmBankingSetting()`, surfaces `WBE2020` and other failures via the shared error dialog (review Major Finding #1 fix for the Delete path), refetches the list on success via a refetch-token bumped against `useApiData`'s deps, closes any details/edit modal pinned to the deleted row, and shows a localized success dialog. `EditFirmBankingDetailsDialog`'s Save button is now `disabled` (was silently calling `onClose`) — no other behaviour change to the Edit dialog.

**Tenant scope:** every operation runs against `BIZ_KEY = VN_VNM0004_000001` from `TenantContext`.

**Legend:**
- ☐ Not yet verified
- ✅ Verified — parity confirmed
- ⚠️ Intentional divergence — see note
- ❌ Parity broken — must fix before merge

---

## 1. Operation × Dimension parity matrix

One row per operation × dimension. Each cell is checkable in Phases 2–4.

### 1.1 List — `GET /api/v1/settings/firm-banking-settings` ✅ IMPLEMENTED (Phase 2 Path A backend + Phase 3 Path A frontend)

| Dimension | Check | JEX reference | Status |
|---|---|---|---|
| Functional | Returns every `TB_FIRM_CONF` row for `BIZ_KEY` from `TenantContext` (no client filters) | `comm_0220_001_r001_act.jsp:91-94`, `IDO.TB_FIRM_CONF_R004.xml:24-67` | ☐ |
| Functional | List joins `ACCT_MSTR` LEFT JOIN — rows with no matching account still appear (null `BAL`, `CCY_CD`, `EXPI_DT`) | `IDO.TB_FIRM_CONF_R004.xml:62-64` | ☐ |
| Functional | List joins `TB_FIRM_BANK_CODE` for `BANK_NM` (filtered to `ANT_DIV_CD='NAPAS'`) | feature §5.1 + `comm_0220_002_c001_act.jsp:116-118` | ⚠️ Refactor folds the join into `V_FIRM_BANKING_SETTING`; JEX surfaces `BANK_NM` separately |
| Functional | Order: row order matches JEX (no explicit ORDER BY in IDO — preserve insertion order or document the divergence) | `IDO.TB_FIRM_CONF_R004.xml` (no ORDER BY) | ☐ |
| Validation | No client filters; missing fields cannot fail validation | n/a | ☐ |
| Permission | JWT required | `WSVC.comm_0220_001_r001.xml:28` `<login>Y</login>` | ☐ |
| Permission | `NORM_USER_ONLY_YN=Y` enforcement (feature OQ-5) | `WSVC.comm_0220_001_r001.xml:26` | ☐ — deferred per OQ-5 |
| Permission | Tenant isolation: response only contains rows for caller's `BIZ_KEY` | `comm_0220_001_r001_act.jsp:46-47` | ☐ |
| Audit | None — list does not write audit log | `comm_0220_001_r001_act.jsp` (no `AuditLogsInfm` call) | ☐ |
| UI | List rendered via `FirmBankingSettingsPanel.tsx` → `FirmBankingLinkedAccounts.tsx` → `FirmBankingAccountItem.tsx` | mapping §5.1 | ☐ |
| UI | Bank logo resolves from `BankLogo.tsx` keyed on `out_bank_cd` | existing static UI | ☐ |
| UI | `wdrl_act_no` displayed masked (frontend masking only — wire is cleartext) | feature §9 | ☐ |
| Wire format | All field names lowercase snake_case | `wecms-api/CLAUDE.md` §API Naming | ☐ |

### 1.2 Detail — `GET /api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}` ✅ IMPLEMENTED (Phase 2 Path A backend + Phase 3 Path A frontend)

| Dimension | Check | JEX reference | Status |
|---|---|---|---|
| Functional | Returns single row for PK `(biz_key, out_bank_cd, wdrl_act_no)` | `comm_0220_003_r001_act.jsp:69-72` | ☐ |
| Functional | Audit log returned via separate `…/audit` resource (was `REC` field in JEX) | feature §5.3, `comm_0220_003_r001_act.jsp:74-89` | ⚠️ Refactor splits audit into its own GET |
| Validation | `out_bank_cd` required | `comm_0220_003_r001_act.jsp:31` | ☐ |
| Validation | `wdrl_act_no` required | `comm_0220_003_r001_act.jsp:32` | ☐ |
| Validation | Path `@Pattern` rejects out-of-range chars | feature §5.2 | ☐ |
| Validation | Row not found → `BusinessException("WBE_NOT_FOUND")` | refactor stance (JEX returns empty `null` row implicitly) | ⚠️ Refactor adds explicit not-found |
| Permission | JWT required | default `SecurityConfig` | ☐ |
| Permission | Tenant isolation: PK lookup uses `BIZ_KEY` from `TenantContext`, not from the request body | feature §5.2 | ☐ |
| Audit | None on read | n/a | ☐ |
| UI | Renders in `FirmBankingDetailsDialog.tsx` (read-only) or `EditFirmBankingDetailsDialog.tsx` (edit mode) — toggle by menu item clicked | mapping §5 | ☐ |
| UI | Audit log loads lazily on Audit tab open | feature §5.3 | ☐ |
| Wire format | Lowercase snake_case | global Jackson config | ☐ |

### 1.3 Audit log — `GET /api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}/audit` ✅ IMPLEMENTED (Phase 2 Path A backend + Phase 3 Path A frontend)

| Dimension | Check | JEX reference | Status |
|---|---|---|---|
| Functional | Returns audit rows filtered by `(biz_key, ref_no='TB_FIRM_CONF', ref_no1=out_bank_cd, ref_no2=wdrl_act_no, wsvc_id='comm_0220_001', lang_cd=caller's locale)` | `comm_0220_003_r001_act.jsp:74-86` | ☐ |
| Functional | `wsvc_id` value: legacy `'comm_0220_001'` vs refactor `'firm-banking-settings'` (feature OQ-2 / OQ-6) | mapping §6 | ☐ — pending PO sign-off |
| Functional | Sorted by `reg_dtm` desc | implicit JEX order — confirm | ☐ |
| Validation | Same path validation as Detail | feature §5.3 | ☐ |
| Permission | JWT + tenant isolation | same | ☐ |
| Audit | None on read | n/a | ☐ |
| UI | Audit tab in `FirmBankingDetailsDialog.tsx` populates from this resource | mapping §5.1 | ☐ |
| Wire format | Lowercase snake_case | global | ☐ |

### 1.4 Create — `POST /api/v1/settings/firm-banking-settings` ✅ IMPLEMENTED 2026-05-09 (Phase 2 backend + Phase 3 frontend, OQ-8a Option (c) provisional)

| Dimension | Check | JEX reference | Status |
|---|---|---|---|
| Functional | Inserts row into `TB_FIRM_CONF` | `comm_0220_002_c001_act.jsp:134`, `TbFirmConfDAO.java:62-96` | ✅ `FirmBankingSettingsService.create` |
| Functional | `file_type` derived server-side per OQ-8a Option (c): `970424→SHB`, `970436→VCB`, `970457→WRB`. **Anything else returns `WBE0001`.** Future bank onboarding tracked via `TODO(future-bank-onboarding)` in `BANK_TO_FILE_TYPE`. | `comm_0220_002_c001_act.jsp:123-127` + DEV check 2026-05-09 | ✅ |
| Functional | `bat_tran_branch_div` defaults to `'Y'` (V15) | `TbFirmConfDAO.java:70-72` | ✅ |
| Functional | `bat_tran_branch_limit` defaults to `2000` (V16) | `TbFirmConfDAO.java:74-77` | ✅ |
| Functional | `org_stat` defaults to `'1'` (AVAILABLE) — frontend always supplies "1" until UX adds a status selector (V17) | `TbFirmConfDAO.java:64-67` | ✅ |
| Functional | `reg_dttm`, `start_dttm`, `mod_dttm` server-set; `end_dttm` left null (open-ended) (V18) | `TbFirmConfDAO.java:79-89` | ✅ |
| Functional | `last_edtr` set to `TenantContext.currentUserNm()` (V19) | `comm_0220_002_c001_act.jsp:130` | ✅ |
| Validation | All five fields required: `out_bank_cd`, `bank_prtn_id`, `van_org_cd`, `org_stat`, `wdrl_act_no` (V1–V5 / V20 / V21) — DTO `@NotBlank` + `@Size` | `comm_0220_002_c001_act.jsp:38-43` | ✅ |
| Validation | `org_stat ∈ {0,1,2,3}` (V6) — DTO `@Pattern("^[0-3]$")` | `OrgStat.java:7-10` | ✅ |
| Validation | `out_bank_cd ∈ {970424 Shinhan, 970436 Vietcombank, 970457 Woori}` (V8) — service `BANK_TO_FILE_TYPE.get(...)` returns null → `WBE0001 Invalid OUT_BANK_CD` | `comm_0220_002_c001_act.jsp:123-127` + DEV evidence 2026-05-09 + OQ-8a Option (c) | ✅ |
| Validation | `out_bank_cd` exists in `NAPAS_BANK_INFO` (V9) | `comm_0220_002_c001_act.jsp:91-95` | ⏳ **DEFERRED** — `NAPAS_BANK_INFO` not migrated locally; V8 strict reject is strictly stronger for the supported set so V9 contributes no extra safety today. Re-enable when the table lands locally (separate MR). |
| Validation | `out_bank_cd` exists in `TB_FIRM_BANK_CODE` for `(ANT_DIV_CD='NAPAS', BANK_CD=outBankCd)` (V10) — `TbFirmBankCodeRepository.existsNapasBankCode()` → `WBE0002 There is no BANK` on miss | `comm_0220_002_c001_act.jsp:116-121` | ✅ |
| Validation | ~~`wdrl_act_no` exists in `ACCT_MSTR` for `(biz_key, bank_cd)` (V11)~~ — **NOT enforced**, legacy parity (OQ-3 resolved 2026-05-09; ticket [#42004](https://www.kosign.com.kh/redmine/issues/42004)) | `comm_0220_002_c001_act.jsp:99-108` (commented out — refactor preserves) | ✅ — service does not call `AccountRepository.existsBy…` |
| Validation | PK uniqueness (V12) — refactor uses `WBE2021` (allocated per OQ-1) instead of legacy `WBE0003` | `comm_0220_002_c001_act.jsp:84-88` | ✅ |
| Permission | JWT + tenant isolation | same | ✅ |
| Permission | `NORM_USER_ONLY_YN=Y` (OQ-5) | WSVC | ☐ — deferred (auth-hardening sprint, OQ-5 default) |
| Audit | Refactor writes four locale audit rows (EN/KO/VI/KM) under one `audit_logs_seq_no` via shared `AuditLogService.append()` (legacy JEX writes three) | `comm_0220_002_c001_act.jsp:138-174`, mapping §6 | ⚠️ Refactor extends to KM (intentional) |
| Audit | `ref_no='TB_FIRM_CONF'`, `ref_no1=out_bank_cd`, `ref_no2=wdrl_act_no`, `wsvc_id='firm-banking-settings'` (OQ-6 override 2026-05-09 — refactor slug, NOT legacy `comm_0220_001`) | `comm_0220_002_c001_act.jsp:164-166` + OQ-6 override | ✅ |
| Audit | Audit-log write isolated in `@Transactional(REQUIRES_NEW)` (Phase 1 P-1 — `AuditLogService`) | refactor design (feature OQ-6) | ✅ |
| UI | `NewFirmBankingDialog.tsx` rewrites the static UI as a real form (bounded-bank dropdown + 2 text inputs + searchable Withdrawal Account autocomplete + Submit) | mapping §5 | ✅ |
| UI | Submit enabled only when schema validation passes — bank picked, partner_id + van_code non-empty (≤ 20 chars), AND a Withdrawal Account row is selected (free-form typed text is NOT valid; mirrors legacy gate `acc_no_selected = $('#acc_no li.selected').length > 0`) | feature §5.4 + legacy `comm_0220_002.ts:177-180` | ✅ |
| UI | **Withdrawal Account is a searchable autocomplete** loaded dynamically per bank — not a static dropdown / free input. Mirrors legacy `comm_0220_002.ts#fuctionLoadWithdrawalAccounts` + `searchAccounts`. | legacy `comm_0220_002.ts:225-292` | ✅ |
| UI | Selecting a bank loads accounts via `fetchAccountsByBank(out_bank_cd)`; out-of-order responses guarded by an `ignore` flag in the load effect | refactor design | ✅ |
| UI | Clicking the Withdrawal Account input opens the dropdown; typing filters the loaded list client-side (no debounce — list is small, in-memory) | legacy `comm_0220_002.ts:97-117, 270-292` | ✅ |
| UI | Selecting a row stores the raw `acct_no` in form state; the input shows the masked `<acctNo> (<ccyCd>)` chip | legacy `comm_0220_002.ts:117, 261` | ✅ |
| UI | Withdrawal Account input is disabled until a Bank Name is selected | refactor UX | ✅ |
| UI | Clearing / changing the bank resets the loaded accounts, the typed query, and the selected row | refactor UX | ✅ |
| UI | Currency suffix `(VND)` / `(USD)` is rendered client-side from `ccy_cd` (legacy mirror); backend ships only the raw 3-letter code | legacy `comm_0220_002.ts:261` | ✅ |
| UI | Account masking comes from the backend `acct_no_masked` field (server applies `LogMask.acct`); frontend never re-implements masking | OQ-4 + LogMask | ✅ |
| UI | On success: success dialog → onCreated callback (panel bumps refetch token → list refetches) → onClose | feature §13 | ✅ |
| UI | On error: shared error dialog renders backend `status.code` + `status.message` + `trace_id` (incl. `WBE0001` / `WBE0002` / `WBE2021`) | feature §13 | ✅ |
| UI | `file_type` is NEVER on the wire from the frontend — confirmed by absence of the field in `ApiFirmBankingSettingCreateRequest` and a controller test (`create_clientSendingFileType_isIgnored_neverEchoedBack`) | OQ-8a | ✅ |
| UI | Bank dropdown limited to Shinhan / Vietcombank / Woori; the union, the backend `BANK_TO_FILE_TYPE` map AND the V68 `NAPAS_BANK_INFO` seed all stay in lockstep (TODO(future-bank-onboarding) markers on all three) | OQ-8a | ✅ |
| Wire format | Lowercase snake_case in / out (5 wire fields: `out_bank_cd`, `wdrl_act_no`, `bank_prtn_id`, `van_org_cd`, `org_stat`) | global | ✅ |
| Lookup endpoint | `GET /api/v1/banking/accounts/by-bank?out_bank_cd={NAPAS}` mirrors legacy `comm_1004_001` — resolve NAPAS via `NAPAS_BANK_INFO` (V68), then `WHERE biz_key = ? AND bank_cd = ?` against `ACCT_MSTR`. Returns `{acct_no, acct_no_masked, ccy_cd, acct_nick_nm, acct_nm}`. No keyword param, no debounce, no pagination — load-once-per-bank + client filter. | legacy `comm_1004_001_act.jsp:56-66` | ✅ |

### 1.5 Update — `PUT /api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}` ✅ IMPLEMENTED (Phase 2 backend + Phase 3 frontend, 2026-05-09)

| Dimension | Check | JEX reference | Status |
|---|---|---|---|
| Functional | Updates `bank_prtn_id`, `van_org_cd`, `org_stat` only | `comm_0220_003_u001_act.jsp:80-84`, `IDO.TB_FIRM_CONF_U001.xml` | ☐ |
| Functional | `file_type` not changed on update | mapping §5 (feature §5.5) | ☐ |
| Functional | `mod_dttm` server-set | `TbFirmConfDAO.java:101-103` | ☐ |
| Functional | `last_edtr` set to current user `name` | `comm_0220_003_u001_act.jsp:81` | ☐ |
| Functional | Body MUST NOT carry `out_bank_cd` / `wdrl_act_no` (path is canonical) | feature §5.5 | ☐ |
| Validation | Three editable fields required (V3, V4, V5) | `comm_0220_003_u001_act.jsp:34-37` | ☐ |
| Validation | `org_stat ∈ {0,1,2,3}` (V6) | same | ☐ |
| Validation | Row exists for PK (V13) — else `WBE_NOT_FOUND` | `comm_0220_003_u001_act.jsp:79` (`ValidationHelper.validateTbFirmConf`) | ☐ |
| Validation | Status transitions unrestricted (V22) — any `org_stat` → any other | refactor preserves JEX behavior | ☐ |
| Permission | JWT + tenant isolation | same | ☐ |
| Audit | Three (refactor: four) audit rows on update | `comm_0220_003_u001_act.jsp:88-123` | ⚠️ KM extension |
| UI | `EditFirmBankingDetailsDialog.tsx` submits and stays open until 200 OK | mapping §5 | ☐ |
| UI | On success: closes, list refetches, success toast | same | ☐ |
| UI | Cancel/close discards changes (no unsaved-changes guard) | feature §4.2 | ☐ |
| Wire format | Lowercase snake_case in / out | global | ☐ |

### 1.6 Delete — `DELETE /api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}` ✅ IMPLEMENTED (Phase 2 backend + Phase 3 frontend, 2026-05-09)

| Dimension | Check | JEX reference | Status |
|---|---|---|---|
| Functional | Hard-deletes the row | `comm_0220_001_d001_act.jsp:70`, `TbFirmConfDAO.java:124-130`, `IDO.TB_FIRM_CONF_D001.xml` | ☐ |
| Functional | No soft-delete column on `TB_FIRM_CONF` | feature §5.6 | ☐ |
| Validation | PK fields validated via path `@Pattern` (V1, V2) | feature §5.6 | ☐ |
| Validation | Row exists for PK (V13) — else `WBE_NOT_FOUND` | `comm_0220_001_d001_act.jsp:66` | ☐ |
| Permission | JWT + tenant isolation | same | ☐ |
| Audit | **JEX writes none. Refactor writes none — legacy parity.** (OQ-2 resolved 2026-05-09) | `comm_0220_001_d001_act.jsp` (no `AuditLogsInfm` write), feature §5.6 | ☐ |
| UI | Confirm dialog → DELETE → refetch | `comm_0220_001.ts:92-108` | ☐ |
| UI | Confirm dialog uses shared `<ConfirmDialog>` styling | feature §4.2 | ☐ |
| Wire format | Lowercase snake_case | global | ☐ |

---

## 2. Cross-cutting parity

| Concern | Check | Status |
|---|---|---|
| `ApiResponse<T>` envelope shape: `{ status: { error, code, message }, data, ... }` | ☐ |
| Error envelope adds `timestamp`, `path`, `trace_id` (`X-Trace-Id` header echo) | `wecms-api/CLAUDE.md` §Response envelope | ☐ |
| `Accept-Language` resolved to `LangCd` and stamped into JWT claim, then read back from `TenantContext` (not re-parsed) | `JwtAuthenticationFilter` + `TenantContext` | ☐ |
| Audit-log strings localized into 4 languages (EN/KO/VI/KM) via `MessageResolver` | mapping §6 | ☐ |
| All JEX `@JsonProperty("UPPER_SNAKE")` annotations dropped on the refactor side | `wecms-api/CLAUDE.md` §Refactoring rules | ☐ |
| No `JexData` / `IDODynamic` mimicry in Java | `wecms-api/CLAUDE.md` §JEX is read-only reference | ☐ |
| Frontend wire types in `lib/types/api.ts` use snake_case | mapping §5.1 | ☐ |
| Frontend mappers in `lib/api/mappers.ts` translate snake → camel | mapping §5.1 | ☐ |
| Typography tokens used (`text-table-cell`, `text-button`, etc.) — no `text-[14px]` literals | root `CLAUDE.md` §Global Typography, memory `feedback_typography_source` | ☐ |
| Desktop-first layout (memory `feedback_desktop_first_ui`) | ☐ |
| Figma WB365 referenced before drafting any new dialog (memory `feedback_figma_first`) | ☐ |
| View Setting registration: `WEB_SVC_ID = 'firm-banking-settings'` seeded in a follow-up migration if/when the panel exposes configurable columns (memory `feedback_view_setting_global`) | ☐ — deferred |

---

## 3. Database parity

| Check | Source | Status |
|---|---|---|
| `TB_FIRM_CONF` has 14 columns matching DEV exactly | DEV `information_schema.columns` 2026-05-09 | ☐ |
| All NOT-NULL columns match DEV nullability | same | ☐ |
| Defaults preserved verbatim: `ORG_STAT='0'`, `BAT_TRAN_BRANCH_DIV='Y'`, `BAT_TRAN_BRANCH_LIMIT=2000` | database doc §2 | ☐ |
| PK `(biz_key, out_bank_cd, wdrl_act_no)` | `pg_constraint` | ☐ |
| Hibernate `validate` passes against the entity (CHAR vs VARCHAR traps mitigated via `@JdbcTypeCode(SqlTypes.CHAR)`) | database doc §8 | ☐ |
| `V_FIRM_BANKING_SETTING` view returns the same row count as the JEX list IDO when run against an identical seed | database doc §3 + §10 | ☐ |
| `BANK_NM` resolves correctly via `TB_FIRM_BANK_CODE.ANT_DIV_CD='NAPAS'` filter | database doc §3 | ☐ |
| `wdrl_act_no` stored plain text (DEV parity, OQ-4 resolved 2026-05-09) | feature §7.4 | ☐ — confirm no `@Convert` on `FirmBankingSetting.wdrlActNo` and no feature-local encryption helper |
| `wdrl_act_no` masked in API list / detail responses by default (`xxx-xxx-1234`) | feature §9 | ☐ |
| `wdrl_act_no` cleartext only in the edit-dialog detail variant | feature §9 | ☐ |
| Logging masks `wdrl_act_no` in DEBUG / WARN / ERROR — never logs full account number | feature §7.4.1 | ☐ |

---

## 4. Error code parity

| Refactor code | Mapped JEX behavior | Status |
|---|---|---|
| `WBI0000` (success) | All ok-paths | ☐ |
| `WBE_VALIDATION` | `ValidationHelper.validateRequired` + bean-validation failures | ☐ |
| `WBE0001` (added 2026-05-09 per OQ-8a Option (c)) | V8 — `out_bank_cd` outside the supported 3-bank set (`970424`/`970436`/`970457`). Argument: `"OUT_BANK_CD"`. | ✅ |
| `WBE0002` (added 2026-05-09) | V10 — bank not in `TB_FIRM_BANK_CODE` for `(ANT_DIV_CD='NAPAS', BANK_CD=outBankCd)`. Argument: `"BANK"`. V9 (`NAPAS_BANK_INFO`) deferred — see §1.4. | ✅ (V10) / ⏳ (V9 deferred) |
| `WBE2020` (allocated 2026-05-09 per OQ-1) | Firm banking setting not found (Detail / Update / Delete / Audit when PK absent) | ☐ |
| `WBE2021` (allocated 2026-05-09 per OQ-1) | Duplicate firm banking setting (Create when PK already exists) | ☐ |
| `WBE2022` (reserved per OQ-1 — not used in v1) | Invalid status transition — future use | n/a |
| `WBE2023` (reserved per OQ-1 — not used in v1) | Sensitive field access denied / view-secret — future use | n/a |
| `WBE9999` | Unhandled exception | ☐ |

Phase 2 must add `WBE2020`–`WBE2023` to `messages.properties` + [docs/_shared/error-code-registry.md](../../../../docs/_shared/error-code-registry.md). `WBE2020` and `WBE2021` carry feature-specific text; `WBE2022` and `WBE2023` are reserved placeholders (text added if/when they are activated).

---

## 5. Open-questions tracker (mirrored from feature §13)

| OQ | Status | Owner | Resolves before |
|---|---|---|---|
| OQ-1 — Error code allocation | ✅ resolved 2026-05-09 — see §4 Error code parity above | Sonny | — |
| OQ-2 — Audit on Delete | ✅ resolved 2026-05-09 — keep legacy parity (no audit row on Delete) | Mr. Jung | — |
| OQ-3 — Re-enable V11 (`ACCT_MSTR` existence) | ✅ resolved 2026-05-09 — keep legacy parity (V11 stays disabled; ticket [#42004](https://www.kosign.com.kh/redmine/issues/42004)) | Mr. Jung | — |
| OQ-4 — Encryption-at-rest utility | ✅ resolved 2026-05-09 — plain text in Phase 2 (legacy parity); masking is presentation-layer only; no `@Convert` on the column; future encryption is a project-wide migration | Sonny | — |
| OQ-5 — `NORM_USER_ONLY_YN` enforcement | ✅ default accepted 2026-05-09 — skip enforcement in v1; defer `userType` JWT wiring to an auth-hardening sprint | Arthur | — (does not block v1) |
| OQ-6 — `wsvc_id` in audit rows: legacy or refactor id | ✅ **overridden 2026-05-09** — refactor slug `firm-banking-settings`, NOT legacy `comm_0220_001`. The earlier "keep legacy" default was retired in favour of slug-based audit ids across every refactor surface; V67 back-fills any pre-existing local rows. Compatibility consequence accepted (refactor is local-only R&D; JEX never queries the local DB). | Audit-log feature owner | — |
| OQ-7 — Approval-line for firm-banking changes | 🅿️ parked — future feature ticket; v1 preserves JEX (no approval gate) | Mr. Jung | — |
| OQ-8 — DEV `out_bank_cd` distinct values check | ✅ verified 2026-05-09 — 3 distinct values (Shinhan / Vietcombank / Woori); see §1.4 V8 row above | Arthur | — |
| **OQ-8a — `out_bank_cd` outside the supported mapping** | ✅ **provisionally resolved 2026-05-09 — Option (c)**: bounded-bank set `{970424, 970436, 970457}`; anything else → `WBE0001`; `file_type` server-derived; client never sends `file_type`. Sonny chose without waiting for PO sign-off so the Create slice could ship same day; Mr. Jung may override later (decision is reversible). Future bank onboarding tracked via `TODO(future-bank-onboarding)` markers in `BANK_TO_FILE_TYPE` (backend) and `SUPPORTED_BANKS` (frontend). | Sonny → Mr. Jung (override allowed) | — |

---

## 6. Sign-off gate

This checklist must be **green** (no ☐ in §1, §2, §3, §4) before:

- Phase 2 → Phase 3 handoff (backend ready)
- Phase 3 → Phase 4 handoff (frontend ready, ready for QA)
- Phase 4 → MR / sprint close (QA passed)

Any ⚠️ items must have an explicit PO sign-off recorded in the MR description.

**Phase 2 entry condition (final, as of 2026-05-09):** OQ-1, OQ-2, OQ-3, OQ-4, OQ-6, OQ-8 resolved; OQ-8a provisionally resolved (Option c — PO override allowed); OQ-5 default-accepted (deferred to auth-hardening sprint); OQ-7 parked. **All five operations (List / Detail / Audit-read / Create / Update / Delete) are wired end-to-end on the backend AND the frontend.** Phase 4 (Mr. Youvita QA) is unblocked — coordinate sign-off + PO confirmation of OQ-8a Option (c).
