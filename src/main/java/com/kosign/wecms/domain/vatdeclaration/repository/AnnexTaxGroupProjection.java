package com.kosign.wecms.domain.vatdeclaration.repository;

import java.math.BigDecimal;

/**
 * One VAT-rate subtotal of the annex — the group total shown under each
 * 01-1/GTGT section (KKKNT / 0% / 5% / 8% / 10% / KCT). Aggregated over the
 * same {@code BUSA_DOC_DETL ⋈ BUSA_DOC_MSTR} filter the row list uses, so the
 * footer and the rows can never disagree.
 */
public interface AnnexTaxGroupProjection {
    String getGroupCd();            // raw EINV_TAXRATE, e.g. "8%" / "KCT" / "KKKNT"
    long getRowCount();
    BigDecimal getTotalSplyAmt();   // Σ taxable value (excl. VAT)
    BigDecimal getTotalVatAmt();    // Σ VAT (floor per line)
    BigDecimal getTotalReducedAmt(); // Σ 2-point reduction on 8% lines (Resolution 204/2025)
}
