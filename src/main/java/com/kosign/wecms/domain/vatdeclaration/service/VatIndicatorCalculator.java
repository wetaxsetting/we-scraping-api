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
     * Recomputes every derived indicator, deriving the three <b>source</b> cells
     * ([25] [31] [33]) from their driver — used by the create flow where nothing
     * is user-supplied yet.
     */
    public void recompute(VatDeclarationIndicator ind) {
        recompute(ind, java.util.Set.of());
    }

    /**
     * Recomputes every derived indicator in place from the editable fields.
     *
     * <p>[25] [31] [33] are <b>source</b> cells, not pure formula cells: the
     * prototype prefills them from the rate but lets the user override
     * ({@code propagate()} only re-derives when the driver cell is edited).
     * So a key present in {@code clientProvided} keeps the value already set on
     * {@code ind}; otherwise it is derived ([25]=[24], [31]=[30]x5%,
     * [33]=[32]x10%). The pure formula cells ([27] [28] [34] [35] [36] [40a]
     * [40] [41] [43]) are always overwritten.
     *
     * @param clientProvided ct-keys the client explicitly sent on this request
     * @throws BusinessException {@code WBE_VAT_DECL_INVALID_INDICATOR} when an
     *         input is negative, or the [40b] ≤ [40a] / [42] ≤ [41] caps break.
     */
    public void recompute(VatDeclarationIndicator ind, java.util.Set<String> clientProvided) {
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

        // Revenue and purchase cells may be NEGATIVE. A downward-adjustment
        // invoice can exceed the period's own revenue when it cancels part of an
        // earlier period's; the period then genuinely booked negative net
        // revenue, and rejecting it blocked a lawful filing (QA S-07). PO ruled
        // 2026-08-13 that negative indicators are permitted.
        //
        // What stays guarded is not "the rest" but the cells where a minus sign
        // changes the MEANING rather than the size — the carried credit, the
        // three magnitude-style adjustments whose sign the formula supplies, and
        // the offset/refund family bounded by max(0,…) terms. The reasoning per
        // cell is on VatDeclarationService.MAY_BE_NEGATIVE.
        assertNonNegative(ct22, ct37, ct38, ct39a, ct40b, ct42,
                nz(ind.getNoActivity()));

        // Source cells auto-fill ONLY when their driver arrives in the SAME
        // request. The previous rule was "keep the client's value when sent,
        // else derive" — but {@code clientProvided} lives for one request, so a
        // value the user typed yesterday was re-derived by any unrelated edit
        // today. Setting [25] to 5,517,132 and then touching any other cell put
        // it back to [24] = 5,517,133, with no message (QA S-29). Auto-fill is
        // for the moment the driver changes; it is not a standing rule that
        // outranks what the user entered.
        BigDecimal ct25 = derive(clientProvided, "ct25", "ct24")
                ? ct24 : nz(ind.getDeductVatAmt());
        BigDecimal ct31 = derive(clientProvided, "ct31", "ct30")
                ? floorDivide(ct30, FIVE_PCT_DIVISOR) : nz(ind.getSalesAmtVat5());

        // [33] is NEVER derived. [32] is a MIXED bucket: Resolution 204/2025
        // reduced 10% to 8% and both rates land in it, so no single divisor can
        // be right. floor([32]/10) turned 8% tax into 10% tax — measured at
        // 80,219,866 -> 100,273,868 on one edit, overstating output VAT by
        // 20,054,002 (QA S-26). And because the two rates share the cell, the
        // error cannot be detected by inspecting the total afterwards.
        //
        // Sync sets this cell from the invoices' real tax; a hand-typed
        // declaration sets it directly. Neither needs a guess from [32].
        BigDecimal ct33 = nz(ind.getSalesAmtVat10());
        BigDecimal ct27 = ct29.add(ct30).add(ct32).add(ct32a);
        // [28] = [31] + [33]. ct33a is deliberately NOT summed here: the PO ruled
        // on 2026-08-08 that [33a] does not exist on form 01/GTGT and must not be
        // counted. The evidence agrees — neither shipped template mentions [33a]
        // (both carry [32a]), and no locale file has a label for it.
        //
        // Including it overstated [28], and with it [35], [36] and the amount
        // payable, on any period carrying other-rate sales. The frontend and the
        // Excel template were already correct; this side was the outlier.
        //
        // The column and the DTO field stay — the value is still stored and still
        // validated non-negative above. Only the total drops it.
        BigDecimal ct28 = ct31.add(ct33);
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

    /**
     * Should {@code cell} be auto-filled from {@code driver} in this request?
     *
     * <p>Only when the driver is being written and the cell itself is not — the
     * user is editing revenue and expects the tax to follow. A request that
     * mentions neither leaves the cell exactly as the user left it.
     */
    private static boolean derive(java.util.Set<String> provided, String cell, String driver) {
        return !provided.contains(cell) && provided.contains(driver);
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
