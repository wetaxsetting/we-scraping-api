# Chart of Accounts — Source Code Index

All paths relative to `wecms-api/` (backend) and `wecms-web/` (frontend). Package: `com.kosign.wecms.domain.setting.chartofaccounts`.

---

## 1. Backend (wecms-api)

### Controller
| File | Role |
|---|---|
| `src/main/java/com/kosign/wecms/domain/setting/chartofaccounts/controller/ChartOfAccountsController.java` | 9 endpoints under `/api/v1/settings/chart-of-accounts`; class-level `@PreAuthorize @menuAuthz.canAccess('settings-core-chart-of-accounts')` (`:79`), `@Validated`; path-id `@Pattern(\d{1,14})` |

### Service & domain logic
| File | Role |
|---|---|
| `.../service/ChartOfAccountsService.java` | `list` (`:132-153`), `tree` (`:160-166`), `detail` (`:175-194`), `create`/revive (`:216-319`), `update` (`:326-392`), `delete`+re-parent (`:415-473`), `deactivate` (`:490-523`), `bulkUpload` (`:584-599`), `bulkDelete` (`:533-548`), `bulkDeactivate` (`:556-571`); audit append; `utcNow14()` stamps; seq alloc + 1-shot retry |
| `.../service/ChartOfAccountsValidator.java` | `assertNotRestricted` (`:59`), `assertNotRenamingToRestricted` (`:71`), `assertNotSelfParent` (`:85`), `assertParentExists` (`:99`), `assertParentRequired` (`:125`), `assertCalRuleLevel3Only` (`:149`), `assertNoCycle` (`:165`), `assertDepthCap` (`:202`), `indexActiveByCode` (`:219`) |
| `.../service/ChartOfAccountsTreeBuilder.java` | `buildRows` (DFS pre-order, `:69`), `buildTree` (parent-picker nodes, `:144`), `resolveLevel` (cap-bounded walk, `:253`), breadcrumb composition (`:194-214`) |
| `.../ChartOfAccountsConstants.java` | `WSVC_SLUG`, `AUDIT_REF_NO`, **`MAX_HIERARCHY_DEPTH = 3`** (`:41`), `RESTRICTED_CODES` (`:49`), `ARCHIVE_CODE_SUFFIX`, `DEL_YN_*`, `STTS_*`, label-key prefix |
| `.../enums/CalRule.java` | `PLUS("P")` / `MINUS("M")` + `fromCode` |

### Repository & entity
| File | Role |
|---|---|
| `.../repository/ChartOfAccountRepository.java` | `findByBizKeyAndDelYn` (`:33`), `searchByBizKey` (JPQL LOWER LIKE code/EN, `:41-51`), `findByBizKeyAndChartAcctCdAndDelYn` (`:57`), `…AndChartAcctSeqNoNot` (`:65`), `findByBizKeyAndParentChartAcctCdAndDelYn` (`:73`), `reparentActiveChildren` (`@Modifying`, `:80-94`) |
| `.../entity/ChartOfAccount.java` | `@Entity @Table("CHART_ACCT_INFM") @IdClass`; 15 columns; CHAR(1) flags via `@JdbcTypeCode(SqlTypes.CHAR)` |
| `.../entity/ChartOfAccountId.java` | `Serializable` composite key (`bizKey`, `chartAcctSeqNo` — both `String`) |

### DTOs (`.../dto/`)
| File | Role |
|---|---|
| `ChartOfAccountRow.java` | list/detail/create/update response (16 fields incl. computed `chartAcctLevel`/`hasChildren`/`restricted` + audit) |
| `ChartOfAccountListResponse.java` | `items` + `totalCount` + `restrictedCodes` |
| `ChartOfAccountTreeResponse.java` / `ChartOfAccountTreeNode.java` | parent-picker tree + `breadcrumb` |
| `ChartOfAccountCreateRequest.java` / `ChartOfAccountUpdateRequest.java` | identical record shape; bean validation on components |
| `ChartOfAccountBulkUploadRequest.java` / `…Response.java` | `items` (≤500) / `{created, failed}` |
| `ChartOfAccountBulkActionRequest.java` / `…Response.java` | `chartAcctSeqNos` (≤500) / `{processed, failed}` |
| `ChartOfAccountsSortField.java` | sort whitelist enum (`CHART_ACCT_CD`/`CHART_ACCT_NM_EN`/`STTS`/`MOD_DTM`) |

### Migrations & shared
| File | Role |
|---|---|
| `src/main/resources/db/migration/V94__chart_of_accounts_schema.sql` | table + PK + UK + indexes |
| `V95__wb_get_next_val_chart_acct_infm.sql` | seq branch |
| `V96__chart_of_accounts_seed_restricted_roots.sql` | ACC-01/02/03 per tenant |
| `V97__chart_of_accounts_seed_demo.sql` | 9 demo rows (tenant 000001) |
| `.../common/legacy/LegacyFunctionRepository.java` | `nextChartAcctSeqNo` funnel (`:137-139`) |
| `messages.properties` | `WBE_CHART_OF_ACCOUNTS_*` codes |

