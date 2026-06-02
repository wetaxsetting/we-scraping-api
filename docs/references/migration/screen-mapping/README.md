# migration/screen-mapping/

JEX JSP screen → refactor Next.js route mapping.

## Purpose

For each JEX screen (`<menu_id>_<seq>.act` / `_act.jsp`), document the refactor route, layout host, and any UX deltas (column reordering, filter shape, default sort).

## What goes here

- Per-screen markdown (`<refactor-slug>-screen.md`)
- Column-level mapping (JEX label → refactor token-driven label, i18n key, View Setting `screenId`)
- UX deltas with the PO decision date

## Naming convention

- Filename uses the **refactor slug** (kebab-case). JEX `<menu_id>_<seq>` appears only in the body as the source reference.

## Related

- Feature spec: [`../../features/`](../../../features/)
- Frontend route: [`wecms-web/docs/architecture/routes/`](../../../../../wecms-web/docs/architecture/routes/)
- API mapping: the per-feature `*-mapping.md` docs in [`../`](../)
