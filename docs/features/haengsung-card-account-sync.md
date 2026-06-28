# HAENGSUNG Card / Account-History Sync — Backend Feature Spec (Separate ERP feature)

**Slug:** `haengsung-card-account-sync` · **Domain:** Banking/ERP-sync · **Status:** Out of payment-Refresh scope — separate ERP sync feature · **Product type:** `H` only · **Last updated:** 2026-06-28

> **Docs only — no Java written.** This is **not** part of the payment Refresh feature ([haengsung-payment-refresh.md](haengsung-payment-refresh.md)) and **not** a Phase 1 blocker. Distinct ERP sync flow, documented here for an explicit scope boundary. Legacy source of truth: `WECMS DEV/WECMS/src/batch/jex/wabooks/erp/ERP_0100_003.java`.

## 1. Purpose

Push WeCMS-collected **account transaction history** and **corporate-card approvals** out to the Haengsung Oracle ERP. Pure outbound sync; no payment data, no `TB_TRNS_*`.

## 2. Direction

**WeCMS → Oracle** (one-way push).

## 3. Tables

| Section | Oracle target | WeCMS source |
|---|---|---|
| Account history | `TB_ACCT_HISTORY` (`TB_ACCT_HISTORY_C002`) | `ACCT_TRNS_HSTR` (read `ACCT_TRNS_HSTR_R029`; sent-flag `ACCT_TRNS_HSTR_U002`) |
| Card approval | `TB_CARD_APPROVAL` (`TB_CARD_APPROVAL_C002`) | `CARD_MSTR` (read `CARD_MSTR_R005`) + `CARD_TRNS_HSTR` (sent-flag `CARD_TRNS_HSTR_U003`) |

## 4. Flow

1. Select tenants `BUSI_INFM WHERE prdt_type='H' AND del_yn='N'`.
2. **Section 1 — Account history** (own transaction): read `ACCT_TRNS_HSTR` rows (`hand_reg_yn='N'`, `coalesce(erp_snd_yn,'N')='N'`); insert each into `TB_ACCT_HISTORY` (`BIZ_NO`=taxId, `CMS_PUT_DTTM`, `ERP_GET_ST='0'`); count VND vs FCY by `CCY_CD`; set WeCMS `erp_snd_yn='Y'`. Write two `ERP_TRNS_HSTR` rows (VND `TRNS_TYPE_CD=01`, FCY `08`), even on 0.
3. **Section 2 — Card approval** (own transaction): read card-approval rows (`coalesce(erp_snd_yn,'N')='N'`); insert each into `TB_CARD_APPROVAL` (`BIZ_NO`, `CMS_PUT_DTTM`, `ERP_GET_ST='0'`, `CARD_NM=CARD_HLDR_NM`, `PURCHASE_ST=PRCH_STTS`); set WeCMS `erp_snd_yn='Y'`. Write one `ERP_TRNS_HSTR` row (`TRNS_TYPE_CD=07`), even on 0.
4. All `ERP_TRNS_HSTR` rows are `ERP_TYPE='W'` (WeCMS→ERP).

## 5. Schedule / frequency

JEX batch (`ERP_0100_003`, `@JexDataInfo(type=BAT)`). No cron in source — configured in Jex Studio batch scheduler / `TB_BATCH_SCHEDULE_*`. Frequency not stated in source; confirm in Jex Studio.

## 6. Why this is separate from payment Refresh

- Touches **no** `TB_TRNS_MAST`/`TB_TRNS_DETAIL` and **no** WeCMS payment tables (`SCDUL_*`/`OVRS_*`).
- Opposite direction (WeCMS→Oracle push, vs Refresh's Oracle→WeCMS import).
- Different domains (account history + corporate card), different tables, independent schedule.
- Maps to separate refactor features — Account Inquiry / Transaction History and Card Transactions — not the payment Refresh.

**Impact if not built with Phase 1:** none on payment Refresh — account history + card approvals simply aren't pushed to ERP.

## 7. Cross-references
- Scope boundary: [haengsung-payment-refresh.md §25](haengsung-payment-refresh.md#25-scope-decision-for-first-implementation).
- Sibling ERP sync: [haengsung-exchange-rate-sync.md](haengsung-exchange-rate-sync.md).
- Legacy source: `WECMS DEV/WECMS/src/batch/jex/wabooks/erp/ERP_0100_003.java`.
