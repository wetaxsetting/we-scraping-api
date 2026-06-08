# Smart Organizer — Migration Guide (JEX → Spring Boot)

Service-by-service mapping from legacy JEX `fnsh_0200_*` to the refactored Spring Boot implementation,
plus the design decisions that shaped the port. Companion to the workspace-level
[JEX → Spring service mapping](../../../references/migration/service-mapping.md) and the
[JEX migration guide](../../../../../docs/jex/features/smart-organizer/migration-guide.md).

---

## 1. Service mapping

| Legacy (JEX) | Refactor (Spring) |
|---|---|
| `fnsh_0200_001_r001` (read eligible) IDO `ACCT_TRNS_HSTR_R028` | `GET /api/v1/banking/smart-organizer/eligible` → `SmartOrganizerService.listEligible` (native `LATERAL` query) |
| `fnsh_0200_001_c001` (confirm) | `POST /api/v1/banking/smart-organizer/confirm` → `SmartOrganizerService.confirm` |
| `fnsh_0200_002_r001` (list rules) IDO `RMRK_ORG_INFM_R002` | `GET /…/rules` → `OrganizerRuleService.list` |
| `fnsh_0200_002_c001` (create/update/delete) IDO `RMRK_ORG_INFM_C001`/`_U001` | `POST /…/rules`, `PUT /…/rules/{id}`, `DELETE /…/rules/{id}` → `OrganizerRuleService.create/update/delete` |
| `fnsh_0200_002_u001` (reorder `DISP_ORD`) | `POST /…/rules/reorder` → `OrganizerRuleService.reorder` |
| `fnsh_0200_001.ts` / `fnsh_0200_002.ts` | `SmartOrganizerPage.tsx` / `OrganizerSettingsDialog.tsx` |

## 2. The matching engine port (R01)

Legacy `IDO.ACCT_TRNS_HSTR_R028.xml` embeds the matcher as a correlated sub-query that builds a
`JSON_BUILD_OBJECT` of the winning rule's classification (with display-name look-ups inline). The
refactor keeps the same semantics but uses a **`LATERAL` join** that returns the rule columns directly
(display names are resolved in Java by `resolveRows`, not in SQL):

- `ORDER BY r.disp_ord ASC, r.rmrk_seq_no ASC LIMIT 1` reproduces "lowest order wins".
- Exact arm gated by direction (`RCV_PAY_DV` vs `INOUT_DV`); Contains arm direction-agnostic
  (preserved legacy `AND … OR …` precedence — PO decision).
- **Tenant boundary kept on both arms** (`r.biz_key = h.biz_key`) — the refactor does **not** carry
  the legacy cross-tenant leak.
- The `NOT EXISTS (… acct_trns_rcv_pay_infm …)` "unconfirmed only" clause and the
  `acct_type_cd IN ('03','04','05','06')` filter are preserved verbatim.

> Verified by a golden parity test on the live PostgreSQL engine: within-tenant 5/5 identical
> (exact, contains, direction-escape, lowest-order), cross-tenant negative passes. See
> [docs/jex/audits/smart-organizer-remediation-plan.md](../../../../../docs/jex/audits/smart-organizer-remediation-plan.md) §8.1.

## 3. Display names — SQL → Java (R07)

Legacy resolves names inside the IDO sub-query (`wb_get_bank_nm`, `wb_get_chart_acct_nm`,
`wb_get_chart_acct_lv_nm`, and correlated look-ups into `BUSI_USAG_SET_INFM`, `BUSI_VAS_ACCT_INFM`,
`TRCO_INFM`). The refactor moves resolution to `SmartOrganizerService.resolveRows` with **per-page
memoised caches** (one map per dimension) — equivalent output, fewer repeated DB hits within a page.
The one reduction: the chart "level breadcrumb" (`CHART_ACCT_LEVEL_NM`) is leaf-name only in the
refactor (tracked as a non-blocking gap).

## 4. `RCV_PAY_DV` derivation (R03 deviation)

Legacy `fnsh_0200_002_c001_act.jsp:103-109` derives a rule's direction from the Purpose's
`USAG_GRP_CD` (`4`→RCV, `6`→PAY). In the refactor DB `BUSI_USAG_SET_INFM.USAG_GRP_CD` is **unpopulated**,
so direction is instead an explicit user choice (`rcv_pay_dv` on the rule). This is a **deliberate
deviation** that restores the lost ability to create PAY rules. The confirm-time direction validation
likewise degrades to a presence check. Re-keying existing rules that defaulted to `RCV` is a data task,
not a code change.

## 5. Sequence allocation

Legacy uses `wb_get_next_val` (PL/pgSQL) for `RCV_PAY_SEQ_NO` and `RMRK_SEQ_NO`. The refactor calls the
same function through `LegacyFunctionRepository.nextVal(...)` (a Phase-1 bridge). V173 adds the
`rmrk_org_infm.rmrk_seq_no` branch to the function so rule creation can allocate ids.

## 6. Currency (R06)

Legacy `fnsh_0200_001` hardcodes `"VND"` on amounts (correct for VND, wrong for FCY). The refactor
projects `CCY_CD` + `BAL` on the wire and formats client-side by the account's real currency — one
code path for VND and FCY (see [business-flow.md §6](business-flow.md#6-vnd-and-fcy-one-path)).

## 7. What was intentionally NOT carried over

- **Cross-tenant match leak** (legacy `R028` omitted `BIZ_KEY` on the rule join) — refactor enforces it.
- **`JSON_BUILD_OBJECT` in SQL** — replaced by Java resolution for testability.
- **Trailing-TAB WSVC field ids** (e.g. `TRCO_KEY\t\t`) — normalized away by snake_case wire contract.
- **A separate FCY screen** — VND/FCY are one parameterized path, not two screens.

## 8. Migration risks / residual

1. R03 direction is user-selected, not derived (data re-key needed for legacy `RCV`-defaulted rules).
2. R07 chart breadcrumb is leaf-name only.
3. Reorder endpoint exists; the drag-save UI wiring is deferred on the frontend.
4. FCY live-render smoke test: data path verified at the SQL layer, but the dev seed has no
   organizer-eligible FCY account — see [refactor-gap-analysis.md](refactor-gap-analysis.md) §R06.

See [refactor-gap-analysis.md](refactor-gap-analysis.md) for the full parity table.
