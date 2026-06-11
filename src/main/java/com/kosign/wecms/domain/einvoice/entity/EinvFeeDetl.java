package com.kosign.wecms.domain.einvoice.entity;

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
 * Per-invoice fee line — {@code EINV_FEE_DETL} (V15). Written by the
 * e-Invoice phase-2 detail-save (legacy {@code nxisas_einvoice_c002}
 * fee-array branch {@code :791-823}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "EINV_FEE_DETL")
@IdClass(EinvFeeDetlId.class)
public class EinvFeeDetl {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "DOC_ID", length = 50, nullable = false)
    private String docId;

    @Id
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "DISP_ORD", precision = 10, scale = 0, nullable = false)
    private Integer dispOrd;

    @Column(name = "EINV_FEENAME", length = 100)
    private String einvFeename;

    @Column(name = "EINV_FEE")
    private BigDecimal einvFee;

    @Column(name = "REG_ID", length = 100)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;
}
