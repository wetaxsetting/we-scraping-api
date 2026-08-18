# VAT Declaration — Source Attachments

Binary source materials that accompany the VAT Declaration
[source requirements](../). They are **inputs handed over by the business / GDT**, not
implementation specs — the implementation spec is the feature folder
[`../../`](../../README.md).

**Status:** historical · **Last reviewed:** 2026-08-17

> **Relocated 2026-08-17 (P2).** These seven files previously lived in a top-level
> `wecms-api/docs/vat-declaration/` folder alongside the Markdown requirements. The Markdown moved
> into [`../`](../) on 2026-08-17 (P1); the binaries followed here in P2 so the whole source-requirement
> package sits in one place and `docs/` no longer carries an orphan top-level folder.

## Inventory

| File | Kind | Referenced by |
|---|---|---|
| `01_GTGT_TT80_2024-Q2-2024.xls` | GDT official declaration form, Q2/2024 (HTKK export) | [`../overview.md`](../overview.md) §attachments · [`../../README.md`](../../README.md) (main-form Excel parity target) |
| `01_GTGT_TT80_2024-Q2-2024 BS L1.xls` | GDT supplementary declaration #1 — multi-sheet (adjusted, `01-KHBS`, `01-1_KHBS`) | [`../overview.md`](../overview.md) §attachments |
| `2026-WeCMS_VN_API_v1.0.xlsx` | Vendor/partner API specification workbook | [`../tech-specs.md`](../tech-specs.md) |
| `WeCMS VAT Declaration_Requirement Design.xlsx` | Requirement design — indicator mapping + screen layout | [`../overview.md`](../overview.md) §attachments · [`../prd.md`](../prd.md) |
| `WeCMS_VAT_Report_download_VN.xlsx` | Declaration-report Excel download template (Vietnamese) | [`../overview.md`](../overview.md) §attachments |
| `WeCMS_VAT_Report_download_EN.xlsx` | Declaration-report Excel download template (English) | [`../overview.md`](../overview.md) §attachments |
| `VAT_Declaration_Report.html` | Rendered report sample (visual reference for the export layout) | [`../design-brief.md`](../design-brief.md) |

## Rules

- **Read-only source material.** Do not edit these files — they are the handover artifacts of record.
  A correction belongs in the Markdown requirement doc or the feature spec, with a note pointing here.
- **Do not delete.** They are the only copy of the GDT form shapes the export must match.
- New VAT source binaries land here, next to the requirement doc that cites them.

## Related

| Doc | Why it lives elsewhere |
|---|---|
| [`../../README.md`](../../README.md) | The current implementation spec (this folder is only the source input) |
| [`../../../../../references/database/addon/vat-declaration-db.md`](../../../../../references/database/addon/vat-declaration-db.md) | Schema reference belongs with database references |
| [`../../../../../deployment/vat-declaration-sit-2026-08-13.md`](../../../../../deployment/vat-declaration-sit-2026-08-13.md) | Dated SIT deployment record |
| [`../../../../../planning/open-questions/vat-declaration-follow-up.md`](../../../../../planning/open-questions/vat-declaration-follow-up.md) | Open questions belong in planning |
| [`../../../../../sql/2026/create-vat-declaration-tables-2026-08-04.sql`](../../../../../sql/2026/create-vat-declaration-tables-2026-08-04.sql) | Manual DBA script — see [`sql/README.md`](../../../../../sql/README.md) |
