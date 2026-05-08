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
 * Mirrors legacy wecmsvndev.ACCT_TRNS_HSTR for the Sprint 1 column
 * subset (Transaction History VND/FCY). The "confirmed" UI flag is
 * derived in service via AcctTrnsRcvPayInfmRepository.existsByPk(...) —
 * see AcctTrnsRcvPayInfmRepository for the rationale.
 *
 * BANK_CD + ACCT_NO are loosely linked to ACCT_MSTR via plain @Column
 * String IDs (no @JoinColumn / cascade) per CLAUDE.md.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ACCT_TRNS_HSTR")
@IdClass(AcctTrnsHstrId.class)
public class AcctTrnsHstr {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "ACCT_SEQ_NO", length = 20, nullable = false)
    private String acctSeqNo;

    @Column(name = "BANK_CD", length = 20)
    private String bankCd;

    @Column(name = "ACCT_NO", length = 100)
    private String acctNo;

    @Column(name = "TRSC_DT", length = 8)
    private String trscDt;

    @Column(name = "TRSC_TM", length = 6)
    private String trscTm;

    @Column(name = "CCY_CD", length = 3)
    private String ccyCd;

    @Column(name = "INOUT_DV", length = 1)
    private String inoutDv;

    @Column(name = "TRSC_AMT")
    private BigDecimal trscAmt;

    @Column(name = "BAL")
    private BigDecimal bal;

    @Column(name = "RMRK", length = 500)
    private String rmrk;

    @Column(name = "RMRK2", length = 500)
    private String rmrk2;

    @Column(name = "LINK_MATCH_YN", length = 1, nullable = false)
    private String linkMatchYn;

    @Column(name = "BANK_REF_NO", length = 100)
    private String bankRefNo;

    @Column(name = "REG_ID", length = 100)
    private String regId;

    @Column(name = "REG_DTM", length = 14)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100)
    private String modId;

    @Column(name = "MOD_DTM", length = 14)
    private String modDtm;
}
