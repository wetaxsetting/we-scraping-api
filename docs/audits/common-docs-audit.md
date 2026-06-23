# Common (Cross-Cutting) — Documentation Re-Audit (Docs vs. Code)

**Audit date:** 2026-06-23
**Auditor:** Claude (code-as-source-of-truth audit)
**Trigger:** standardize the Common documentation to the same methodology applied to Settings, Payment, Add-on Service, and Auth; the Common docs were last touched 2026-06-01 and the implementation has since moved (notably Notification was removed and re-implemented).
**Scope:** `wecms-api/docs/features/common/**`, `wecms-web/docs/features/common/**`, `wecms-api/src/main/java/com/kosign/wecms/{common,domain/setting,domain/navigation,domain/common,domain/payment/notification}/**`, `wecms-web/{components,lib,hooks}/**`.
**Companions:** [common-source-index.md](common-source-index.md) (validated source references) · [common-gap-analysis.md](common-gap-analysis.md) (gaps + open decisions).

---

## Executive summary

The Common domain is the **least-documented** of the five domains. Where Settings / Payment / Add-on each had complete per-module folders that only needed line-level corrections, **Common had only two feature docs** — `view-setting.md` and `audit-log.md` (both flat files, both accurate and current) — plus two category READMEs. Every other live Common capability (excel-export, excel-import, file-management, **notification**, navigation, common-code-lookup, table-sort, dialog-popup, error-envelope) is **documented only in CLAUDE.md and the per-consumer feature specs**, never as a Common module. So the dominant finding is **missing documentation**, not contradicted documentation.

The one genuine **contradiction** is high-value and was corrected this pass: both Common READMEs describe **Notification** as the old refactor-only `domain/notification/` + `NOTIFICATION` table (V238) shape that was **removed 2026-06-10**, and link a spec in the **deprecated** `docs/refactor-wecms/` tree. The code today is `domain/payment/notification/` on legacy **`BLTB_*`** tables with a different endpoint set (`comm_0192_007` family) — exactly the "use BLTB tables, not a refactor-only table" pattern the removal decision predicted. Those README rows + links were corrected.

This pass is **documentation only** — no backend or frontend code was changed. It delivers the three audit artifacts and the two README corrections; the per-module folder build is **Phase 2** (scoped to the 11-module *core cross-cutting set*; domain-owned capabilities such as `approval`, `reporting-account`, and contacts `attachment` are cross-linked, not re-foldered).

---

## Per-area findings

### view-setting

**Documentation Status:** **Accurate & current** (flat file) → to be **promoted to a folder** in Phase 2.

**Files Updated:** none this pass.
**Corrections Made:** none — `wecms-api/docs/features/common/view-setting.md` and the WEB twin correctly describe merged sys+user, lazy-init, delete-then-insert save, reset-then-reinit, special `CHECKBOX`/`ETC` handling, semantic kebab `WEB_SVC_ID`, and the seed-only adoption model.
**Remaining Gaps** *(gap vocab)*: **PARTIAL** doc form only — single flat file, not the 9-file/6-file folder. `SIZE` end-to-end consumption still deferred (matches code).

| Axis | Result |
|---|---|
| Doc ↔ API | ✅ 4 endpoints + `WBE2001` match `ScreenOptionController`/`Service` |
| Doc ↔ Database | ✅ `SYS_SCR_OPTION_WECMS` / `USER_SCR_OPTION_WECMS` (FIX_YN removed 2026-06-11 reflected) |
| Doc ↔ Frontend | ✅ shared popover + hook + registry described correctly |

### audit-log

**Documentation Status:** **Accurate & current** (flat file) → promote to folder in Phase 2.

**Files Updated:** none this pass.
**Corrections Made:** none — locale fan-out, `REQUIRES_NEW`, never-throws, slug `WSVC_ID`, seq via `wb_get_next_val` all correct.
**Remaining Gaps:** **MATCH**; doc form is flat-file only (promote to folder). The consumer list in the doc should be refreshed against the current write-paths during Phase 2 (no contradiction, just additions).

| Axis | Result |
|---|---|
| Doc ↔ API | ✅ write-only, no endpoint — matches |
| Doc ↔ Database | ✅ `AUDIT_LOGS_INFM`, slug in `WSVC_ID` |
| Doc ↔ Frontend | ✅ `AuditLogsDialog` read-view exists (not yet cited in the doc — add in Phase 2) |

### notification — **corrected (doc contradicted code)**

**Documentation Status:** **Outdated → corrected this pass.**

**Files Updated**
- [wecms-api/docs/features/common/README.md](../features/common/README.md) — Notifications scope row.
- [wecms-web/docs/features/common/README.md](../../../wecms-web/docs/features/common/README.md) — Notifications scope row.

