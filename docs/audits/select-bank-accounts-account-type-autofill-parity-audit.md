# Select Bank Accounts — Account Type auto-fill parity audit

**System:** Refactor (wecms-api + wecms-web) vs. Legacy JEX
**Feature:** `login-credentials` → "Select bank accounts" registration popup
**Date:** 2026-07-16
**Author:** AI (Claude) parity review requested by Sonny
**Legacy screen:** `fnsh_0001_001` (Firm Banking) → `comm_0218_nxisas` popup
**Verdict:** **PARTIAL** — Tier-1 server mapping is wired but **inert** (reference table unseeded / off-inventory); Tier-2 client fallback is **missing** and mislabelled as legacy parity. Result: refactor popup shows an empty **"Select"** where legacy auto-fills the Account Type.

> **Implementation status (2026-07-16 — this fix):**
> - ✅ **Tier-2 implemented** (FE, no contract change) — `mapScrapedToBankRow` now defaults an
>   unmapped account to **Current** for its currency via the new `defaultCurrentAcctTypeCd`
>   ([selectBankAccountsMockData.ts](../../../wecms-web/components/shared/popups/selectBankAccountsMockData.ts)).
>   `tsc --noEmit` clean. This alone restores the legacy auto-fill in the screenshot.
> - 📄 **Tier-1 seed prepared, PO-pending** — manual DBA script
>   [`Claude Code (AI)/sql/nxisas_acct_type_mapping_20260716.sql`](../../../Claude%20Code%20%28AI%29/sql/nxisas_acct_type_mapping_20260716.sql)
>   (18 legacy rows, idempotent, DBA-run). Requires PO sign-off to add
>   `NXISAS_ACCT_TYPE_MAPPING` to the baseline before it runs in a shared env; the
>   already-written `NxisasAcctTypeMappingRepository` lights up with no Java change once seeded.
> - ↔️ **Backend save is already safe** — `AccountRegistrationService.coalesceAcctType` defaults a
>   blank code to `"03"`. With Tier-2 now populating the correct currency-specific code, the wrong
>   USD/KRW → `03` fallback no longer fires in practice.

---

## 1. What the user observed

In the live JEX popup, the Account Type column is **auto-selected** when the popup opens
(`Current (VND)`, `Current (VND)`, `Current (USD)` for the three VCB accounts). In the
refactor popup the same column shows the placeholder **"Select"** for every row.

Legacy supports three account-type families per currency — **Current / Savings / Fixed
Deposit** — exactly as the user described.

---

## 2. Legacy behaviour (source of truth)

Auto-fill in legacy is a **two-tier** mechanism.

### Tier 1 — server maps the scraped vendor string → `ACCT_TYPE_CD`

`fnsh_0101_001_r001_act.jsp:135-176` (`WECMS DEV/`): the Coocon `ACCT_LIST` job returns the
bank's raw account-type text (`acct.getAcctType()`), stored as `NXISAS_ACCT_TYPE`. The server
looks it up in **`NXISAS_ACCT_TYPE_MAPPING`** keyed by `(BANK_CD, CCY_CD, NXISAS_ACCT_TYPE)` and,
when a row matches, sets `ACCT_TYPE_CD` on the response row:

```java
// fnsh_0101_001_r001_act.jsp:167-175
NxisasAcctTypeMappingPk pk = new NxisasAcctTypeMappingPk();
pk.copyFrom(recOutItem);
pk.setBankCd(bankCd);
NxisasAcctTypeMapping m = NxisasAcctTypeMappingDAO.getInstance().findByPk(util, idoCon, pk);
if (m != null) recOutItem.put("ACCT_TYPE_CD", m.getAcctTypeCd().getValue());
```

Live `NXISAS_ACCT_TYPE_MAPPING` on JEX DEV — **18 rows, codes `02`/`03`/`04` only** (never
Savings 05/06 or KRW 07/08):

