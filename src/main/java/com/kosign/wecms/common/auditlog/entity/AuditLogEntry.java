package com.kosign.wecms.common.auditlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Shared audit-log row. Maps to {@code AUDIT_LOGS_INFM} (V63).
 * Mirrors legacy JEX {@code wabooks.entity.AuditLogsInfm} on the
 * read side; the writer (shared {@code AuditLogService}) is owned
 * by the future audit-log feature MR (Phase 1 P-1).
 *
 * <p>Each business event (Create / Update on a refactored feature)
 * fans out to one row per supported locale (EN / KO / VI / KM) so the
 * detail dialog can render the activity and detail strings in the
 * caller's language without server-side re-translation. PK includes
 * {@code lang_cd} to make the locale fan-out unique.
 *
 * <p>The legacy detail-audit query at
 * {@code comm_0220_003_r001_act.jsp:74-86} filters by
 * {@code (BIZ_KEY, REF_NO, REF_NO1, REF_NO2, WSVC_ID, LANG_CD)};
 * V63 adds an index on the same tuple to support that lookup.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "AUDIT_LOGS_INFM")
@IdClass(AuditLogEntryId.class)
public class AuditLogEntry {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "AUDIT_LOGS_SEQ_NO", nullable = false)
    private BigDecimal auditLogsSeqNo;

    /** Per-feature stable identifier — refactor route slug
     *  (e.g. {@code 'firm-banking-settings'}). The refactor uses the
     *  slug for new writes (OQ-6 override 2026-05-09). Legacy JEX
     *  rows in this column carry the JEX page id verbatim
     *  ({@code 'comm_0220_001'} for firm-banking-settings) — those
     *  rows are immutable history. See workspace CLAUDE.md
     *  §"Refactor identifier naming". */
    @Id
    @Column(name = "WSVC_ID", length = 50, nullable = false)
    private String wsvcId;

    /** {@code EN}/{@code KO}/{@code VI}/{@code KM}. */
    @Id
    @Column(name = "LANG_CD", length = 3, nullable = false)
    private String langCd;

    /** Logical entity name (e.g. {@code 'TB_FIRM_CONF'}). */
    @Column(name = "REF_NO", length = 50, nullable = false)
    private String refNo;

    /** First key column of the referenced row
     *  (e.g. {@code OUT_BANK_CD} for firm-banking-settings). */
    @Column(name = "REF_NO1", length = 50, nullable = false)
    private String refNo1;

    /** Second key column
     *  (e.g. {@code WDRL_ACT_NO} for firm-banking-settings). */
    @Column(name = "REF_NO2", length = 50)
    private String refNo2;

    @Column(name = "REF_NO3", length = 50)
    private String refNo3;

    @Column(name = "REF_NO4", length = 50)
    private String refNo4;

    @Column(name = "REF_NO5", length = 50)
    private String refNo5;

    /** Activity category (e.g. {@code 'Firm Banking Details'}). */
    @Column(name = "AUDIT_LOGS_ACTV", length = 50)
    private String auditLogsActv;

    /** Activity detail (e.g. {@code 'Register new firmbanking'}). */
    @Column(name = "AUDIT_LOGS_ACTV_DTL", nullable = false, columnDefinition = "TEXT")
    private String auditLogsActvDtl;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_NM", length = 100, nullable = false)
    private String regNm;

    /** {@code yyyyMMddHHmmss} string. */
    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;
}
