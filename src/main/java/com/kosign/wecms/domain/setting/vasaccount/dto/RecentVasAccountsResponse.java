package com.kosign.wecms.domain.setting.vasaccount.dto;

import java.util.List;

/**
 * RECENT quick-pick payload for the Transaction-History VAS-account
 * selector — the current user's most-recently-used VAS-account codes,
 * newest first (top-N). Server-side, derived from
 * {@code ACCT_TRNS_RCV_PAY_INFM.VAS_ACCT_CD} (PO decision 2026-07-24 —
 * no new table, no localStorage). Wire: {@code { "vas_acct_cds": [...] }}
 * via the global Jackson SNAKE_CASE strategy. The frontend intersects
 * these codes with the already-loaded VAS options, so only ids ship here.
 */
public record RecentVasAccountsResponse(List<String> vasAcctCds) {}
