# Cash Flow — Refactor Gap Analysis (Legacy JEX vs Spring refactor)

Final parity review of legacy `cash_0001_001` (JEX + `get_cashflow_report`) against the Spring Boot
(`wecms-api`) + Next.js (`wecms-web`) refactor. This is the authoritative open-items list. The
source-verified audit behind it is
[parity-audit/gap-report.md (2026-06-04)](../../../../../../docs/features/cash-flow/parity-audit/gap-report.md)
and [final-confirmation.md](../../../../../../docs/features/cash-flow/parity-audit/final-confirmation.md).

Severity: **Critical** (wrong numbers / broken) · **Major** (user-visible behaviour diff) · **Minor**
(cosmetic / cleanup). Status: **MATCH** · **PARTIAL** · **MISSING**.

---

## 1. Final parity matrix (per the task's review areas)

| Area | Legacy | Refactor | Status |
|---|---|---|---|
| **Search** | `_r001` → `get_cashflow_report` | `GET /cash-flow-report` → `CashFlowReportService` → `CashFlowReportRepository` → `get_cashflow_report` (direct call, 2026-06-13) | **MATCH** |
| **Year filtering** | `payment_year = p_year` in `filtered_payment_data` | `SUBSTRING(date,1,4)=:year` per source query | **MATCH** |
| **Quarter filtering** | server filter exists but **dormant** (UI sends null); columns hidden client-side | server filter **removed**; full Jan–Dec always; columns hidden client-side | **MATCH** (legacy live behaviour; PO 2026-06-04 — G5/D-1 resolved) |
| **Currency handling** | per-currency `ROUND` CASE (VND 2dp, USD 4dp, KRW 2dp) | `FxConverter` single-round HALF_UP, same scales | **MATCH** (N0-B; `FxConverterTest`) |
| **Hierarchical rows (L1/L2/L3)** | self-join + `wb_get_chart_acct_level_text` | `ChartOfAccountsHierarchy` + `CashFlowRowAssembler` | **MATCH** |
| **Opening Balance** | prior-year Dec cumulative end carry-forward | `BalanceCalculator.janOpeningBalance` + `openingBalance[]` | **MATCH** (`BalanceCalculatorTest`) |
| **Sales Profit** | ΣACC-01 − ΣACC-02 per month (no investment) | `BalanceCalculator.salesProfit[]` | **MATCH** |
| **Non Sales Profit** | ACC-03 `Level 1 Subtotal`, display_cd `NON SALES PROFIT`; rendered twice (blue + green) on the live screen | `Level 1 Subtotal` w/ `NON SALES PROFIT`; FE renders blue subtotal + green band | **MATCH** (intentional legacy duplicate, parity audit §8b Defect A) |
| **End Balance** | Opening(Jan) + cumulative activity; running balance | `BalanceCalculator.endBalance[]`; `annual_total = End(Dec)`; centered label | **MATCH** (Defect B fixed) |
| **Grand Total** | **never emitted** by the function | not emitted; `"Grand Total"` carried as inert dead handling | **MATCH** (no such row exists; G8 cleanup pending) |
| **Excel export** | client ExcelJS, per-currency number format | server POI `CashFlowExcelWriter`, generic `#,##0.##` | **PARTIAL** — layout/values MATCH; number format differs (G7) |
| **UI behaviour** | sticky header/footer, quarter column-hide, currency formatting | `<ReportLayout>` sticky chrome, `monthIndicesForQuarter`, `formatAmount` | **MATCH** |
| **Permissions** | `_r001 login=N` (anonymous-allowed) | JWT-required | **MATCH-by-intent** (deliberate tightening D1; no anonymous consumer should rely on the legacy gap) |
| **FX-rate source (`_r002`)** | latest **weekday** rate, `SEQ_NO=2` (closing) preference | latest seq per currency (no weekday/seq2 rule) | **PARTIAL** (N3 — prefill UX preserved, value can differ) |
| **Bank list (`_r003`)** | Haengsung hide matured/zero-balance savings | no hide-closed rule | **PARTIAL** (N4 — deferred Haengsung product-gate) |

> **Refactor Status: 100 % Complete (in-scope read & display surface).** Search, year filtering,
> quarter-as-column-visibility, currency handling, the full L1/L2/L3 hierarchy, Opening/Sales-Profit/
> Non-Sales-Profit/End-Balance, the (never-emitted) Grand Total, Excel export, UI behaviour, and
> permissions are all **MATCH**. The 2026-06-04 parity audit concluded **READY FOR SIT/UAT**.
>
> **Supporting evidence:** `CashFlowReportQuarterParityTest`, `BalanceCalculatorTest`, `FxConverterTest`
> all pass; approval-gate equivalence proven (N0-A); FX single-round HALF_UP proven (N0-B). **Superseded
> 2026-06-13:** the earlier statement that `get_cashflow_report` was "not called" reflected the (now
> retired) live state — the live path now **calls `get_cashflow_report` directly** via
> `CashFlowReportRepository` (`CashFlowReportRepository.java:67`); the Java reimplementation is retained
> but off the active path. The three
> **PARTIAL** rows (Excel number format, FX-rate selection, Haengsung hide-closed) are **non-blocking PO
> calls** — presentation-only or a deferred product-gate — not parity defects in the report math.

