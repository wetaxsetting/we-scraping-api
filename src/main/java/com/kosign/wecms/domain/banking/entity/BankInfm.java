package com.kosign.wecms.domain.banking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Vietnamese bank registry — mirrors legacy wecmsvndev.bank_infm.
 * Bank codes are global (not tenant-scoped); seeded from the real DEV
 * registry by V20.
 *
 * Per the 2026-04-29 audit: every banking page that needs a display
 * name MUST resolve it through {@link BankInfmRepository} — never via
 * an in-code static map.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "BANK_INFM")
public class BankInfm {

    @Id
    @Column(name = "BANK_CD", length = 20, nullable = false)
    private String bankCd;

    @Column(name = "START_DATE", length = 8, nullable = false)
    private String startDate;

    @Column(name = "END_DATE", length = 8, nullable = false)
    private String endDate;

    @Column(name = "RPRS_BANK_NM", length = 200, nullable = false)
    private String rprsBankNm;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100)
    private String modId;

    @Column(name = "MOD_DTM", length = 14)
    private String modDtm;
}
