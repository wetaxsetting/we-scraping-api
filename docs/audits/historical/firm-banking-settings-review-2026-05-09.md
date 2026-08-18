# Firm Banking Settings — Independent Phase 2/3 Review

**Reviewer:** independent (did not author the code under review)
**Date:** 2026-05-09
**Scope:** Phase 2 (backend, MR delivered earlier today) + Phase 3 (frontend, MR delivered earlier today) — read-only Path-A slice (`List` / `Detail` / `Audit-read`).
**Method:** read every authored file directly; cross-check against [docs/refactor-wecms/features/settings/firm-banking-settings.md](../../features/settings/firm-banking-settings.md), [parity checklist](../legacy-parity/firm-banking-settings-parity-checklist.md), and JEX `comm_0220_*` source. Read-only DEV DB queries via the `postgres` MCP per [.claude/rules/database.md](../../../../.claude/rules/database.md).

> ⚠️ **Migration-numbering note (added 2026-08-17).** This is a **dated, point-in-time audit**. It was
> written against the pre-rebaseline migration set, where the tables discussed shipped as `V62`
> (`TB_FIRM_CONF`), `V63` (`AUDIT_LOGS_INFM`), `V64` (the `V_FIRM_BANKING_SETTING` view) and `V65` (demo
> seed). The [2026-06-09 rebaseline](../../references/database/flyway-migration-baseline.md) reassigned every number:
>
> | Historical (as reviewed 2026-05-09) | Current status |
> |---|---|
> | `V62__firm_banking_settings_source_table.sql` | **CURRENT** → [`V100__tb_firm_conf.sql`](../../../src/main/resources/db/migration/V100__tb_firm_conf.sql) |
> | `V63__audit_logs_infm_prerequisite.sql` | **CURRENT** → [`V8__audit_logs_infm.sql`](../../../src/main/resources/db/migration/V8__audit_logs_infm.sql) |
> | `V64__firm_banking_settings_view.sql` | **REPLACED BY CODE** — no view migration exists; the projection is a Hibernate `@Subselect` on the entity |
> | `V65__firm_banking_settings_demo_seed.sql` | **DROPPED** — demo seeds were removed in the rebaseline; no current equivalent |
>
> Findings below are preserved as written. Line anchors have been re-pointed to the current files where a
> column still exists, and dropped where the referenced construct no longer does.

---

## 1. Verdict

**APPROVED-WITH-FOLLOWUPS.**

The read-only slice is functional, JEX-parity-faithful on every behaviour the parity checklist tracks, and security-baseline-clean (tenant scoping, server-side `wdrl_act_no` masking, no cleartext in logs, no SQL injection surface, no XSS sinks, no secrets in seeds, JWT-gated). However, **two MAJOR findings** affect operational correctness on the frontend: silent error-swallowing on API failure, and a mapper bank-brand fallback that mislabels unknown bank codes. Both are recoverable with small targeted edits in a follow-up MR; neither blocks the read-only slice from running. The backend is clean; all backend findings are MINOR or NIT.

The read-only contract (List / Detail / Audit-read) holds end-to-end.

---

## 2. Findings

