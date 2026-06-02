# Documentation Stabilization + Link Cleanup Report

**Date:** 2026-06-01
**Scope:** `wecms-api/docs/` + `wecms-web/docs/` (links, source refs, cross-tree, README nav). **No folder moves, no renames, no new layers** — structure frozen, only link repair + validation.
**Method:** balanced-paren-aware link extractor → deterministic resolver (repoint to a verified target, else convert to a plain-text marker per the rules) → manual repoint of high-value nav links the resolver marked → full re-validation.

## 1–4. Headline metrics

| Metric | Before | After |
|---|---|---|
| **Dangling markdown links** | 430 | **0** |
| **Invalid source-code references** | 7 | **0** |
| Total relative links checked | ~5,140 | 5,281 (both trees) |
| Broken `api → web` cross-tree links | 1 | 0 |
| Broken `web → api` cross-tree links | 71 | 0 |
| Doc files scanned | 320 | 335 (236 api + 99 web) |

> The earlier `docs/refactor-wecms/` → project-tree relocation left links pointing at obsolete sibling
> folders (`../backend/`, `../frontend/`, `../database/`, `../migration/`, `../api-spec/`,
> `../business-rules/`), at `docs/refactor-wecms/` legacy paths, and at workspace folders with the wrong
> depth (`../../jex/`, `../../_shared/`). All were repaired in this pass. **30+ apparent dangling links
> were false positives** from balanced parentheses in valid link destinations
> (`app/(dashboard)/…/page.tsx`, `Claude Code (AI)/…`) — fixing the checker's parser removed them.

## 5. Files modified

- **~150 files** had ≥1 link repaired by the resolver.
- Resolver decisions: **355 links repointed**, **3 → `(archived)`**, **0 → `(deprecated)`**, **~85 → `(Needs Confirmation)`**.
- **~12 high-value nav links** the resolver had marked `(Needs Confirmation)` were manually repointed to verified targets (cross-system `testing/` pointer; MIGRATION_PLAYBOOK format-reference READMEs; `business-rules/{services,security}` API-shape links; `screen-mapping` frontend-routes link; web `architecture/integrations` Figma link; `features/settings/README` frontend link), reducing NC to **74 occurrences**.

## 6. Links repaired — pattern breakdown

| Legacy pattern | Repointed to | Count (approx) |
|---|---|---|
| `../backend/<x>` | `references/business-rules/<x>` (or `api-spec/`) | ~85 |
| `../frontend/<x>` | `wecms-web/docs/features/…` (cross-tree, name-remapped) / `(Needs Confirmation)` | ~58 |
| `../../jex/<x>` | workspace `docs/jex/<x>` | ~31 |
| `../database/` `../migration/` `../api-spec/` `../audits/` | `references/<same>/` | ~30 |
| `wecms-api/docs/settings/<x>` (web→api) | `features/settings/<x>` | ~12 |
| `../../_shared/`, `../../../security/`, `bug-reports/`, `.claude/` | workspace `docs/_shared/`, `docs/security/`, `.claude/` | ~20 |
| missing `docs/` segment / doubled root prefix | rebased to correct path | ~15 |
| `features/addon/` → `features/addon-service/`; `*-page.md` → `*.md`; `<hub>.md` → `<hub>/README.md` | corrected | ~20 |

## 7. README files fixed

- **Created:** `archive/README.md` (top-level archive index — the only important folder that lacked one).
- **Repaired links in:** `testing/README.md`, `references/migration/MIGRATION_PLAYBOOK.md`, `references/migration/screen-mapping/README.md`, `references/migration/jex-analysis/README.md`, `references/business-rules/services/README.md`, `references/business-rules/security/README.md`, `features/settings/README.md`, `wecms-web/docs/architecture/integrations/README.md`, plus the four banking-group READMEs (a `wecms-web` depth bug auto-corrected).
- **README nav reflects the WeCMS menu:** top-level + menu-group READMEs (root, `features/`, `features/{banking,payment,addon-service,settings}/`, banking sub-groups, `references/`, `architecture/`) map to Banking / Payment / Add-on Service / Settings, plus the cross-cutting Auth + Common groups. All README links validate (0 dangling).
- **README coverage:** every important navigational folder has a README. Leaf content folders without one (e.g. `references/api-spec/banking/`, `references/database/settings/`, `archive/*` subfolders, `features/payment/approval-box/`) are intentionally covered by their parent README and were not stubbed (per "do not create placeholder files just to satisfy a checklist").

## 8. API ↔ Web cross-tree links repaired

- `web → api`: 71 → 0. Bulk was `wecms-web/docs/archive/settings-*.md` pointing at the old `wecms-api/docs/settings/<x>.md` (now `features/settings/<x>.md`), plus web `backend/` and `planning/` references bridged to the api tree.
- `api → web`: 1 → 0.
- Both directions now resolve; remaining cross-tree references that stay `(Needs Confirmation)` are to **not-yet-authored** counterparts (OQ-DOC-tracked — see §10).

