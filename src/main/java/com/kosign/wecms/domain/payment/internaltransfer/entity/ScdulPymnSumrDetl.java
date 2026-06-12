package com.kosign.wecms.domain.payment.internaltransfer.entity;

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
 * Payment &gt; Internal Transfer — batch line. One row per
 * recipient/amount line within a {@link ScdulPymnSumr} batch.
 * Mirrors legacy {@code WECMS DEV/WECMS/src/wabooks/entity/ScdulPymnSumrDetl.java}
 * + the V85 column-for-column schema.
 *
 * <p>Each line carries a recipient bank/account/amount + optional ERP
 * linkage ({@code CHART_ACCT_*}) + transfer-network discriminator
 * ({@code ANT_DIV_CD}). The Reporting Account fields
 * ({@code CHART_ACCT_SEQ_NO} / {@code CHART_ACCT_CD}) can be set at
 * upload time or later via the
 * {@code PATCH .../lines/{lineId}/reporting-account} endpoint
 * (mirrors legacy {@code pymn_0009_002_u002}).
 *
 * <p>Tenant scoping: every repository method MUST constrain on
 * {@code BIZ_KEY} from
 * {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "SCDUL_PYMN_SUMR_DETL")
@IdClass(ScdulPymnSumrDetlId.class)
public class ScdulPymnSumrDetl {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "SCDUL_PYMN_NO", length = 10, nullable = false)
    private String scdulPymnNo;

    @Id
    @Column(name = "SCDUL_PYMN_DETL_NO", length = 20, nullable = false)
    private String scdulPymnDetlNo;

    @Column(name = "TRAN_AMT", precision = 20, scale = 6, nullable = false)
    private BigDecimal tranAmt;

    @Column(name = "TRNF_CCY_CD", length = 3, nullable = false)
    private String trnfCcyCd;

    /** Transfer-network discriminator — {@code N}/{@code NAPAS}/{@code INTER}
     *  for NAPAS, {@code C}/{@code CITAD} for CITAD, {@code S}/{@code SWIFT}
     *  for SWIFT. See {@code CutoffValidator}. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ANT_DIV_CD", length = 5)
    private String antDivCd;

    @Column(name = "RCPT_NM", length = 100)
    private String rcptNm;

    @Column(name = "TAR_BANK_CD", length = 15)
    private String tarBankCd;

    @Column(name = "FINC_MDA_NO", length = 100, nullable = false)
    private String fincMdaNo;

    @Column(name = "TRAN_USAG", length = 200)
    private String tranUsag;

    @Column(name = "TAR_ACCT_RMRK", length = 150)
    private String tarAcctRmrk;

    @Column(name = "WDRL_ACCT_RMRK", length = 150)
    private String wdrlActRmrk;

    @Column(name = "PAY_TRAN_DT", length = 8)
    private String payTranDt;

    @Column(name = "PAY_TRAN_SEQ", length = 10)
    private String payTranSeq;

    @Column(name = "PAY_FILE_SEQ_NO", length = 7)
    private String payFileSeqNo;

    @Column(name = "PAY_SEQ", precision = 4, scale = 0)
    private BigDecimal paySeq;

    @Column(name = "TRCO_KEY", length = 20)
    private String trcoKey;

    @Column(name = "HAND_TRCO_KEY", length = 20)
    private String handTrcoKey;

    @Column(name = "DOC_NO", length = 50)
    private String docNo;

    @Column(name = "NOTE", length = 200)
    private String note;

    @Column(name = "CITAD_BANK_CD", length = 50)
    private String citadBankCd;

    @Column(name = "TRCO_CMNM", length = 200)
    private String trcoCmnm;

    @Column(name = "TRCO_CMENM", length = 200)
    private String trcoCmenm;

    @Column(name = "CHART_ACCT_CD", length = 20)
    private String chartAcctCd;

    @Column(name = "CHART_ACCT_SEQ_NO", length = 20)
    private String chartAcctSeqNo;

    @Column(name = "ERP_FILE_DATE", length = 8)
    private String erpFileDate;

    @Column(name = "ERP_FILE_NUM", length = 8)
    private String erpFileNum;

    @Column(name = "ERP_FILE_SEQ", precision = 10, scale = 0)
    private BigDecimal erpFileSeq;

    @Column(name = "ERP_ATTRIBUTE2", length = 150)
    private String erpAttribute2;

    @Column(name = "DEPO_CCY_CD", length = 3)
    private String depoCcyCd;

    @Column(name = "ACCT_SEQ_NO", length = 20)
    private String acctSeqNo;

    @Column(name = "BANK_TRNS_SHORT_ID", length = 20)
    private String bankTrnsShortId;

    @Column(name = "BANK_TRNS_FULL_ID", length = 100)
    private String bankTrnsFullId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "LINK_MATCH_YN", length = 1)
    private String linkMatchYn;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;
}
