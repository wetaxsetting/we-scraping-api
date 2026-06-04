package com.kosign.wecms.domain.einvoice.entity;

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
 * Per-invoice tax-rate breakdown line — {@code EINV_TAX_DETL} (V15).
 * Written by the e-Invoice phase-2 detail-save (legacy
 * {@code nxisas_einvoice_c002} tax-array branch {@code :825-865}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "EINV_TAX_DETL")
@IdClass(EinvTaxDetlId.class)
public class EinvTaxDetl {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "DOC_ID", length = 50, nullable = false)
    private String docId;

    @Id
    @Column(name = "DISP_ORD", nullable = false)
    private Integer dispOrd;

    @Column(name = "EINV_TAXRATE", length = 50)
    private String einvTaxrate;

    @Column(name = "AMT")
    private BigDecimal amt;

    @Column(name = "TAX_AMT")
    private BigDecimal taxAmt;

    @Column(name = "REG_ID", length = 100)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;
}
