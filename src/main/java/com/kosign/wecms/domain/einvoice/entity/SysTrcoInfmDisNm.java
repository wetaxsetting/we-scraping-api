package com.kosign.wecms.domain.einvoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * Global company-form → abbreviation reference table backing the legacy
 * display-name normalisation (legacy {@code SYS_TRCO_INFM_DIS_NM}).
 *
 * <p><b>Read at runtime only through the ported DB function</b>
 * {@code wb_get_normalized_trco_nick_nm(name)} (V251), consumed via
 * {@code LegacyFunctionRepository.getNormalizedTrcoNickNm} &mdash; the exact
 * legacy path (IDO {@code SYS_TRCO_INFM_DIS_NM_R002}:
 * {@code SELECT wb_get_normalized_trco_nick_nm(:NAME)}) and the same pattern
 * as {@code wb_get_napas_bank_nm_or_citad_bank_nm}. This JPA entity is mapped
 * for inventory completeness + schema validation coverage; the normalisation
 * logic lives in the ported function (do NOT reimplement it in Java &mdash; it
 * must byte-match legacy). Global reference data: NO {@code BIZ_KEY}.
 *
 * <p>Added to the legacy-table inventory 2026-07-14 (PO decision, D5.2b).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "SYS_TRCO_INFM_DIS_NM")
public class SysTrcoInfmDisNm {

    /** PK &mdash; {@code NUMERIC(10,0)} in DEV; mapped {@code BigDecimal} so
     *  Hibernate {@code validate} accepts the numeric column. */
    @Id
    @Column(name = "SEQ_NO", precision = 10, scale = 0, nullable = false)
    private BigDecimal seqNo;

    /** Abbreviation (e.g. {@code "CTY TNHH"}). */
    @Column(name = "ABBR_NM", length = 50)
    private String abbrNm;

    /** Full company-form phrase matched against the contact name. */
    @Column(name = "ABBR_FUL_NM", length = 100)
    private String abbrFulNm;

    /** English full name (reference only). */
    @Column(name = "ABBR_GGL_FUL_NM", length = 100)
    private String abbrGglFulNm;

    /** {@code 'Y'} = long full-name → abbreviation; {@code 'N'} = short
     *  abbreviation to strip. CHAR(1). */
    @Column(name = "DISP_YN", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String dispYn;

    @Column(name = "REG_ID", length = 100)
    private String regId;

    @Column(name = "REG_DTM", length = 14)
    private String regDtm;
}
