package com.kosign.wecms.domain.setting.firmbankingsettings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Minimal projection of {@code TB_FIRM_BANK_CODE} (V59) — the Korean
 * firm-banking bank-code dictionary used by the firm-banking-settings
 * Create endpoint's V10 referential check
 * ({@code (ANT_DIV_CD='NAPAS', BANK_CD=outBankCd)} must exist; see
 * JEX {@code comm_0220_002_c001_act.jsp:116-121}).
 *
 * <p><b>Sprint scope (column subset).</b> The legacy DEV table has 14
 * columns; this entity maps only the two columns the Create check
 * needs ({@code BANK_CD} as PK + {@code ANT_DIV_CD} as predicate
 * column). Hibernate {@code ddl-auto = validate} is fine with this —
 * it complains about entity columns that are missing from the DB,
 * not the other way around. When a future feature needs more
 * columns (e.g. a refactor of bank registry CRUD), extend this
 * entity in that MR; do not pre-emptively widen here.
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
public class TbFirmBankCode {

    /** NAPAS bank code (e.g. {@code 970424} Shinhan / {@code 970436}
     *  Vietcombank / {@code 970457} Woori). PK on the legacy table. */
    @Id
    @Column(name = "BANK_CD", length = 15, nullable = false)
    private String bankCd;

    /** Ancillary-division code; the V10 check filters on
     *  {@code 'NAPAS'} per the legacy action JSP. */
    @Column(name = "ANT_DIV_CD", length = 5, nullable = false)
    private String antDivCd;

    /** 3-character CITAD bank code (e.g. {@code 203} VCB). Populated
     *  for CITAD rows so the Internal Transfer Excel parser can
     *  resolve a 3-char user input to a bank name. */
    @Column(name = "CITAD_BANK_CD", length = 3)
    private String citadBankCd;

    /** Vietnamese long-form bank name. Fallback display when
     *  {@link #engAbrvatNm} is blank (matches the legacy parser's
     *  preference for the abbreviated English name). */
    @Column(name = "BANK_NM", length = 300)
    private String bankNm;

    /** English-abbreviated bank name (e.g. {@code "ShinhanBank"},
     *  {@code "WooriBank"}, {@code "VCB"}). What the legacy
     *  {@code pymn_0009_002} parser returns from {@code getBankName}
     *  and stamps into {@code TAR_BANK_NM} on the parsed-line wire. */
    @Column(name = "ENG_ABRVAT_NM", length = 300)
    private String engAbrvatNm;
}
