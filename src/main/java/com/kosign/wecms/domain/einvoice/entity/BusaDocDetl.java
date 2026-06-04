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
 * Per-invoice line item — mirrors legacy {@code wabooks/entity/BusaDocDetl.java}
 * ({@code BUSA_DOC_DETL}). Written by the e-Invoice phase-2 detail-save
 * (refactor port of {@code nxisas_einvoice_c002}, product-line branch
 * {@code nxisas_einvoice_c002_act.jsp:740-789}).
 *
 * <p>Column subset: the V238 table carries every column the legacy entity
 * declares; this JPA entity maps the columns the detail-save writes
 * (Hibernate {@code validate} ignores the unmapped {@code TAX_AMT} /
 * {@code RCV_PAY_AMT} / {@code NOTE} / {@code TXTN_PTRN} / {@code ITEM_DETL}
 * / {@code UOM} / {@code BEF_DIS_AMT} columns).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "BUSA_DOC_DETL")
@IdClass(BusaDocDetlId.class)
public class BusaDocDetl {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "DOC_ID", length = 50, nullable = false)
    private String docId;

    @Id
    @Column(name = "DISP_ORD", nullable = false)
    private Integer dispOrd;

    @Column(name = "USER_ID", length = 100)
    private String userId;

    @Column(name = "ITEM_CD", length = 50)
    private String itemCd;

    @Column(name = "ITEM_NM", length = 500)
    private String itemNm;

    @Column(name = "ITEM_SPEC", length = 4000)
    private String itemSpec;

    @Column(name = "ITEM_QUNT")
    private BigDecimal itemQunt;

    @Column(name = "ITEM_UNIT_AMT")
    private BigDecimal itemUnitAmt;

    @Column(name = "SPLY_AMT")
    private BigDecimal splyAmt;

    @Column(name = "DC_AMT")
    private BigDecimal dcAmt;

    @Column(name = "CCY_CD", length = 3)
    private String ccyCd;

    @Column(name = "EINV_PRODUCTNAME", length = 500)
    private String einvProductname;

    @Column(name = "EINV_TAXRATE", length = 10)
    private String einvTaxrate;

    @Column(name = "REG_ID", length = 100)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;
}
