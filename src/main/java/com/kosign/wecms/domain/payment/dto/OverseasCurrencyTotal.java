package com.kosign.wecms.domain.payment.dto;

import java.math.BigDecimal;

/**
 * Per-currency totals for the overseas scheduled-payment list.
 * Mirrors WSVC.pymn_0007_001_r001.xml → TOT_REC fields.
 * Wire names auto-derived via global Jackson SNAKE_CASE strategy.
 */
public record OverseasCurrencyTotal(
        String ccyCd,
        BigDecimal totlAmt,
        Integer totCntCcyCd,
        Integer totTrnsCnt
) {}
