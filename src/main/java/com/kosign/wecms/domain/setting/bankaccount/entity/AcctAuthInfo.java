package com.kosign.wecms.domain.setting.bankaccount.entity;

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
 * Per-user-per-account permission row for Settings &gt; Bank Account.
 * Provisioned by V245. Legacy twin is {@code wabooks.entity.AcctAuthInfo}.
 *
 * <p>{@code USE_YN} and {@code AUTH_TYPE} are {@code bpchar(1)} on the live
 * JEX schema (verified on JEX DEV 2026-06-09); both map with
 * {@code @JdbcTypeCode(SqlTypes.CHAR)} so Hibernate {@code validate} accepts
 * the {@code char(1)} columns instead of expecting {@code varchar} (V38
 * {@code sort_yn} / V94 {@code chart_acct_infm} precedent).
 *
 * <p>PK is {@code (BIZ_KEY, USER_ID, BANK_CD, ACCT_NO, AUTH_TYPE)} — the
 * authority kind is part of the key so the same user can hold both a
 * VIEW ({@code 'V'}) row and a WITHDRAW ({@code 'W'}) row for one
 * account independently. {@code USE_YN='N'} soft-revokes without
 * losing audit history (mirrors legacy `comm_0220_*` revoke path).
 *
 * <p>Audit columns are {@code VARCHAR(14)} legacy strings (yyyyMMddHHmmss)
 * to match the rest of the JEX-parity schema — not the refactor's
 * {@code BaseEntity} {@code TIMESTAMPTZ} pattern. See V127 header.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ACCT_AUTH_INFO")
@IdClass(AcctAuthInfoId.class)
public class AcctAuthInfo {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "USER_ID", length = 100, nullable = false)
    private String userId;

    @Id
    @Column(name = "BANK_CD", length = 20, nullable = false)
    private String bankCd;

    @Id
    @Column(name = "ACCT_NO", length = 100, nullable = false)
    private String acctNo;

    @Id
    @Column(name = "AUTH_TYPE", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String authType;

    @Column(name = "USE_YN", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String useYn;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100)
    private String modId;

    @Column(name = "MOD_DTM", length = 14)
    private String modDtm;
}
