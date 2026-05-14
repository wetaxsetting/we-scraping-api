package com.kosign.wecms.domain.setting.logincredentials.dto;

import java.math.BigDecimal;

/**
 * One row in the Login Credentials audit-logs popup (legacy
 * {@code comm_0218_005}). Reads from {@code AUDIT_LOGS_INFM} filtered
 * by the current tenant + the refactor slug {@code "login-credentials"}
 * + the target row's {@code REF_NO} + {@code REF_NO1}.
 */
public record LoginCredentialAuditRow(
        BigDecimal auditLogsSeqNo,
        String     auditLogsActv,
        String     auditLogsActvDtl,
        String     regId,
        String     regNm,
        String     regDtm
) { }
