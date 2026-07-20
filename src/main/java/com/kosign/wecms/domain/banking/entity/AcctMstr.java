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

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ACCT_TYPE_CD", length = 2, nullable = false)
    private String acctTypeCd;

    @JdbcTypeCode(SqlTypes.CHAR)
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
     * FK into {@code BANK_CERT_INFO(BIZ_KEY, SEQ_NO)}. NULL when the
     * account has never been bound to a bank certificate. Drives the
     * disconnected-bank check on Account Inquiry — the legacy
     * {@code fnsh_0005_001.ts:711} predicate ({@code cert_info_con == ''
     * && cert_info_dv == ''}) is satisfied whenever this FK is null OR
     * points to a {@code BANK_CERT_INFO} row whose
     * {@code CERT_INFO_CON} / {@code CERT_INFO_DV} are blank — the
     * legacy IDO {@code ACCT_MSTR_R001} returns nulls in both cases.
     * Schema column already mapped on the legacy table; surfaced here
     * because Sprint 2 needs the FK to resolve cert state. The
     * {@code CERT_INFO_CON} / {@code CERT_INFO_DV} columns on
     * {@code ACCT_MSTR} that V23 added are legacy DDL leftovers that
     * the live system never populates — they live on
     * {@code BANK_CERT_INFO} instead, keyed by this seq.
     */
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "BANK_CERT_SEQ", precision = 10, scale = 0)
    private Long bankCertSeq;

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
     * Login / history scrape-status columns. Legacy {@code fnsh_0001_001}
     * tracks three independent per-job status codes and synthesises the
     * displayed Success / Failed / Processing state from them via
     * {@code wb_get_acct_result_stts(LST_LGN_STTS, ACCT_BAL_INQ_STTS,
     * ACCT_HIS_INQ_STTS, ACCT_BAL_LST_INQ_DTM)}
     * ({@code IDO.ACCT_MSTR_R004.xml:240}); the c001 sync writes them on
     * every scrape ({@code fnsh_0001_001_c001_act.jsp}).
     *
     * <p>HR-2: the Bank Transaction Sync orchestrator now stamps these
     * (login + history) alongside the already-mapped
     * {@code ACCT_BAL_INQ_STTS} (balance) so a failed scrape leaves the
     * row showing the failure instead of a stale success. Columns added
     * in V23 ({@code VARCHAR(20)} / {@code VARCHAR(255)} /
     * {@code VARCHAR(14)}, all nullable).
     */
    @Column(name = "LST_LGN_STTS", length = 20)
    private String lstLgnStts;

    @Column(name = "LST_LGN_STTS_MSG", length = 255)
    private String lstLgnSttsMsg;

    @Column(name = "ACCT_HIS_INQ_STTS", length = 20)
    private String acctHisInqStts;

    @Column(name = "ACCT_HIS_INQ_MSG", length = 255)
    private String acctHisInqMsg;

    @Column(name = "ACCT_HIS_LST_INQ_DT", length = 14)
    private String acctHisLstInqDt;

    /**
     * Stash of the previous {@code BAL} value at the moment the balance
     * was last corrected by a successful balance-refresh. Mirrors legacy
     * {@code _u001_act.jsp:117-122}: when the engine reports a new
     * balance that differs from what's on the row, the OLD balance is
     * copied here before {@code BAL} is overwritten so users can see the
     * delta. Schema column was added in V23 ({@code NUMERIC(20,6)
     * DEFAULT 0}); first Java consumer is Own Account Transfer's
     * balance-refresh endpoint.
     */
    @Column(name = "CORC_BF_BAL")
    private BigDecimal corcBfBal;

    /**
     * Last successful bank-login timestamp, {@code yyyyMMddHHmmss}.
     * Legacy {@code ACCT_MSTR_U002} stamps this after a successful
     * NXISAS scrape (see {@code fnsh_0001_001_c001_act.jsp:266-287}).
     * The refactor's transaction-sync stub bumps this + {@code
     * ACCT_BAL_LST_INQ_DTM} together so the on-screen "Last Update"
     * column visibly moves without inventing fake transactions.
     * Schema column added in V23 ({@code VARCHAR(14) NULL}).
     */
    @Column(name = "LST_LGN_SUC_DTM", length = 14)
    private String lstLgnSucDtm;

    /**
     * Last successful TRANSACTION-scrape timestamp, {@code yyyyMMddHHmmss}
     * (legacy {@code TRN_LST_SUC_DTM}). This is the column the Bank
     * Transaction Sync popup reads to compute its default scrape window:
     * the earliest {@code TRN_LST_SUC_DTM} across the selected accounts,
     * floored to 3 months back → today, used as ONE batch window for every
     * account (legacy {@code nxisas_tranList.getInitStartDate()} +
     * {@code fnsh_0001_001_r001} parity). Distinct from
     * {@code ACCT_HIS_LST_INQ_DT} (the "Last Update" display column).
     * Schema column is the legacy DEV {@code VARCHAR(14) NULL}.
     */
    @Column(name = "TRN_LST_SUC_DTM", length = 14)
    private String trnLstSucDtm;

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

    @JdbcTypeCode(SqlTypes.CHAR)
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
