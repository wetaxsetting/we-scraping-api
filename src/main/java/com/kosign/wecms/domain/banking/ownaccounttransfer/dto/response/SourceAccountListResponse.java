package com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response envelope for
 * {@code GET /api/v1/banking/own-account-transfer/source-accounts}.
 *
 * <p>{@code allowed_bank_cds} is shipped on every response so the
 * client's bank-popover always renders the canonical allow-list for
 * the tenant — independent of which rows happened to come back from
 * the DB query (e.g. a HAENGSUNG tenant with no VietinBank rows still
 * shows VietinBank as the only popover option).
 */
public record SourceAccountListResponse(
        List<SourceAccountRow> list,
        long                   totalCount,
        BigDecimal             totBal,
        BigDecimal             totAviBal,
        List<String>           allowedBankCds
) { }
