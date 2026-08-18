# Expense Payment → Scheduled Payment Dispatch — SIT/UAT Deployment Checklist (G3)

**Scope:** firm-banking transmission for the Expense Payment "Transfer" (dispatch) action.
**Status:** the dispatch **DB-row creation path is real and runs by default**; **actual bank
transmission is config-gated OFF in every profile** (local/sit/uat/prod) and must be
explicitly enabled + provisioned to test end-to-end.

---

## Current behaviour (verified from source)

When a user clicks Transfer (`POST /api/v1/payment/expense-payment/{id}/dispatch`):

1. Approval gate (G1) + cut-off-time validation run.
2. `ExpenseDispatchExecutor.execute()` writes the firm-banking rows **unconditionally**:
   - VND → `TB_PAY_TARGET` (one per line), `PROC_STAT='9'` (pending).
   - non-VND → `TB_PAY_FILE_MST` + `TB_PAY_FILE_DATA`, `PROC_STAT='9'` (pending).
3. Master is stamped `TRNF_YN='Y'` + `TRNF_DTM`, transaction commits.
4. **Post-commit bank hand-offs are no-ops unless their flag is on** (all default `false`):
   - VND/API real-time 202 → `RealtimeDispatchRegistrar` (`wecms.dispatch.realtime-dispatch`).
   - File 00301 reception → `DispatchAutoReceiveRegistrar` (`wecms.dispatch.auto-receive`).
   - EFS/VAN send → `EfsApiClient`; with `wecms.dispatch.efs.enabled=false` the
     `MockEfsApiClient` is wired and **throws** if a send is ever attempted.
   - SFTP file channel → `wecms.dispatch.sftp.enabled=false` ⇒ `MockSftpClient` (no I/O).

**Net:** out of the box (including SIT/UAT), Transfer creates pending DB rows but performs
**no real bank exchange**. This is intentional, not a defect.

---

## Config flags (defaults — every `application-*.yml`)

| Property | Env var | Default | Effect when `false` |
|---|---|---|---|
| `wecms.dispatch.efs.enabled` | `WECMS_EFS_ENABLED` | `false` | `MockEfsApiClient` active (throws on send) |
| `wecms.dispatch.sftp.enabled` | `WECMS_SFTP_ENABLED` | `false` | `MockSftpClient` active (no network I/O) |
| `wecms.dispatch.auto-receive` | `WECMS_DISPATCH_AUTO_RECEIVE` | `false` | 00301 File reception hand-off skipped |
| `wecms.dispatch.realtime-dispatch` | `WECMS_DISPATCH_REALTIME_DISPATCH` | `false` | 202 VND real-time hand-off skipped |

---

## SIT/UAT checklist — to test REAL transmission

- [ ] `WECMS_EFS_ENABLED=true`
- [ ] EFS base URL configured (`wecms.dispatch.efs.base-url` — "B1")
- [ ] VAN API key provisioned in `TB_VAN_API_INFO` (read via `VanApiKeyService` — "B2")
- [ ] `WECMS_SFTP_ENABLED=true` + SFTP host / port / username / key (File channel)
- [ ] `WECMS_DISPATCH_AUTO_RECEIVE=true` (File 00301 reception hand-off)
- [ ] `WECMS_DISPATCH_REALTIME_DISPATCH=true` (VND 202 real-time hand-off)
- [ ] Firm-banking reference data present for the out-bank(s): `TB_FIRM_CONF`
      (`BANK_PRTN_ID`), `TB_FIRM_BANK_CODE` (`ENG_ABRVAT_NM`, `BANK_STAS='1'`),
      withdrawal account in `ACCT_MSTR`
      — **required for File-path (non-VND) dispatch**: a missing `TB_FIRM_CONF`
      row now blocks dispatch with `WBE2089` (legacy parity — legacy NPE-blocks),
      so this row MUST exist before a USD/File transfer is attempted.
- [ ] Cut-off-time window open for the chosen network (NAPAS/CITAD/SWIFT)

### DB-row-only validation (default config — no flags)

- [ ] Dispatch an **approved** batch → rows appear in `TB_PAY_TARGET` (VND) or
      `TB_PAY_FILE_MST`/`TB_PAY_FILE_DATA` (non-VND) with `PROC_STAT='9'`; master
      `TRNF_YN='Y'`. No EFS/SFTP/202/00301 call fires.
- [ ] Do **not** set `auto-receive`/`realtime-dispatch=true` while `efs.enabled=false`
      — that half-on state would invoke `MockEfsApiClient.send()` and throw.

---

**G3 verdict:** transmission code exists and is `@ConditionalOnProperty`-gated
(enablement-ready, not absent). End-to-end bank transmission cannot be exercised at
SIT/UAT until the flags above are set and B1/B2/SFTP are provisioned. No code change is
made to force-enable transmission (deliberate).
