package com.kosign.wecms.common.auditlog.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Composite identity for {@link AuditLogEntry}. Mirrors the legacy
 * {@code AUDIT_LOGS_INFM} PK
 * {@code (BIZ_KEY, AUDIT_LOGS_SEQ_NO, WSVC_ID, LANG_CD)}.
 *
 * <p>Each business event writes one row per locale (EN/KO/VI/KM
 * when the writer feature lands) so the same {@code seq_no} appears
 * once per locale; the lang_cd component is what makes the PK unique
 * across the locale fan-out.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AuditLogEntryId implements Serializable {
    private String     bizKey;
    private BigDecimal auditLogsSeqNo;
    private String     wsvcId;
    private String     langCd;
}
