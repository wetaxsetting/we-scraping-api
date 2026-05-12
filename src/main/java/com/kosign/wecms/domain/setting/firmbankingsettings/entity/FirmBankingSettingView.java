package com.kosign.wecms.domain.setting.firmbankingsettings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * Read-only projection of {@code V_FIRM_BANKING_SETTING} (V64), the
 * list-view shape that backs the firm-banking-settings list endpoint.
 * Mirrors legacy {@code IDO.TB_FIRM_CONF_R004} column-for-column.
 *
 * <p>Two LEFT JOINs are folded into the view (so the entity stays
 * flat):
 * <ul>
 *   <li>{@code ACCT_MSTR} on {@code (BIZ_KEY, WDRL_ACT_NO = ACCT_NO)}
 *       — populates {@link #ccyCd}, {@link #bal}, {@link #expiDt}.
 *       A firm-conf row with no matching ledger row still appears
 *       (nulls).</li>
 *   <li>{@code TB_FIRM_BANK_CODE} on {@code (ANT_DIV_CD='NAPAS',
 *       BANK_CD = OUT_BANK_CD)} — populates {@link #bankNm}
 *       (COALESCE of localized + English bank name).</li>
 * </ul>
 *
 * <p>{@link Immutable @Immutable} flags the entity as read-only —
 * Hibernate skips dirty-checking and never flushes state changes.
 * Tenant scoping is the caller's responsibility: every repository
 * method must constrain on {@code BIZ_KEY} from
 * {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Immutable
@Table(name = "V_FIRM_BANKING_SETTING")
@IdClass(FirmBankingSettingId.class)
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
