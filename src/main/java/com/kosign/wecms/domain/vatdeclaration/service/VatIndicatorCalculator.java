package com.kosign.wecms.domain.vatdeclaration.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.vatdeclaration.entity.VatDeclarationIndicator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Formula matrix of Mẫu 01/GTGT (TT80) — the backend calculation authority.
 * Mirrors the PO Business-Rules doc §1 (docs/vat-declaration/3.): every
 * derived (grey-cell) indicator is recomputed from the editable inputs with
 * <b>integer VND math</b> (floor on the rate multiplications, never floats)
 * so the exported XML matches HTKK to the đồng.
 *
 * <pre>
 *   [25] = [24]                    [27] = [29]+[30]+[32]+[32a]
 *   [31] = floor([30] × 5%)        [28] = [31]+[33]+[33a]
 *   [33] = floor([32] × 10%)       [34] = [26]+[27]        [35] = [28]
 *   [36] = [35] − [25]
 *   hieu_so = [36] − [22] + [37] − [38] − [39a]
 *   [40a] = max(hieu_so, 0)        [40] = [40a] − [40b]  ([40b] ≤ [40a])
 *   [41]  = max(−hieu_so, 0)       [43] = [41] − [42]    ([42] ≤ [41])
 * </pre>
 */
@Component
public class VatIndicatorCalculator {

    private static final BigDecimal FIVE_PCT_DIVISOR = BigDecimal.valueOf(20);  // ×5%  = ÷20
    private static final BigDecimal TEN_PCT_DIVISOR  = BigDecimal.valueOf(10);  // ×10% = ÷10

    /**
     * Recomputes every derived indicator in place from the editable fields
     * ({@code ct21/22/23/24/23a/24a/26/29/30/32/32a/33a/37/38/39a/40b/42}).
     *
     * @throws BusinessException {@code WBE_VAT_DECL_INVALID_INDICATOR} when an
     *         input is negative, or the [40b] ≤ [40a] / [42] ≤ [41] caps break.
     */
    public void recompute(VatDeclarationIndicator ind) {
        BigDecimal ct22  = nz(ind.getPrevCredit());
        BigDecimal ct24  = nz(ind.getPurchAmtVat());
        BigDecimal ct26  = nz(ind.getSalesAmtNondecl());
        BigDecimal ct29  = nz(ind.getSalesAmt0());
        BigDecimal ct30  = nz(ind.getSalesAmt5());
        BigDecimal ct32  = nz(ind.getSalesAmt10());
        BigDecimal ct32a = nz(ind.getSalesAmtNontax());
        BigDecimal ct33a = nz(ind.getSalesVatOther());
        BigDecimal ct37  = nz(ind.getAdjDecrease());
        BigDecimal ct38  = nz(ind.getAdjIncrease());
        BigDecimal ct39a = nz(ind.getTransfVat());
        BigDecimal ct40b = nz(ind.getInvestOffsetVatAmt());
        BigDecimal ct42  = nz(ind.getVatRefundReq());

        assertNonNegative(ct22, ct24, ct26, ct29, ct30, ct32, ct32a, ct33a,
                ct37, ct38, ct39a, ct40b, ct42,
                nz(ind.getNoActivity()), nz(ind.getPurchAmt()),
                nz(ind.getImpAmt()), nz(ind.getImpAmtVat()));

        BigDecimal ct25 = ct24;
        BigDecimal ct31 = floorDivide(ct30, FIVE_PCT_DIVISOR);
        BigDecimal ct33 = floorDivide(ct32, TEN_PCT_DIVISOR);
        BigDecimal ct27 = ct29.add(ct30).add(ct32).add(ct32a);
        BigDecimal ct28 = ct31.add(ct33).add(ct33a);
        BigDecimal ct34 = ct26.add(ct27);
        BigDecimal ct35 = ct28;
        BigDecimal ct36 = ct35.subtract(ct25);

        BigDecimal hieuSo = ct36.subtract(ct22).add(ct37).subtract(ct38).subtract(ct39a);
        BigDecimal ct40a = hieuSo.max(BigDecimal.ZERO);
        BigDecimal ct41  = hieuSo.negate().max(BigDecimal.ZERO);

        if (ct40b.compareTo(ct40a) > 0 || ct42.compareTo(ct41) > 0) {
            throw new BusinessException("WBE_VAT_DECL_INVALID_INDICATOR");
        }
        BigDecimal ct40 = ct40a.subtract(ct40b);
        BigDecimal ct43 = ct41.subtract(ct42);

        ind.setDeductVatAmt(ct25);
        ind.setSalesAmtVat5(ct31);
        ind.setSalesAmtVat10(ct33);
        ind.setTolSalesAmt(ct27);
        ind.setTotSalesAmtVat(ct28);
        ind.setTotalSalesAmt(ct34);
        ind.setTotalSalesAmtVat(ct35);
        ind.setVatIncurred(ct36);
        ind.setVatPayableActivity(ct40a);
        ind.setVatPayableNet(ct40);
        ind.setVatNotDeducted(ct41);
        ind.setCreditNext(ct43);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Integer floor of {@code value / divisor} — HTKK truncates the rate products. */
    private static BigDecimal floorDivide(BigDecimal value, BigDecimal divisor) {
        return value.divideToIntegralValue(divisor);
    }

    private static void assertNonNegative(BigDecimal... values) {
        for (BigDecimal v : values) {
            if (v.signum() < 0) {
                throw new BusinessException("WBE_VAT_DECL_INVALID_INDICATOR");
            }
        }
    }
}