| # | Severity | Dimension | File:line | Issue | Suggested fix |
|---|---|---|---|---|---|
| 1 | **Major** | A / B / D | [components/settings/firm-banking-settings/FirmBankingSettingsPanel.tsx:78-83](../../../../wecms-web/components/settings/firm-banking-settings/FirmBankingSettingsPanel.tsx#L78-L83) + [FirmBankingDetailsDialog.tsx:92-107](../../../../wecms-web/components/settings/firm-banking-settings/FirmBankingDetailsDialog.tsx#L92-L107) | API errors are silently swallowed. The panel falls back to `mockLinkedAccounts` on `list.error`, and the detail dialog renders empty values when `detail.error` / `audit.error` is non-null. There is **no** error toast or dialog raised — `useApiData` only sets `state.error`, no global subscriber. The sibling [`FirmBankingHistoryPanel.tsx:155-176`](../../../../wecms-web/components/settings/firm-banking-history/FirmBankingHistoryPanel.tsx) calls `dialog.open({ type: "error", … })` for non-validation backend errors; this feature does not. For a banking-integration Settings page, mistaking a backend failure for "no rows" or a stale mock is a real operational hazard. | Mirror the firm-banking-history pattern: add a `useEffect` watching `list.error` (and the dialog's `detail.error` / `audit.error`) that calls `dialog.open({ type: "error", ... })` with a localized message + the `ApiCallError.code` for the trace-id surface. |
| 2 | **Major** | B / Code quality | [lib/api/mappers.ts:732-733](../../../../wecms-web/lib/api/mappers.ts#L732-L733) | Mapper falls back to `"vcb"` brand for any `out_bank_cd` outside `{970424, 970436, 970457}`: `const brand: BankBrand = NAPAS_TO_BRAND[api.out_bank_cd] ?? "vcb";`. The Javadoc immediately above (lines 666-670) claims unknown codes "fall back to the initial-letter `<BankLogo>` rendering — no exception thrown" — but the code returns the VCB brand, which makes `BankLogo` paint a Vietcombank green circle. An unknown bank visually labels as Vietcombank. | Use a brand value not associated with a real bank for the unknown case — the cleanest fix is to widen `BankBrand` to include `"unknown"` (or treat `null`) and have `BankLogo` render the initial-letter path for that value. Smaller fix: pick `"hd_bank"` (no SVG mark; renders red circle with initial) and update the Javadoc to match. The current code + comment are inconsistent. |
| 3 | **Minor** | F | [components/settings/firm-banking-settings/FirmBankingDetailsDialog.tsx:114-116](../../../../wecms-web/components/settings/firm-banking-settings/FirmBankingDetailsDialog.tsx#L114-L116) | Tautological ternary: `const bankName = (detail.data?.out_bank_cd ? account.bankName : account.bankName) || "";`. Both ternary branches return `account.bankName`. Dead conditional; suggests the author intended to use a `bankNm` field from the live detail (which the `FirmBankingSettingDetail` DTO does not include). | Replace with `const bankName = account.bankName ?? "";`. Or — if the live bank name from the detail JOIN is desired — extend `ApiFirmBankingSettingDetail` + backend DTO with `bank_nm` and use that. Current shape is stuck in a no-op. |
| 4 | **Minor** | UX / Code quality | [components/settings/firm-banking-settings/FirmBankingDetailsDialog.tsx:119](../../../../wecms-web/components/settings/firm-banking-settings/FirmBankingDetailsDialog.tsx#L119) | `wdrlActNoDisplay` flickers format when the detail loads. While loading, `account.accountNumber` is `"7000****5280 (VND)"` (masked + currency suffix from the list mapper); after loading, `detail.data?.wdrl_act_no` is `"700005285280"` (cleartext, no currency suffix, different length). The dialog "swaps shape" in front of the user. | Either (a) mask the cleartext on display in the dialog (defeats the cleartext-for-edit-roundtrip purpose — not recommended), or (b) construct a uniform display string that always carries the currency suffix during loading and after, so only the digits portion changes. Cheap fix: format both states the same way at the dialog level. |
| 5 | **Minor** | A (CLAUDE.md compliance) | [components/settings/firm-banking-settings/FirmBankingSettingsPanel.tsx:73-77](../../../../wecms-web/components/settings/firm-banking-settings/FirmBankingSettingsPanel.tsx#L73-L77) + `mockData.ts` (removed Sprint 1 — Needs Confirmation if still referenced) | Comment claims "the failure is surfaced to ops via the global error toast that the api client raises through the shared `useApiData` hook." This is **false** — `useApiData` raises no toast (see `lib/api/useApiData.ts:48-52`). The comment misrepresents behavior; future maintainers will assume errors are surfaced when they are not. Coupled with finding #1 (no error UI). | Remove the false claim and replace with the truthful statement: errors set `list.error`/`detail.error`/`audit.error`; the panel currently does not surface them. Then fix #1. |
| 6 | **Minor** | C / Schema parity | [V8__audit_logs_infm.sql:21](../../../src/main/resources/db/migration/V8__audit_logs_infm.sql#L21) *(anchor re-pointed — `ref_no2` is line 21 in the current file)* + [FirmBankingSetting.java:73](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/entity/TbFirmConf.java) | `AUDIT_LOGS_INFM.REF_NO2` is `VARCHAR(50)` (mirrors DEV). `TB_FIRM_CONF.WDRL_ACT_NO` is `VARCHAR(100)`. The audit-read endpoint filters `REF_NO2 = wdrlActNo`. If `wdrl_act_no > 50` chars, the lookup fails; the future Update/Create writer's audit-row insert would fail with a DataAccessException. JEX has the same gap (DEV-verified). All current DEV values are 12 digits, so this is theoretical, but it is a latent JEX-parity bug. | Document explicitly in the future audit-log writer feature: clamp `REF_NO2 = wdrlActNo.substring(0, 50)` OR widen `REF_NO2` to `VARCHAR(100)` in a refactor-only deviation migration. Either choice should be a recorded decision; current state has neither. |
| 7 | **Minor** | E / B | `V_FIRM_BANKING_SETTING` view — **REPLACED BY CODE** (historical `V64__firm_banking_settings_view.sql`; no view migration exists today, the projection is a Hibernate `@Subselect`) | LEFT JOIN to `ACCT_MSTR` is on `(BIZ_KEY, WDRL_ACT_NO = ACCT_NO)` only — without `BANK_CD`. Since `ACCT_MSTR` PK is `(biz_key, bank_cd, acct_no)`, two ledger rows could share `(biz_key, acct_no)` across different banks (same account number registered under two banks). DEV verified zero duplicates today (`SELECT acct_no, COUNT(*) ... HAVING COUNT(*) > 1` returned empty), so this is theoretical. JEX `IDO.TB_FIRM_CONF_R004.xml` has the same join shape — parity-preserved. | Document the duplication risk in the view's `COMMENT ON VIEW` so future migrations know the constraint. If duplicates appear in PROD, `SELECT DISTINCT` on the view's PK columns will mask the problem; the proper fix is to add `AND AM.BANK_CD = …mapping_to_napas_code…` once a NAPAS↔CITAD bank-code translation function is available. |
| 8 | **Minor** | F | [FirmBankingSetting.java:97-100](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/entity/TbFirmConf.java) | `@Column(... columnDefinition = "CHAR(1) DEFAULT 'Y'")` on `batTranBranchDiv`. With `spring.jpa.hibernate.ddl-auto = validate`, `columnDefinition` is mostly ignored — the migration owns the schema. The `DEFAULT 'Y'` clause here is duplicative of V62's `BAT_TRAN_BRANCH_DIV CHAR(1) NOT NULL DEFAULT 'Y'` and could mislead a reader into thinking the entity is the source of truth. Same applies to `fileType` (no DEFAULT redundancy there, but `columnDefinition = "CHAR(3)"` adds nothing for `validate`). | Drop `columnDefinition` from both annotations — `length` + `nullable` + `@JdbcTypeCode(SqlTypes.CHAR)` are sufficient for `validate`. Migration alone owns the DEFAULT clause. |
| 9 | **Minor** | F / Tests | [wecms-web/package.json](../../../../wecms-web/package.json) | No frontend test framework (`vitest`/`jest`/`@testing-library`) — `npm run lint` is the only test-flavoured script. The new `lib/api/firmBankingSettings.ts` client + `mapFirmBankingSettingRow` / `mapFirmBankingAuditEntry` mappers + the panel's loading/empty/fallback logic ship without unit tests. The brief acknowledges this and forbids adding deps without confirmation. | Track as a separate enabling-MR: introduce Vitest + `@testing-library/react`, then port the matching test cases (mapper edge cases, mock-fallback branch, dialog loading→loaded transition). Until then, regressions risk landing silently. |
| 10 | **Nit** | A | [FirmBankingSettingsService.java:73](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/service/FirmBankingSettingsService.java#L73) | `LEGACY_AUDIT_WSVC_ID` constant is package-private (no modifier). Used only within this class. The visibility is wider than needed; package-private suggests an intent to share with a sibling class but no sibling consumes it. | Make the constant `private` to match the file's other constants (`REF_NO_TB_FIRM_CONF` is `private`). |
| 11 | **Nit** | A | historical `V62__firm_banking_settings_source_table.sql` header — **MOOT**: the current [`V100__tb_firm_conf.sql`](../../../src/main/resources/db/migration/V100__tb_firm_conf.sql) is generated table DDL and carries no such header comment | The migration header references "V38 'direction locked' rule" but the actual rule lives in [wecms-api/CLAUDE.md §"Refactor identifier naming"](../../../CLAUDE.md). Future readers may search for the rule unsuccessfully. | Quote the file path explicitly: "see `wecms-api/CLAUDE.md` §V38 direction-locked rule". |

---

## 3. Parity checklist — final ticked state

I verified each row by reading the actual code, not the doc summary. Where the doc claims ✅ but the underlying code has a real gap, I downgrade to ⚠️ here.

### 1.1 List — `GET /api/v1/settings/firm-banking-settings`

| Dimension | Status | Notes |
|---|---|---|
| Functional — returns every `TB_FIRM_CONF` row for `BIZ_KEY` | ✅ | Repo: `findAllByBizKeyOrderByOutBankCdAscWdrlActNoAsc` ([FirmBankingSettingViewRepository.java](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/repository/FirmBankingSettingViewRepository.java)) |
| Functional — LEFT JOIN to `ACCT_MSTR` | ✅ | historical `V64` view — now a Hibernate `@Subselect` (REPLACED BY CODE) |
| Functional — JOIN to `TB_FIRM_BANK_CODE` for `BANK_NM` | ✅ | V64; verified 970424 + 970436 + 970457 seeded in V61/V65 |
| Functional — order matches JEX | ⚠️ | JEX has no explicit ORDER BY; refactor adds `ORDER BY OUT_BANK_CD ASC, WDRL_ACT_NO ASC` for stability. **Documented divergence**; not a bug, but worth re-verifying once Update/Delete land that may shuffle insertion order. |
| Permission — JWT required | ✅ | `SecurityConfig.java:41` `anyRequest().authenticated()` |
| Permission — `NORM_USER_ONLY_YN=Y` | ☐ | Per OQ-5 default — deferred. |
| Permission — Tenant isolation | ✅ | `requireBizKey()` from `TenantContext`; PK includes `BIZ_KEY` |
| Audit — list does not write | ✅ | Service has no `auditLogRepository.save` call on this path |
| UI — masked `wdrl_act_no` on wire | ✅ | `LogMask.acct(v.getWdrlActNo())` in `toListRow` |
| UI — mapper produces correct brand | ⚠️ | **Finding #2** — unknown codes mislabeled as VCB |
| UI — error path surfaces to user | ❌ | **Finding #1** — silent fallback to mock, no toast |
| Wire format — snake_case | ✅ | Verified in `FirmBankingSettingsControllerTest#list_returnsSnakeCaseRows_andMaskedWdrlActNo` |

### 1.2 Detail — `GET /…/{out_bank_cd}/{wdrl_act_no}`

| Dimension | Status | Notes |
|---|---|---|
| Functional — single row by PK | ✅ | `settingRepository.findById(new FirmBankingSettingId(bizKey, …))` |
| Validation — required path segments | ✅ | `requireField` defence-in-depth + `@Pattern` |
| Validation — `WBE2020` on missing | ✅ | Service throws; controller test verifies envelope |
| Permission — JWT + tenant isolation | ✅ | Same as list |
| Audit — read does not write | ✅ | No mutation path in `detail()` |
| UI — cleartext `wdrl_act_no` | ✅ | `toDetail(e)` does not mask |
| UI — display flicker on detail load | ⚠️ | **Finding #4** |
| UI — bankName tautology | ⚠️ | **Finding #3** |
| UI — error path surfaces to user | ❌ | **Finding #1** |
| Wire format — snake_case | ✅ | Controller test verifies |

### 1.3 Audit log — `GET /…/{out_bank_cd}/{wdrl_act_no}/audit`

| Dimension | Status | Notes |
|---|---|---|
| Functional — filter shape mirrors `comm_0220_003_r001_act.jsp:74-86` | ✅ | Service test pins it; repo derived method matches |
| Functional — `wsvc_id = 'comm_0220_001'` legacy verbatim (OQ-6) | ✅ | Service `LEGACY_AUDIT_WSVC_ID` constant; controller test verifies |
| Functional — sorted `reg_dtm DESC` | ✅ | `…OrderByRegDtmDesc` derived method |
| Validation — does NOT pre-check row existence | ✅ | Service test `auditLog_doesNotPreCheckRowExistence_returnsEmptyForUnknownRow` |
| Permission — JWT + tenant isolation | ✅ | `bizKey` passed as predicate |
| UI — empty / loading states rendered | ✅ | New i18n keys in en/ko/vi |
| UI — error path surfaces to user | ❌ | **Finding #1** |
| Wire format — snake_case | ✅ | Controller test verifies |

### 1.4 Create — `POST` — ⏳ pending OQ-8a

Not implemented, parity-checklist-correct.

### 1.5 Update — `PUT` — ⏳ pending P-1

Not implemented, parity-checklist-correct.

### 1.6 Delete — `DELETE` — ⏳ pending P-1

Not implemented, parity-checklist-correct.

### Cross-cutting

| Dimension | Status | Notes |
|---|---|---|
| `ApiResponse<T>` envelope | ✅ | Both paths use `ApiResponse.ok(...)`; `GlobalExceptionHandler` wraps errors |
| Error envelope `timestamp` / `path` / `trace_id` | ✅ | Wired by `GlobalExceptionHandler` (verified pre-existing wiring) |
| `Accept-Language` → `LangCd` → `TenantContext` | ✅ | Service reads `TenantContext.currentLangCd()` |
| Audit-log strings localized into 4 locales | ⚠️ | Demo seed (V65) only inserts EN rows. Non-EN callers see empty audit list. The future writer feature will fix this — current behavior is read-only side, demo-data limitation. |
| Legacy `@JsonProperty("UPPER_SNAKE")` stripped | ✅ | Refactor records use Java field names + global SNAKE_CASE strategy |
| No `JexData` / `IDODynamic` mimicry | ✅ | Pure Spring Data JPA |
| Frontend wire types snake_case | ✅ | `lib/types/api.ts` |
| Mapper translates wire→panel | ✅ | `mapFirmBankingSettingRow`, `mapFirmBankingAuditEntry` |
| Typography tokens (no `text-[14.5px]` etc.) | ✅ | New code uses existing `text-wb-13`/`text-wb-14`/etc. classes |
| Desktop-first layout | ✅ | Inherited from existing static UI; new code did not change layout |
| Figma WB365 referenced | ⚠️ | No Figma URL for this MR; relied on existing static UI as source. Acceptable per the brief's branch ("if a Figma URL is provided…"). |

### Database parity

| Dimension | Status | Notes |
|---|---|---|
| `TB_FIRM_CONF` 14 cols match DEV | ✅ | V62 verified column-for-column |
| Defaults `ORG_STAT='0'`, `BAT_TRAN_BRANCH_DIV='Y'`, `BAT_TRAN_BRANCH_LIMIT=2000` | ✅ | V62 |
| PK `(biz_key, out_bank_cd, wdrl_act_no)` | ✅ | V62 |
| `AUDIT_LOGS_INFM` 15 cols match DEV | ✅ | V63 |
| Index for audit-read filter tuple | ✅ | V63 `ix_audit_logs_infm_ref_lookup` |
| Hibernate `validate` traps mitigated | ✅ | `@JdbcTypeCode(SqlTypes.CHAR)` on `fileType` + `batTranBranchDiv` |
| `wdrl_act_no` plain text (OQ-4) | ✅ | No `@Convert` on column; DEV verified plain numeric |
| API masks `wdrl_act_no` on list | ✅ | `LogMask.acct` in `toListRow` |
| Detail returns cleartext (edit-dialog round-trip) | ✅ | `toDetail` passes through unmasked |
| Logging masks `wdrl_act_no` always | ✅ | Both log statements in service use `LogMask.acct(...)` |

### Error code parity

| Refactor code | Used | Notes |
|---|---|---|
| `WBI0000` (success) | ✅ | All ok-paths |
| `WBE_VALIDATION` | ✅ | Service `requireField` |
| `WBE2020` not-found | ✅ | Service `detail()`; messages.properties seeded |
| `WBE2021` duplicate | n/a | No Create yet — message seeded |
| `WBE2022` reserved | n/a | Future use |
| `WBE2023` reserved | n/a | Future use |
| `WBE9999` server fault | ✅ | Service `requireBizKey()` fallback |

### OQ tracker

| OQ | Status | Verifier |
|---|---|---|
| OQ-1 (error codes) | ✅ resolved + verified in messages.properties | grep |
| OQ-2 (no audit on delete) | ✅ no Delete impl yet, no audit code in `auditLog()` either | code read |
| OQ-3 (V11 disabled) | ✅ no `AccountRepository.existsBy…` call exists | grep |
| OQ-4 (plain-text masking) | ✅ no `@Convert`; DEV plain numeric verified | code + DEV |
| OQ-5 (no `@PreAuthorize`) | ✅ controller has none | code read |
| OQ-6 (legacy `wsvc_id`) | ✅ `LEGACY_AUDIT_WSVC_ID = "comm_0220_001"` | code read |
| OQ-7 (no approval-line) | ✅ no `Appr*` import or call | grep |
| OQ-8 (3-bank set) | ✅ documented in V8 + V14 + §5.4 | doc read |
| OQ-8a (Create scoping) | ⏳ open — Create not implemented | feature §13 |

---

## 4. Risk register

| Risk | Why it matters | Mitigation horizon |
|---|---|---|
| **Frontend silent error swallowing** | A user opens the Settings panel during a backend outage and sees the static mock data. They might trust the displayed bindings as live and act on them. For a banking-integration feature that authorizes outbound money movement, that confusion has real consequences. | **Address in next MR** alongside the backend Update/Delete wire-up — Finding #1. |
| **Mapper bank-brand mislabel** | Unknown bank codes paint as Vietcombank. Visually wrong; if a banking partner from outside the supported set ever lands in DEV/PROD, every page shows them in green VCB livery. | **Address in next MR** — Finding #2. |
| **Audit `REF_NO2` truncation latent bug** | When the future Update/Create writer lands, an account number ≥ 51 chars would silently fail to write the audit row OR raise a `DataAccessException`. JEX has the same gap. | Owned by the audit-log feature MR (P-1). Document the schema mismatch explicitly there. |
| **Audit demo seed is EN-only** | Korean / Vietnamese callers see "No audit history yet." even when EN rows exist. Cosmetic but misleading during demo. | Either extend V65 to seed all 4 locales, or accept it and fix when the writer feature lands. |
| **No frontend test coverage** | Every behavioural regression in the new mapper / panel / dialog ships unguarded. The mapper has at least three behaviours worth testing (bank-brand fallback, masked-vs-cleartext, ledger-null fallback) that no test exercises. | **Author a separate enabling MR** that introduces Vitest + @testing-library and ports the test cases from the firm-banking-settings backend tests' shape. |
| **Encryption-at-rest deferred (OQ-4)** | `wdrl_act_no` ships plaintext on disk. Project-wide encryption is a separate epic. While that's outstanding, anyone with `SELECT` on the local DB sees raw account numbers. | Local-only R&D risk; for any future deploy beyond developer laptops, the encryption MR is a hard prerequisite. |
| **OQ-8a still open** | Create endpoint can't be coded until Mr. Jung decides how the refactor handles bank codes outside `{970424, 970436, 970457}`. The Vietcombank row in DEV proves the strict-2-bank rule needs revision. | Awaiting PO decision. Until then, NewFirmBankingDialog stays static. |
| **`NORM_USER_ONLY_YN` enforcement deferred** | JEX's `WSVC.comm_0220_001_r001.xml` gates the list to non-WTA users. Refactor allows any authenticated user. WTA accounts are rare in DEV/UAT, but the gate is on the JEX side and currently absent on refactor. | Cross-cutting auth-hardening sprint will populate `TenantContext.userType` and a `@PreAuthorize` can land then. Track per OQ-5. |
| **List endpoint has no pagination** | JEX has none either, so this is parity-preserving. But if any tenant ever registers more than a few dozen bindings, the response payload grows. | Phase 1 §3 documents this; revisit at first complaint. |

---

## 5. Sign-off recommendation

**Merge** the read-only Phase 2/3 slice as-is, with **two follow-up tickets opened in the same sprint**:

1. **Surface API errors on the firm-banking-settings panel + details dialog** (Findings #1 + #5). Mirrors the firm-banking-history pattern. Small change (~20 LOC across two files); no test infrastructure required.
2. **Fix the unknown-bank-code mapper fallback** (Finding #2). Either widen `BankBrand` or change the fallback target. ~10 LOC + Javadoc fix.

Findings #3 / #4 / #8 / #10 / #11 are MINOR / NIT and can ride along in the same follow-up MR or wait for the next firm-banking-settings touchpoint.

The backend Phase 2 slice has **zero MAJOR or BLOCKER findings**. The frontend Phase 3 slice has 2 MAJOR findings that are local, low-risk, and easy to address.

---

## 6. Verification log

Files read end-to-end during this review:

- [wecms-api/src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/controller/FirmBankingSettingsController.java](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/controller/FirmBankingSettingsController.java)
- [wecms-api/src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/service/FirmBankingSettingsService.java](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/service/FirmBankingSettingsService.java)
- [wecms-api/src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/entity/FirmBankingSetting.java](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/entity/TbFirmConf.java)
- [wecms-api/src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/entity/FirmBankingSettingView.java](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/entity/FirmBankingSettingView.java)
- [wecms-api/src/main/java/com/kosign/wecms/common/auditlog/entity/AuditLogEntry.java](../../../src/main/java/com/kosign/wecms/common/auditlog/entity/AuditLogsInfm.java)
- [wecms-api/src/main/java/com/kosign/wecms/common/security/LogMask.java](../../../src/main/java/com/kosign/wecms/common/security/LogMask.java)
- [wecms-api/src/main/resources/db/migration/V100__tb_firm_conf.sql](../../../src/main/resources/db/migration/V100__tb_firm_conf.sql)
- [wecms-api/src/main/resources/db/migration/V8__audit_logs_infm.sql](../../../src/main/resources/db/migration/V8__audit_logs_infm.sql)
- `wecms-api/src/main/resources/db/migration/V64__firm_banking_settings_view.sql` — *historical; REPLACED BY CODE (`@Subselect`), file no longer exists*
- `wecms-api/src/main/resources/db/migration/V65__firm_banking_settings_demo_seed.sql` — *historical; DROPPED in the 2026-06-09 rebaseline, file no longer exists*
- [wecms-api/src/main/resources/messages.properties](../../../src/main/resources/messages.properties)
- [wecms-api/src/main/resources/messages_en.properties](../../../src/main/resources/messages_en.properties)
- [wecms-api/src/main/java/com/kosign/wecms/config/SecurityConfig.java](../../../src/main/java/com/kosign/wecms/config/SecurityConfig.java) (security baseline)
- [wecms-api/src/main/java/com/kosign/wecms/common/exception/GlobalExceptionHandler.java](../../../src/main/java/com/kosign/wecms/common/exception/GlobalExceptionHandler.java)
- [wecms-api/src/test/java/com/kosign/wecms/domain/setting/firmbankingsettings/service/FirmBankingSettingsServiceTest.java](../../../src/test/java/com/kosign/wecms/domain/setting/firmbankingsettings/service/FirmBankingSettingsServiceTest.java)
- [wecms-api/src/test/java/com/kosign/wecms/domain/setting/firmbankingsettings/controller/FirmBankingSettingsControllerTest.java](../../../src/test/java/com/kosign/wecms/domain/setting/firmbankingsettings/controller/FirmBankingSettingsControllerTest.java)
- [wecms-web/lib/types/api.ts](../../../../wecms-web/lib/types/api.ts) (firm-banking-settings additions)
- [wecms-web/lib/api/firmBankingSettings.ts](../../../../wecms-web/lib/api/firmBankingSettings.ts)
- [wecms-web/lib/api/mappers.ts](../../../../wecms-web/lib/api/mappers.ts) (new mappers + helpers)
- [wecms-web/lib/api/useApiData.ts](../../../../wecms-web/lib/api/useApiData.ts) (existing — used to verify the "global toast" claim)
- [wecms-web/components/settings/firm-banking-settings/types.ts](../../../../wecms-web/components/settings/firm-banking-settings/types.ts)
- `mockData.ts` (removed Sprint 1 — Needs Confirmation if still referenced)
- [wecms-web/components/settings/firm-banking-settings/FirmBankingSettingsPanel.tsx](../../../../wecms-web/components/settings/firm-banking-settings/FirmBankingSettingsPanel.tsx)
- [wecms-web/components/settings/firm-banking-settings/FirmBankingDetailsDialog.tsx](../../../../wecms-web/components/settings/firm-banking-settings/FirmBankingDetailsDialog.tsx)
- [wecms-web/components/settings/firm-banking-history/FirmBankingHistoryPanel.tsx](../../../../wecms-web/components/settings/firm-banking-history/FirmBankingHistoryPanel.tsx) (sister-feature comparison for finding #1)
- [wecms-web/locales/en.json](../../../../wecms-web/locales/en.json) / `ko.json` / `vi.json` (verified all 4 keys × 3 locales)

DEV DB read-only verifications (`postgres` MCP — read-only per [.claude/rules/database.md](../../../../.claude/rules/database.md)):

- `tb_firm_conf.wdrl_act_no` is plain numeric (15/15 rows, length 12, all digits) — confirms OQ-4
- `acct_mstr` PK is `(biz_key, bank_cd, acct_no)`; no `(biz_key, acct_no)` duplicates within tenant — confirms finding #7 is theoretical
- `audit_logs_infm` has 1059 rows for `(ref_no='TB_FIRM_CONF', wsvc_id='comm_0220_001')` for tenant `VN_VNM0004_000001` — JEX is happily writing/reading these; the `VARCHAR(50) REF_NO2` constraint hasn't blown up because all values are 12 digits

No write operations executed against DEV. No JEX trees modified. No code changes made (review-only mode).
