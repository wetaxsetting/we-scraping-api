package com.kosign.wecms.domain.payment.internaltransfer.entity;

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
 * Payment &gt; Internal Transfer — batch master. Mirrors legacy
 * {@code WECMS DEV/WECMS/src/wabooks/entity/ScdulPymnSumr.java} +
 * the V85 column-for-column schema.
 *
 * <p>One row per uploaded file equals one "Scheduled Transfer". The
 * Internal Transfer screens filter on
 * {@code SCDUL_PYMN_TYPE_CD = '03'} — other Payment-module variants
 * (Vendor / Expense / Payroll / Card / Overseas) share the same table
 * with different discriminator values.
 *
 * <p>Sprint state machine — captured by two columns:
 * <ul>
 *   <li>{@code APPR_SEQ_NO} — null until the user requests approval;
 *       then carries the {@code EXPT_RESOL_MSTR} sequence number. Used
 *       by the delete guard at
 *       {@code PymnHelper#pymn_0009_001_d001} parity logic.</li>
 *   <li>{@code TRNF_YN} — {@code 'N'} draft / pre-dispatch, {@code 'Y'}
 *       after the Transfer (dispatch) action runs.</li>
 * </ul>
 *
 * <p>The audit columns ({@code REG_ID} / {@code REG_DTM} / {@code MOD_ID} /
 * {@code MOD_DTM}) follow the legacy DEV {@code VARCHAR(14)} shape rather
 * than the project-wide {@link com.kosign.wecms.common.entity.BaseEntity}
 * pattern (which uses {@code OffsetDateTime}). Reason: this entity needs to
 * write {@code yyyyMMddHHmmss} strings to match the legacy IDO query
 * predicates that the Internal Transfer detail view IDOs use. The same
 * approach was taken by {@link com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr}.
 *
 * <p>Tenant scoping: every repository method MUST constrain on
 * {@code BIZ_KEY} from
 * {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "SCDUL_PYMN_SUMR")
@IdClass(ScdulPymnSumrId.class)
public class ScdulPymnSumr {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "SCDUL_PYMN_NO", length = 20, nullable = false)
    private String scdulPymnNo;

    /** Payment-type discriminator. Always {@code "03"} for Internal Transfer
     *  per legacy {@code pymn_0009_001_r001_act.jsp:61}. Other Payment
     *  modules use {@code "01"} (Vendor) / {@code "02"} (Expense) / etc. */
    @Column(name = "SCDUL_PYMN_TYPE_CD", length = 2, nullable = false)
    private String scdulPymnTypeCd;

    @Column(name = "OUT_BANK_CD", length = 20, nullable = false)
    private String outBankCd;

    @Column(name = "WDRL_ACT_NO", length = 100, nullable = false)
    private String wdrlActNo;

    @Column(name = "CCY_CD", length = 3, nullable = false)
    private String ccyCd;

    @Column(name = "TITL", length = 100, nullable = false)
    private String titl;

    @Column(name = "TOTAL_TRAN_AMT", precision = 20, scale = 6, nullable = false)
    private BigDecimal totalTranAmt;

    @Column(name = "TOT_TRNS_CNT", precision = 15, scale = 0, nullable = false)
    private BigDecimal totTrnsCnt;

    /** Linked approval-resolution sequence number (FK to EXPT_RESOL_MSTR).
     *  Null while the batch is still a draft. Once set, the batch is
     *  immutable until dispatch — see delete guard at
     *  {@code WBE2062}. */
    @Column(name = "APPR_SEQ_NO", length = 15)
    private String apprSeqNo;

    /** Per-batch approval outcome — stamped by the {@code POST
     *  /{id}/approval-action} endpoint when an approver acts on the
     *  batch. Legacy {@code APPRVR_STTS} vocabulary applied at the
     *  master level:
     *
     *  <ul>
     *    <li>{@code null} — initial state after Request Approval was
     *        submitted but before any approver has acted.</li>
     *    <li>{@code "P"} — pending (current approver awaiting action).</li>
     *    <li>{@code "I"} — in progress (multi-approver chain mid-flight).</li>
     *    <li>{@code "C"} — approved (final approver accepted).</li>
     *    <li>{@code "X"} — rejected (any approver rejected).</li>
     *  </ul>
     *
     *  Phase A: only {@code "C"} / {@code "X"} are written (single-step
     *  approve / reject mirroring legacy {@code comm_0193_001}). The
     *  per-approver chain that produces {@code "P"} / {@code "I"} arrives
     *  with the EXPT_RESOL_APPRVR_INFM port.
     */
    @Column(name = "APPR_STTS", length = 1)
    private String apprStts;

    /** {@code 'N'} (draft / pre-dispatch) or {@code 'Y'} (dispatched). */
    @Column(name = "TRNF_YN", length = 1, nullable = false)
    private String trnfYn;

    @Column(name = "TRNF_DTM", length = 14)
    private String trnfDtm;

    @Column(name = "PAY_TRAN_DT", length = 8)
    private String payTranDt;

    @Column(name = "PAY_FILE_SEQ_NO", length = 20)
    private String payFileSeqNo;

    @Column(name = "ERP_FILE_DATE", length = 8)
    private String erpFileDate;

    @Column(name = "ERP_FILE_NUM", length = 50)
    private String erpFileNum;

    @Column(name = "ERP_PROC_CMPL_YN", length = 1, nullable = false)
    private String erpProcCmplYn;

    @Column(name = "WDRL_CCY_CD", length = 3)
    private String wdrlCcyCd;

    @Column(name = "ACCT_SEQ_NO", length = 20)
    private String acctSeqNo;

    @Column(name = "BANK_TRNS_SHORT_ID", length = 20)
    private String bankTrnsShortId;

    @Column(name = "BANK_TRNS_FULL_ID", length = 100)
    private String bankTrnsFullId;

    @Column(name = "LINK_MATCH_YN", length = 1)
    private String linkMatchYn;

    /** Uploaded Excel file size in bytes. Flattened from legacy
     *  {@code FileInfo} bean. */
    @Column(name = "FILE_SIZE", precision = 20, scale = 0)
    private BigDecimal fileSize;

    /** Original Excel file name supplied by the user. */
    @Column(name = "ORG_FILE_NM", length = 500)
    private String orgFileNm;

    /** Server-rewritten file name on the configured storage root. */
    @Column(name = "SAVE_FILE_NM", length = 500)
    private String saveFileNm;

    /** Absolute path on disk (under
     *  {@code wecms.internal-transfer.storage-root}). */
    @Column(name = "FULL_PATH", length = 1000)
    private String fullPath;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    /** {@code yyyyMMddHHmmss} format — server time at create (UTC).
     *  Legacy {@code VARCHAR(14)} shape. */
    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;
}
