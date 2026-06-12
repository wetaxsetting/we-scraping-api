package com.kosign.wecms.domain.setting.firmbankingsettings.entity;

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
 * Settings &gt; Firm Banking Settings — outbound firm-banking channel
 * binding. Mirrors legacy JEX {@code wabooks.entity.TbFirmConf} and
 * the {@code TB_FIRM_CONF} table column-for-column (14 columns,
 * V62 migration).
 *
 * <p>Sprint scope (Phase 2 read-only slice — Path A):
 * <ul>
 *   <li>Mapped for Detail-by-PK reads.</li>
 *   <li>List reads go through {@link FirmBankingSettingView} — the
 *       LEFT JOIN view that includes {@code BANK_NM} / {@code CCY_CD}
 *       / {@code BAL} / {@code EXPI_DT} from the join partners.</li>
 *   <li>Create / Update / Delete are out of scope until OQ-8a + the
 *       shared {@code AuditLogService} are resolved (Phase 1 §13).</li>
 * </ul>
 *
 * <p>{@code FILE_TYPE} and {@code BAT_TRAN_BRANCH_DIV} are
 * {@code bpchar} on disk (CHAR(3) / CHAR(1)) for DEV parity. Hibernate
 * maps Java {@code String} to {@code varchar} by default, so the two
 * fields carry {@link JdbcTypeCode @JdbcTypeCode(SqlTypes.CHAR)} so
 * {@code spring.jpa.hibernate.ddl-auto = validate} accepts them. This
 * follows the V38 "direction locked" rule from
 * {@code wecms-api/CLAUDE.md} (entity-side annotation &gt;
 * CHAR→VARCHAR migration).
 *
 * <p>Encryption: NONE for {@code wdrlActNo} or any other field. OQ-4
 * (resolved 2026-05-09) ships plain text. Masking is presentation-
 * layer only; never log the full {@code wdrlActNo} — go through
 * {@link com.kosign.wecms.common.security.LogMask#acct(String)}.
 *
 * <p>Tenant scoping: {@code bizKey} is part of the composite PK and
 * is the first predicate of every repository query. Repositories must
 * read it from {@link com.kosign.wecms.common.tenant.TenantContext}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "TB_FIRM_CONF")
@IdClass(TbFirmConfId.class)
public class TbFirmConf {

    /** Tenant key. PK component. */
    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    /** NAPAS bank code (Shinhan {@code 970424} / Vietcombank {@code 970436} /
     *  Woori {@code 970457}). PK component. */
    @Id
    @Column(name = "OUT_BANK_CD", length = 15, nullable = false)
    private String outBankCd;

    /** Tenant company account number (plain text, see class Javadoc).
     *  PK component. */
    @Id
    @Column(name = "WDRL_ACT_NO", length = 100, nullable = false)
    private String wdrlActNo;

    @Column(name = "BANK_PRTN_ID", length = 20, nullable = false)
    private String bankPrtnId;

    @Column(name = "VAN_ORG_CD", length = 20, nullable = false)
    private String vanOrgCd;

    /** {@code OrgStat} discriminator — string {@code 0..3}
     *  (WAITING / AVAILABLE / SUSPENDED / TERMINATED). DB DEFAULT is
     *  {@code '0'}; legacy DAO override sets {@code '1'} on Create
     *  (Phase 1 V17). */
    @Column(name = "ORG_STAT", length = 1, nullable = false)
    private String orgStat;

    /** {@code bpchar(3)} on disk; entity-side JDBC-type hint per V38
     *  rule. Server-derived from {@link #outBankCd} on Create:
     *  Shinhan→{@code SHB}, Vietcombank→{@code VCB},
     *  Woori→{@code WRB}. Behavior for unmapped codes pending OQ-8a. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "FILE_TYPE", length = 3, nullable = false, columnDefinition = "CHAR(3)")
    private String fileType;

    /** {@code bpchar(1)} on disk; DEFAULT {@code 'Y'}. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "BAT_TRAN_BRANCH_DIV", length = 1, nullable = false,
            columnDefinition = "CHAR(1) DEFAULT 'Y'")
    private String batTranBranchDiv;

    /** {@code NUMERIC(7,0)} — max 9 999 999. Mapped to {@link BigDecimal}
     *  to preserve scale parity with legacy. */
    @Column(name = "BAT_TRAN_BRANCH_LIMIT", nullable = false,
            precision = 7, scale = 0)
    private BigDecimal batTranBranchLimit;

    /** Legacy {@code yyyyMMddHHmmss} string. */
    @Column(name = "REG_DTTM", length = 14, nullable = false)
    private String regDttm;

    @Column(name = "START_DTTM", length = 14)
    private String startDttm;

    @Column(name = "END_DTTM", length = 14)
    private String endDttm;

    @Column(name = "MOD_DTTM", length = 14)
    private String modDttm;

    /** Display name of the user who last edited the row (legacy uses
     *  {@code WbUser.getName()}, not the user id). */
    @Column(name = "LAST_EDTR", length = 20)
    private String lastEdtr;
}
