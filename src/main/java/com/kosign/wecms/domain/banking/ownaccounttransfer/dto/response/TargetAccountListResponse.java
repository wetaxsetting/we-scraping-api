package com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response;

import java.util.List;

/**
 * Response envelope for
 * {@code GET /api/v1/banking/own-account-transfer/target-accounts}.
 * Mirrors the source-account list shape so the frontend's mapper layer
 * can stay symmetric.
 *
 * <p>Wire shape is lowercase snake_case via the global Jackson naming
 * strategy.
 */
public record TargetAccountListResponse(
        List<TargetAccountRow> list,
        long                   totalCount
) { }
