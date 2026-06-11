package com.kosign.wecms.domain.banking.dto;

import java.util.List;

public record TransactionListResponse(
        List<TransactionRow> rows,
        int totalCount,
        String depositTotal,
        String withdrawTotal
) { }
