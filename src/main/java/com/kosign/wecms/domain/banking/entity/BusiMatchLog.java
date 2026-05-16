package com.kosign.wecms.domain.banking.entity;

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

/**
 * Audit log of bank-transaction ↔ payment-transaction match operations
 * (Smart Organizer feature, legacy {@code comm_0196_001}). One row per
 * (bank transaction, payment transaction) pair.
 *
 * <p>{@code LOCKED_YN='Y'} marks a live match — same {@code PAY_TRNS_ID}
 * cannot be matched to two bank rows concurrently. Unmatch flips the
 * flag back to {@code 'N'} rather than deleting (preserves history).
 *
 * <p>Audit columns are VARCHAR(14) in legacy {@code busi_match_log} (NOT
 * {@code OffsetDateTime}), so this entity holds them as plain {@code
 * String}s and does NOT extend {@link com.kosign.wecms.common.entity.BaseEntity}.
 * Same pattern as {@link AcctTrnsRcvPayInfm}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "BUSI_MATCH_LOG")
@IdClass(BusiMatchLogId.class)
public class BusiMatchLog {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "MATCH_LOG_SEQ", length = 20, nullable = false)
    private String matchLogSeq;

    @Column(name = "BANK_TRNS_SHORT_ID", length = 20, nullable = false)
    private String bankTrnsShortId;

    @Column(name = "BANK_TRNS_FULL_ID", length = 100, nullable = false)
    private String bankTrnsFullId;

    @Column(name = "PAY_TRNS_ID", length = 50, nullable = false)
    private String payTrnsId;

    /** Match type — {@code 'M'} = manual via Smart Organizer popup,
     *  {@code 'A'} = automated (reserved for future bulk matching). */
    @Column(name = "MATCH_TYPE_CD", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String matchTypeCd;

    @Column(name = "MEMO", length = 500)
    private String memo;

    @Column(name = "MATCHED_AT", length = 14, nullable = false)
    private String matchedAt;

    @Column(name = "MATCHED_BY", length = 100, nullable = false)
    private String matchedBy;

    @Column(name = "LAST_MATCHED_AT", length = 14)
    private String lastMatchedAt;

    @Column(name = "LAST_MATCHED_BY", length = 100)
    private String lastMatchedBy;

    /** {@code 'Y'} = match is live, {@code 'N'} = unmatched (kept for
     *  audit). Defaults to {@code 'N'} at the DB level. */
    @Column(name = "LOCKED_YN", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String lockedYn;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100, nullable = false)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;
}