| Bank | CCY | Scraped text | → `ACCT_TYPE_CD` |
|---|---|---|---|
| VCB `11704004` | VND | `Tài khoản thanh toán` | `03` Current |
| Shinhan `11704088` | USD | `DEMAND DEPOSIT-USD` | `04` Current |
| Shinhan `11704088` | VND | `DEMAND DEPOSIT-VND` | `03` Current |
| Shinhan `11704088` | VND | `TIME DEPOSIT-VND` / `Tiền gửi có kỳ hạn (TT) - VND` | `02` Fixed |
| Vietin `11704001` | VND | `Tài khoản tiền gửi` | `02` Fixed |
| Woori `11704020` | VND | `Tiền gửi có kỳ hạn` | `02` Fixed |
| ACB `11704080` | VND | `Checking Account` | `03` Current |
| … (18 rows total; VCB has **only** the single VND row above) | | | 02/03/04 only |

### Tier 2 — client fallback when the server left it blank

`comm_0218_nxisas.ts:1317-1332` (`WECMS_STATIC/`): when the server returned no `ACCT_TYPE_CD`,
the popup defaults to the **first option for that currency**, which is always **Current**:

```ts
if (!typeCd) {
    const acctType = getAcctType(ccy)[0]?.ACCT_TYPE;   // VND→03, USD→04, KRW→07 = Current
    if (acctType) account.ACCT_TYPE_CD = acctType;
}
// then: if resolved code is "02", default ACCT_DV_CD = "1" (예금)
```

The dropdown option set (`comm_0218_nxisas.ts:1236-1258`):

| Currency | Options (code → label) |
|---|---|
| VND | `03` Current, `05` Savings, `02` Fixed Deposit |
| USD | `04` Current, `06` Savings, `02` Fixed Deposit |
| KRW | `07` Current, `08` Savings, `02` Fixed Deposit |

Display mapping: `osmc.ui.ts:945-975` (`getAcctType(code)`).

### Why the VCB screenshot shows what it shows

- `0441000688210` VND → scrape `Tài khoản thanh toán` → **Tier 1** → `03` **Current (VND)** ✔
- `1063895376` VND → same → `03` **Current (VND)** ✔
- `1051996380` USD → VCB has **no USD mapping row** → server leaves it blank → **Tier 2** picks
  `getAcctType('USD')[0]` = `04` **Current (USD)** ✔

> ⚠️ **Legacy quirk to preserve deliberately.** The USD row carries a Maturity Date
> (`23-Sep-2025`) — a Fixed-Deposit signal — yet still auto-fills **Current**, because there is
> no USD mapping row and the fallback blindly picks the first option. Legacy does **not** infer
> "Fixed" from the presence of a maturity date. Bug-for-bug parity means replicating this;
> "fixing" it is a deliberate divergence needing PO sign-off.

---

## 3. Refactor state

### Tier 1 — wired but inert

| Piece | File | Status |
|---|---|---|
| Reference lookup | [NxisasAcctTypeMappingRepository.java](../../src/main/java/com/kosign/wecms/domain/banking/nxisas/NxisasAcctTypeMappingRepository.java) | ✅ implemented — native query over `nxisas_acct_type_mapping`, guarded by `to_regclass` so a missing table returns an empty map (never throws) |
| Resolution in scrape | [BankScrapingService.java](../../src/main/java/com/kosign/wecms/domain/banking/nxisas/BankScrapingService.java) `runAcctList` (596-611) | ✅ resolves `acctTypeCd` per account via `loadBankMappings` + `resolve` |
| Wire carrier | [ScrapedAccount.java](../../src/main/java/com/kosign/wecms/domain/banking/nxisas/dto/ScrapedAccount.java) `acctTypeCd` → wire `acct_type_cd` | ✅ present |

**But the mechanism is dormant** because its reference table is not present:

- **`NXISAS_ACCT_TYPE_MAPPING` is NOT in the refactor table baseline** — absent from
  [legacy-table-inventory.md](../references/database/legacy-table-inventory.md) (the canonical
  141-table baseline) **and** from the older PROD Active Table List in
  [wecms-api/CLAUDE.md](../../CLAUDE.md).
- There is **no Flyway/DBA seed** for it. The residual list in
  [login-credentials/refactor-gap-analysis.md](../features/settings/login-credentials/refactor-gap-analysis.md)
  item #4 already notes "`NXISAS_ACCT_TYPE_MAPPING` DBA seed" as pending.
- Net effect: `loadBankMappings` returns `Map.of()` → `resolve(...)` returns `null` →
  `acct_type_cd` is **always `null`** on the wire today.

### Tier 2 — missing (and mislabelled as parity)

