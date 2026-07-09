# Internal Transfer Withdrawal-Account Listing — Remediation Plan (G1 / G2 / G3)

> _**Historical one-off audit.** A point-in-time record of a completed verification / remediation — findings are preserved unchanged. For the feature’s current state see its `features/.../refactor-gap-analysis.md`; for context see the [audits index](README.md)._

**System:** Refactor (wecms-api / wecms-web) · **Baseline:** JEX legacy (source of truth)
**Date:** 2026-06-10 · **Author:** Fix-planning review (Claude)
**Status:** Remediation plan only — **no code/tests/schema changed.** Implementation NOT started.
**Parent audit:** `internal-transfer-upload-withdrawal-account-listing-audit-2026-06-10.md`

Covers the three listing-filter findings from the parent audit: **G1** (bank-code restriction), **G2** (`ACCT_AUTH_INFO` authorization filter), **G3** (`TB_FIRM_CONF` fallback-to-all). The label/currency findings (G4/G5) are out of scope here (P2, defer) and tracked in the parent audit.

---

## 1. Re-verification against legacy

All three confirmed again from source — no change from the parent audit.

| Gap | Legacy (source of truth) | Refactor (current) | Confirmed divergent? |
|---|---|---|---|
| **G1** bank restriction | `and bank_cd in ('11704088', '11704020')` — `comm_1001_001_act.jsp:31` (Shinhan VN + Woori VN) | No bank predicate in `AcctMstrRepository.findWithdrawalAccounts` JPQL (`:360-387`) | ✅ Yes |
| **G2** authorization | `and EXISTS (ACCT_AUTH_INFO AAI … AAI.USER_ID IS NOT NULL AND AAI.USE_YN='Y' AND AAI.AUTH_TYPE IN ('V','W'))` — `comm_1001_001_act.jsp:43-52` | No `ACCT_AUTH_INFO` join/filter in the query or `InternalTransferService.listWithdrawalAccounts` (`:1319-1339`) | ✅ Yes |
| **G3** firm-banking | `and EXISTS (TB_FIRM_CONF TFC … TFC.ORG_STAT='1')` — **mandatory** — `comm_1001_001_act.jsp:54-61` | `EXISTS(FirmBankingSetting…orgStat='1') OR NOT EXISTS(any active FirmBankingSetting)` — fallback-to-all — `AcctMstrRepository:366-382` | ✅ Yes (fallback branch only) |

Shared filters already at parity (no action): `BIZ_KEY`, `ACTV_YN='Y'`, `ACCT_TYPE_CD IN ('03'..'08')`, default-select-first, submit mapping to `OUT_BANK_CD`/`WDRL_ACT_NO`/`WDRL_CCY_CD`, label format string.

## 2. Was any business requirement intentionally changed during refactoring?

| Gap | Intentional change? | Evidence |
|---|---|---|
| **G1** | **No.** No design note, MR, or comment records dropping the bank restriction. The refactor relied on the firm-banking `EXISTS` to implicitly scope banks; the explicit 2-bank legacy guard was simply not ported. → **Migration gap.** |
| **G2** | **No.** The sibling Own Account Transfer path *does* apply the V/W permission filter in Java (`OwnAccountTransferService:207-228, 793-796`), and `AccountAccessGuard` enforces it for Transaction History. The withdrawal-accounts query just omits it. No record of an intentional decision to drop authorization here. → **Migration gap (security-relevant).** |
| **G3** | **Partially intentional — infra accommodation, not a business change.** The `OR NOT EXISTS` fallback is documented in `AcctMstrRepository:350-358` as a workaround for the un-provisioned shared R&D DB (no write access to seed `TB_FIRM_CONF`), explicitly claiming "strict parity resumes once firm-banking is provisioned." It was never a business decision to widen production visibility. → **Documented deviation to be gated/removed for SIT+.** |

## 3. Impact assessment

| Dimension | G1 (bank restriction) | G2 (authorization) | G3 (firm-banking fallback) |
|---|---|---|---|
| **Security** | Low — bank set, not user authority | **High** — a user can select a withdrawal (debit) account they hold no `V`/`W` grant on; authorization boundary on the *source of funds* is missing | Medium — fallback lists every active account when no firm config exists, bypassing the firm-banking gate |
| **Data visibility** | High — accounts from banks other than Shinhan/Woori appear in the dropdown | **High** — accounts outside the user's permitted set appear | High (only when the fallback branch fires — i.e. tenant has zero active `FirmBankingSetting` rows) |
| **Payment processing** | High — a selected non-firm-banking bank/account would fail or misroute at transfer execution (`OUT_BANK_CD` NAPAS mapping + `TB_FIRM_CONF` expected downstream) | Medium — an unauthorized source account could be persisted into `SCDUL_PYMN_SUMR` and proceed to approval/execution | Medium — same downstream risk if a non-firm-configured account is selected via the fallback |
| **SIT risk** | High — functional-parity tests comparing legacy vs refactor dropdown contents fail outright (legacy shows 2 banks, refactor shows all) | **High** — authorization test cases (non-admin user limited to granted accounts) fail; flagged as a security defect | Medium — only manifests on a tenant with no firm-banking config; on the seeded SIT tenant it may be masked, then surface in PROD-like data |

