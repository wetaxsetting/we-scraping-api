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
 * Payment &gt; Card Transactions &mdash; card registry. Mirrors legacy
 * JEX {@code wabooks.entity.CardMstr} (file
 * {@code WECMS DEV/WECMS/src/wabooks/entity/CardMstr.java}) and the V176
 * column-for-column schema.
 *
 * <p>Read-only in this MR. Surfaces three flows:
 * <ul>
 *   <li>Dropdown population for the All-Cards filter
 *       ({@code GET /api/v1/payment/card-transactions/cards}).</li>
 *   <li>LEFT JOIN target for the ledger query (display columns
 *       {@code CARD_NICK_NM} / {@code CARD_HLDR_NM} that the legacy
 *       {@code CARD_MSTR_R002} IDO joins).</li>
 *   <li>Optional {@code BANK_CD} narrow on the cards endpoint when the
 *       frontend picks a single bank first.</li>
 * </ul>
 *
 * <p>The entity maps the full 25 DEV columns (Sprint 0 service reads
 * 6 of them — {@code CARD_NM}, {@code CARD_NICK_NM}, {@code CARD_HLDR_NM},
 * {@code BANK_CD}, {@code CARD_NO}, {@code MOD_DTM}). Legacy parity
 * precedent: {@code ScdulPymnSumr} maps every column.
 *
 * <p>{@code CARD_TYPE_CD} / {@code ACTV_YN} are {@code CHAR(N)} on DEV,
 * mapped via {@link JdbcTypeCode @JdbcTypeCode(SqlTypes.CHAR)} so
 * Hibernate's {@code ddl-auto: validate} accepts {@code bpchar} against
 * Java {@code String} fields.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "CARD_MSTR")
@IdClass(CardMstrId.class)
public class CardMstr {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "BANK_CD", length = 20, nullable = false)
    private String bankCd;

    @Id
    @Column(name = "CARD_NO", length = 100, nullable = false)
    private String cardNo;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "CARD_TYPE_CD", length = 2, nullable = false)
    private String cardTypeCd;

    @Column(name = "ISSU_DT", length = 8)
    private String issuDt;

    @Column(name = "EXPI_DT", length = 8)
    private String expiDt;

    @Column(name = "CARD_NM", length = 50)
    private String cardNm;

    @Column(name = "CARD_NICK_NM", length = 50)
    private String cardNickNm;

    @Column(name = "CARD_HLDR_NM", length = 50, nullable = false)
    private String cardHldrNm;

    @Column(name = "CARD_HIS_INQ_STTS", length = 20)
    private String cardHisInqStts;

    @Column(name = "CARD_HIS_INQ_MSG", length = 255)
    private String cardHisInqMsg;

    @Column(name = "CARD_HIS_LST_INQ_DT", length = 14)
    private String cardHisLstInqDt;

    /** Unbounded NUMERIC on DEV — entity carries it as {@link BigDecimal}
     *  without precision/scale so Hibernate's {@code ddl-auto: validate}
     *  doesn't pin a specific shape (DEV's column is plain {@code numeric}). */
    @Column(name = "PRN_SEQ")
    private BigDecimal prnSeq;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ACTV_YN", length = 1, nullable = false)
    private String actvYn;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100, nullable = false)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;

    @Column(name = "NXISAS_ACCT_TYPE", length = 50)
    private String nxisasAcctType;

    /** Unbounded NUMERIC on DEV — same precision-omitted shape as
     *  {@link #prnSeq}. */
    @Column(name = "BANK_CERT_SEQ")
    private BigDecimal bankCertSeq;

    @Column(name = "TRN_LST_SUC_DTM", length = 14)
    private String trnLstSucDtm;

    @Column(name = "LST_LGN_STTS", length = 20)
    private String lstLgnStts;

    @Column(name = "LST_LGN_STTS_MSG", length = 255)
    private String lstLgnSttsMsg;

    @Column(name = "LST_LGN_SUC_DTM", length = 14)
    private String lstLgnSucDtm;

    @Column(name = "PYMN_ACCT_NO", length = 50)
    private String pymnAcctNo;
}
