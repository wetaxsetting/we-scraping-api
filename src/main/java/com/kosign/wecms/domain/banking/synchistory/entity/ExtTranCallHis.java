package com.kosign.wecms.domain.banking.synchistory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * External-call audit log — one row per outbound bank-gateway call.
 * Mirrors legacy {@code wecmsvndev.EXT_TRAN_CALL_HIS}. Schema lives in
 * V16 (Sprint 1 Operation Status module created the table for a
 * sibling feature); this entity surfaces it for the Account Inquiry
 * sync-history popup (legacy {@code fnsh_0001_003_r001}).
 *
 * <p>Tenant scoping is service-side per the V16 header note:
 * {@code BIZ_KEY} is nullable on the legacy table, so the repository
 * adds {@code BIZ_KEY = :bizKey} explicitly in every query. Composite
 * PK {@code (SVC_ID, REF_DATE, SEQ_NO)} matches the legacy DDL.
 *
 * <p>Each refactor consumer reads rows scoped to its own
 * {@code SVC_ID}. Sync-history specifically asks for
 * {@code 'REAL_TIME_ONLINE_INQUIRY'} — the legacy
 * {@code fnsh_0001_003_r001} hardcoded value.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "EXT_TRAN_CALL_HIS")
@IdClass(ExtTranCallHisId.class)
public class ExtTranCallHis {

    @Id
    @Column(name = "SVC_ID", length = 50, nullable = false)
    private String svcId;

    @Id
    @Column(name = "REF_DATE", length = 8, nullable = false)
    private String refDate;

    @Id
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "SEQ_NO", nullable = false)
    private BigDecimal seqNo;

    @Column(name = "WEB_SVC_ID", length = 50, nullable = false)
    private String webSvcId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "RSLT_CD", length = 10)
    private String rsltCd;

    @Column(name = "ERR_CONT", length = 1000)
    private String errCont;

    @Column(name = "BIZ_KEY", length = 20)
    private String bizKey;

    @Column(name = "USER_ID", length = 100)
    private String userId;

    @Column(name = "BANK_CD", length = 20)
    private String bankCd;

    @Column(name = "ACCT_NO", length = 100)
    private String acctNo;

    @Column(name = "START_DATE", length = 8)
    private String startDate;

    @Column(name = "END_DATE", length = 8)
    private String endDate;

    @Column(name = "TAXCODE", length = 100)
    private String taxcode;
}
