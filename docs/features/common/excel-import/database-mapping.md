# Excel Import — Database Mapping (Backend)

**Part of:** Common → excel-import
**Last verified:** 2026-06-23

## No own table

Excel Import has **no table of its own**. It is a behaviour, not an entity. Each owning feature writes into **its own** existing legacy table; the only cross-cutting write is one row appended to `AUDIT_LOGS_INFM` per successful import via the shared audit writer.

This is consistent with the workspace rule that refactor-only constructs get no new table — import is layered over existing legacy tables (see CLAUDE.md §1 and memory `project_notification_module_removed.md` for the established "no refactor-only table" pattern).

## Per-feature target tables (confirmed)

| Owning feature | Target table | Entity `@Table` | Key generation | Audit slug (`WSVC_ID`) |
|---|---|---|---|---|
| Purpose Settings | `BUSI_USAG_SET_INFM` | `BusiUsagSetInfm` (`BusiUsagSetInfm.java:50`) | `wb_get_next_val` via `LegacyFunctionRepository.nextUseUsagCd(bizKey, "")` (`PurposeExcelService.java:616`) | `purpose-settings` |
| VAS Account | `BUSI_VAS_ACCT_INFM` | `BusiVasAcctInfm` (`BusiVasAcctInfm.java:42`) | **Java MAX+1** via `VasAcctSeqNoGenerator.next(bizKey)` (`VasAccountExcelService.java:346`) — deliberate refactor deviation (entity Javadoc `BusiVasAcctInfm.java:51-57`) | `vas-account` |
| Chart of Accounts | `CHART_ACCT_INFM` (per owning feature) | (see Chart of Accounts docs) | (per owning feature) | (per owning feature) |
| Contacts | `TRCO_*` trading-company tables (per owning feature) | (see Contacts docs) | (per owning feature) | (per owning feature) |

> ⚠️ CoA / Contacts target table + key-generation details are owned by those features — confirm in their `database-mapping.md` rather than relying on this row. Marked partial here on purpose.

## Key-generation policy

- **Purpose Settings** follows the workspace single-executor rule — all key gen funnels through `wb_get_next_val` (`LegacyFunctionRepository`), per memory `project_wb_get_next_val_funnel.md`.
- **VAS Account** uses a Java `MAX+1` generator instead of `wb_get_next_val`. This is documented in the entity Javadoc as a deliberate refactor preference over the legacy branch. ⚠️ This is a **deviation** from the funnel rule and should be tracked as such — see [refactor-gap-analysis.md](refactor-gap-analysis.md). It is not silent (the Javadoc states it) but it is not one of the funnel memo's listed exceptions either.

## Audit write

Every successful import (canonical features) appends exactly one row to `AUDIT_LOGS_INFM` through `auditLogService.append(...)`:

- Purpose: `wsvcId="purpose-settings"`, `refNo="BUSI_USAG_SET_INFM"`, `refNo1=insertedCount` (`PurposeExcelService.java:171-174,492-499`).
- VAS: `wsvcId="vas-account"`, `refNo="BUSI_VAS_ACCT_INFM"`, `refNo1=insertedCount`, `refNo2="APPEND"/"REPLACE"` (`VasAccountExcelService.java:156-160,363-370`).

The `WSVC_ID` carries the **kebab-case slug**, per the workspace identifier-naming rule (CLAUDE.md Cross-Tree Rules). Shared writer details: [`../audit-log/database-mapping.md`](../audit-log/database-mapping.md).

## Related

- [interface-specification.md](interface-specification.md) · [business-flow.md](business-flow.md) · [README.md](README.md)
- Owning-feature DB mappings: [`../../settings/purpose/database-mapping.md`](../../settings/purpose/database-mapping.md) · [`../../settings/vas-account/database-mapping.md`](../../settings/vas-account/database-mapping.md) · [`../../settings/chart-of-accounts/database-mapping.md`](../../settings/chart-of-accounts/database-mapping.md) · [`../../settings/contacts/database-mapping.md`](../../settings/contacts/database-mapping.md)
