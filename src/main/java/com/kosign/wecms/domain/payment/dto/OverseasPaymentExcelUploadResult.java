package com.kosign.wecms.domain.payment.dto;

/**
 * Response body for {@code POST /api/v1/payment/overseas-payment/excel-upload}.
 * Returns the allocated batch key so the frontend can navigate to the
 * detail page or refresh the scheduled-payment list.
 */
public record OverseasPaymentExcelUploadResult(
        /** Newly allocated {@code OVRS_PYMN_SUMR.OVRS_PYMN_NO}. */
        String ovrs_pymn_no
) {}
