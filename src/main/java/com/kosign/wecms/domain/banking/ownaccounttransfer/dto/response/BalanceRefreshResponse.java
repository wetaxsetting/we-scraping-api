package com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response;

import java.math.BigDecimal;

/**
 * Response body for
 * {@code POST /api/v1/banking/own-account-transfer/balance-refresh}.
 * Mirrors legacy {@code WSVC.trns_0001_001_u001.xml:41-47} output set
 * — the source-account row's identity columns echo the request, the
 * patched balance reflects the post-update state (when the engine
 * reported success) or the pre-existing value (otherwise), and
 * {@code acct_bal_lst_inq_dtm} carries the {@code yyyyMMddHHmmss}
 * UTC timestamp the service stamped on the row.
 *
 * <p>Wire format is lowercase snake_case via the global Jackson naming
 * strategy.
 */
public record BalanceRefreshResponse(
        String     bankCd,
        String     acctNo,
        BigDecimal bal,
        String     ccyCd,
        String     acctBalLstInqDtm
) { }
