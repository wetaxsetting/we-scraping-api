package com.kosign.wecms.domain.payment.validation.dto;

import java.util.List;

/**
 * Response envelope for {@code GET /api/v1/payment/blocked-chars}. One
 * record per {@code FieldCd} that has an active rule for the
 * {@code (bank_cd, pymn_type)} pair. Empty list when no rules are
 * registered — frontend renders no info icons in that case.
 */
public record BlockedCharsRulesResponse(List<BlockedCharsRuleRow> rules) { }
