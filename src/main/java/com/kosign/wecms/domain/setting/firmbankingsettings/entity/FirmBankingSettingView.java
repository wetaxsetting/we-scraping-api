package com.kosign.wecms.domain.setting.firmbankingsettings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * Read-only projection backing the firm-banking-settings list endpoint.
 *
 * <p><b>Backed by a Hibernate {@link Subselect @Subselect}, NOT a DB table or
 * view.</b> The legacy JEX DB has no {@code v_firm_banking_setting} object — the
 * list is served by {@code IDO.TB_FIRM_CONF_R004.xml} ({@code TB_FIRM_CONF LEFT
 * JOIN ACCT_MSTR}). An earlier refactor materialized that query (plus a bank-name
 * enrichment) as a {@code V_FIRM_BANKING_SETTING} view (archived V64), but the
 * view does not exist in legacy JEX and a refactor-only view is not provisioned.
 * Instead this entity carries the same query as a {@code @Subselect}, so
 * Hibernate runs it against the legacy base tables and {@code ddl-auto: validate}
 * never looks for a {@code v_firm_banking_setting} relation.
 *
 * <p>Two LEFT JOINs are folded in (so the entity stays flat) — both targets are
 * legacy tables, so no refactor-only object is involved:
 * <ul>
 *   <li>{@code ACCT_MSTR} on {@code (BIZ_KEY, WDRL_ACT_NO = ACCT_NO)} — the legacy
 *       {@code IDO.TB_FIRM_CONF_R004} join; populates {@link #ccyCd}, {@link #bal},
 *       {@link #expiDt}. A firm-conf row with no matching ledger row still appears
 *       (nulls).</li>
 *   <li>{@code TB_FIRM_BANK_CODE} on {@code (ANT_DIV_CD='NAPAS', BANK_CD =
 *       OUT_BANK_CD)} — populates {@link #bankNm} (COALESCE of localized + English
 *       bank name). The legacy IDO omits this (JEX resolves the bank name
 *       frontend-side); the refactor resolves it server-side over the legacy
 *       {@code TB_FIRM_BANK_CODE} dictionary so the wire shape stays stable.</li>
 * </ul>
 *
 * <p>{@link Immutable @Immutable} flags the entity read-only. {@link Synchronize
 * @Synchronize} lists the base tables so Hibernate auto-flushes pending writes to
 * them before querying this entity (avoids stale reads). Tenant scoping is the
 * caller's responsibility: every repository method must constrain on
 * {@code BIZ_KEY} from
 * {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Immutable
@Synchronize({"TB_FIRM_CONF", "ACCT_MSTR", "TB_FIRM_BANK_CODE"})
@Subselect("""
        SELECT
            TFC.BIZ_KEY                                 AS BIZ_KEY,
            TFC.OUT_BANK_CD                             AS OUT_BANK_CD,
            COALESCE(TFBC.BANK_NM, TFBC.BANK_ENG_NM)    AS BANK_NM,
            AM.CCY_CD                                   AS CCY_CD,
            TFC.BANK_PRTN_ID                            AS BANK_PRTN_ID,
            TFC.VAN_ORG_CD                              AS VAN_ORG_CD,
            TFC.ORG_STAT                                AS ORG_STAT,
            TFC.WDRL_ACT_NO                             AS WDRL_ACT_NO,
            TFC.FILE_TYPE                               AS FILE_TYPE,
            TFC.BAT_TRAN_BRANCH_DIV                     AS BAT_TRAN_BRANCH_DIV,
            TFC.BAT_TRAN_BRANCH_LIMIT                   AS BAT_TRAN_BRANCH_LIMIT,
            TFC.REG_DTTM                                AS REG_DTTM,
            TFC.START_DTTM                              AS START_DTTM,
            TFC.END_DTTM                                AS END_DTTM,
            TFC.MOD_DTTM                                AS MOD_DTTM,
            TFC.LAST_EDTR                               AS LAST_EDTR,
            AM.BAL                                      AS BAL,
            AM.EXPI_DT                                  AS EXPI_DT
        FROM TB_FIRM_CONF TFC
        LEFT JOIN ACCT_MSTR AM
            ON  TFC.BIZ_KEY     = AM.BIZ_KEY
            AND TFC.WDRL_ACT_NO = AM.ACCT_NO
        LEFT JOIN TB_FIRM_BANK_CODE TFBC
            ON  TFBC.ANT_DIV_CD = 'NAPAS'
            AND TFBC.BANK_CD    = TFC.OUT_BANK_CD
        """)
@IdClass(TbFirmConfId.class)
public class FirmBankingSettingView {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "OUT_BANK_CD", length = 15, nullable = false)
    private String outBankCd;

    @Id
    @Column(name = "WDRL_ACT_NO", length = 100, nullable = false)
    private String wdrlActNo;

    /** Localized bank name from {@code TB_FIRM_BANK_CODE} (COALESCE
     *  of {@code BANK_NM} and {@code BANK_ENG_NM}). Nullable when the
     *  bank code is not in the dictionary. */
    @Column(name = "BANK_NM", length = 300)
    private String bankNm;

    /** Currency from the joined {@code ACCT_MSTR} row. Nullable when
     *  no matching ledger row exists. */
    @Column(name = "CCY_CD", length = 3)
    private String ccyCd;

    @Column(name = "BANK_PRTN_ID", length = 20)
    private String bankPrtnId;

    @Column(name = "VAN_ORG_CD", length = 20)
    private String vanOrgCd;

    @Column(name = "ORG_STAT", length = 1)
    private String orgStat;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "FILE_TYPE", length = 3, columnDefinition = "CHAR(3)")
    private String fileType;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "BAT_TRAN_BRANCH_DIV", length = 1, columnDefinition = "CHAR(1)")
    private String batTranBranchDiv;

    @Column(name = "BAT_TRAN_BRANCH_LIMIT", precision = 7, scale = 0)
    private BigDecimal batTranBranchLimit;

    @Column(name = "REG_DTTM", length = 14)
    private String regDttm;

    @Column(name = "START_DTTM", length = 14)
    private String startDttm;

    @Column(name = "END_DTTM", length = 14)
    private String endDttm;

    @Column(name = "MOD_DTTM", length = 14)
    private String modDttm;

    @Column(name = "LAST_EDTR", length = 50)
    private String lastEdtr;

    /** Account balance from the joined {@code ACCT_MSTR} row. Nullable
     *  when no matching ledger row exists. */
    @Column(name = "BAL", precision = 23, scale = 6)
    private BigDecimal bal;

    /** Account expiry date ({@code yyyyMMdd}) from the joined
     *  {@code ACCT_MSTR} row. Nullable when not set in the ledger. */
    @Column(name = "EXPI_DT", length = 8)
    private String expiDt;
}
