package com.kosign.wecms.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for {@code POST /api/v1/payment/overseas-payment/excel-upload}.
 *
 * <p>The frontend sends the metadata gathered from the three Excel-upload
 * dialog steps: the parsed row totals (Step 2) and the selected withdrawal
 * account (Step 3). The actual file bytes are NOT re-sent — the frontend
 * already previewed and validated them client-side; the backend saves only
 * the summary record into {@code OVRS_PYMN_SUMR}.
 */
public record OverseasPaymentExcelUploadRequest(

        /** Original filename shown in the UI file chip (Step 2). */
        @NotBlank @Size(max = 300)
        String file_nm,

        /** ISO currency code of the batch, e.g. {@code "USD"}.
         *  Use {@code "MULTI"} when the Excel contains multiple currencies. */
        @NotBlank @Size(max = 10)
        String ccy_cd,

        /** Sum of all parsed transfer amounts. */
        @NotNull @Positive
        BigDecimal total_tran_amt,

        /** Number of data rows (transactions) in the batch. */
        @NotNull @Positive
        Integer tot_trns_cnt,

        /** Withdrawal bank code (e.g. {@code "010"}). */
        @NotBlank @Size(max = 20)
        String wdrl_bank_cd,

        /** Withdrawal bank display name (e.g. {@code "Woori VN"}). */
        @NotBlank @Size(max = 100)
        String wdrl_bank_nm,

        /** Withdrawal account number. */
        @NotBlank @Size(max = 100)
        String wdrl_acct_no
) {}
