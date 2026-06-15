# Chart of Accounts — Functional Specification (Backend)

**Slug:** `chart-of-accounts` · **Table:** `CHART_ACCT_INFM` · Documents the **shipped** implementation.

See also: [interface-specification.md](interface-specification.md) (endpoints), [database-mapping.md](database-mapping.md) (columns), [refactor-gap-analysis.md](refactor-gap-analysis.md) (parity).

---

## 1. Capabilities

| Capability | Implemented | Endpoint(s) |
|---|---|---|
| List / search / sort accounts | ✅ | `GET /` (`search`, `sort_field`, `sort_dir`) |
| Parent-picker tree projection | ✅ | `GET /tree` |
| Single-row detail (deep-link refresh) | ✅ | `GET /{chart_acct_seq_no}` |
| Create (with revive-on-recreate) | ✅ | `POST /` |
| Update (rename / re-parent / status / cal-rule) | ✅ | `PUT /{chart_acct_seq_no}` |
| Single soft-delete (with child re-parent) | ✅ | `DELETE /{chart_acct_seq_no}` |
| Bulk create from Excel rows | ✅ | `POST /bulk-upload` |
| Bulk soft-delete | ✅ | `POST /bulk-delete` |
| Bulk deactivate (`STTS='I'`) | ✅ | `POST /bulk-deactivate` |
| **Bulk activate** (`STTS='A'`) | ❌ **not implemented** — single-row only via `PUT`. See [gap CA-G1](refactor-gap-analysis.md). | — |
| **Excel export** | ❌ **not implemented** — only a static template is downloadable. | — |
| Account classification (`CHART_ACCT_TYPE`) | mapped but **unused** (all NULL in DEV; carried for forward-compat) | — |

---

## 2. Field model

| Field | Type | Required | Rule |
|---|---|---|---|
| `chartAcctSeqNo` | String, `VARCHAR(14)` numeric-text | server-allocated | Per-tenant surrogate PK; allocated via `wb_get_next_val('chart_acct_infm','chart_acct_seq_no',bizKey)` (MAX+1). Immutable. |
| `chartAcctCd` | String ≤ 20 (`[A-Za-z0-9_\-]+`) | ✅ | User-facing code. Unique among active rows per `uk_chart_acct_cd (BIZ_KEY, CHART_ACCT_CD, DEL_YN)`. **Immutable on update** (UI disables the field; backend still supports rename — CA-G7). Backend `@Size(max=20)`; **front-end caps at 10** (CA-G5). |
| `chartAcctNmEn` | String ≤ 255 | ✅ | English name. |
| `chartAcctNmVn` | String ≤ 255 | ✗ | Vietnamese name. Stored `null` when blank — **not** defaulted to EN (legacy defaulted; CA-G8). |
| `chartAcctNmKo` | String ≤ 255 | ✗ | Korean name. Same null behaviour. |
| `chartAcctType` | String ≤ 100 | ✗ | Classification — unused in DEV. |
| `parentChartAcctCd` | String ≤ 20 | ✅ (PO 2026-05-25) | **Parent is required** on create AND update — level 1 is reserved for the three seeded roots. Must reference an existing active row in the tenant. |
| `calRule` | `P` / `M` / null | ✗ | `+`/`-` sign for financial-report aggregation. **Allowed on level-3 rows only** (PO 2026-05-25). Rejected on levels 1–2. |
| `stts` | `A` / `I` | ✅ | Active / Inactive status (CHAR(1)). |
| `delYn` | `N` / `Y` | server-managed | Soft-delete flag. List queries filter `DEL_YN='N'`. Never surfaced on the wire. |
| `chartAcctLevel` | Integer 1–3 / null | computed | Server-computed by walking the parent chain. `null` for orphan/cycle/over-cap legacy rows. |
| `hasChildren` | Boolean | computed | True if any active child references this code. |
| `restricted` | Boolean | computed | True iff code ∈ `{ACC-01, ACC-02, ACC-03}`. |

Audit columns `regId` / `regDtm` / `modId` / `modDtm` are `VARCHAR(14)` `yyyyMMddHHmmss` stamps (`ChartOfAccountsService.utcNow14()`).

---

## 3. Business rules

### 3.1 Hierarchy & depth — max depth 3

- **`MAX_HIERARCHY_DEPTH = 3`** (`ChartOfAccountsConstants.java:41`). This is **tighter than legacy**: the legacy R002 read query capped the picker at 4 levels and the legacy upload guarded at max 5 — the refactor deliberately enforces 3 at write time (PO directive 2026-05-25).
- Level 1 is reserved for the three seeded roots; user-created nodes start at level 2.
- Depth is enforced on **both** create and update by `ChartOfAccountsValidator.assertDepthCap(...)` → `WBE_CHART_OF_ACCOUNTS_DEPTH_EXCEEDED`. The validator computes the new node depth (walk to root) **plus** the subtree height of the edited node, so an edit that would push any descendant past level 3 is rejected.
- Level is computed in Java by `ChartOfAccountsTreeBuilder.resolveLevel(...)` (cap-bounded walk, cycle-safe → returns `null`).

> **Stale Javadoc note (CA-G14):** several Javadoc comments still say "level 1..4". The authoritative cap is **3** (`Constants.java:41`). The `/tree` filter admits 1..4 defensively but `resolveLevel` caps at the constant.

### 3.2 Restricted roots

`ACC-01` (REVENUE), `ACC-02` (EXPENSES), `ACC-03` (INVESTMENT) are seeded for every tenant (V96) and **cannot be edited or deleted** — `ChartOfAccountsValidator.assertNotRestricted(...)` throws `WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE` (legacy `WBE1178` parity). Renaming any row **to** a restricted code is also blocked. The set is a literal match (`Set.of("ACC-01","ACC-02","ACC-03")`), not a flag column.

