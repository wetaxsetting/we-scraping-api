# Excel Import — Migration Guide (Legacy JEX → Refactor)

**Part of:** Common → excel-import
**Last verified:** 2026-06-23

How the legacy JEX Excel-upload behaviour maps onto the refactor's import endpoints, and why the refactor spread it across features rather than into one shared service.

## Legacy JEX shape

In JEX, each Settings screen that supported Excel upload did so inside its own `_act.jsp` / WSVC handler — there was no shared "excel import" framework either. The upload was a per-screen action: parse the posted file, validate against that screen's table, insert via the screen's DAO, and write an audit row. So the legacy starting point is **already per-feature**.

> ⚠️ **UNVERIFIED:** the exact legacy WSVC ids for each upload action (e.g. the Purpose / VAS upload WSVCs) were not re-read for this doc. Cite the owning feature's `migration-guide.md` for the precise JEX source-of-truth file:line. Per workspace rule, legacy WSVC ids are preserved only as JEX file:line citations / migration-traceability prose — the refactor uses kebab-case slugs everywhere else.

## Refactor mapping

| Legacy concern | Refactor equivalent |
|---|---|
| Per-screen upload action (`_act.jsp`) | Per-feature controller endpoints (`{base}/excel/preview` + `{base}/excel/import`, or `bulk-upload`) |
| Screen DAO insert | Per-feature service persistence (`@Transactional`, re-validated) |
| `wb_get_next_val` key gen | Preserved for Purpose (`LegacyFunctionRepository`); VAS deviates to Java MAX+1 (flagged) |
| Audit insert | Shared `auditLogService.append(...)` with slug `WSVC_ID` |
| Client-side template validation | Frontend `templateValidator.ts` (header pre-check) + server header check (`WBE2042`) |

## Why the spread (and the C3 open decision)

The refactor did **not** introduce a single shared backend import service. The frontend `<ExcelUploadDialog>` shell was unified (it is genuinely reusable chrome), but the backend stayed per-feature — and even diverged into two architectures:

- **Canonical** (Purpose, VAS): server-side multipart preview + atomic import.
- **Variant** (Chart of Accounts, Contacts): client-side parse + per-row, non-atomic JSON import.

Whether to consolidate the backend into a shared import service is **open decision C3** (MISSING) — see [refactor-gap-analysis.md](refactor-gap-analysis.md). It is not a defect to fix silently; it needs a PO decision because consolidating would change four feature contracts at once and risk the migration-safety priority. For now the per-feature spread is the documented, intentional state.

## Migration-safety note

All four features write to **existing legacy tables** (`BUSI_USAG_SET_INFM`, `BUSI_VAS_ACCT_INFM`, CoA / Contacts tables). No import-specific table was created, so there is nothing extra to migrate — JEX → refactor data movement is unaffected by the import feature itself.

## Related

- [refactor-gap-analysis.md](refactor-gap-analysis.md) · [database-mapping.md](database-mapping.md) · [README.md](README.md)
- Owning-feature migration guides: [`../../settings/purpose/migration-guide.md`](../../settings/purpose/migration-guide.md) · [`../../settings/vas-account/migration-guide.md`](../../settings/vas-account/migration-guide.md) · [`../../settings/chart-of-accounts/migration-guide.md`](../../settings/chart-of-accounts/migration-guide.md) · [`../../settings/contacts/migration-guide.md`](../../settings/contacts/migration-guide.md)
