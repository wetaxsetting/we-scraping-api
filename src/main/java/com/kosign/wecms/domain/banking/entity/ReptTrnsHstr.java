package com.kosign.wecms.domain.banking.entity;

import com.kosign.wecms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * HAENGSUNG Daily Financial ledger row — the base table behind
 * {@code fn_get_haengsung_daily_financial_report} (legacy {@code rept_0017_001}).
 *
 * <p><b>Sprint column subset.</b> Only the fields the HS report screen edits are
 * mapped: the composite PK plus the five user-editable columns
 * ({@code REPT_CD}, {@code PYMN_CD}, {@code DESC_NM_EN}, {@code DESC_NM_KO},
 * {@code MEMO}). Audit columns are inherited from {@link BaseEntity}. Every
 * other physical column ({@code trsc_amt}, {@code bal}, {@code erp_snd_yn}, …)
 * is intentionally unmapped — Hibernate {@code validate} ignores unmapped
 * columns, and a partial-field UPDATE never touches them, so the read path
 * (the DB function) stays the single source of truth for balances.
 *
 * <p>Write path mirrors legacy {@code rept_0017_001_u001_act.jsp} →
 * {@code ReptTrnsHstrDAO.patch} → IDO {@code REPT_TRNS_HSTR_U000}
 * ({@code UPDATE REPT_TRNS_HSTR SET :FIELDS_TO_UPDATE WHERE BIZ_KEY=? AND
 * ACCT_SEQ_NO=?}) — a per-field auto-save on blur/Enter, no key generation, no
 * schema change.
 */
@Entity
@Table(name = "REPT_TRNS_HSTR")
@IdClass(ReptTrnsHstrId.class)
@Getter
@Setter
@NoArgsConstructor
public class ReptTrnsHstr extends BaseEntity {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "ACCT_SEQ_NO", length = 20, nullable = false)
    private String acctSeqNo;

    /** Receipt Code (입금코드) — user-editable on TRANSACTION rows. */
    @Column(name = "REPT_CD", length = 20)
    private String reptCd;

    /** Payment Code (출금코드) — user-editable on TRANSACTION rows. */
    @Column(name = "PYMN_CD", length = 20)
    private String pymnCd;

    /** Description (English) (적요(현지)) — user-editable. */
    @Column(name = "DESC_NM_EN", length = 255)
    private String descNmEn;

    /** Description (Korean) (적요(번역)) — user-editable. */
    @Column(name = "DESC_NM_KO", length = 255)
    private String descNmKo;

    /** Memo (메모) — user-editable. */
    @Column(name = "MEMO", length = 500)
    private String memo;
}
