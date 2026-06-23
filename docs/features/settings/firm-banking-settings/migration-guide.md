# Firm Banking Settings — Migration Guide (JEX → Spring)

How each JEX layer became its Spring counterpart. Legacy behaviour reference: WSVC family
`comm_0220_001/002/003` over `TB_FIRM_CONF`. Deep narrative + OQ log:
[../firm-banking-settings.md](../firm-banking-settings.md).

---

## Layer-by-layer

| JEX layer | JEX artifact | Spring counterpart |
|---|---|---|
| Screen | `comm_0220_001_view.jsp` (+ `002`/`003`) | `<FirmBankingSettingsPanel>` (Settings modal panel — no route) |
| List read | `comm_0220_001_r001` (IDO `TB_FIRM_CONF_R004` + `ACCT_MSTR` join) | `GET /firm-banking-settings` → `service.list` → `FirmBankingSettingViewRepository` (`@Subselect`) |
| Detail read | `comm_0220_003_r001` (entity leg, IDO `TB_FIRM_CONF_R001`) | `GET /{out_bank_cd}/{wdrl_act_no}` → `service.detail` → `FirmBankingSettingRepository.findById` |
| Audit read | `comm_0220_003_r001` (audit `REC` over `AUDIT_LOGS_INFM`) | `GET /{out_bank_cd}/{wdrl_act_no}/audit` → `service.auditLog` → `AuditLogEntryRepository` |
| Create | `comm_0220_002_c001` (IDO `TB_FIRM_CONF_C001` + `TB_FIRM_BANK_CODE_R001` + `AUDIT_LOGS_INFM_C001`) | `POST /firm-banking-settings` → `service.create` (bank guard + audit write) |
| Update | `comm_0220_003_u001` (IDO `TB_FIRM_CONF_U001` + `AUDIT_LOGS_INFM_C001`) | `PUT /{out_bank_cd}/{wdrl_act_no}` → `service.update` |
| Delete | `comm_0220_001_d001` (IDO `TB_FIRM_CONF_D001`) | `DELETE /{out_bank_cd}/{wdrl_act_no}` → `service.delete` (hard delete, no audit) |
| Entity | `TbFirmConf` + `TbFirmConfDAO` | `TbFirmConf` + `TbFirmConfId` (`@IdClass`) + Spring Data `FirmBankingSettingRepository` |
| List view | (legacy IDO join, no view object) | `@Subselect` entity `FirmBankingSettingView` (replaces the planned `V_FIRM_BANKING_SETTING`) |
| Bank-name resolve | join `TB_FIRM_BANK_CODE` on `(ANT_DIV_CD='NAPAS', BANK_CD)` | same join inside the `@Subselect`; `TbFirmBankCodeRepository` for the Create guard |
| `FILE_TYPE` derive | inline JSP branch on `OUT_BANK_CD` | `BANK_TO_FILE_TYPE` map in `FirmBankingSettingsService` (SHB/VCB/WRB) |
| Key gen | composite PK supplied by the form | composite PK supplied by the Create request (no `wb_get_next_val`) |

---

## Identifier translation (slug, not WSVC id)

Every refactor identifier surface carries the kebab slug **`firm-banking-settings`** — REST URI, Java
package (`…domain.setting.firmbankingsettings`), class names (`FirmBankingSettingsController`,
`FirmBankingSettingsService`, `TbFirmConf`), DTO records, i18n keys (`audit.firm_banking_settings.*`),
log tags, **and the audit `WSVC_ID`**. The menu permission code is **`settings-firm-banking-settings`**.
Legacy WSVC ids (`comm_0220_001`, …) appear only in JEX file:line citations and migration prose.

---

## Behaviour changes from legacy (intentional)

| Area | Legacy | Refactor |
|---|---|---|
| Detail + audit | one action returns row + audit `REC` together | split — detail endpoint (row) + separate `/audit` sub-resource (audit list) |
| Audit `WSVC_ID` | `comm_0220_001` | slug `firm-banking-settings` (OQ-6 override 2026-05-09; the refactor writes the slug from the start, so no back-fill migration ships — the planned "V67 back-fill" was never needed) |
| Audit locales | 3 rows per event (EN/KO/local) | per-locale rows via the shared `AuditLogService` |
| List view object | IDO join, no DB view | Hibernate `@Subselect` entity (no `V_FIRM_BANKING_SETTING` created) |
| `wdrl_act_no` on the wire | full account number | **cleartext** on list + detail (PK the FE addresses for writes); display masking is frontend-only (OQ-4) |
| Bank scope | inline branch on Shinhan/Woori | bounded allow-list `{970424, 970436, 970457}` (V8, OQ-8a Option c) |
| Authorization | menu visibility only (no explicit check in the action JSP) | explicit `@menuAuthz.canAccess('settings-firm-banking-settings')` (F-3) |

---

## Dropped / dead legacy flows (not ported)

| Legacy flow | Disposition |
|---|---|
| Approval-line gating | **DEAD LEGACY** — JEX has no approval-line integration for `comm_0220`; refactor preserves immediate writes (OQ-7). A future approval requirement is a new feature, not a parity item. |
| `V9` `NAPAS_BANK_INFO` existence check | **PARTIAL / deferred** — `NAPAS_BANK_INFO` not migrated locally; `V8` (3-bank allow-list) is strictly stronger for the supported set. Re-enable when the table lands. |
| `V11` `ACCT_MSTR` existence check on `wdrl_act_no` | **DEAD LEGACY** — disabled in legacy too (ticket #42004); kept disabled (OQ-3). |
| `wdrl_act_no` encryption | **PARTIAL / deferred** — stays plain-text in DB (OQ-4); masking is presentation-only; project-wide encryption is a separate migration. |

See [refactor-gap-analysis.md](refactor-gap-analysis.md) for the full MATCH / APPROVED DEVIATION /
DEAD LEGACY / PARTIAL / MISSING matrix + the OQ decision log.

---

## Schema (no Flyway in this feature's runtime path)

`TB_FIRM_CONF`, `TB_FIRM_BANK_CODE`, `ACCT_MSTR`, `AUDIT_LOGS_INFM` are all pre-existing in the
[legacy-table-inventory](../../../references/database/legacy-table-inventory.md) and were brought into the
refactor schema by the settings-groundwork migrations — in the **live (post-2026-06-09 renumber) set**,
`TB_FIRM_BANK_CODE` is `V97__tb_firm_bank_code.sql` and `TB_FIRM_CONF` is `V100__tb_firm_conf.sql`
(`AUDIT_LOGS_INFM` is `V8`). Older docs / Javadoc citing V59 / V62 / V64 predate that renumber — verify the
actual filename under `src/main/resources/db/migration/` before quoting a version.
`V_FIRM_BANKING_SETTING` is intentionally **not** materialised as a DB object — the
`@Subselect` entity supplies the join at query time. No `wb_get_next_val` branch is added for
`TB_FIRM_CONF` (client-supplied composite PK).
