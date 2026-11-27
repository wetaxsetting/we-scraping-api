package com.kosign.wecms.domain.weinvoice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceRow;

import java.math.BigDecimal;
import java.util.List;

/** Test-only bridge to the package-private detail helpers of {@link InvoiceQueryService}. */
public final class InvoiceQueryServiceTestAccess {

    private InvoiceQueryServiceTestAccess() {
    }

    public static BigDecimal fee(JsonNode pay, JsonNode raw) {
        return InvoiceQueryService.fee(pay, raw);
    }

    public static BigDecimal effectiveTotal(BigDecimal total, List<InvoiceRow.Ref> by) {
        return InvoiceQueryService.effectiveTotal(total, by);
    }
}
