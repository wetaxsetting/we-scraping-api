package com.kosign.wecms.domain.payment.dispatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bridge row linking a payment-module detail line
 * ({@code SCDUL_PYMN_SUMR_DETL.(BIZ_KEY, SCDUL_PYMN_NO, SCDUL_PYMN_DETL_NO)})
 * to its API-path dispatch row ({@link TbPayTarget}
 * via {@code (TRAN_DT, TRAN_SEQ)}).
 *
 * <p>Written by the dispatch service when an API/VND batch executes —
 * the row exists for the payment detail from creation time with
 * {@code TRAN_DT} / {@code TRAN_SEQ} NULL, and the dispatch step UPDATEs
 * those two columns with the allocated values.  After that, the per-line
 * mirror columns {@code SCDUL_PYMN_SUMR_DETL.PAY_TRAN_DT} /
 * {@code .PAY_TRAN_SEQ} are stamped on the detail row itself so the UI
 * read path can resolve the dispatch row without going through this
 * conn table.
 *
 * <p>The file-path dispatch has no equivalent conn table — file-path
 * linkage is encoded directly on {@code SCDUL_PYMN_SUMR.PAY_FILE_SEQ_NO} /
 * {@code SCDUL_PYMN_SUMR_DETL.PAY_SEQ}.
 *
 * <p><b>Spelling note:</b> the DB table is {@code SCDU_PYMN_…}, not
 * {@code SCDUL_PYMN_…} — preserved verbatim from DEV.  Do NOT rename.
 *
 * <p><b>DEV parity:</b> {@code SCDUL_PYMN_NO} is {@code VARCHAR(10)} on
 * DEV across both this conn table AND its source {@code SCDUL_PYMN_SUMR}.
 * The local {@code SCDUL_PYMN_SUMR.SCDUL_PYMN_NO} (V131) was declared
 * {@code VARCHAR(20)} — that V131 drift is a separate corrective; this
 * entity carries the correct DEV-parity {@code length = 10}.
 */
@Entity
@Table(name = "SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN")
@IdClass(ScduPymnSumrDetlPayTargetConnId.class)
@Getter
@Setter
@NoArgsConstructor
public class ScduPymnSumrDetlPayTargetConn {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "SCDUL_PYMN_NO", length = 10, nullable = false)
    private String scdulPymnNo;

    @Id
    @Column(name = "SCDUL_PYMN_DETL_NO", length = 20, nullable = false)
    private String scdulPymnDetlNo;

    @Column(name = "TRAN_DT", length = 8)
    private String tranDt;

    @Column(name = "TRAN_SEQ", length = 10)
    private String tranSeq;
}
