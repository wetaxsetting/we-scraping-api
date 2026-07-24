package com.kosign.wecms.domain.vatdeclaration.dto;

import java.math.BigDecimal;

/**
 * Spring Data projection for one annex line derived from an invoice
 * ({@code BUSA_DOC_MSTR} + {@code BUSA_DOC_DETL}). Internal to the service —
 * mapped to {@link AnnexRow} before it reaches the wire.
 */
public interface AnnexInvoiceProjection {

    /** Source invoice {@code BUSA_DOC_MSTR.DOC_ID}. */
    String getSrcDocId();

    /** {@code 'O'} output/sales, {@code 'I'} input/purchase. */
    String getIoDv();

    String getInvNo();

    String getInvSymbol();

    /** Issue date, {@code YYYYMMDD}. */
    String getInvDt();

    String getTrcoTaxCode();

    String getTrcoNm();

    String getProdNm();

    BigDecimal getSplyAmt();

    /** Display rate as stored: {@code '8%'} / {@code '10%'} / {@code 'KCT'} / {@code 'KKKNT'}. */
    String getVatRate();

    BigDecimal getVatAmt();

    /** 2-point reduction on 8% lines (Resolution 204/2025/QH15); 0 otherwise. */
    BigDecimal getReducedAmt();

    /** {@code 'Y'} once the invoice has been closed into a locked declaration. */
    String getVatDeclProcYn();

    /** Which declaration closed it ({@code VAT_DECL.SID}); null while unprocessed. */
    Long getVatDeclSid();
}
