# View Setting — Test Scenarios (Backend)

**Part of:** Common → view-setting
**Last verified:** 2026-06-23

## SIT / UAT scenarios

| # | Scenario | Steps | Expected |
|---|---|---|---|
| T1 | First load lazy-init | New user, GET `/screen-options/banking-account-inquiry-vnd/default` | Sys rows copied into `USER_SCR_OPTION_WECMS` for the scope; response = merged list ordered by `disp_ord` |
| T2 | Second load skips init | Repeat T1 | No new user rows inserted; same merged list |
| T3 | Save toggles + reorder | PUT full list with one `use_yn='N'` + a reordered `disp_ord` | User scope wiped + re-inserted; GET reflects the toggle + order |
| T4 | Save preserves order of hidden rows | PUT list including `use_yn='N'` rows (not omitted) | Hidden rows persist with `use_yn='N'`; column order intact across reload |
| T5 | Special-field lock on Save | PUT with `CHECKBOX.use_yn='N'` + reordered `CHECKBOX.disp_ord` | Persisted as `use_yn='Y'`, `disp_ord` = sys seed (0) regardless of payload |
| T6 | Reset | DELETE `/screen-options/banking-account-inquiry-vnd/default` after a save | User rows wiped + re-init from sys; response = pure sys defaults |
| T7 | Unknown screenId | GET `/screen-options/banking-unknown-page/default` | `WBE2001` (regex passes, no sys row) |
| T8 | Legacy id rejected | GET `/screen-options/fnsh_0001_001/default` | `WBE_VALIDATION` (underscore fails `@Pattern`) |
| T9 | Empty save payload | PUT with `{ items: [] }` | `WBE_VALIDATION` (`@NotEmpty`) |
| T10 | Unknown FIELD_ID in payload | PUT including a FIELD_ID not in the sys seed | Silently dropped; GET reconciles to canonical set |
| T11 | Areas | GET `/screen-options/banking-account-inquiry-vnd/areas` | `{ web_svc_id, areas:[{dym_area_id:'default', column_count:N}] }` |
| T12 | Tenant isolation | User A saves; User B (same screen) loads | B sees sys defaults / B's own overrides, never A's |
| T13 | Unauthenticated identity | Load with null bizKey/userId (no JWT identity) | Pure sys defaults, no lazy-init write, no exception |

## JUnit coverage

`⚠️ UNVERIFIED / NOT FOUND` — no automated test for View Setting exists under
`wecms-api/src/test/**` as of 2026-06-23 (no `ScreenOptionServiceTest`, no controller test). The
scenarios above are therefore **untested by JUnit** and are exercised only via SIT/UAT manual flow.

## Gaps

1. **No unit/integration test** for `ScreenOptionService` (load/save/reset/areas, special-field lock,
   lazy-init, tenant scope) — highest-priority coverage gap. Recommend a Mockito test for the merge +
   special-field logic and a `@SpringBootTest` for the lazy-init + delete-then-insert round-trip.
2. **Multi-area path untested** — `listAreas` + multi-area save/load has no shipped consumer and no
   test with >1 `DYM_AREA_ID` per screen. Combined with the `@IdClass` gap (refactor-gap-analysis #13)
   this is an untested correctness risk for any future multi-area page.
3. **SIZE end-to-end** — deferred; no test asserts width round-trips, since it is not consumed yet.
