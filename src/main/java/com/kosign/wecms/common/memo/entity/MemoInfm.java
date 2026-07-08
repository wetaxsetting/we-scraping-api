package com.kosign.wecms.common.memo.entity;

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
 * Polymorphic memo / note row. One row per memo, attached to any
 * parent record via {@code (WEB_SVC_ID, REF_NO[, REF_NO2..REF_NO9])}.
 *
 * <p>Mirrors legacy {@code wabooks.entity.MemoInfm}. Shared refactor
 * infrastructure (relocated to {@code common/memo} 2026-07-07 when a
 * second consumer — Transaction History VND/FCY — joined Card
 * Transactions): Memoable parents attach by passing their own
 * {@code WEB_SVC_ID} + parent ids.
 * <ul>
 *   <li>Card Transactions: single-key — {@code REF_NO = CARD_SEQ_NO}.</li>
 *   <li>Transaction History VND/FCY: 4-key — {@code REF_NO = BANK_CD},
 *       {@code REF_NO2 = ACCT_NO}, {@code REF_NO3 = TRSC_DT},
 *       {@code REF_NO4 = ACCT_SEQ_NO} (legacy {@code fnsh_0002_001} /
 *       {@code fnsh_0006_001} parity).</li>
 * </ul>
 *
 * <p>{@code COLOR} stores the legacy sticker token verbatim
 * ({@code fileG/fileB/fileY/fileW/fileR/fileP}); {@code fileW} = no color.
 * DEV column is {@code varchar(6)} which fits every value.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MEMO_INFM")
@IdClass(MemoInfmId.class)
public class MemoInfm {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "WEB_SVC_ID", length = 50, nullable = false)
    private String webSvcId;

    @Id
    @Column(name = "SEQ_NO", precision = 10, scale = 0, nullable = false)
    private BigDecimal seqNo;

    @Column(name = "MEMO", length = 200)
    private String memo;

    @Column(name = "COLOR", length = 6)
    private String color;

    @Column(name = "REF_NO", length = 50, nullable = false)
    private String refNo;

    @Column(name = "REF_NO2", length = 50)
    private String refNo2;

    @Column(name = "REF_NO3", length = 50)
    private String refNo3;

    @Column(name = "REF_NO4", length = 50)
    private String refNo4;

    @Column(name = "REF_NO5", length = 50)
    private String refNo5;

    @Column(name = "REF_NO6", length = 50)
    private String refNo6;

    @Column(name = "REF_NO7", length = 50)
    private String refNo7;

    @Column(name = "REF_NO8", length = 50)
    private String refNo8;

    @Column(name = "REF_NO9", length = 50)
    private String refNo9;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100, nullable = false)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;
}