### Downstream readers (out of this feature's scope, validate against COA)
| File | Behaviour |
|---|---|
| `domain/banking/.../TransactionService.java:355-362` | validates code+seq+`STTS='A'` before writing reporting account ✅ |
| `domain/payment/cardtransactions/.../CardTransactionsService.java:1678-1690` | same validation ✅ |
| `domain/payment/internaltransfer/.../InternalTransferService.java:503-504,572-577` | **skips** existence/active guard (CA-G10 — Payment-module defect, not COA) |
| `domain/banking/.../cashflow/ChartOfAccountsHierarchy.java` | Java reimplementation of `wb_get_chart_acct_nm`/`_lv_nm` for Cash Flow report |

---

## 2. Frontend (wecms-web)

| File | Role |
|---|---|
| `components/settings/chart-of-accounts/ChartOfAccountsPanel.tsx` | Main panel — list fetch, search, inline add/edit, bulk delete/deactivate, dialogs |
| `components/settings/chart-of-accounts/ChartOfAccountsTable.tsx` | Read-only two-level tree table; checkbox selection on child rows |
| `components/settings/chart-of-accounts/ChartOfAccountsInlineRow.tsx` | Inline create/edit form row |
| `components/settings/chart-of-accounts/ChartOfAccountsTableColumns.tsx` | Shared `<colgroup>` |
| `components/settings/chart-of-accounts/ExcelUploadDialog.tsx` | Excel upload wrapper (template download + preview + per-row bulk-upload) |
| `components/settings/chart-of-accounts/InlineDropdown.tsx` | Parent / Calc-rule / Status dropdown |
| `components/settings/chart-of-accounts/useChartOfAccounts.ts` | List hook + mutation hook (create/update/remove/bulkRemove/bulkDeactivate) |
| `components/settings/chart-of-accounts/useExcelUpload.ts` | SheetJS parse + per-row validation (depth ≤ 3, dup, required, parent) |
| `components/settings/chart-of-accounts/mappers.ts` | wire ↔ in-memory mapping |
| `components/settings/chart-of-accounts/types.ts` | `ChartOfAccountsRow` (group/child union), `ChartOfAccountsFormValues`, `InlineRowMode` |
| `lib/api/chartOfAccounts.ts` | API client (list/tree/detail/create/update/delete/bulk-upload/bulk-delete/bulk-deactivate) |
| `lib/types/api.ts:2887-2992` | wire types `ApiChartOfAccount*` |
| `components/settings/SettingsModal.tsx:197` / `SettingsNav.tsx:63` | panel mount + nav entry (`settings-core-chart-of-accounts`) |
| `locales/{en,ko,vi}.json` | `settings.chart_of_accounts.*` keys |

---

## 3. Legacy JEX source (read-only reference)

| Legacy artifact | Path / id | Refactor counterpart |
|---|---|---|
| Main view | `comm_0223_001_view.jsp` | `ChartOfAccountsPanel.tsx` |
| List action | `comm_0223_001_r001_act.jsp` | `GET /` → `ChartOfAccountsService#list` |
| Tree action | `comm_0223_001_r002_act.jsp` | `GET /tree` → `#tree` + `ChartOfAccountsTreeBuilder` |
| Create | `comm_0223_001_c001_act.jsp` | `POST /` → `#create` |
| Update | `comm_0223_001_u001_act.jsp` | `PUT /{seq}` → `#update` (bulk parent-cleanup `:238-256` **dropped**, OQ-3) |
| Parent-null patch | `comm_0223_001_u002_act.jsp` | folded into `#delete` (re-parent) |
| Soft-delete | `comm_0223_001_d001_act.jsp` | `DELETE /{seq}` → `#delete` |
| Excel view + insert | `comm_0223_002_view.jsp` / `comm_0223_002_c001_act.jsp` | `ExcelUploadDialog.tsx` + `POST /bulk-upload` → `#bulkUpload` |
| Entity / DAO | `wabooks/entity/ChartAcctInfm.java`, `wabooks/dao/ChartAcctInfmDAO.java` | `ChartOfAccount` / `ChartOfAccountRepository` |
| TS hierarchy | `WECMS_STATIC/.../comm_0223_001.ts`, `comm_0223_002.ts` | server-side `TreeBuilder` + FE `useExcelUpload.ts` |

Full IDO mapping (R001–R004, C001, U000–U003, D001) is in [migration-guide.md](migration-guide.md) §1 and the preserved [migration reference](../../../references/migration/chart-of-accounts.md).
