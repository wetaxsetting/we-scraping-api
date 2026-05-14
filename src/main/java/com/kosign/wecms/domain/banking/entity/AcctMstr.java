package com.kosign.wecms.domain.banking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * Mirrors legacy wecmsvndev.ACCT_MSTR for the Sprint 1 column subset
 * (Account Inquiry VND/FCY). Additional legacy columns
 * (loan/interest/cert/VAS) are intentionally NOT mapped — extend in
 * later sprints when a feature needs them.
 *
 * Legacy uses VARCHAR(14) for REG_DTM/MOD_DTM, so this entity does NOT
 * extend BaseEntity (which assumes OffsetDateTime). Audit columns are
 * declared inline as String to match the legacy column type.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ACCT_MSTR")
@IdClass(AcctMstrId.class)
public class AcctMstr {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "BANK_CD", length = 20, nullable = false)
    private String bankCd;

    @Id
    @Column(name = "ACCT_NO", length = 100, nullable = false)
    private String acctNo;

    @Column(name = "ACCT_TYPE_CD", length = 2, nullable = false)
    private String acctTypeCd;

    @Column(name = "CCY_CD", length = 3, nullable = false)
    private String ccyCd;

    @Column(name = "ACCT_NICK_NM", length = 50)
    private String acctNickNm;

    @Column(name = "ACCT_NM", length = 100)
    private String acctNm;

    /**
     * Manual-registration flag. Legacy {@code wb_format_acct_nick_nm}
     * branches on {@code Y} for one of the regex CASE arms (preserved
     * verbatim in the V44 PL/pgSQL function — see
     * {@code db/migration/V44__bank_infm_detl_and_nickname_fns.sql}).
     * Mapped here so Hibernate can pass the value to the @Formula call
     * below; the DB column already exists from earlier migrations
     * (verified on local 2026-05-03).
     */
    @Column(name = "HAND_REG_YN", length = 1)
    private String handRegYn;

    /**
     * Account nickname formatted via legacy {@code wb_format_acct_nick_nm}
     * (installed by V44). Hibernate splices this SQL fragment into every
     * {@code SELECT} that loads {@link AcctMstr}, so list / find /
     * findFiltered all see the formatted display value without any
     * per-query plumbing. Aggregation queries that don't load the entity
     * (count / distinct bank list / per-currency totals) skip the
     * function call automatically — no overhead there.
     *
     * <p>Read-only at the JPA layer — there is no setter target. Use
     * {@link #setAcctNickNm(String)} to update the underlying column;
     * the formatted value is recomputed on the next load.
     *
     * <p>Refactor strategy: parity-critical legacy DB functions are
     * migrated to the refactor PostgreSQL via Flyway and called via
     * {@code @Formula} rather than translated to Java. See
     * {@code docs/refactor-wecms/architecture/db-function-strategy.md}.
     */
    @Formula("wb_format_acct_nick_nm(acct_nick_nm, bank_cd, acct_no, hand_reg_yn)")
    private String formattedAcctNickNm;

    @Column(name = "BAL")
    private BigDecimal bal;

    @Column(name = "REAL_AMT")
    private BigDecimal realAmt;

    @Column(name = "ACCT_BAL_INQ_STTS", length = 20)
    private String acctBalInqStts;

    @Column(name = "ACCT_BAL_INQ_MSG", length = 255)
    private String acctBalInqMsg;

    @Column(name = "ACCT_BAL_UPD_DTM", length = 14)
    private String acctBalUpdDtm;

    @Column(name = "ACCT_BAL_LST_INQ_DTM", length = 14)
    private String acctBalLstInqDtm;

    /**
     * Manually-assigned print/display order. Legacy {@code fnsh_0001_001}
     * ORDERs Account Inquiry by {@code PRN_SEQ ASC} so users can pin
     * priority accounts to the top of the list. Schema column added in
     * V23 as {@code NUMERIC(2,0) NULL} — JEX caps the column at 2 digits
     * so it never reaches the {@link Integer#MAX_VALUE} range; mapping to
     * {@link Integer} keeps the application-level type natural while
     * {@code @JdbcTypeCode(SqlTypes.NUMERIC)} overrides Hibernate's
     * default {@code Integer → INTEGER} JDBC mapping so schema {@code
     * validate} accepts the {@code numeric} column without a refactor-
     * only column-type migration. Same pattern as {@code SysScrOptionWecms.sortYn}
     * (V38 — keep DEV-parity DB type, adapt the entity).
     *
     * <p>Pre-V23 seed rows (V22) carry NULL; PostgreSQL's default
     * {@code ASC} sorts NULLs last, so unassigned rows fall through to
     * the {@code BANK_CD, ACCT_NO} secondary order in {@code
     * AccountService.DEFAULT_SORT}.
     */
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "PRN_SEQ", precision = 2, scale = 0)
    private Integer prnSeq;

    @Column(name = "ACTV_YN", length = 1, nullable = false)
    private String actvYn;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100)
    private String modId;

    @Column(name = "MOD_DTM", length = 14)
    private String modDtm;

    // ---------------------------------------------------------------
    // Settings > Bank Account fields. Columns added to the local
    // schema in V23 (`sprint1_legacy_schema_parity`); legacy column
    // semantics in `WECMS DEV/WECMS/src/wabooks/entity/AcctMstr.java`.
    // Banking > Account Inquiry doesn't read these — kept nullable +
    // optional on the entity so the existing inquiry path is
    // unaffected.
    // ---------------------------------------------------------------

    /** Free-form description / memo. Legacy `CONT` column; max 200
     *  chars. The Settings table renders the value as the
     *  "Description" cell; empty / null falls back to a dash placeholder
     *  on the frontend. */
    @Column(name = "CONT", length = 200)
    private String cont;

    /** Maturity date for saving / fixed-deposit accounts. Legacy
     *  `EXPI_DT` is `VARCHAR(8)` (`yyyyMMdd`) per V23; the service
     *  parses / formats at the wire boundary so the frontend stays
     *  ISO-like (`yyyy-MM-dd`) and the DB stores legacy-compatible
     *  values. Null for current accounts. */
    @Column(name = "EXPI_DT", length = 8)
    private String expiDt;

    /** Foreign key into {@code BUSI_VAS_ACCT_INFM} — the VAS account
     *  this bank account is mapped to. Stored as `NUMERIC(10,0)` on
     *  disk (V23); maps to `Long` here, with `@JdbcTypeCode(NUMERIC)`
     *  so Hibernate validate accepts the column type. Null when no
     *  VAS mapping is set. */
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "VAS_ACCT_SEQ_NO", precision = 10, scale = 0)
    private Long vasAcctSeqNo;

    /** Denormalised VAS account name copy. Legacy mirrors the
     *  matching `BUSI_VAS_ACCT_INFM.VAS_ACCT_NM` value on every
     *  edit; refactor preserves that contract so list / inquiry
     *  paths can read the label without joining. */
    @Column(name = "VAS_ACCT_NM", length = 50)
    private String vasAcctNm;

    /** Denormalised VAS account code copy. Legacy mirrors the
     *  matching `BUSI_VAS_ACCT_INFM.VAS_ACCT_CD`. */
    @Column(name = "VAS_ACCT_CD", length = 50)
    private String vasAcctCd;
}