- [selectBankAccountsMockData.ts](../../../wecms-web/components/shared/popups/selectBankAccountsMockData.ts)
  `mapScrapedToBankRow` → `normalizeAcctTypeCd(acc.acct_type_cd ?? acc.acct_type)` accepts a value
  **only** when it is already one of `02–08`; free-text or `null` → `""`.
- [SelectBankAccountsDialog.tsx](../../../wecms-web/components/shared/popups/SelectBankAccountsDialog.tsx)
  `AccountTypeSelect` honours `value` only when it matches a currency-specific code; blank → the
  "Select" placeholder. **There is no "default to first option / Current" fallback.**
- The mapper comment calls the blank result *"legacy parity: blank when unmappable"* — this is
  **incorrect**. Legacy is not blank-when-unmappable; legacy applies the Tier-2 Current fallback.

### Root cause of the empty "Select"

Both tiers are effectively absent: Tier 1 returns `null` (unseeded off-inventory table) **and**
Tier 2 was never ported. Either tier alone would have produced `Current (VND/USD)` for the VCB
rows — the popup shows "Select" because neither is active.

---

## 4. Impact analysis (before any change)

Per the root CLAUDE.md "understand-before-implement" rule, the affected surfaces:

- **API side:** seeding `NXISAS_ACCT_TYPE_MAPPING` activates the already-written Tier-1 code with
  no Java change. Adding it to the baseline is a **table-scope decision requiring PO (Mr. Jung)
  sign-off** (it is off-inventory today). Migration safety: the table is a legacy JEX reference
  table copied column-for-column — low drift risk; it maps vendor strings, holds no tenant data.
- **WEB side:** Tier-2 fallback is a pure FE change in `mapScrapedToBankRow` (or the dialog) — no
  contract change. Must default to `getAcctType(ccy)[0]` = Current per currency, and set the
  `ACCT_DV_CD="1"` default when the resolved code is `02` (legacy `comm_0218_nxisas.ts:1327-1329`).
- **Coordination:** API and WEB are paired. Tier 1 without Tier 2 still leaves USD-mapping-gap
  banks (like VCB USD) blank; Tier 2 without Tier 1 loses the correct Fixed/Savings distinction
  the mapping table encodes. **Both are needed for full parity.**

---

## 5. Recommendations

1. **Tier 2 (FE, no backend dependency) — do first.** In `mapScrapedToBankRow`, when
   `normalizeAcctTypeCd(...)` yields `""`, fall back to the first option for the currency
   (`03`/`04`/`07`). Fix the misleading "legacy parity: blank" comment. This alone restores the
   VCB screenshot behaviour. Also apply the `02 → ACCT_DV_CD "1"` default on save.
2. **Tier 1 (DB + PO) — do for correctness.** Raise adding `NXISAS_ACCT_TYPE_MAPPING` to the
   baseline with Mr. Jung; ship a **manual DBA seed** under `Claude Code (AI)/sql/` (copied
   verbatim from live JEX DEV — the 18 rows) per the manual-DDL policy. Once seeded, the
   already-written repository lights up with no Java change.
3. **Decide the maturity-date quirk explicitly.** Confirm whether the refactor keeps bug-for-bug
   parity (auto-fill Current even when a maturity date is present) or "fixes" it to infer Fixed
   Deposit — a PO decision, since it diverges from legacy.

---

## 6. Source-of-truth references

- Legacy popup markup: `WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0218_nxisas_view.jsp:221-280`
- Legacy render + two-tier auto-fill: `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0218_nxisas.ts:1227-1372`
- Legacy server mapping: `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/fnsh/fnsh_0101_001_r001_act.jsp:128-206`
- Legacy code→label: `WECMS DEV/WECMS_STATIC/ts-src/js/wabooks/osmc.ui.ts:945-975`
- Live table: `NXISAS_ACCT_TYPE_MAPPING` (JEX DEV — 18 rows, codes 02/03/04 only)
- Refactor Tier-1: `BankScrapingService.runAcctList`, `NxisasAcctTypeMappingRepository`, `ScrapedAccount`
- Refactor FE: `wecms-web/components/shared/popups/{selectBankAccountsMockData.ts,SelectBankAccountsDialog.tsx}`
- Frontend counterpart audit: [wecms-web/docs/features/settings/login-credentials/account-type-autofill-parity.md](../../../wecms-web/docs/features/settings/login-credentials/account-type-autofill-parity.md)
