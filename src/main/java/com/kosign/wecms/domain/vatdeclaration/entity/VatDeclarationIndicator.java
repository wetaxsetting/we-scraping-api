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
 * Field-split declaration indicators — {@code VAT_DECL_ITEMS}
 * . One row per declaration revision; each
 * column maps 1:1 to an HTKK XML tag ({@code ct21}..{@code ct43}) so the XML
 * exporter can bind by name (the header's {@code indicators} JSONB stays the
 * raw snapshot; this table is the queryable projection).
 *
 * <p>Column naming per the confirmed design sheet (2026-07-15 rev):
 * {@code ct40a} = {@code VAT_PAYABLE_ACTIVITY}, {@code ct40} =
 * {@code VAT_PAYABLE_NET}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "VAT_DECL_ITEMS")
public class VatDeclarationIndicator extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    /** Owning declaration ({@code VAT_DECL.SID}); UK per revision. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "VAT_DECL_ID", nullable = false)
    private VatDeclaration declaration;

    /** ct21 — no purchase/sale activity in the period ("X" flag). */
    @Column(name = "NO_ACTIVITY", precision = 20, scale = 6)
    private BigDecimal noActivity;

    /** ct22 — deductible VAT carried over from the previous period. */
    @Column(name = "PREV_CREDIT", precision = 20, scale = 6)
    private BigDecimal prevCredit;

    /** ct23 — value of purchased goods/services. */
    @Column(name = "PURCH_AMT", precision = 20, scale = 6)
    private BigDecimal purchAmt;

    /** ct24 — VAT of purchased goods/services. */
    @Column(name = "PURCH_AMT_VAT", precision = 20, scale = 6)
    private BigDecimal purchAmtVat;

    /** ct23a — of which: imported goods/services (value). */
    @Column(name = "IMP_AMT", precision = 20, scale = 6)
    private BigDecimal impAmt;

    /** ct24a — of which: imported goods/services (VAT). */
    @Column(name = "IMP_AMT_VAT", precision = 20, scale = 6)
    private BigDecimal impAmtVat;

    /** ct25 — input VAT deductible this period. */
    @Column(name = "DEDUCT_VAT_AMT", precision = 20, scale = 6)
    private BigDecimal deductVatAmt;

    /** ct26 — revenue not subject to declaration/payment. */
    @Column(name = "SALES_AMT_NONDECL", precision = 20, scale = 6)
    private BigDecimal salesAmtNondecl;

    /** ct27 — total taxable sales revenue. */
    @Column(name = "TOL_SALES_AMT", precision = 20, scale = 6)
    private BigDecimal tolSalesAmt;

    /** ct28 — total output VAT on taxable sales. */
    @Column(name = "TOT_SALES_AMT_VAT", precision = 20, scale = 6)
    private BigDecimal totSalesAmtVat;

    /** ct29 — revenue at 0%. */
    @Column(name = "SALES_AMT_0", precision = 20, scale = 6)
    private BigDecimal salesAmt0;

    /** ct30 — revenue at 5%. */
    @Column(name = "SALES_AMT_5", precision = 20, scale = 6)
    private BigDecimal salesAmt5;

    /** ct31 — VAT at 5%. */
    @Column(name = "SALES_AMT_VAT_5", precision = 20, scale = 6)
    private BigDecimal salesAmtVat5;

    /** ct32 — revenue at 10%. */
    @Column(name = "SALES_AMT_10", precision = 20, scale = 6)
    private BigDecimal salesAmt10;

    /** ct33 — VAT at 10%. */
    @Column(name = "SALES_AMT_VAT_10", precision = 20, scale = 6)
    private BigDecimal salesAmtVat10;

    /** ct32a — non-taxable revenue. */
    @Column(name = "SALES_AMT_NONTAX", precision = 20, scale = 6)
    private BigDecimal salesAmtNontax;

    /** ct33a — VAT of goods/services at other rates. */
    @Column(name = "SALES_VAT_OTHER", precision = 20, scale = 6)
    private BigDecimal salesVatOther;

    /** ct34 — total sales revenue ([34] = [26] + [27]). */
    @Column(name = "TOTAL_SALES_AMT", precision = 20, scale = 6)
    private BigDecimal totalSalesAmt;

    /** ct35 — total output VAT ([35] = [28]). */
    @Column(name = "TOTAL_SALES_AMT_VAT", precision = 20, scale = 6)
    private BigDecimal totalSalesAmtVat;

    /** ct36 — VAT incurred in the period ([36] = [35] − [25]). */
    @Column(name = "VAT_INCURRED", precision = 20, scale = 6)
    private BigDecimal vatIncurred;

    /** ct37 — downward adjustment. */
    @Column(name = "ADJ_DECREASE", precision = 20, scale = 6)
    private BigDecimal adjDecrease;

    /** ct38 — upward adjustment. */
    @Column(name = "ADJ_INCREASE", precision = 20, scale = 6)
    private BigDecimal adjIncrease;

    /** ct39a — VAT received via handover, deductible this period. */
    @Column(name = "TRANSF_VAT", precision = 20, scale = 6)
    private BigDecimal transfVat;

    /** ct40a — VAT payable from business activity {([36]−[22]+[37]−[38]−[39a]) ≥ 0}. */
    @Column(name = "VAT_PAYABLE_ACTIVITY", precision = 20, scale = 6)
    private BigDecimal vatPayableActivity;

    /** ct40b — investment-project input VAT offset ([40b] ≤ [40a]). */
    @Column(name = "INVEST_OFFSET_VAT_AMT", precision = 20, scale = 6)
    private BigDecimal investOffsetVatAmt;

    /** ct40 — VAT still payable this period ([40] = [40a] − [40b]). */
    @Column(name = "VAT_PAYABLE_NET", precision = 20, scale = 6)
    private BigDecimal vatPayableNet;

    /** ct41 — VAT not yet fully deducted {([36]−[22]+[37]−[38]−[39a]) ≤ 0}. */
    @Column(name = "VAT_NOT_DEDUCTED", precision = 20, scale = 6)
    private BigDecimal vatNotDeducted;

    /** ct42 — VAT requested for refund ([42] ≤ [41]). */
    @Column(name = "VAT_REFUND_REQ", precision = 20, scale = 6)
    private BigDecimal vatRefundReq;

    /** ct42a — of [42]: input-VAT account 1331 (goods &amp; services). Breakdown only, no formula. */
    @Column(name = "VAT_REFUND_REQ_1331", precision = 20, scale = 6)
    private BigDecimal vatRefundReq1331;

    /** ct42b — of [42]: input-VAT account 1332 (fixed assets). Breakdown only, no formula. */
    @Column(name = "VAT_REFUND_REQ_1332", precision = 20, scale = 6)
    private BigDecimal vatRefundReq1332;

    /** ct43 — deductible VAT carried to the next period ([43] = [41] − [42]). */
    @Column(name = "CREDIT_NEXT", precision = 20, scale = 6)
    private BigDecimal creditNext;

    @Column(name = "DEL_YN", length = 1, nullable = false)
    private String delYn;

    @Builder
    public VatDeclarationIndicator(VatDeclaration declaration,
                                   BigDecimal noActivity, BigDecimal prevCredit,
                                   BigDecimal purchAmt, BigDecimal purchAmtVat,
                                   BigDecimal impAmt, BigDecimal impAmtVat,
                                   BigDecimal deductVatAmt, BigDecimal salesAmtNondecl,
                                   BigDecimal tolSalesAmt, BigDecimal totSalesAmtVat,
                                   BigDecimal salesAmt0, BigDecimal salesAmt5, BigDecimal salesAmtVat5,
                                   BigDecimal salesAmt10, BigDecimal salesAmtVat10,
                                   BigDecimal salesAmtNontax, BigDecimal salesVatOther,
                                   BigDecimal totalSalesAmt, BigDecimal totalSalesAmtVat,
                                   BigDecimal vatIncurred, BigDecimal adjDecrease, BigDecimal adjIncrease,
                                   BigDecimal transfVat, BigDecimal vatPayableActivity,
                                   BigDecimal investOffsetVatAmt, BigDecimal vatPayableNet,
                                   BigDecimal vatNotDeducted, BigDecimal vatRefundReq,
                                   BigDecimal vatRefundReq1331, BigDecimal vatRefundReq1332,
                                   BigDecimal creditNext, String delYn) {
        this.declaration = declaration;
        this.noActivity = noActivity;
        this.prevCredit = prevCredit;
        this.purchAmt = purchAmt;
        this.purchAmtVat = purchAmtVat;
        this.impAmt = impAmt;
        this.impAmtVat = impAmtVat;
        this.deductVatAmt = deductVatAmt;
        this.salesAmtNondecl = salesAmtNondecl;
        this.tolSalesAmt = tolSalesAmt;
        this.totSalesAmtVat = totSalesAmtVat;
        this.salesAmt0 = salesAmt0;
        this.salesAmt5 = salesAmt5;
        this.salesAmtVat5 = salesAmtVat5;
        this.salesAmt10 = salesAmt10;
        this.salesAmtVat10 = salesAmtVat10;
        this.salesAmtNontax = salesAmtNontax;
        this.salesVatOther = salesVatOther;
        this.totalSalesAmt = totalSalesAmt;
        this.totalSalesAmtVat = totalSalesAmtVat;
        this.vatIncurred = vatIncurred;
        this.adjDecrease = adjDecrease;
        this.adjIncrease = adjIncrease;
        this.transfVat = transfVat;
        this.vatPayableActivity = vatPayableActivity;
        this.investOffsetVatAmt = investOffsetVatAmt;
        this.vatPayableNet = vatPayableNet;
        this.vatNotDeducted = vatNotDeducted;
        this.vatRefundReq = vatRefundReq;
        this.vatRefundReq1331 = vatRefundReq1331;
        this.vatRefundReq1332 = vatRefundReq1332;
        this.creditNext = creditNext;
        this.delYn = delYn == null ? "N" : delYn;
    }
}
