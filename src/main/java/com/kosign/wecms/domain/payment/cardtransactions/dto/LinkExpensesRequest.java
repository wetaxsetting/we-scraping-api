package com.kosign.wecms.domain.payment.cardtransactions.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payment &gt; Card Transactions &mdash; Link Expenses request body.
 * Mirrors legacy {@code card_0001_002_c001_act.jsp} input shape:
 *
 * <pre>{@code
 *   {
 *     "card_seq_no": "1234",
 *     "items": [
 *       { "doc_id": "1C25MGA-4510332", "dedu_amt": "44000" },
 *       { "doc_id": "1C25MGA-4510328", "dedu_amt": "152000" }
 *     ]
 *   }
 * }</pre>
 *
 * <p>Server validates that {@code SUM(dedu_amt) == CARD_TRNS_HSTR.TRSC_AMT}
 * exactly &mdash; the sum of every allocated amount must match the
 * card row's transacted amount (legacy {@code WBE0001}). Refactor
 * code: {@code WBE_LINK_AMOUNT_MISMATCH}.
 */
public record LinkExpensesRequest(
        @NotBlank
        String cardSeqNo,
        @NotEmpty
        @Valid
        List<Item> items) {

    public record Item(
            @NotBlank
            String docId,
            @NotNull
            @PositiveOrZero
            BigDecimal deduAmt) {
    }
}