### 3.3 Parent & cycle guards (refactor additions)

- **Parent required** (`assertParentRequired`) → `WBE_CHART_OF_ACCOUNTS_PARENT_REQUIRED`.
- **Parent must exist** (active, same tenant) (`assertParentExists`) → `WBE_CHART_OF_ACCOUNTS_PARENT_NOT_FOUND`.
- **Self-parent rejected** (`assertNotSelfParent`) → `WBE_CHART_OF_ACCOUNTS_SELF_PARENT`. (Legacy allowed it — DEV seq 104 is self-referencing; grandfathered rows render with `chart_acct_level=null`.)
- **Cycle rejected on update** (`assertNoCycle` walks the ancestor chain from the new parent) → `WBE_CHART_OF_ACCOUNTS_CYCLE`.

### 3.4 Cal-rule

`CAL_RULE` (`P`/`M`) is permitted only on level-3 rows (`assertCalRuleLevel3Only`) → `WBE_CHART_OF_ACCOUNTS_CAL_RULE_LEVEL3_ONLY`. Levels 1–2 are grouping headers and carry no sign. This **supersedes** the legacy "shown/required under the INVESTMENT (ACC-03) branch" rule (CA-G4, pending PO ratification of directive 2026-05-25).

### 3.5 Code uniqueness, revive, archive-rename (soft-delete interplay)

The unique key `(BIZ_KEY, CHART_ACCT_CD, DEL_YN)` allows at most one active **and** one soft-deleted row per code. Create logic:

1. Active row with the requested code exists → `WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE`.
2. Soft-deleted row with the same code exists → **revive** it (flip `DEL_YN='N'`, overwrite fields, reuse the original `chart_acct_seq_no`).
3. Otherwise → INSERT with a freshly allocated seq_no (one-shot retry on `DataIntegrityViolationException`).

Delete symmetrically defends the UK: if a soft-deleted row with the same code already exists, the row being deleted is **archive-renamed** to `prefix(0..4) + last5(seq_no) + 'D'` before flipping `DEL_YN='Y'`.

### 3.6 Delete cascade — child re-parent

Delete is **soft** (`DEL_YN='Y'`), never a hard delete. Active children of the deleted node are re-parented (in one `@Modifying` query, `reparentActiveChildren`) to the deleted node's **own parent** — promoting them one level up. Legacy did not recursively delete descendants either; the one divergence is that deleting a (non-restricted) root sets its children's parent to `NULL` rather than leaving them dangling (CA-G11, low reachability since roots are restricted and new roots are blocked).

### 3.7 Activation / deactivation

- **Single-row:** via `PUT /{seq}` setting `stts` (`A`/`I`). The inline edit form's status dropdown is the canonical re-activation path.
- **Bulk deactivate:** `POST /bulk-deactivate` flips `STTS='I'` per seq_no, idempotent (already-inactive rows pass through with no write), restricted roots tallied as `failed`.
- **Bulk activate: not implemented** (CA-G1) — accepted as a PO-deviation since single-row re-activation covers the need.

### 3.8 Excel import processing

The frontend parses + validates the workbook (SheetJS) and POSTs a JSON array of create requests to `POST /bulk-upload`. The service iterates `req.items()`, calling the single-row `create()` through the Spring proxy so each row runs in its own transaction; failures are tallied (`{created, failed}`) and do **not** roll back committed rows. Backend does **not** force `STTS='A'` (FE hardcodes `"A"` — CA-G9 hardening note). Backend has **no Apache POI parser** for COA — parsing is entirely front-end.

### 3.9 Search, sort, tenant scope

- List filters `DEL_YN='N'`, scoped to `TenantContext.currentBizKey()`, optional case-insensitive `LIKE` on `chart_acct_cd` OR `chart_acct_nm_en`.
- Sort whitelist `ChartOfAccountsSortField`: `CHART_ACCT_CD`, `CHART_ACCT_NM_EN`, `STTS`, `MOD_DTM`. Default `CHART_ACCT_CD ASC, CHART_ACCT_SEQ_NO ASC` (legacy R001 parity). Unknown/blank values fall back to default — never echoed into SQL.
- **No server-side pagination** — load-all matches legacy.

---

## 4. Authorization & audit

- Class-level `@PreAuthorize("@menuAuthz.canAccess('settings-core-chart-of-accounts')")` — every endpoint requires the menu grant. Live at runtime (F-3 menuAuthz rollout). OQ-1 (whether USER differs from ADMIN on writes) is still open with PO; the menu gate is live regardless.
- Each Create / Update / Delete writes one `AUDIT_LOGS_INFM` row with `WSVC_ID = "chart-of-accounts"` (slug) and `REF_NO = "CHART_ACCT_INFM"`.

---

## 5. Open questions / pending PO ratification

Tracked in [refactor-gap-analysis.md](refactor-gap-analysis.md) and the authoritative audit. Summary:

| Ref | Item | Status |
|---|---|---|
| OQ-1 | USER role read-only vs full CRUD | Menu gate live; ADMIN/USER split pending PO |
| OQ-3 | Legacy bulk parent-cleanup branch (`u001:238-256`) | **Dropped** by design; ratify drop with PO |
| CA-G1 | Bulk Activate missing | PO-approved-deviation candidate |
| CA-G4 | Cal-rule level-3-only (was INVESTMENT-branch) | Ratify PO directive 2026-05-25 |
| CA-G5 | Code length 20 (backend) vs 10 (UI/legacy) | Confirm / optionally tighten |
| CA-G6 | Parent required on update | Confirm (likely intended) |
| CA-G7 | Code immutable on update | Confirm |
