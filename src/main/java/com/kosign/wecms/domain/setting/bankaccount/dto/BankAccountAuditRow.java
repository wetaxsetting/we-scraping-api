package com.kosign.wecms.domain.setting.bankaccount.dto;

import java.math.BigDecimal;

/**
 * One row in the Settings &gt; Bank Account audit-logs popup. Mirrors legacy
 * {@code comm_0183_004_r001} (action {@code api/comm/comm_0183_004_r001_act.jsp}
 * + {@code WSVC.comm_0183_004_r001.xml}) which reads {@code AUDIT_LOGS_INFM}
 * filtered by {@code (BIZ_KEY, WSVC_ID='comm_0183_001', REF_NO IN
 * ('ACCT_AUTH_INFO','ACCT_MSTR'), REF_NO1=bankCd, REF_NO2=acctNo, LANG_CD)}.
 *
 * <p>Field mapping (logical → wire):
 * <ul>
 *   <li>{@code dateTime}        → {@code regDtm} ({@code yyyyMMddHHmmss}; the
 *       frontend splits it into the date / time lines, mirroring legacy
 *       {@code comm_0183_004.formatTimestamp})</li>
 *   <li>{@code userName}        → {@code regNm} (legacy {@code REG_NM};
 *       refactor populates it at write time via {@code TenantContext})</li>
 *   <li>{@code activity}        → {@code auditLogsActv} (legacy
 *       {@code AUDIT_LOGS_ACTV})</li>
 *   <li>{@code activityDetails} → {@code auditLogsActvDtl} (legacy
 *       {@code AUDIT_LOGS_ACTV_DTL})</li>
 * </ul>
 *
 * <p>Shape mirrors {@code LoginCredentialAuditRow} so both audit popups share
 * the same frontend dialog + mapper pattern.
 */
public record BankAccountAuditRow(
        BigDecimal auditLogsSeqNo,
        String     auditLogsActv,
        String     auditLogsActvDtl,
        String     regId,
        String     regNm,
        String     regDtm
) { }
