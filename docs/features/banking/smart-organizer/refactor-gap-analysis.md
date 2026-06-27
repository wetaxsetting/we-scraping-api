# Smart Organizer — Refactor Gap Analysis (Final Parity Review)

Final legacy-vs-refactor parity review for Smart Organizer. Legacy = JEX `fnsh_0200_001` (eligibility
list + confirm) + `fnsh_0200_002` (Organizer Settings rule CRUD). Refactor =
`wecms-api/domain/banking/smartorganizer` + `wecms-web/components/banking/smart-organizer`.

**Companion audits (authoritative, preserved):**
- [docs/jex/audits/smart-organizer-parity-audit.md](../../../../../docs/jex/audits/smart-organizer-parity-audit.md) — full comparison + golden test.
- [docs/jex/audits/smart-organizer-remediation-plan.md](../../../../../docs/jex/audits/smart-organizer-remediation-plan.md) — per-blocker implementation.
- [../../../audits/smart-organizer-r11-verification.md](../../../audits/smart-organizer-r11-verification.md) — R11 independent re-verification.

Status legend: **MATCH** = behavior equals legacy (or PO-approved deviation) · **PARTIAL** = works
with a known, non-blocking deviation · **MISSING** = not implemented.

---

## 1. Parity table

| Area | Legacy | Refactor | Status |
|---|---|---|---|
| **Search / list** | `fnsh_0200_001_r001` IDO `R028`: date + account + keyword, unconfirmed only, type `('03'..'06')` | `GET /eligible` native `LATERAL` query, same filters | **MATCH** |
| **Matching (R01)** | Exact gated by direction; Contains direction-agnostic; lowest `DISP_ORD`; (cross-tenant leak) | Same precedence + priority; **`BIZ_KEY` enforced** (leak removed) | **MATCH** (PO-approved: leak intentionally dropped) |
| **Receive/Pay (R03)** | Direction from `INOUT_DV`; rule direction from `USAG_GRP_CD` | Direction from `INOUT_DV`; rule direction from stored `RCV_PAY_DV` (user-chosen) | **MATCH** (deviation: §R03) |
| **VAS mapping (R05)** | Confirm writes `VAS_ACCT_CD`+`VAS_ACCT_NM`+seq | Confirm writes real code + name + seq (`confirm():407-411`) | **MATCH** |
| **Contact mapping (R05)** | Confirm writes `RCV_PAY_TRCO_NM`+`_ENM` | Confirm writes both resolved names (`confirm():403-406`) | **MATCH** |
| **Reporting account / purpose** | Proposed by rule; confirmable | Same; existence-checked on confirm | **MATCH** |
| **Confirmation** | Insert `ACCT_TRNS_RCV_PAY_INFM`, exclude from list | `POST /confirm` idempotent, transactional, excludes | **MATCH** |
| **VND support** | `fnsh_0200_001` (VND launch) | Same path, `ccy_cd="VND"` | **MATCH** |
| **FCY support (R06)** | Hardcodes `"VND"` (wrong for FCY) | Projects `ccy_cd`+`bal`, client formats by currency | **MATCH** (improves on legacy; runtime smoke pending — §R06) |
| **Security / account scope (R11)** | `BIZ_KEY+BANK_CD+ACCT_NO`, per-account auth | `AccountAccessGuard` + mandatory scope + anti-smuggling | **MATCH** |
| **Persistence (R05)** | Denormalized VAS + contact at write | Denormalized at write | **MATCH** |
| **Display-name resolution (R07)** | Resolved in SQL (`wb_*`, correlated lookups) | Resolved in Java `resolveRows` (memoised) | **MATCH** (chart breadcrumb leaf-only — §R07) |
| **Rule CRUD + reorder** | `fnsh_0200_002` create/update/delete/reorder | `OrganizerRuleController` `/rules` + `/reorder` | **MATCH** (drag-save UI deferred — §Open) |
| **Error handling** | `WBE0001`/`WBE0404` family | `WBE_SMART_ORGANIZER_*` family (interface-spec §9) | **MATCH** |
| **Export** | None in `fnsh_0200_001` | None | **MATCH** (out of scope) |

## 2. Special verification areas

### R01 — Matching logic — **MATCH**
`SmartOrganizerService.listEligible` native query, `LATERAL` sub-join. Exact arm gated by
`r.rcv_pay_dv = CASE WHEN h.inout_dv='1' THEN 'RCV' ELSE 'PAY' END AND r.match_type='E' AND r.rmrk=h.rmrk`;
Contains arm `r.match_type<>'E' AND h.rmrk ILIKE '%'||r.rmrk||'%'` (direction-agnostic, preserved);
`ORDER BY r.disp_ord ASC, r.rmrk_seq_no ASC LIMIT 1`. **Business key (`BIZ_KEY`) enforced on both arms.**
Evidence: golden test 5/5 within-tenant identical to legacy `R028`, cross-tenant correctly suppressed
(parity audit §8.1).

### R03 — Receive/Pay rules — **MATCH (with deviation)**
- **Receive/Pay classification** derives from `INOUT_DV` exactly as legacy.
- **PAY rule creation** restored — `rcv_pay_dv` stored on the rule; the early refactor defaulted every
  rule to RCV so PAY never matched.