**Overall:** G2 is the most serious (security + processing + SIT). G1 is a hard functional-parity failure. G3 is conditional (data-state dependent) and already documented.

## 4. Recommended implementation plan

> All three changes are **backend-only**. The wire contract (`GET …/withdrawal-accounts` → `WithdrawalAccountRow[]`) and the frontend dialog are **unchanged** — the list simply returns fewer (legacy-correct) rows. No `wecms-web` change required.

### G2 — reinstate `ACCT_AUTH_INFO` V/W authorization filter (highest priority)

**Approach (recommended): Java-side intersection, mirroring the established Own Account Transfer pattern.**
- In `InternalTransferService.listWithdrawalAccounts(ccyCd)`:
  1. Resolve `userId` from `TenantContext.currentUserId()` (and `authGrCd` for the admin decision below).
  2. Load the user's active grants once: `acctAuthRepo.findByBizKeyAndUserIdAndUseYn(bizKey, userId, "Y")` (existing method, `AcctAuthInfoRepository:91-92`).
  3. Build a set of `(bankCd, acctNo)` keys where `AUTH_TYPE IN ('V','W')` (legacy `comm_1001_001_act.jsp:51`). Reuse `OwnAccountTransferConstants.AUTH_TYPE_VIEW` / `AUTH_TYPE_WITHDRAW`.
  4. Filter the repository result to accounts present in that set.
- **Admin handling — decision required (see §6 Needs-Confirmation):** legacy `comm_1001_001` has **no** admin bypass (the `EXISTS` is unconditional). The refactor's Own Account Transfer *does* bypass for `authGrCd=ADMIN`. **Recommend matching legacy (no bypass)** for strict parity; if the team prefers the refactor-wide admin convention, gate it explicitly and document the divergence.

**Alternative (closest literal parity): JPQL `EXISTS` subquery** added to `findWithdrawalAccounts`:
```
AND EXISTS (SELECT 1 FROM AcctAuthInfo aa
             WHERE aa.bizKey = a.bizKey AND aa.bankCd = a.bankCd AND aa.acctNo = a.acctNo
               AND aa.userId = :userId AND aa.useYn = 'Y' AND aa.authType IN ('V','W'))
```
Trade-off: the Java approach matches the existing refactor convention (own-account-transfer, AccountService) and keeps the tenant-slice query clean + test fixtures simpler; the JPQL approach is byte-for-byte closest to legacy. **Recommend the Java approach for consistency**, with the `userId` param threaded into the service.

- **Tables/entities:** none new — `AcctAuthInfo` / `AcctAuthInfoId` / `AcctAuthInfoRepository` exist; `ACCT_AUTH_INFO` seeded by V245/V246.
- **API impact:** none (response shape unchanged; fewer rows).
- **Test impact:** new `InternalTransferServiceTest` cases — (a) USER with V grant sees the account, (b) USER with W grant sees it, (c) USER with no grant does **not**, (d) soft-revoked (`USE_YN='N'`) excluded, (e) admin behavior per the §6 decision. Seed `ACCT_AUTH_INFO` rows in the test fixture.

### G1 — reinstate bank-code restriction

**Approach:** add the legacy predicate to `AcctMstrRepository.findWithdrawalAccounts`:
```
AND a.bankCd IN :withdrawalBankCds
```
bound from a named constant (e.g. `InternalTransferConstants.WITHDRAWAL_BANK_CDS = List.of("11704088","11704020")`) so the legacy literal is documented and single-sourced, not scattered.
- **Decision required (see §6):** confirm the bank set is still exactly Shinhan VN (`11704088`) + Woori VN (`11704020`) — it is a hardcoded business constraint in legacy and may change. If the intent is "any firm-banking-integrated bank," the firm-banking `EXISTS` (G3, made mandatory) already expresses that and the explicit list may be redundant — but **legacy is the baseline, so port the list unless the lead confirms otherwise.**
- **Tables/entities:** none new.
- **API impact:** none.
- **Test impact:** assert an active `03–08` account on a third bank is excluded; assert Shinhan/Woori accounts still included.

### G3 — make firm-banking mandatory; gate the fallback out of SIT/PROD

**Approach (two parts):**
1. **Seed firm-banking config for the SIT/R&D tenant** so the mandatory `EXISTS` returns rows — a Flyway seed adding `FirmBankingSetting` (`TB_FIRM_CONF`, `ORG_STAT='1'`) rows for `VN_VNM0004_000001`'s Shinhan/Woori withdrawal accounts (mirrors the data a real tenant has). This is the clean fix — it makes the `EXISTS` branch behave like legacy and removes the need for the fallback.
2. **Gate the fallback** so it cannot widen visibility outside local R&D — either:
   - remove the `OR NOT EXISTS(...)` branch from the JPQL and rely on (1); **or**
   - keep it behind a property (`wecms.internal-transfer.firm-banking-fallback`, default `false`; `true` only in `application-local.yml`) via a conditional query/method. Removal is preferred once (1) lands.
