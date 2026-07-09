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

/**
 * Projection of {@code TB_FIRM_BANK_CODE} (V59) — the Korean
 * firm-banking bank-code dictionary.
 *
 * <p>The dictionary is read by two domains today:
 * <ul>
 *   <li><b>firm-banking-settings</b> — V10 referential check
 *       ({@code (ANT_DIV_CD='NAPAS', BANK_CD=outBankCd)} must exist;
 *       see JEX {@code comm_0220_002_c001_act.jsp:116-121}).</li>
 *   <li><b>contacts</b> — bank-picker autocomplete for the Add /
 *       Edit Contact dialog; reads NAPAS and CITAD rows + their
 *       display labels and status (mirrors legacy
 *       {@code comm_0109_001_r002}, called from
 *       {@code comm_0184_002.ts:44} via {@code cardComboDataNapasSearch}).</li>
 * </ul>
 *
 * <p>Column scope was widened in MR-contacts-add to expose the
 * display fields the bank picker needs ({@code CITAD_BANK_CD},
 * {@code BANK_NM}, {@code ENG_ABRVAT_NM}, {@code BANK_ENG_NM},
 * {@code BANK_STAS}). Hibernate {@code ddl-auto = validate} is fine
 * with the partial subset — the DB still has unmapped columns
 * ({@code BANK_BRANCH_NM}, {@code BANK_BRANCH_ENG_NM},
 * {@code REG_DTTM}, {@code MOD_DTTM}, {@code STANDARD_NAME},
 * {@code DIRECT_BANK_CODE}), the validator only flags entity columns
 * that are missing from the DB. {@code BANK_BRANCH_DIV} was mapped in
 * MR-contacts-bankpicker-dedup for the CITAD headquarters filter.
 *
 * <p>The matching {@link com.kosign.wecms.domain.setting.firmbankingsettings.repository.TbFirmBankCodeRepository}
 * is read-only; the dictionary is global (not tenant-scoped — there
 * is no {@code BIZ_KEY} column).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "TB_FIRM_BANK_CODE")
@IdClass(TbFirmBankCodeId.class)
public class TbFirmBankCode {

    /** NAPAS bank code (e.g. {@code 970424} Shinhan / {@code 970436}
     *  Vietcombank / {@code 970457} Woori) for {@code ANT_DIV_CD='NAPAS'}
     *  rows; CITAD code for {@code ANT_DIV_CD='CITAD'} rows. Part of the
     *  composite legacy PK {@code (ANT_DIV_CD, BANK_CD)}. */
    @Id
    @Column(name = "BANK_CD", length = 15, nullable = false)
    private String bankCd;

    /** Ancillary-division code; {@code 'NAPAS'} / {@code 'CITAD'}.
     *  Discriminator the contacts bank picker uses to route the
     *  selected code back as either {@code napas_bank_cd} or
     *  {@code citad_bank_cd} on the Save payload. */
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ANT_DIV_CD", length = 5, nullable = false)
    private String antDivCd;

    /** Companion CITAD code on a NAPAS row (rarely populated); the
     *  primary CITAD code lives in {@link #bankCd} for
     *  {@code ANT_DIV_CD='CITAD'} rows. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "CITAD_BANK_CD", length = 3)
    private String citadBankCd;

    /** Localised bank name (Vietnamese). Falls back to
     *  {@link #bankEngNm} on the wire when blank. */
    @Column(name = "BANK_NM", length = 300)
    private String bankNm;

    /** English bank name — drives the dropdown label when locale is
     *  English. */
    @Column(name = "BANK_ENG_NM", length = 300)
    private String bankEngNm;

    /** English abbreviation (e.g. {@code "ShinhanBank"} for
     *  {@code 970424}) — legacy popup renders this as the prefix
     *  before the long bank name. */
    @Column(name = "ENG_ABRVAT_NM", length = 300)
    private String engAbrvatNm;

    /** Status flag (`'Y'`/`'N'`). Active-bank flag in the legacy
     *  dictionary (legacy {@code R002} filters {@code BANK_STAS='1'}).
     *  NOT the recipient-flow discriminator — that is derived from
     *  NAPAS availability in {@code ContactsService.listBanks()}. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "BANK_STAS", length = 1)
    private String bankStas;

    /** Bank-branch division. {@code '1'} = headquarters row (one per
     *  bank), blank = a branch row. The contacts bank picker keeps only
     *  CITAD {@code '1'} rows (legacy {@code R002} {@code CITAD_BKCD}
     *  CTE: {@code BANK_BRANCH_DIV = '1'}) so a bank is not repeated
     *  once per CITAD branch. {@code VARCHAR(1)} in the DB — no
     *  {@code @JdbcTypeCode(CHAR)}. */
    @Column(name = "BANK_BRANCH_DIV", length = 1)
    private String bankBranchDiv;
}
