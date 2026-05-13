package com.kosign.wecms.domain.payment.internaltransfer.dto;

/**
 * Response body for {@code POST /api/v1/payment/internal-transfer}.
 * Wire field: {@code scdul_pymn_no} (the generated PK).
 */
public record CreateScheduledTransferResponse(
        String scdulPymnNo) { }
