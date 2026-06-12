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
}
