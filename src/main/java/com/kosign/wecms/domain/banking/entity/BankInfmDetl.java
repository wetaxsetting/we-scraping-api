package com.kosign.wecms.domain.banking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Per-bank detail attributes — mirrors legacy {@code wecmsvndev.bank_infm_detl}.
 *
 * <p>Read-only reference data. The one attribute the refactor consumes today is
 * the <b>short display name</b> ({@code BANK_DETL_INFO_CD = '10'}, date-effective
 * via {@code START_DATE}/{@code END_DATE}) — e.g. {@code "Shinhan VN"} vs the
 * {@code bank_infm.rprs_bank_nm} long name {@code "Shinhan(Vietnam)"}. This is
 * the legacy {@code wb_get_bank_nm(bank_cd, 'S')} source. Sprint subset: only
 * the PK columns + {@code BANK_DETL_INFO_CONT} are mapped ({@code ddl-auto=
 * validate} ignores the unmapped audit columns).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "BANK_INFM_DETL")
@IdClass(BankInfmDetlId.class)
public class BankInfmDetl {

    @Id
    @Column(name = "BANK_CD", length = 20, nullable = false)
    private String bankCd;

    @Id
    @Column(name = "BANK_DETL_INFO_CD", length = 3, nullable = false)
    private String bankDetlInfoCd;

    @Id
    @Column(name = "START_DATE", length = 8, nullable = false)
    private String startDate;

    @Id
    @Column(name = "END_DATE", length = 8, nullable = false)
    private String endDate;

    @Id
    @Column(name = "SEQ_NO", nullable = false)
    private BigDecimal seqNo;

    @Column(name = "BANK_DETL_INFO_CONT", length = 2000, nullable = false)
    private String bankDetlInfoCont;
}