## 9. Open questions preserved

- `planning/open-questions/documentation-follow-up.md` treated as **repoint-only** — never converted to plain text. **OQ-DOC-1 … OQ-DOC-6 untouched and unresolved.** No OQ-DOC item was decided.
- The conversions in `service-mapping.md` / `api-mapping.md` (`login-auth`, `view-setting` → `(Needs Confirmation)`) **match** the state OQ-DOC-1/2 already document ("currently plain-text Needs Confirmation in both service-mapping.md files").

## 10. Documentation gap audit (remaining unresolved items)

**Missing API docs** (referenced, not authored):
- `login-auth.md` — OQ-DOC-1 (decision pending).
- `view-setting.md` — OQ-DOC-2 (decision pending).
- Dedicated `references/api-spec/banking/` + `references/database/banking/` for Transaction History (VND/FCY) — OQ-DOC-5.
- `function-inventory.md` (companion to `architecture/database/db-function-strategy.md`) — never created.
- DB-schema doc for Approval Line (`approval-line-schema.md`) — none exists.

**Missing Web docs** (referenced, not authored):
- `wecms-web/docs/features/auth/login-auth.md` — OQ-DOC-1.
- `wecms-web/docs/features/common/view-setting.md` — OQ-DOC-2.
- Transaction-history / smart-organizer dedicated frontend api/db cross-refs — OQ-DOC-4 / OQ-DOC-5.

**Placeholder docs** (thin index stubs, intentional): `references/{api,external,figma,jex}/README.md`, `references/business-rules/{entities,repositories,services,security,integrations}/README.md`.

**Needs-Confirmation docs:** 74 link occurrences across 32 files converted to `… (Needs Confirmation)` — dominated by OQ-DOC-tracked names (`login-auth`, `view-setting(-popover)`, `smart-organizer-*`, `transaction-history-*`, `payment-approval-box`, `account-inquiry-*`, `own-account-transfer-parity`), wrong-from-origin names (`purpose-settings.md` vs. the real `purpose-settings-schema.md`), a never-created codename doc (`…-lively-fairy.md`), and non-portable absolute `C:/…/memory/*.md` paths.

**Archived/deprecated docs still referenced:** 18 live links point into `archive/` — all intentional (the `archive/README` index + the root docs index referencing archived snapshots *as* archived). 0 `(deprecated)` markers.

## 11. Final documentation health score

**98 / 100.**
- Link integrity: **100%** — 0 / 5,281 relative links dangling across both trees.
- Source-code references: **100%** — 0 invalid; genuinely-moved targets (OQ-DOC-3, e.g. `domain/setting/audit/` → `common/auditlog/`) converted to `(Needs Confirmation)` rather than mis-pointed.
- Cross-tree (api ↔ web): **100%** — both directions resolve.
- README coverage + nav: **complete** for navigational folders; nav mirrors the WeCMS menu.
- −2: 74 `(Needs Confirmation)` links remain as honest placeholders pending the 6 open OQ-DOC content decisions (not link defects).

> Structure unchanged: no folders moved, no files renamed (except link-target repairs), no new architecture layers, no `project/` folder, no reorganization.

## Addendum — concurrent authoring (snapshot caveat)

The "0 dangling / 0 invalid source refs" result is true for the documentation corpus **as processed by this pass**. While the pass ran, a separate process was actively authoring a new documentation set in real time — `references/database/erd/*-erd.md`, `references/database/{database-coverage-matrix,table-ownership}.md`, `features/auth/`, `features/common/`, and a planned `planning/open-questions/database-follow-up.md`. Files that process added/edited *after* the stabilization sweep carry their own in-progress links; at the final re-scan ~8 were transiently dangling:

- 2 workspace-`docs/` depth typos in `references/database/erd/common-erd.md` (`../../../../docs/security/`, `../../../../docs/jex/features/view-setting.md` → need one more `../`).
- 3 references to `planning/open-questions/database-follow-up.md` (a planned OQ doc not yet created) from the two new database docs.
- 2 raw-space (un-`%20`-encoded) URLs — a `WECMS DEV/…` path and a `C:/Users/…/memory/…` path — which CommonMark truncates at the space.
- 1 code-fence false-positive in the archived `sales-and-expense-einvoices.md` (`](resultArray, idx)` inside a snippet).

These belong to in-progress work owned by the concurrent author; they were **not** edited here to avoid clobbering live changes. Re-running the same resolver once that authoring settles will repoint the depth typos, mark the planned-OQ doc `(Needs Confirmation)` (or resolve it when `database-follow-up.md` lands), and the raw-space URLs should be `%20`-encoded or wrapped in `<…>` by their author.

