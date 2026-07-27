package com.kosign.wecms.domain.payment.dispatch.entity;

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
 * Firm Banking gateway audit log — minimal projection for the
 * Phase 2 Result detail surface.
 *
 * <p>Source table {@code TB_FIRM_TRAN} (V104) carries 30+ columns. This
 * entity maps the original Result-detail projection ({@code RS_CD} /
 * {@code RS_MSG}) plus the columns the <b>processing-result check</b>
 * (refactor of legacy {@code PROCESSING_RESULT_CHECK_BATCH}) needs to
 * select aged real-time (202) transfers and reflect their confirmed
 * outcome: {@code TRAN_TP}, {@code TRAN_TM}, {@code PROC_STAT},
 * {@code VAN_ORG_CD}, {@code OUT_BANK_CD}, {@code BANK_PRTN_ID},
 * {@code WDRL_ACT_NO}, {@code ORG_RESP_CD}, {@code ORG_RESP_MSG},
 * {@code RS_DTTM}. Remaining columns stay unmapped (Hibernate
 * {@code validate} ignores DB columns not declared on the entity);
 * adding mappings is additive and needs no migration (V104 already
 * created every column).
 *
 * <p>The PK on the local schema is {@code (TRAN_DT, BIZ_KEY, TRAN_SEQ)}
 * — three columns, matching the lookup the
 * {@link com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver}
 * needs (no OUT_BANK_CD in the join chain).
 */
@Entity
@Table(name = "TB_FIRM_TRAN")
@IdClass(TbFirmTranId.class)
@Getter
@Setter
@NoArgsConstructor
public class TbFirmTran {