- **Tables/entities:** none new (`FirmBankingSetting` + `NapasBankInfo` exist; `TB_FIRM_CONF` via V62). One Flyway **seed** migration for the test tenant.
- **API impact:** none.
- **Test impact:** with firm config present → only configured accounts returned; without the fallback → no over-inclusion when config is absent (returns empty, matching legacy).

## 5. Classification

| Gap | Classification | Rationale |
|---|---|---|
| **G2** authorization | **MUST FIX BEFORE SIT** | Security/authorization defect on the debit source account; SIT authorization scenarios will fail and it is unsafe to expose unauthorized withdrawal accounts. |
| **G1** bank restriction | **MUST FIX BEFORE SIT** | Hard functional-parity failure (dropdown contents differ from legacy) and selecting a non-firm-banking bank would break transfer execution. |
| **G3** firm-banking fallback | **MUST address before SIT via data seeding; fallback-removal CAN fix during SIT** | Seed the SIT tenant's firm-banking config before SIT (part 1) so behavior matches legacy on the SIT dataset. The code-side fallback removal/gating (part 2) can land during SIT hardening, provided the seed guarantees the `EXISTS` branch is the one exercised. If SIT will run against any tenant lacking firm config, promote part 2 to must-fix-before-SIT. |

(Parent-audit P2 items — **G4** bank-name source, **G5** currency mechanism, and the submitted-currency `WDRL_CCY_CD` question — **can defer after SIT**; confirm during SIT, fix in a follow-up.)

## 6. Evidence, risk, and recommended order

### Legacy evidence
- Bank restriction: `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_1001_001_act.jsp:31`
- Authorization: `…/comm_1001_001_act.jsp:43-52` (`ACCT_AUTH_INFO`, `AUTH_TYPE IN ('V','W')`, `USE_YN='Y'`)
- Firm-banking (mandatory): `…/comm_1001_001_act.jsp:54-61` (`TB_FIRM_CONF`, `ORG_STAT='1'`)
- Query shell: `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.ACCT_MSTR_R011.xml`

### Refactor evidence
- Query (gaps): `wecms-api/.../banking/repository/AcctMstrRepository.java:360-387` (no bank predicate, no `ACCT_AUTH_INFO`, fallback `:366-382`, documented Javadoc `:350-358`)
- Service: `wecms-api/.../payment/internaltransfer/service/InternalTransferService.java:1319-1339`
- Pattern to mirror (G2): `wecms-api/.../banking/ownaccounttransfer/service/OwnAccountTransferService.java:207-228, 793-796`; constants `OwnAccountTransferConstants.AUTH_TYPE_VIEW/WITHDRAW`
- Existing auth assets: `AcctAuthInfo` / `AcctAuthInfoId` / `AcctAuthInfoRepository` (`findByBizKeyAndUserIdAndUseYn:91-92`, existence check `:98-99`), `AccountAccessGuard`; table V245/V246
- Firm-banking assets: `FirmBankingSetting` (`TB_FIRM_CONF` via V62), `NapasBankInfo` (V68)

### Risk assessment
- **Doing the fixes:** Low implementation risk — all entities/repositories exist, the wire contract and frontend are untouched, and the change only *narrows* the result set. Main risk is the **admin-bypass parity decision** (G2) and the **bank-list currency** (G1) — both resolved by the §6 confirmations below.
- **Not doing the fixes:** High — G2 leaves an authorization hole on the debit source; G1 breaks legacy parity and can produce unexecutable transfers; G3 can over-expose accounts on un-provisioned tenants.

### Needs Confirmation (resolve before coding)
1. **G2 admin bypass:** match legacy (no bypass — recommended) or follow the refactor-wide admin-bypass convention used by Own Account Transfer? Legacy `comm_1001_001` has no admin branch.
2. **G1 bank set:** is `('11704088','11704020')` still the exact, current withdrawal-bank list, or should the set derive from firm-banking config?
3. **G3 SIT data:** will SIT run only against tenants with seeded firm-banking config (then part 2 can fix during SIT), or against PROD-like tenants that may lack it (then part 2 is must-fix-before-SIT)?
4. **Runtime baseline:** a live same-tenant/same-user diff of legacy `comm_1001_001` vs refactor `/withdrawal-accounts` to quantify the extra rows before/after the fix (acceptance evidence).

### Recommended implementation order
1. **G2 + G1 together (one MR)** — both modify `findWithdrawalAccounts` + `InternalTransferService.listWithdrawalAccounts`; combine to avoid two passes over the same query. Land the authorization filter (G2) and the bank restriction (G1), add the service tests. *Must fix before SIT.*
2. **G3 part 1 (firm-banking seed)** — Flyway seed for the SIT/R&D tenant so the mandatory `EXISTS` returns rows. *Before SIT data setup.*
3. **G3 part 2 (remove/gate fallback)** — once the seed is in place, drop the `OR NOT EXISTS` branch (or property-gate it). *During SIT hardening, or before SIT if §6.3 says SIT tenants may lack firm config.*
4. **Acceptance** — run the §6.4 live diff to confirm the refactor dropdown now matches legacy for the same tenant + user.

This document is documentation only — no application source, tests, or schema were changed.
