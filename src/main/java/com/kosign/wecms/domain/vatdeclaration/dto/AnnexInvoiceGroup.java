package com.kosign.wecms.domain.vatdeclaration.dto;

import java.util.List;

/** Accordion group — annex lines sharing one {@code prodNm} (WEB_VAT_INV_002). */
public record AnnexInvoiceGroup(
        String prodNm,
        int rowCount,
        String totalSplyAmt,
        String totalReducedAmt,
        List<AnnexRow> rows
) {
}