    @Id
    @Column(name = "TRAN_DT", length = 8, nullable = false)
    private String tranDt;

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "TRAN_SEQ", length = 10, nullable = false)
    private String tranSeq;

    /** Bank response code (e.g. {@code "0000"} = success, others = error
     *  reason). 4-char varchar on DEV. Populated by the bank-side daemon
     *  after the API call resolves. */
    @Column(name = "RS_CD", length = 4)
    private String rsCd;

    /** Human-readable bank response message. Nullable on DEV — the
     *  File path never populates this column. */
    @Column(name = "RS_MSG", length = 200)
    private String rsMsg;

    // ── Processing-result-check columns (real-time 202 → 203 reflect) ──

    /** Transaction type — {@code "201".."204"} (real-time legs). {@code bpchar(3)}. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "TRAN_TP", length = 3)
    private String tranTp;

    /** Transaction time {@code HHmmss}. {@code bpchar(6)}. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "TRAN_TM", length = 6)
    private String tranTm;

    /** Processing status — {@code 0}=success / {@code 3}=fail / {@code 6}=in-progress. {@code bpchar(1)}. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "PROC_STAT", length = 1)
    private String procStat;

    @Column(name = "VAN_ORG_CD", length = 20)
    private String vanOrgCd;

    @Column(name = "OUT_BANK_CD", length = 15)
    private String outBankCd;

    @Column(name = "BANK_PRTN_ID", length = 20)
    private String bankPrtnId;

    @Column(name = "WDRL_ACT_NO", length = 100)
    private String wdrlActNo;

    /** True outcome of the original transfer returned by the 203 inquiry. */
    @Column(name = "ORG_RESP_CD", length = 4)
    private String orgRespCd;

    @Column(name = "ORG_RESP_MSG", length = 150)
    private String orgRespMsg;

    /** Result-received timestamp {@code yyyyMMddHHmmss}. */
    @Column(name = "RS_DTTM", length = 14)
    private String rsDttm;

    // ── 203 processing-result-inquiry audit row (B3 / audit M-4 — legacy TB_FIRM_TRAN_C001/_U003) ──
    // The scheduled result-check inserts one TRAN_TP='203' ledger row per inquiry that points
    // back at the original 202 via ORG_TRAN_DT/ORG_TRAN_SEQ and, on success, carries the confirmed
    // 203 detail (ORG_PROC_TM + the withdrawal/target remarks). All columns already exist in V104.

    /** Original transaction date the 203 inquiry confirms (legacy {@code ORG_TRAN_DT}). {@code varchar(8)}. */
    @Column(name = "ORG_TRAN_DT", length = 8)
    private String orgTranDt;

    /** Original transaction sequence the 203 inquiry confirms (legacy {@code ORG_TRAN_SEQ}). {@code varchar(50)}. */
    @Column(name = "ORG_TRAN_SEQ", length = 50)
    private String orgTranSeq;

    /** Original processing time returned by the 203 inquiry (legacy {@code ORG_PROC_TM}). {@code varchar(6)}. */
    @Column(name = "ORG_PROC_TM", length = 6)
    private String orgProcTm;

    /** Withdrawal-side remark from the 203 inquiry (legacy {@code WDRL_ACCT_RMRK}). {@code varchar(150)}. */
    @Column(name = "WDRL_ACCT_RMRK", length = 150)
    private String wdrlAcctRmrk;

    /** Target-side remark from the 203 inquiry (legacy {@code TAR_ACCT_RMRK}). {@code varchar(150)}. */
    @Column(name = "TAR_ACCT_RMRK", length = 150)
    private String tarAcctRmrk;

    // ── 202 real-time transfer write seam (C2 — TB_FIRM_TRAN_C001 insert / _U004 update) ──
    // All columns already exist in V104; the mappings below are additive (no migration).

    @Column(name = "TAR_BANK_CD", length = 15)
    private String tarBankCd;

    /** {@code A}=account / {@code C}=card. {@code varchar(1)} on DEV (not bpchar). */
    @Column(name = "FINC_MDA_DIV", length = 1)
    private String fincMdaDiv;

    @Column(name = "FINC_MDA_NO", length = 100)
    private String fincMdaNo;

    @Column(name = "FINC_MDA_OWNER_NM", length = 150)
    private String fincMdaOwnerNm;

    /** Other-bank network code — {@code INTER} / {@code CITAD} / {@code NAPAS}. {@code bpchar(5)}. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ANT_DIV_CD", length = 5)
    private String antDivCd;

    /** Transfer currency. {@code bpchar(3)}. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "TRSC_CCY_CD", length = 3)
    private String trscCcyCd;

    @Column(name = "TRAN_AMT", precision = 23, scale = 2)
    private BigDecimal tranAmt;

    @Column(name = "TRAN_USAG", length = 200)
    private String tranUsag;

    /** Request timestamp {@code yyyyMMddHHmmss}. */
    @Column(name = "RQ_DTTM", length = 14)
    private String rqDttm;

    /** Post-transfer balance (integer.decimal combined). Written by the 202 result (U004). */
    @Column(name = "BAL_AMT", precision = 23, scale = 2)
    private BigDecimal balAmt;

    /** Post-transfer balance sign — {@code 0}=+ / {@code 1}=-. {@code bpchar(1)}. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "BAL_AMT_SIGN", length = 1)
    private String balAmtSign;

    // ── 204 FX-inquiry result columns (written by the TRAN_TP='204' row via U005) ──
    // Mirror the same four columns on TB_PAY_TARGET. Populated only on the Woori /
    // non-VND exchange-transfer pre-step (legacy NAPAS_REAL_TIME_BATCH U005).

    /**
     * Withdrawal currency (legacy {@code WITHD_CCY_CD}, {@code varchar(3)}) — written on the 202
     * {@code TB_FIRM_TRAN} row (audit D1/M-8; legacy C001 sets it on every transfer, VND included).
     */
    @Column(name = "WITHD_CCY_CD", length = 3)
    private String withdCcyCd;

    /** Deposit currency from the 204 FX inquiry ({@code VND}). {@code varchar(3)}. */
    @Column(name = "DEP_CCY_CD", length = 3)
    private String depCcyCd;

    /** Applied exchange rate from the 204 FX inquiry. */
    @Column(name = "APL_EXC_RATE", precision = 23, scale = 2)
    private BigDecimal aplExcRate;

    /** Withdrawal amount from the 204 FX inquiry. */
    @Column(name = "WITHD_AMT", precision = 23, scale = 2)
    private BigDecimal withdAmt;

    /** Deposit amount from the 204 FX inquiry. */
    @Column(name = "DEP_AMT", precision = 23, scale = 2)
    private BigDecimal depAmt;
}
