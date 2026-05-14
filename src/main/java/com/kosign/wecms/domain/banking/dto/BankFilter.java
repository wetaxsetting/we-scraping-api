package com.kosign.wecms.domain.banking.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * One bank/account pair in the cash-flow report's bank scope filter.
 * Mirrors the legacy nested {@code BANK_FILT} REC ({@code BANK_CD} +
 * {@code ACCT_NO}) from {@code WSVC.cash_0001_001_r001.xml:32-72}.
 */
public record BankFilter(
        @NotBlank String bankCd,
        @NotBlank String accountNo
) { }
