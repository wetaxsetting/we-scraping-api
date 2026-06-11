package com.kosign.wecms.domain.payment.cardtransactions.entity;

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
 * Card-link master row. One per linked card transaction. The child
 * documents (expenses / receipts) live on
 * {@code BUSA_DOC_RCV_PAY_HIS}.
 *
 * <p>Schema landed in V200; DEV parity verified via {@code postgres_dev_server_jexframe}
 * MCP 2026-05-21.
 *
 * <p>Powers the legacy {@code card_0001_002_c001} create path; the
 * Card Transactions list joins on {@code (BIZ_KEY, CARD_SEQ_NO)} to
 * show the linked-state chip on each row.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "CARD_TRNS_RCV_PAY_INFM")
@IdClass(CardTrnsRcvPayInfmId.class)
public class CardTrnsRcvPayInfm {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "CARD_SEQ_NO", length = 20, nullable = false)
    private String cardSeqNo;

    @Id
    @Column(name = "RCV_PAY_SEQ_NO", precision = 5, scale = 0, nullable = false)
    private BigDecimal rcvPaySeqNo;

    @Column(name = "BANK_CD", length = 20, nullable = false)
    private String bankCd;

    @Column(name = "CARD_NO", length = 100, nullable = false)
    private String cardNo;

    @Column(name = "TRSC_DT", length = 8, nullable = false)
    private String trscDt;

    @Column(name = "RCV_PAY_AMT", precision = 20, scale = 6, nullable = false)
    private BigDecimal rcvPayAmt;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100, nullable = false)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;
}
