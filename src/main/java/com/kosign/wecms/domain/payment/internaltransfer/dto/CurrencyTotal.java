package com.kosign.wecms.domain.payment.internaltransfer.dto;

import java.math.BigDecimal;

/**
 * Per-currency total surfaced in the list response. Mirrors legacy
 * {@code TOT_REC} sub-record (see
 * {@code WSVC.pymn_0009_001_r001.xml:rule_Sub_0}).
 */
public record CurrencyTotal(
        BigDecimal totalAmount,
        BigDecimal totalCount,
        String     ccyCd,
        BigDecimal totalTransactionCount) { }