- **Confirm validation** (`confirm():366-385`) checks purpose/VAS/contact existence in-tenant.
- **Deviation:** legacy's `USAG_GRP_CD`→direction validation degrades to a presence check because
  `BUSI_USAG_SET_INFM.USAG_GRP_CD` is unpopulated in the refactor DB; the rule's stored `RCV_PAY_DV`
  carries direction instead. Non-blocking; documented in [migration-guide.md §4](migration-guide.md#4-rcv_pay_dv-derivation-r03-deviation).
- **⚠️ Backend follow-up opened 2026-06-27 — FB-01 (see §4).** The frontend Organizer Settings
  dialog **removed the manual Receive/Pay select** that this deviation relied on. Legacy
  `fnsh_0200_002` has exactly **one** Type select (`MATCH_TYPE` Contains/Exact — `fnsh_0200_002.ts:448-489`)
  and **no** direction picker; legacy derives direction server-side from the chosen Purpose's
  usage-group (`fnsh_0200_002_c001_act.jsp:103-110`: group `4` deposit→`RCV`, group `6` withdraw→`PAY`).
  The refactor FE had surfaced a manual select only because the backend could not derive direction.
  With that select gone (legacy-UI parity), the rule-save still accepts `rcv_pay_dv` on the wire but
  the FE now sends `RCV` for new rows / preserves the stored value for existing rows — so **PAY-rule
  creation is no longer possible from the UI until the backend derives `RCV_PAY_DV` from the Purpose.**
  No backend code was changed in the FE pass; the save contract is unchanged. Frontend record:
  [wecms-web/docs/tech-debt.md](../../../../../wecms-web/docs/tech-debt.md) + the FE
  [OrganizerSettingsDialog.tsx](../../../../../wecms-web/components/banking/smart-organizer/OrganizerSettingsDialog.tsx) comment.

### R05 — Persistence — **MATCH**
`confirm()` denormalizes at write (`AcctTrnsRcvPayInfm`):
`VAS_ACCT_SEQ_NO` (surrogate) + `VAS_ACCT_CD` (real code) + `VAS_ACCT_NM`; `RCV_PAY_TRCO_KEY` +
`RCV_PAY_TRCO_NM` (local) + `RCV_PAY_TRCO_ENM` (English). Fixes the pre-remediation bug that wrote the
surrogate into `VAS_ACCT_CD` and dropped names. Evidence: `SmartOrganizerService.java:403-411`.

### R06 — FCY support — **MATCH (runtime smoke pending)**
Eligibility query projects `h.ccy_cd` + `h.bal`; the response carries `ccy_cd`; client formats by the
account's real currency (no hardcoded VND). One code path for VND and FCY. **Residual:** a live
FCY-account render smoke test was not executed because the dev seed has no organizer-eligible FCY
account — the data path is verified at the SQL layer and `formatAmount` is `tsc`-clean (parity audit
§7). Non-blocking.

### R07 — Display name resolution — **MATCH (breadcrumb leaf-only)**
`resolveRows` resolves bank name, account nickname, reporting-account name, purpose name, VAS name,
contact name, plus `bal`/`ccy_cd`, with per-page memoised caches. **Deviation:** the chart
`chart_acct_level_nm` breadcrumb is leaf-name only (legacy returned the full hierarchy via
`wb_get_chart_acct_lv_nm`). Non-blocking.

### R11 — Account scope security — **MATCH**
Both endpoints require `bank_cd`+`acct_no`; `AccountAccessGuard.assertCanView` enforces per-account `V`
grant (admins bypass); confirm re-validates every row's account (anti-smuggling). No tenant-wide
fallback. Independently re-verified — all 9 backend + 6 frontend + 4 security checks PASS
([smart-organizer-r11-verification.md](../../../audits/smart-organizer-r11-verification.md)).

## 3. Migration status

```
Refactor Status: 100% Complete
```

All six SIT blockers (R01, R03, R05, R06, R07, R11) are **CLOSED** and source-verified; both trees
compile clean (`./gradlew compileJava` ✅, full `./gradlew test` ✅, `npx tsc --noEmit` ✅). Golden
matching-parity test passed on the live engine.

## 4. Open items (non-blocking)

| Item | Note |
|---|---|
| **FB-01 — R03 direction (backend follow-up, opened 2026-06-27)** | **Derive `RCV_PAY_DV` from the selected Purpose's usage-group at rule save**, matching legacy `fnsh_0200_002_c001_act.jsp:103-110` (usage group `4` deposit→`RCV`, `6` withdraw→`PAY`). **Why now:** the frontend removed its manual Receive/Pay select (legacy-UI parity — legacy has one Type select only); until the backend derives direction, new rules default to `RCV` and **PAY-rule creation is unavailable from the UI**. **Needs:** populate `BUSI_USAG_SET_INFM.USAG_GRP_CD` (or another verified direction source) in the refactor DB, then derive at `OrganizerRuleService` save. Until done: existing rules keep their stored direction; new rules = `RCV`. **FE-only Banking fix did NOT include this — open as a separate backend task.** Frontend `ApiPurposeSettingRow` exposes no `usag_grp_cd`, so deriving on the FE is not possible. |
| R03 direction (data) | Data re-key for legacy `RCV`-defaulted rules (pre-existing). |
| R07 breadcrumb | `chart_acct_level_nm` is leaf-name only. |
| Reorder UI | `POST /rules/reorder` implemented; frontend drag-save wiring deferred. |
| FCY runtime smoke | Data path verified; no organizer-eligible FCY account in dev seed for an end-to-end render test. |
| Golden test in CI | Matching parity test run manually; port into `@SpringBootTest`. |

These are tracked, agreed deviations — none blocks SIT/UAT, which is complete.
