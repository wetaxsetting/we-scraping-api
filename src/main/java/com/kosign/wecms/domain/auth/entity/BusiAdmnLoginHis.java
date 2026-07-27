package com.kosign.wecms.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mirrors legacy {@code wabooks.entity.BusiAdmnLoginHis} — a company-scoped
 * audit of successful <b>business-admin</b> logins through the WeCMS customer
 * channel ({@code ChnlType.TYPE_C}). Written alongside {@code USER_LOGIN_HIS}
 * on a successful login when the user is a company admin
 * ({@code BUSI_ADMN_INFM} has a row for the resolved (BIZ_KEY, USER_ID)),
 * mirroring {@code logn_2001_001_r001_act.jsp:270-284}. Feeds the admin
 * login-history screen (legacy {@code comm_0213_001}).
 *
 * <p><b>PK is 4-column</b> — {@code (BIZ_KEY, REG_ID, REG_DTM, LOGIN_UUID)} —
 * matching the live JEX DDL (verified 2026-07-24). The legacy JEX entity's
 * logical PK is only 3-column (BIZ_KEY, REG_ID, REG_DTM) and the physical
 * {@code LOGIN_UUID} is filled by a DB {@code DEFAULT}; that default exists so
 * two logins by the same admin in the same second (identical 14-char REG_DTM)
 * do not collide. The refactor maps all four PK columns and <b>sets
 * {@code LOGIN_UUID} explicitly in Java</b> ({@code UUID.randomUUID()}) rather
 * than relying on the DB default — Hibernate needs the full id value at insert
 * time, and a random UUID is the exact equivalent of the legacy DB default.
 *
 * <p>No {@code BaseEntity} — {@code REG_ID}/{@code REG_DTM} are the PK and the
 * table has no {@code MOD_ID}/{@code MOD_DTM} audit columns (same pattern as
 * {@link UserLoginHis}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "BUSI_ADMN_LOGIN_HIS")
@IdClass(BusiAdmnLoginHisId.class)
public class BusiAdmnLoginHis {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    /** Legacy {@code BUSI_ADMN_LOGIN_HIS.REG_DTM} is {@code varchar(14)}
     *  {@code yyyyMMddHHmmss} (UTC) — part of the composite PK. Generated
     *  UTC-now, mirroring legacy {@code BusiAdmnLoginHisDAO.create} →
     *  {@code DateTimeUtils.utcNow()} + {@code WB_FORMATTER_DTM14}, and kept
     *  identical to the {@code USER_LOGIN_HIS.REG_DTM} written in the same
     *  login so the two audit rows share a timestamp. */
    @Id
    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    /** DB PK tiebreaker. Live DDL carries a {@code DEFAULT}; the refactor sets
     *  it in Java (see class Javadoc) so Hibernate has the full id at insert. */
    @Id
    @Column(name = "LOGIN_UUID", length = 50, nullable = false)
    private String loginUuid;

    @Column(name = "IP_ADDR", length = 100, nullable = false)
    private String ipAddr;

    /** Admin display name — legacy sources this from {@code BUSI_ADMN_INFM.USER_NM}
     *  (not {@code USER_INFM}). Nullable per the live DDL. */
    @Column(name = "REG_NM", length = 100)
    private String regNm;

    @Column(name = "LGN_CHNL", length = 3, nullable = false)
    private String lgnChnl;

    public static final String CHNL_PC = "PC";
}
