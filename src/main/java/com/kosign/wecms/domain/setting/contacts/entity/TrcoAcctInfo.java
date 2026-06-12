package com.kosign.wecms.domain.setting.contacts.entity;

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
 * Settings &gt; Contacts &mdash; bank account row (Local contact only).
 * Mirrors legacy {@code TRCO_ACCT_INFO} column-for-column for the
 * Sprint subset.
 *
 * <p>Bank-name resolution &mdash; {@link #bankNm} stores the legacy
 * display copy verbatim from DEV. The list / detail paths additionally
 * resolve a fresh display name via the legacy DB function
 * {@code wb_get_napas_bank_nm_or_citad_bank_nm} (OQ-7 resolved
 * 2026-05-12) through {@code LegacyFunctionRepository.resolveNapasOrCitadBankName}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "TRCO_ACCT_INFO")
@IdClass(TrcoAcctInfoId.class)
public class TrcoAcctInfo {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "TRCO_KEY", length = 20, nullable = false)
    private String trcoKey;

    @Id
    @Column(name = "TRCO_ACCT_KEY", length = 20, nullable = false)
    private String trcoAcctKey;

    @Column(name = "ACCT_NO", length = 100, nullable = false)
    private String acctNo;

    /** Display order &mdash; legacy default {@code 1}. JEX
     *  {@code NUMERIC(10,0)} &mdash; mapped via {@code @JdbcTypeCode(NUMERIC)}
     *  so Hibernate validate passes against the live JEX column (2026-06-09
     *  JEX-parity alignment; supersedes the earlier V89 local-INTEGER
     *  deviation). */
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "DISP_ORD", precision = 10, scale = 0, nullable = false)
    private Integer dispOrd;

    /** Bank display name (legacy stores the resolved name verbatim). */
    @Column(name = "BANK_NM", length = 200)
    private String bankNm;

    /** NAPAS code (Vietnamese bank registry). One of NAPAS / CITAD
     *  is expected on Local accounts, but DEV permits both NULL. */
    @Column(name = "NAPAS_BANK_CD", length = 10)
    private String napasBankCd;

    /** CITAD code (Vietnamese bank registry). Mutually exclusive with
     *  {@link #napasBankCd} in practice but DEV does not enforce it. */
    @Column(name = "CITAD_BANK_CD", length = 8)
    private String citadBankCd;

    /** Account-currency code &mdash; {@code 'VND'}, {@code 'USD'}, etc.
     *  No CHECK constraint on DEV. */
    @Column(name = "ACCT_CCY_CD", length = 3)
    private String acctCcyCd;

    /** Account owner display name (often the contact's name &mdash;
     *  legacy free text, no FK). */
    @Column(name = "FINC_MDA_OWNER_NM", length = 150)
    private String fincMdaOwnerNm;

    @Column(name = "OWAC_ADDR", length = 500)
    private String owacAddr;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100, nullable = false)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;
}
