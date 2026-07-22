package com.kosign.wecms.domain.payment.entity;

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
 * Overseas Payment Master — individual / manual payment records (type = 'M').
 * Source: wabooks.entity.OvrsPymnMstr (JEX DEV).
 * Table created by V85.
 *
 * <p>REG_DTM / MOD_DTM are VARCHAR(14) for legacy parity.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "OVRS_PYMN_MSTR")
@IdClass(OvrsPymnMstrId.class)
public class OvrsPymnMstr {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "OVRS_PYMN_NO", length = 14, nullable = false)
    private String ovrsPymnNo;

    @Column(name = "DBT_ACCT_NO", length = 100)
    private String dbtAcctNo;

    @Column(name = "DBT_ACCT_CD", length = 15)
    private String dbtAcctCd;

    @Column(name = "DBT_CCY_CD", length = 3)
    private String dbtCcyCd;

    @Column(name = "TRNF_CCY_CD", length = 3)
    private String trnfCcyCd;

    @Column(name = "TRNF_AMT", precision = 20, scale = 6)
    private BigDecimal trnfAmt;

    @Column(name = "TOTAL_TRAN_AMT", precision = 20, scale = 6)
    private BigDecimal totalTranAmt;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "TOT_TRNS_CNT", precision = 10, scale = 0)
    private Integer totTrnsCnt;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "FEE_PAYER_CD", length = 1)
    private String feePayerCd;

    @Column(name = "BNFC_NM", length = 100)
    private String bnfcNm;

    @Column(name = "BNFC_BANK_CD", length = 15)
    private String bnfcBankCd;

    @Column(name = "BNFC_ACCT_NO", length = 100)
    private String bnfcAcctNo;

    @Column(name = "BNFC_BANK_NAME", length = 200)
    private String bnfcBankName;

    @Column(name = "BNFC_BANK_BIC", length = 15)
    private String bnfcBankBic;

    @Column(name = "BNFC_ADDR", length = 500)
    private String bnfcAddr;

    @Column(name = "MEMO", length = 500)
    private String memo;

    /** Ordering-customer (sender) name for the M-mode SWIFT MT103 field 50K.
     *  Legacy pymn_0007_001_c001_act.jsp:223 SENDER_NM = DBT_ACCT_HLDR (finding L). */
    @Column(name = "DBT_ACCT_HLDR", length = 100)
    private String dbtAcctHldr;

    /** Dispatch shadow keys (finding L, M-mode) — the Result-detail join reads
     *  OVRS_PYMN_MSTR ⋈ TB_PAY_FILE_MST via (PAY_TRAN_DT, PAY_FILE_SEQ_NO),
     *  stamped by {@code OverseasDispatchExecutor.execute(OvrsPymnMstr)}. */
    @Column(name = "PAY_TRAN_DT", length = 8)
    private String payTranDt;

    @Column(name = "PAY_FILE_SEQ_NO", length = 7)
    private String payFileSeqNo;

    @Column(name = "APPR_SEQ_NO", length = 15)
    private String apprSeqNo;

    /**
     * Reporting Account (chart of accounts) classifier. Used by the
     * Cash Flow report's overseas-payment aggregation (legacy
     * {@code get_cashflow_report} reads this column directly off the
     * row, matching the bank / card source pattern). Nullable —
     * unclassified rows are excluded from the cash-flow aggregation
     * via the {@code chart_acct_cd <> ''} predicate.
     */
    @Column(name = "CHART_ACCT_CD", length = 20)
    private String chartAcctCd;

    @Column(name = "CHART_ACCT_SEQ_NO", length = 10)
    private String chartAcctSeqNo;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "TRNF_YN", length = 1, nullable = false)
    private String trnfYn;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "REG_ID", length = 100)
    private String regId;

    @Column(name = "MOD_DTM", length = 14)
    private String modDtm;

    @Column(name = "MOD_ID", length = 100)
    private String modId;
}
