package com.kosign.wecms.domain.vatdeclaration.entity;

import com.kosign.wecms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One annex line — {@code TAX_DECLARATION_ANNEX_ITEMS}. A single generic table
 * covers all four annex types via the {@code ANNEX_TYPE} discriminator
 * ({@link AnnexType}): BKBR (01-1/GTGT output), BKMV (01-2/GTGT input), ALLOC
 * (01-6/GTGT allocation) and REDUCE8 (8% reduction, NQ 204/2025).
 *
 * <p>Invoice-listing columns are common to BKBR/BKMV/REDUCE8; the two
 * type-specific columns are nullable — {@code reducedAmt} (REDUCE8 only) and
 * {@code allocRatio} (ALLOC/01-6 only). The exact 01-6 allocation column set is
 * still PO-pending (OQ-VAT-9); {@code allocRatio} is the placeholder until then.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "TAX_DECLARATION_ANNEX_ITEMS")
public class VatDeclarationAnnexItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    /** Owning declaration ({@code TAX_DECLARATIONS.SID}); tenant scope rides the join. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "VAT_DECL_ID", nullable = false)
    private VatDeclaration declaration;

    /** Which annex this row belongs to — {@link AnnexType#code()}. */
    @Column(name = "ANNEX_TYPE", length = 10, nullable = false)
    private String annexType;

    /** {@code 'I'} input (purchase) / {@code 'O'} output (sale). */
    @Column(name = "IO_DV", length = 1)
    private String ioDv;

    /** FK-by-value to {@code BUSA_DOC_MSTR.DOC_ID}; NULL = manual row. */
    @Column(name = "SRC_DOC_ID", length = 50)
    private String srcDocId;

    @Column(name = "INV_NO", length = 50)
    private String invNo;

    @Column(name = "INV_SYMBOL", length = 20)
    private String invSymbol;

    /** Issue date {@code YYYYMMDD}. */
    @Column(name = "INV_DT", length = 8)
    private String invDt;

    /** Counterparty tax code (buyer for BKBR, seller for BKMV). */
    @Column(name = "TRCO_TAX_CODE", length = 14)
    private String trcoTaxCode;

    @Column(name = "TRCO_NM", length = 400)
    private String trcoNm;

    /** Goods/services name — grouping key for the accordion modal. */
    @Column(name = "PROD_NM", length = 300)
    private String prodNm;

    /** Tax-exclusive value. */
    @Column(name = "SPLY_AMT", precision = 20, scale = 6)
    private BigDecimal splyAmt;

    @Column(name = "VAT_RATE", precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Column(name = "VAT_AMT", precision = 20, scale = 6)
    private BigDecimal vatAmt;

    /** 2% reduction amount — REDUCE8 only. */
    @Column(name = "REDUCED_AMT", precision = 20, scale = 6)
    private BigDecimal reducedAmt;

    /** Allocation ratio — ALLOC (01-6) only (exact shape PO-pending). */
    @Column(name = "ALLOC_RATIO", precision = 9, scale = 6)
    private BigDecimal allocRatio;

    /** {@code 'Y'} = manually added — re-sync must not overwrite/delete it. */
    @Column(name = "HAND_REG_YN", length = 1, nullable = false)
    private String handRegYn;

    @Column(name = "DISP_ORD")
    private Integer dispOrd;

    @Column(name = "DEL_YN", length = 1, nullable = false)
    private String delYn;

    @Builder
    public VatDeclarationAnnexItem(VatDeclaration declaration, String annexType, String ioDv,
                                   String srcDocId, String invNo, String invSymbol, String invDt,
                                   String trcoTaxCode, String trcoNm, String prodNm,
                                   BigDecimal splyAmt, BigDecimal vatRate, BigDecimal vatAmt,
                                   BigDecimal reducedAmt, BigDecimal allocRatio,
                                   String handRegYn, Integer dispOrd, String delYn) {
        this.declaration = declaration;
        this.annexType = annexType;
        this.ioDv = ioDv;
        this.srcDocId = srcDocId;
        this.invNo = invNo;
        this.invSymbol = invSymbol;
        this.invDt = invDt;
        this.trcoTaxCode = trcoTaxCode;
        this.trcoNm = trcoNm;
        this.prodNm = prodNm;
        this.splyAmt = splyAmt;
        this.vatRate = vatRate;
        this.vatAmt = vatAmt;
        this.reducedAmt = reducedAmt;
        this.allocRatio = allocRatio;
        this.handRegYn = handRegYn == null ? "N" : handRegYn;
        this.dispOrd = dispOrd;
        this.delYn = delYn == null ? "N" : delYn;
    }
}