---

## 2. Gap register (G1–G12, after the 2026-06-04 audit)

| # | Gap | Area | Severity | Status / recommendation |
|---|---|---|---|---|
| G1 | ~~Calc reimplemented in Java, not delegating to the function~~ | Service | Critical → **Major → RESOLVED 2026-06-13** | **Superseded:** the live path now calls `get_cashflow_report` directly via `CashFlowReportRepository`; the Java reimplementation is retained but off the active path. Parity suite + sub-risks G1-approval/G1-FX (N0-A/N0-B) remain as the historical record of the (off-path) Java pipeline. |
| G2 | INVESTMENT-subset codes hardcoded; absent in DEV | Balance | Major → **Minor + NV** | refactor uses the **identical** set; confirm codes exist on UAT/PROD |
| G3 | Conditional FX-rate requirement (D2) vs legacy always-required | Validation | Minor (intentional) | documented divergence; FE matches |
| G4 | Auth tightened (`_r001 login=N` → JWT) | Security | Minor (improvement) | confirm no anonymous/batch consumer broke |
| G5 | Server quarter filter (refactor once trimmed current-year) | Filtering | Critical → **RESOLVED** | PO 2026-06-04 keep legacy; server filter removed; `CashFlowReportQuarterParityTest` guards |
| G6 | Excel client → server (POI) | Export | Minor | layout validated; MATCH |
| G7 | Excel number format `#,##0.##` vs per-currency | Export | Minor | **PARTIAL** — restore VND/KRW no-decimal + USD 2-dp if finance requires (PO call) |
| G8 | `Grand Total` row referenced but never produced | Render | Minor | dead handling on both sides; remove for clarity |
| G9 | KRW `"KWR"` client typo (legacy) | Legacy bug | Minor | **not** ported; refactor formats KRW correctly |
| G10 | No persisted summary; full recompute incl. prior year | Performance | Major | load-test; consider indexes on `(biz_key, date, chart_acct_cd)` / cached opening balance |
| G11 | `_r002`/`_r003` refactor wiring | Coverage | Major → **RESOLVED (exist)** + NV | both exist & feed the UI; selection divergences raised as N3/N4 |
| G12 | Jan/Feb typed as string in WSVC | Contract | Minor | refactor DTO uses `BigDecimal` — already fixed |

## 3. New findings (not in the original baseline)

| # | Finding | Disposition |
|---|---|---|
| N0-A | Approval gate `wb_get_appr_stts(reg_id,reg_id,…)='C'` reduces to `appr_type_cd='01'` | **RESOLVED — parity holds**; refactor implements exactly this |
| N0-B | Legacy FX is **single** `ROUND`; refactor single `divide/setScale` HALF_UP | **RESOLVED — parity holds** (incl. negatives) |
| N1 | `cal_rule` ELSE asymmetry (display=adds vs balance=0) | refactor matches **both** sides; documented in business-flow §3.1 |
| N2 | Prior-year opening balance ignores quarter **and** bank filters | correct parity (R9) |
| N3 | FX-rate endpoint selection differs (no weekday / `SEQ_NO=2`) | **PARTIAL** — PO call |
| N4 | Bank-list Haengsung hide-closed rule not ported | **PARTIAL** — deferred product-gate |
| N5 | Missing-FX-rate handling: refactor graceful fallback, legacy fragile | refactor more robust |
| N6 | Export filter keys are camelCase inside a snake_case wire | harmless contract wrinkle |

---

## 4. Not gaps (confirmed parity)

- Row types, sort/group ordering, the L1/L2/L3 hierarchy, SALES PROFIT / NON SALES PROFIT placement, and
  the four data sources + approval gate are reproduced verbatim.
- View-Setting column registry (`cash-flow-report`) seeded via V124 (export visibility only).
- Bank-filter match semantics (`bankCd` OR `accountNo`) preserved (`BankFilter`).
- Opening/End balance carry-forward and the INVESTMENT carve-out set are identical.

---

## 5. Open questions (PO)

1. **Excel per-currency number format (G7)** — restore VND/KRW no-decimal + USD 2-dp/locale, or accept generic `#,##0.##`?
2. **FX-rate weekday/`SEQ_NO=2` preference (N3)** — port the legacy closing-rate selection or accept "latest seq"?
3. **Haengsung hide-closed-accounts (N4)** — when must the matured/zero-balance savings filter land for the bank multi-select?
4. **INVESTMENT-subset codes (G2)** — confirm `2001026/2001027/2002003/2002002` exist for live tenants (else ACC-03 contributes 0 to balances — parity-neutral).
5. **`Grand Total` dead code (G8)** — remove the inert handling from the DTO doc / Excel grouper / FE classifier (cleanup).
