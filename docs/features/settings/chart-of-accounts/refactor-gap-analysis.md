# Chart of Accounts — Refactor Gap Analysis (Parity Review)

> **The authoritative, source-based gap analysis is** [../../../audits/chart-of-accounts-final-parity-audit.md](../../../audits/chart-of-accounts-final-parity-audit.md) (audited 2026-06-07/08, both trees). This file is the **feature-folder summary** of that audit, kept in sync. Do not duplicate the audit — extend it there and reflect the verdict here.

**Status (2026-06-08):** 🟢 **Code-complete for SIT** · 🟡 CONDITIONAL GO — only PO ratification remains. No Chart-of-Accounts code change is required before SIT.

---

## 1. Parity matrix (requested areas)

| Area | Legacy | Refactor | Status |
|---|---|---|---|
| **Search** | `del_yn='N'`, BIZ_KEY, ILIKE code/EN, ORDER BY code | `list()` same; LOWER LIKE; default code ASC; sort whitelist | **MATCH** |
| **Create** | required CD/EN/PARENT/STTS; dup; parent exists; revive | same + parent-required + revive + dup + parent-exists guards | **MATCH** (VN/KO not EN-defaulted — minor, CA-G8) |
| **Update** | parent optional; restricted guard; dup-on-rename; bulk parent-null | per-row update + cycle/depth/self-parent guards; **no** bulk parent-null | **APPROVED DEVIATION** (OQ-3 dropped; parent now required CA-G6; code locked CA-G7) |
| **Activation (single)** | toolbar Activate / inline | via `PUT` status flip | **MATCH** |
| **Deactivation (single)** | toolbar / inline | via `PUT` status flip | **MATCH** |
| **Bulk Activation** | toolbar Activate (all-inactive selection) | **missing** | **MISSING** (CA-G1 — PO-approved-deviation candidate; single-row re-activation covers need) |
| **Bulk Deactivation** | `u002` STTS patch | `POST /bulk-deactivate`, idempotent, restricted-safe | **MATCH** |
| **Hierarchy Management** | picker cap 4; grid uncapped; allows self-ref | cap **3**; DFS pre-order; cycle/self-parent/depth guards | **APPROVED DEVIATION** (tighter, intentional — PO 2026-05-25) |
| **Excel Import** | server parse + validate; max 500; per-cell errors | FE parse+validate (SheetJS, depth≤3, in-file dup); backend per-row create | **MATCH** (validation moved FE; backend takes JSON) |
| **Excel Export** | (legacy has none for this screen) | **not implemented** | **MATCH** (neither side exports; only template download exists) |
| **Parent Validation** | parent exists (`del_yn='N'`) | parent exists + required + self-parent + cycle | **MATCH** (stronger) |
| **Depth Validation** | read-query cap 4; upload guard 5 | write-time cap **3** (create + update, incl. subtree height) | **APPROVED DEVIATION** (tighter, PO-directed) |
| **Status Management** | A/I + restricted guard | A/I + restricted guard; soft-delete `DEL_YN` separate | **MATCH** |

Status legend: **MATCH** · **APPROVED DEVIATION** (intentional, PO-ratification pending) · **PARTIAL** · **MISSING**.

---

## 2. Specially-verified areas (per task)

- **Bulk Activate parity** → **MISSING** by design (CA-G1). Legacy surfaced Activate only on all-inactive selections; new accounts + imports land ACTIVE, so re-activation is marginal and remains available one row at a time via the inline edit status dropdown (`PUT /{seq}`). Classified as a PO-approved deviation, not a blocker.
- **Parent validation parity** → **MATCH+** . Refactor adds required-parent, self-parent, and cycle guards on top of legacy's existence check.
- **Excel Import validation parity** → **MATCH** . Required (code/name_en/parent), in-file duplicate (both rows flagged), DB duplicate (via per-row create → `DUPLICATE_CODE`), and **depth ≤ 3** are all enforced; validation moved to the front-end (SheetJS), backend accepts the validated JSON.
- **Hierarchy depth validation** → the prompt assumed **5 levels**; the **actual cap is 3** (`MAX_HIERARCHY_DEPTH=3`, PO directive 2026-05-25). Enforced on create AND update, including subtree-height on edits. This is an intentional tightening, not a gap.
- **Export parity** → **MATCH** (no export on either side). Note: the shared `common/export` pipeline exists but Chart of Accounts has **no** `ExportRowProvider` and no `/export` endpoint.

---

## 3. Open items (PO ratification only — no code change before SIT)

| Ref | Item | Resolution |
|---|---|---|
| OQ-1 | USER role: read-only vs full CRUD | Menu gate live; confirm model with PO |
| OQ-3 | Legacy bulk parent-cleanup branch | **Dropped** in code; ratify drop, close ledger |
| CA-G1 | Bulk Activate missing | Accept as deviation, or add `bulk-activate` later (non-blocking) |
| CA-G4 | Cal-rule level-3-only | Ratify PO directive 2026-05-25; reconcile V97/V191 level-2 cal-rule seed rows |
| CA-G5 | Code length 20 (backend) vs 10 (UI/legacy) | Confirm; optional `@Size(max=10)` tightening |
| CA-G6 | Parent required on update | Confirm (likely intended) |
| CA-G7 | Code immutable on update | Confirm |
| CA-G2 | Authz Javadoc contradiction | ✅ RESOLVED 2026-06-08 (doc-only fix) — authorization always live at runtime |
| CA-G10 | Internal-transfer reporting-account validation gap | **Payment-module** defect — does NOT gate COA; file against Internal Transfer |
| CA-G8/G9/G11–G16 | VN/KO EN-fallback, force STTS=A on bulk, root-delete child handling, width/cosmetic/docs/dead-code | Deferrable; none block SIT |

---

## 4. Migration status

```
Chart of Accounts — Refactor Status: ~100% functionally complete for SIT.
  • CRUD + hierarchy + import + soft-delete/revive/archive + bulk delete/deactivate + audit: shipped & SIT/UAT-verified.
  • Intentional tightenings (depth 3, parent-required, cal-rule L3, code-immutable) pending PO ratification.
  • One known omission vs legacy: bulk Activate (CA-G1) — accepted deviation, single-row path covers it.
  • Zero data-corrupting defects in the COA feature. The one referential-integrity gap (CA-G10) is in the Payment/Internal-Transfer module, not COA.
```

**Evidence for closed gaps:** see the source-cited rows in the [final parity audit §2–§3](../../../audits/chart-of-accounts-final-parity-audit.md) and [source-code-index.md](source-code-index.md).

**Verdict:** Documentation is complete, current, and aligned with the implemented refactor. The feature is code-complete for SIT; remaining items are PO confirmations tracked above and in the audit's PO Ratification Checklist.
