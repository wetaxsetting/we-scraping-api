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
 * NAPAS inter-bank remittance transfer history (Transfer History page).
 * Maps the Sprint 1 column subset of legacy wecmsvndev.NAPAS_REMIT_TRNS_HSTR.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "NAPAS_REMIT_TRNS_HSTR")
@IdClass(NapasRemitTrnsHstrId.class)
public class NapasRemitTrnsHstr {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "SEQ_NO", nullable = false)
    private BigDecimal seqNo;

    @Column(name = "OUT_BANK_CODE", length = 20, nullable = false)
    private String outBankCode;

    @Column(name = "OUT_ACCT_NO", length = 100, nullable = false)
    private String outAcctNo;

    @Column(name = "TRANS_TYPE", length = 1)
    private String transType;

    @Column(name = "IN_BANK_CODE", length = 20)
    private String inBankCode;

    @Column(name = "IN_NAPAS_BANK_CODE", length = 10)
    private String inNapasBankCode;

    @Column(name = "IN_ACCT_NO", length = 100, nullable = false)
    private String inAcctNo;

    @Column(name = "OWAC_NM", length = 200)
    private String owacNm;

    @Column(name = "CCY_CD", length = 3, nullable = false)
    private String ccyCd;

    @Column(name = "TRSC_AMT", nullable = false)
    private BigDecimal trscAmt;

    @Column(name = "FEE")
    private BigDecimal fee;

    @Column(name = "FEE_DFR_SBJ", length = 1)
    private String feeDfrSbj;

    @Column(name = "RMRK", length = 500)
    private String rmrk;

    @Column(name = "RMRK2", length = 500)
    private String rmrk2;

    @Column(name = "TRNS_STTS", length = 20)
    private String trnsStts;

    @Column(name = "TRNS_MSG", length = 255)
    private String trnsMsg;

    @Column(name = "TRNS_STLM_ID", length = 100)
    private String trnsStlmId;

    @Column(name = "TRNS_STLM_YN", length = 1)
    private String trnsStlmYn;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;
}
