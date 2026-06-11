package com.kosign.wecms.domain.setting.firmbankinghistory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

/**
 * Read-only projection of the Settings &gt; Firm Banking History screen.
 *
 * <p><b>Backed by a Hibernate {@link Subselect @Subselect}, NOT a DB table or
 * view.</b> The legacy JEX DB has no {@code v_firm_banking_history} object — the
 * Firm Banking History screen is served by the inline 5-leg {@code UNION ALL}
 * query in {@code IDO.TB_FIRM_TRAN_R002.xml}. An earlier refactor materialized
 * that query as a {@code V_FIRM_BANKING_HISTORY} view (archived V60), but the
 * view does not exist in legacy JEX and a refactor-only view is not provisioned.
 * Instead this entity carries the <em>same legacy union</em> as a
 * {@code @Subselect}, so Hibernate runs it against the legacy base tables +
 * functions (all present in JEX) and {@code ddl-auto: validate} never looks for
 * a {@code v_firm_banking_history} relation. Verbatim legacy parity with
 * {@code IDO.TB_FIRM_TRAN_R002} (cast-aligned, {@code SVC_NM} typo corrected,
 * per-leg {@code BIZ_KEY} predicate dropped so the repository's outer
 * {@code BIZ_KEY} filter pushes down into each leg).
 *
 * <p>{@link Immutable @Immutable} flags the entity as read-only — Hibernate
 * skips dirty-checking and never flushes state changes. {@link Synchronize
 * @Synchronize} lists the base tables so Hibernate auto-flushes pending writes
 * to them before querying this entity (avoids stale reads).
 *
 * <p>Five legs feed this view, discriminated by {@link #svcType}:
 * <ul>
 *   <li>{@code F} — TB_FIRM_TRAN (Korean firm-banking gateway)</li>
 *   <li>{@code S} — NAPAS_REMIT_TRNS_HSTR + EXT_TRAN_CALL_HIS scraping</li>
 *   <li>{@code G} — NXSIAS_MODULE_TRAN_LOG_HIS GDT e-Invoice inquiry</li>
 *   <li>{@code B} — NXSIAS_MODULE_TRAN_LOG_HIS Vietnam Business inquiry</li>
 * </ul>
 *
 * <p>Tenant scoping is the caller's responsibility — every repository
 * method must constrain on {@code BIZ_KEY} from
 * {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Immutable
@Synchronize({"TB_FIRM_TRAN", "NXSIAS_MODULE_TRAN_LOG_HIS", "NAPAS_REMIT_TRNS_HSTR", "EXT_TRAN_CALL_HIS"})
@Subselect("""
        SELECT
            kr_to_vn_time(TFT.TRAN_DT || TFT.TRAN_TM)                                AS TARN_DTM,
            'F'::VARCHAR(1)                                                          AS SVC_TYPE,
            TFT.TRAN_TP::VARCHAR(50)                                                 AS SVC_NM,
            TFT.TRAN_SEQ::VARCHAR(50)                                                AS SEQ_NO,
            (COALESCE(wb_get_napas_bank_nm(TFT.OUT_BANK_CD), '') || ' - ' ||
             TFT.OUT_BANK_CD)::VARCHAR(200)                                          AS DEP_BANK_CD,
            TFT.RS_CD::VARCHAR(20)                                                   AS RSLT_CD,
            TFT.RS_CD::VARCHAR(2000)                                                 AS RSLT_MSG,
            TFT.TRAN_TP::VARCHAR(5000)                                               AS JOB,
            NULL::VARCHAR(1000)                                                      AS REQ_DATA,
            TFT.BIZ_KEY                                                              AS BIZ_KEY,
            TFT.VAN_ORG_CD                                                           AS VAN_ORG_CD
        FROM TB_FIRM_TRAN TFT
        UNION ALL
        SELECT
            NMTLH.REG_DTM                                                            AS TARN_DTM,
            'G'::VARCHAR(1)                                                          AS SVC_TYPE,
            (CASE WHEN NMTLH.NXISAS_TYPE = 'E-INVOICE_SALES_INQUIRY'
                  THEN 'Sale E-Invoice Inquiry'
                  ELSE 'Expense E-Invoice Inquiry'
             END)::VARCHAR(50)                                                       AS SVC_NM,
            NMTLH.UUID::VARCHAR(50)                                                  AS SEQ_NO,
            NMTLH.NXISAS_MODULE::VARCHAR(200)                                        AS DEP_BANK_CD,
            NMTLH.NXISAS_ERR_CODE::VARCHAR(20)                                       AS RSLT_CD,
            NMTLH.NXISAS_ERR_CONT::VARCHAR(2000)                                     AS RSLT_MSG,
            NMTLH.NXISAS_JOB::VARCHAR(5000)                                          AS JOB,
            NMTLH.REQ_DATA                                                           AS REQ_DATA,
            NMTLH.BIZ_KEY                                                            AS BIZ_KEY,
            NULL::VARCHAR(20)                                                        AS VAN_ORG_CD
        FROM NXSIAS_MODULE_TRAN_LOG_HIS NMTLH
        WHERE NMTLH.NXISAS_TYPE IN ('E-INVOICE_SALES_INQUIRY', 'E-INVOICE_EXPENSE_INQUIRY')
        UNION ALL
        SELECT
            NMTLH.REG_DTM                                                            AS TARN_DTM,
            'B'::VARCHAR(1)                                                          AS SVC_TYPE,
            'Vietnam Business Inquiry'::VARCHAR(50)                                  AS SVC_NM,
            NMTLH.UUID::VARCHAR(50)                                                  AS SEQ_NO,
            NMTLH.NXISAS_MODULE::VARCHAR(200)                                        AS DEP_BANK_CD,
            NMTLH.NXISAS_ERR_CODE::VARCHAR(20)                                       AS RSLT_CD,
            NMTLH.NXISAS_ERR_CONT::VARCHAR(2000)                                     AS RSLT_MSG,
            NMTLH.NXISAS_JOB::VARCHAR(5000)                                          AS JOB,
            NMTLH.REQ_DATA                                                           AS REQ_DATA,
            NMTLH.BIZ_KEY                                                            AS BIZ_KEY,
            NULL::VARCHAR(20)                                                        AS VAN_ORG_CD
        FROM NXSIAS_MODULE_TRAN_LOG_HIS NMTLH
        WHERE NMTLH.NXISAS_JOB IN ('BUSSINESS_REGISTRATION_DETAIL', 'BUSSINESS_REGISTRATION_LIST')
        UNION ALL
        SELECT
            NRTH.REG_DTM                                                             AS TARN_DTM,
            'S'::VARCHAR(1)                                                          AS SVC_TYPE,
            'Funds Transfer (Scraping)'::VARCHAR(50)                                 AS SVC_NM,
            NRTH.SEQ_NO::VARCHAR(50)                                                 AS SEQ_NO,
            (COALESCE(wb_get_bank_nm(NRTH.OUT_BANK_CODE, 's'), '') || ' - ' ||
             NRTH.OUT_BANK_CODE)::VARCHAR(200)                                       AS DEP_BANK_CD,
            NRTH.TRNS_STTS::VARCHAR(20)                                              AS RSLT_CD,
            NRTH.TRNS_MSG::VARCHAR(2000)                                             AS RSLT_MSG,
            'TRANSFER'::VARCHAR(5000)                                                AS JOB,
            NULL::VARCHAR(1000)                                                      AS REQ_DATA,
            NRTH.BIZ_KEY                                                             AS BIZ_KEY,
            NULL::VARCHAR(20)                                                        AS VAN_ORG_CD
        FROM NAPAS_REMIT_TRNS_HSTR NRTH
        UNION ALL
        SELECT
            ETH.REG_DTM                                                              AS TARN_DTM,
            'S'::VARCHAR(1)                                                          AS SVC_TYPE,
            (CASE
                WHEN ETH.SVC_ID = 'BALANCE_INQUIRY'          THEN 'Scraping Balance'
                WHEN ETH.SVC_ID = 'REAL_TIME_ONLINE_INQUIRY' THEN 'Scraping Transaction'
                WHEN ETH.SVC_ID = 'CARD_INQUIRY'             THEN 'Scraping Card Transaction'
             END)::VARCHAR(50)                                                       AS SVC_NM,
            ETH.SEQ_NO::VARCHAR(50)                                                  AS SEQ_NO,
            (COALESCE(wb_get_bank_nm(ETH.BANK_CD, 's'), '') || ' - ' ||
             ETH.BANK_CD)::VARCHAR(200)                                              AS DEP_BANK_CD,
            ETH.RSLT_CD::VARCHAR(20)                                                 AS RSLT_CD,
            ETH.ERR_CONT::VARCHAR(2000)                                              AS RSLT_MSG,
            (CASE
                WHEN ETH.SVC_ID = 'BALANCE_INQUIRY'          THEN 'BALANCE'
                WHEN ETH.SVC_ID = 'REAL_TIME_ONLINE_INQUIRY' THEN 'TRANSACTION'
                WHEN ETH.SVC_ID = 'CARD_INQUIRY'             THEN 'CARD_USING'
             END)::VARCHAR(5000)                                                     AS JOB,
            NULL::VARCHAR(1000)                                                      AS REQ_DATA,
            ETH.BIZ_KEY                                                              AS BIZ_KEY,
            NULL::VARCHAR(20)                                                        AS VAN_ORG_CD
        FROM EXT_TRAN_CALL_HIS ETH
        WHERE ETH.SVC_ID IN ('BALANCE_INQUIRY', 'REAL_TIME_ONLINE_INQUIRY', 'CARD_INQUIRY')
        """)
@IdClass(FirmBankingHistoryViewId.class)
public class FirmBankingHistoryView {

    /** Tenant key. Always part of the WHERE clause; never exposed on the wire. */
    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    /** VN-localized timestamp ({@code YYYYMMDDHHMMSS}). For the {@code F}
     *  leg this is {@code kr_to_vn_time(TRAN_DT || TRAN_TM)}; for every
     *  other leg it equals the source row's {@code REG_DTM}. Used as
     *  the date-range filter target via {@code LEFT(TARN_DTM, 8)
     *  BETWEEN ? AND ?}. */
    @Id
    @Column(name = "TARN_DTM", length = 14)
    private String tarnDtm;

    /** Source-leg discriminator: {@code F}/{@code S}/{@code G}/{@code B}. */
    @Id
    @Column(name = "SVC_TYPE", length = 1)
    private String svcType;

    /** Source-row sequence identifier (per-leg semantics differ:
     *  {@code TRAN_SEQ} for F, {@code SEQ_NO::varchar} for NAPAS/EXT_TRAN,
     *  {@code UUID} for NXSIAS). Free-form keyword search hits this
     *  column case-insensitively. */
    @Id
    @Column(name = "SEQ_NO", length = 50)
    private String seqNo;

    @Column(name = "SVC_NM", length = 50)
    private String svcNm;

    @Column(name = "DEP_BANK_CD", length = 200)
    private String depBankCd;

    @Column(name = "RSLT_CD", length = 20)
    private String rsltCd;

    @Column(name = "RSLT_MSG", length = 2000)
    private String rsltMsg;

    @Column(name = "JOB", length = 5000)
    private String job;

    @Column(name = "REQ_DATA", length = 1000)
    private String reqData;

    @Column(name = "VAN_ORG_CD", length = 20)
    private String vanOrgCd;
}