**Corrections Made**
- "Notifications ✅ implemented — `domain/notification/` — `NOTIFICATION` table (V238) + 4 endpoints + producers; spec: `docs/refactor-wecms/features/notifications.md`" → corrected to: **`domain/payment/notification/` on legacy `BLTB_*` tables** (`BLTB_NOTI_INFM` + 4 sibling tables); endpoints `GET /api/v1/notifications`, `GET /unread-count`, `PUT /{id}/read`, `PUT /read-all`; ports legacy **`comm_0192_007`**; **BLTB branch only** (General `TYPE='G'` not ported); producer leg = `ApprovalNotificationService`.
- Dead link to `docs/refactor-wecms/features/notifications.md` (deprecated tree, describes the removed shape) → repointed to the forthcoming `common/notification/` folder + [notification-popup-legacy-vs-refactor-analysis.md](notification-popup-legacy-vs-refactor-analysis.md).

**Remaining Gaps** *(gap vocab)*
- **DEAD LEGACY:** the old `domain/notification/` + `NOTIFICATION` table (V238) — do not re-document the removed shape.
- **PARTIAL:** General-notification branch (`TYPE='G'`, `GENL_NOTI_*`) intentionally not ported (PO confirm — gap C1).
- **MISSING DOC:** no `common/notification/` folder yet (Phase 2, A4).

| Axis | Result |
|---|---|
| Doc ↔ API | ✅ after fix — 4 endpoints verified against `NotificationController.java` |
| Doc ↔ Database | ✅ after fix — BLTB tables (in legacy inventory); refactor-only `NOTIFICATION` gone |
| Doc ↔ Frontend | ✅ `components/layout/notifications/*` + `lib/api/notification.ts` |

### excel-export · excel-import · file-management · navigation · common-code-lookup · table-sort · dialog-popup · error-envelope

**Documentation Status:** **MISSING as Common docs** (live code, documented only in CLAUDE.md + per-consumer feature specs).

**Files Updated:** none this pass (folders are Phase 2).
**Corrections Made:** none — there was no Common doc to correct.
**Remaining Gaps:** see gap analysis A1–A9. Each becomes a Common module folder in Phase 2. `common-code-lookup` notably owns the only Common reference-data REST endpoint — `GET /api/v1/common/countries` (`CountryController`, legacy `comm_0110_001_r001`) — which had no doc anywhere.

### dashboard-common

**Documentation Status:** **Accurate** — Common README correctly says "not yet in scope."
**Remaining Gaps:** **MISSING** (no code; no stub folder). Kept as a README note only — gap C5.

---

## Cross-cutting findings

### 1. Notification physically lives under `domain/payment/`, but it is a Common feature
The header bell is a global surface shown to every authenticated user (no `@menuAuthz`, allow-listed in `EndpointAuthorizationCoverageTest`). The **Common** doc owns its spec; the source-index records the physical package (`domain/payment/notification/`). Do not relocate the doc into Payment. (gap G1)

### 2. Audit `WSVC_ID` is the refactor slug, not a legacy WSVC id
Override 2026-05-09. Any Common doc citing an audit write uses the slug (`firm-banking-settings`), never `comm_0220_001`. (gap G2)

### 3. CLAUDE.md is the de-facto Common spec — folders summarize, never duplicate
The bulk of Common rules (export pipeline, sort contract, View Setting, dialog stacking, error envelope) live in `wecms-api/CLAUDE.md` and `wecms-web/CLAUDE.md`. Those remain the operational source of truth. Phase-2 folders **cross-link + summarize** them; they must not fork the rule text. The CLAUDE.md "Feature Status" Notification rows were **not** edited (CLAUDE.md is project-instruction config, outside `docs/` audit scope) — recommend refreshing them in the next CLAUDE.md pass since they still reference the navigation/notification history.

### 4. Two identifier namespaces survive in Common
View Setting `WEB_SVC_ID` = semantic kebab route slug (never legacy JEX page id); audit `WSVC_ID` = the same slug. Neither accepts `fnsh_0001_001`-style ids. (gap G3)

---

## Files updated this audit (complete list)

1. `wecms-api/docs/audits/common-source-index.md` *(new)*
2. `wecms-api/docs/audits/common-gap-analysis.md` *(new)*
3. `wecms-api/docs/audits/common-docs-audit.md` *(this file — new)*
4. `wecms-api/docs/features/common/README.md` *(Notification scope row + dead-link fix)*
5. `wecms-web/docs/features/common/README.md` *(Notification scope row + dead-link fix)*

**Not modified (verified accurate):** `wecms-api/docs/features/common/view-setting.md`, `wecms-api/docs/features/common/audit-log.md`, `wecms-web/docs/features/common/view-setting.md` — current; to be promoted to folders in Phase 2.

**Phase 2 (pending review — not done this pass):** build 11 Common module folders (9-file API / 6-file WEB per applicability) for `view-setting`, `audit-log`, `excel-export`, `excel-import`, `file-management`, `notification`, `navigation`, `common-code-lookup`, `table-sort`, `dialog-popup`, `error-envelope`; convert both Common READMEs to summary/navigation docs with the standard banner; cross-link `approval` / `reporting-account` / `attachment` to their owning domains.

---

*Last verified: 2026-06-23.*
