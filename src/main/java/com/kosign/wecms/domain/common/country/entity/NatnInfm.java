package com.kosign.wecms.domain.common.country.entity;

import com.kosign.wecms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Country master row — backs the global phone-country picker.
 *
 * <p>Mirrors the legacy {@code wabooks.entity.NatnInfm} 1:1 on the
 * columns the refactor consumes today. Audit columns inherited from
 * {@link BaseEntity} (TIMESTAMPTZ) deviate from DEV's VARCHAR(14) —
 * documented project-wide refactor convention.
 *
 * <p>Single-tenant reference data (no {@code BIZ_KEY}). All tenants
 * read the same country list — the JEX live system uses the table the
 * same way (see {@code IDO.NATN_INFM_R002.xml}).
 */
@Entity
@Table(name = "NATN_INFM")
@Getter
@Setter
@NoArgsConstructor
public class NatnInfm extends BaseEntity {

    /** ISO 3166-1 alpha-2 country code — primary key (e.g. {@code "VN"}). */
    @Id
    @Column(name = "NTNL_CD", length = 5, nullable = false)
    private String ntnlCd;

    /** Display name in English (e.g. {@code "Vietnam"}). */
    @Column(name = "NATL_NM", length = 100)
    private String natlNm;

    /** Dial code WITHOUT the leading {@code '+'} (e.g. {@code "84"}). */
    @Column(name = "NATN_NUM", length = 5)
    private String natnNum;

    /** Olson timezone id (e.g. {@code "Asia/Ho_Chi_Minh"}). */
    @Column(name = "TIME_ZONE", length = 20)
    private String timeZone;

    @Column(name = "NOTE", length = 255)
    private String note;
}
