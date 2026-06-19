package com.kosign.wecms.domain.payment.cardtransactions.entity;

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
 * Per-document link from a business doc ({@code BUSA_DOC_MSTR}) to a
 * receive/payment record. Card flow writes one row per linked expense
 * with {@code RCV_PAY_WAY_CD = 'R'} ({@code RcvPayWayCd.CARD} — the enum
 * maps {@code 'C'} to CASH), {@code REF_NO = CARD_SEQ_NO},
 * {@code DEDU_AMT = item.dedu_amt}.
 *
 * <p>V13 created the table with the Sprint-1 read subset (7 cols); V23
 * added the remaining 10 columns needed by the link write
 * (USER_ID / RCV_PAY_HIS_SEQ_NO / RCV_PAY_WAY_CD / BANK_CD / ACCT_NO /
 * TRSC_DT / REF_NO / BAL / DEDU_AMT / CARD_NO).
 *
 * <p>JEX has no PK on this table and no {@code SEQ_NO} column; the
 * entity keys on {@code (BIZ_KEY, DOC_ID, RCV_PAY_HIS_SEQ_NO)} — a
 * natural key proven unique on JEX (1022/1022 rows). Card-link rows
 * allocate {@code RCV_PAY_HIS_SEQ_NO} via per-doc MAX+1 at write time.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "BUSA_DOC_RCV_PAY_HIS")
@IdClass(BusaDocRcvPayHisId.class)
public class BusaDocRcvPayHis {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "DOC_ID", length = 50, nullable = false)
    private String docId;

    /** Per-doc sequence — the JEX natural key for this PK-less table
     *  (JEX has no {@code SEQ_NO} column). Allocated per-doc via MAX+1
     *  at write time. */
    @Id
    @Column(name = "RCV_PAY_HIS_SEQ_NO", precision = 5, scale = 0, nullable = false)
    private BigDecimal rcvPayHisSeqNo;

    @Column(name = "USER_ID", length = 100)
    private String userId;

    /** Payment method code — {@code 'R'} for Card link writes
     *  ({@code wabooks.enums.RcvPayWayCd.CARD}; {@code 'C'} is CASH).
     *  Legacy {@code char(1)}. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "RCV_PAY_WAY_CD", length = 1)
    private String rcvPayWayCd;

    @Column(name = "BANK_CD", length = 20)
    private String bankCd;

    @Column(name = "ACCT_NO", length = 100)
    private String acctNo;

    @Column(name = "TRSC_DT", length = 8)
    private String trscDt;

    /** Reference no — the {@code CARD_SEQ_NO} on card-link rows. */
    @Column(name = "REF_NO", length = 100)
    private String refNo;

    @Column(name = "BAL", precision = 20, scale = 6)
    private BigDecimal bal;

    /** Amount allocated to this document from the parent card
     *  transaction. SUM of these for one card MUST equal
     *  {@code CARD_TRNS_HSTR.TRSC_AMT}. */
    @Column(name = "DEDU_AMT", precision = 20, scale = 6)
    private BigDecimal deduAmt;

    @Column(name = "CARD_NO", length = 100)
    private String cardNo;

    @Column(name = "REG_ID", length = 100)
    private String regId;

    @Column(name = "REG_DTM", length = 14)
    private String regDtm;
}
