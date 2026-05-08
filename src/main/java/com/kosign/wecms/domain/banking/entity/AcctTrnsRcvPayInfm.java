package com.kosign.wecms.domain.banking.entity;

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
 * Receipt/payment info linking a transaction (ACCT_TRNS_HSTR) to a
 * receipt or payment record. Sprint 1 reads only enough columns to
 * derive the "confirmed" flag the Transaction History page renders.
 *
 * Column shape mirrors legacy wecmsvndev.acct_trns_rcv_pay_infm —
 * V21 renamed the V13 placeholders (TRCO_KEY → RCV_PAY_TRCO_KEY,
 * CONF_YN → CNFM_YN, etc.) and added the legacy locator columns
 * (BANK_CD, ACCT_NO, TRSC_DT) so this table can be joined back to
 * ACCT_TRNS_HSTR by either the surrogate {@code ACCT_SEQ_NO} or the
 * locator triplet.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ACCT_TRNS_RCV_PAY_INFM")
@IdClass(AcctTrnsRcvPayInfmId.class)
public class AcctTrnsRcvPayInfm {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "ACCT_SEQ_NO", length = 20, nullable = false)
    private String acctSeqNo;

    @Id
    @Column(name = "RCV_PAY_SEQ_NO", length = 20, nullable = false)
    private String rcvPaySeqNo;

    @Column(name = "BANK_CD", length = 20)
    private String bankCd;

    @Column(name = "ACCT_NO", length = 100)
    private String acctNo;

    @Column(name = "TRSC_DT", length = 8)
    private String trscDt;

    @Column(name = "RCV_PAY_TRCO_KEY", length = 20)
    private String rcvPayTrcoKey;

    @Column(name = "RCV_PAY_TRCO_NM", length = 200)
    private String rcvPayTrcoNm;

    @Column(name = "RCV_PAY_TRCO_ENM", length = 200)
    private String rcvPayTrcoEnm;

    @Column(name = "RCV_PAY_USAG_CD", length = 20)
    private String rcvPayUsagCd;

    @Column(name = "VAS_ACCT_CD", length = 50)
    private String vasAcctCd;

    @Column(name = "RCV_PAY_AMT")
    private BigDecimal rcvPayAmt;

    @Column(name = "CNFM_YN", length = 1)
    private String cnfmYn;

    @Column(name = "CNFM_BY_USER_ID", length = 100)
    private String cnfmByUserId;

    @Column(name = "CNFM_BY_USER_NM", length = 50)
    private String cnfmByUserNm;

    @Column(name = "CNFM_DTM", length = 14)
    private String cnfmDtm;

    @Column(name = "REG_ID", length = 100)
    private String regId;

    @Column(name = "REG_DTM", length = 14)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100)
    private String modId;

    @Column(name = "MOD_DTM", length = 14)
    private String modDtm;
}
