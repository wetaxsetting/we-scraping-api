package com.kosign.wecms.domain.payment.match.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Match request body for the domestic payment-line banking-match
 * (legacy {@code comm_0197_001_c001} {@code else} path). The bank
 * transaction is identified by {@code acctSeqNo}; the short / full ids
 * are carried for API symmetry with the legacy contract — the service
 * writes the authoritative values from the loaded bank row.
 */
public record PaymentLineMatchRequest(
        @NotBlank @Size(max = 20)  String acctSeqNo,
        @NotBlank @Size(max = 20)  String bankTrnsShortId,
        @NotBlank @Size(max = 100) String bankTrnsFullId,
        @Size(max = 500)           String memo) {
}
