package com.kosign.wecms.domain.payment.internaltransfer.dto;

import java.util.List;

public record TransferResultListResponse(
        long                    totalCount,
        int                     page,
        int                     size,
        List<CurrencyTotal>     currencyTotals,
        List<TransferResultRow> rows) { }
