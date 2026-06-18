package com.kosign.wecms.domain.setting.bankaccount.dto;

import java.util.List;

/** Settings &gt; Bank Account audit-logs popup payload (latest-first). */
public record BankAccountAuditResponse(List<BankAccountAuditRow> items) { }
