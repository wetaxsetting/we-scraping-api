package com.kosign.wecms.domain.setting.bankaccount.dto;

import java.util.List;

/** Wrapper for the Settings &gt; Bank Account list response. */
public record BankAccountListResponse(List<BankAccountRow> items) { }
