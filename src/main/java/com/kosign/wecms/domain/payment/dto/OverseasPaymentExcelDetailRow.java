package com.kosign.wecms.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A single transaction row from the overseas-payment Excel upload.
 * Sent as part of {@link OverseasPaymentExcelUploadRequest#rows()}.
 *
 * <p>Beneficiary detail fields are resolved client-side via the contacts
 * endpoint during the preview step and forwarded here at submit time.
 */
public record OverseasPaymentExcelDetailRow(

        /** 1-based position within the batch. */
        @NotNull @Positive
        Integer trn_seq,

        @NotBlank @Size(max = 100)
        String bnfc_code,

        @NotBlank @Size(max = 10)
        String ccy_cd,

        @NotNull @PositiveOrZero
        BigDecimal trnf_amt,

        @NotBlank @Size(max = 10)
        String fee_payer_cd,

        @NotBlank @Size(max = 500)
        String memo,

        /** Optional — may be null or empty. */
        @Size(max = 100)
        String rprt_acct_cd,

        @Size(max = 200)
        String bnfc_nm,

        @Size(max = 200)
        String bnfc_bank_nm,

        @Size(max = 20)
        String bic_cd,

        @Size(max = 100)
        String bnfc_acct_no,

        @Size(max = 500)
        String bnfc_addr
) {}
