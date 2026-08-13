package com.kosign.wecms.domain.vatdeclaration.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.vatdeclaration.entity.VatDeclarationIndicator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for {@link VatIndicatorCalculator} — the backend calculation
 * authority for Mẫu 01/GTGT (TT80). Pins:
 *
 * <ul>
 *   <li>the formula matrix, verified against the PO prototype's {@code compute()}
 *       (docs/vat-declaration/VAT_Declaration_Report.html);</li>
 *   <li>integer-VND floor on the rate products (HTKK truncates, never rounds);</li>
 *   <li>the source-cell override rule — [25]/[31]/[33] are derived only when the
 *       client omits them, mirroring the prototype's {@code propagate()};</li>
 *   <li>the two caps [40b] ≤ [40a] and [42] ≤ [41], and the non-negative guard.</li>
 * </ul>
 */
class VatIndicatorCalculatorTest {

    private final VatIndicatorCalculator calculator = new VatIndicatorCalculator();

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    /** Indicator row carrying only the editable cells a caller would set. */
    private static VatDeclarationIndicator blank() {
        VatDeclarationIndicator i = new VatDeclarationIndicator();
        i.setDelYn("N");
        return i;
    }

    @Nested
    @DisplayName("Formula matrix")
    class FormulaMatrix {

        @Test
        @DisplayName("derives every grey cell from the editable inputs")
        void derivesEveryFormulaCell() {
            VatDeclarationIndicator i = blank();
            i.setPrevCredit(bd("5000000"));       // ct22
            i.setPurchAmtVat(bd("80000000"));     // ct24
            i.setSalesAmtNondecl(bd("0"));        // ct26
            i.setSalesAmt0(bd("0"));              // ct29
            i.setSalesAmt5(bd("100000000"));      // ct30
            i.setSalesAmt10(bd("1100000000"));    // ct32 (8% + 10% tron chung)
            i.setSalesAmtVat10(bd("110000000"));  // ct33 — dat thang, khong con suy
            i.setSalesAmtNontax(bd("0"));         // ct32a
            i.setSalesVatOther(bd("0"));          // ct33a

            calculator.recompute(i, Set.of("ct24", "ct30"));

            assertThat(i.getDeductVatAmt()).isEqualByComparingTo("80000000");     // [25] = [24]
            assertThat(i.getSalesAmtVat5()).isEqualByComparingTo("5000000");      // [31] = [30]x5%
            assertThat(i.getSalesAmtVat10()).isEqualByComparingTo("110000000");   // [33] giu nguyen
            // [27] = [29]+[30]+[32]+[32a]
            assertThat(i.getTolSalesAmt()).isEqualByComparingTo("1200000000");
            // [28] = [31]+[33] — [33a] KHONG duoc cong (PO 2026-08-08)
            assertThat(i.getTotSalesAmtVat()).isEqualByComparingTo("115000000");
            assertThat(i.getTotalSalesAmt()).isEqualByComparingTo("1200000000");  // [34] = [26]+[27]
            assertThat(i.getTotalSalesAmtVat()).isEqualByComparingTo("115000000");// [35] = [28]
            assertThat(i.getVatIncurred()).isEqualByComparingTo("35000000");      // [36] = [35]-[25]
            // hieu_so = 35,000,000 - 5,000,000 = 30,000,000 -> payable
            assertThat(i.getVatPayableActivity()).isEqualByComparingTo("30000000"); // [40a]
            assertThat(i.getVatPayableNet()).isEqualByComparingTo("30000000");      // [40] = [40a]-[40b]
            assertThat(i.getVatNotDeducted()).isEqualByComparingTo("0");            // [41]
            assertThat(i.getCreditNext()).isEqualByComparingTo("0");                // [43] = [41]-[42]
        }

        @Test
        @DisplayName("hieu_so ≤ 0 lands on [41]/[43], leaving [40a]/[40] at zero")
        void negativeDifferenceCarriesForward() {
            VatDeclarationIndicator i = blank();
            i.setPrevCredit(bd("50000000"));   // ct22 dominates
            i.setPurchAmtVat(bd("10000000"));  // ct24 -> ct25
            i.setSalesAmt10(bd("100000000"));  // ct32
            i.setSalesAmtVat10(bd("10000000"));// ct33 dat thang

            calculator.recompute(i, Set.of("ct24"));

            // [36] = 10,000,000 - 10,000,000 = 0; hieu_so = 0 - 50,000,000 = -50,000,000
            assertThat(i.getVatPayableActivity()).isEqualByComparingTo("0");
            assertThat(i.getVatPayableNet()).isEqualByComparingTo("0");
            assertThat(i.getVatNotDeducted()).isEqualByComparingTo("50000000");
            assertThat(i.getCreditNext()).isEqualByComparingTo("50000000");
        }

        @Test
        @DisplayName("adjustments [37]/[38]/[39a] move hieu_so in the documented directions")
        void adjustmentsApplyWithCorrectSign() {
            VatDeclarationIndicator i = blank();
            i.setSalesAmt10(bd("100000000"));
            i.setSalesAmtVat10(bd("10000000"));// [33] -> [36] = 10,000,000
            i.setAdjDecrease(bd("3000000"));   // [37] adds
            i.setAdjIncrease(bd("1000000"));   // [38] subtracts
            i.setTransfVat(bd("2000000"));     // [39a] subtracts

            calculator.recompute(i, Set.of());

            // 10,000,000 - 0 + 3,000,000 - 1,000,000 - 2,000,000 = 10,000,000
            assertThat(i.getVatPayableActivity()).isEqualByComparingTo("10000000");
        }
    }

    @Nested
    @DisplayName("Integer-VND floor")
    class IntegerFloor {

        @Test
        @DisplayName("[31] truncates instead of rounding")
        void ratesFloorNeverRound() {
            VatDeclarationIndicator i = blank();
            i.setSalesAmt5(bd("199"));    // x5% = 9.95 -> 9

            calculator.recompute(i, Set.of("ct30"));

            assertThat(i.getSalesAmtVat5()).isEqualByComparingTo("9");
            // [33] khong con o day: [32] tron 8% voi 10% nen khong suy duoc,
            // va viec lam tron chi co nghia khi co mot phep chia de lam tron.
        }
    }

    @Nested
    @DisplayName("Source cells [25] / [31] / [33]")
    class SourceCells {

        @Test
        @DisplayName("[25] and [31] fill from their driver when the driver is written")
        void derivedWhenDriverSent() {
            VatDeclarationIndicator i = blank();
            i.setPurchAmtVat(bd("7000000"));
            i.setSalesAmt5(bd("100000000"));
            i.setDeductVatAmt(bd("999"));        // stale — must give way to [24]
            i.setSalesAmtVat5(bd("999"));        // stale — must give way to [30]/20

            calculator.recompute(i, Set.of("ct24", "ct30"));

            assertThat(i.getDeductVatAmt()).isEqualByComparingTo("7000000");
            assertThat(i.getSalesAmtVat5()).isEqualByComparingTo("5000000");
        }

        /**
         * QA S-29. The old rule re-derived on every request, so a figure the
         * user typed was silently replaced the next time they touched any other
         * cell — measured live: [25] set to 5,517,132, one unrelated edit, back
         * to [24] = 5,517,133, no message.
         */
        @Test
        @DisplayName("[25] survives an edit that touches neither [25] nor [24]")
        void deductibleSurvivesUnrelatedEdit() {
            VatDeclarationIndicator i = blank();
            i.setPurchAmtVat(bd("5517133"));
            i.setDeductVatAmt(bd("5517132"));    // what the user entered earlier

            calculator.recompute(i, Set.of("ct37"));

            assertThat(i.getDeductVatAmt()).isEqualByComparingTo("5517132");
        }

        /**
         * QA S-26. [32] carries 8% AND 10% revenue (Resolution 204/2025), so
         * floor([32]/10) reads 8% tax as 10% tax — measured live at 80,219,866
         * turning into 100,273,868, overstating output VAT by 20,054,002. No
         * divisor can be correct for a mixed bucket, so the cell is never
         * derived at all.
         */
        @Test
        @DisplayName("[33] is never re-derived from [32], even when [32] is written")
        void outputVatNeverDerived() {
            VatDeclarationIndicator i = blank();
            i.setSalesAmt10(bd("1002738681"));   // 8% + 10% mixed
            i.setSalesAmtVat10(bd("80219866"));  // real tax from the invoices

            calculator.recompute(i, Set.of("ct32"));

            assertThat(i.getSalesAmtVat10()).isEqualByComparingTo("80219866");
            assertThat(i.getTotSalesAmtVat()).isEqualByComparingTo("80219866"); // [28]
        }

        @Test
        @DisplayName("the client's value wins when the key is sent")
        void clientValueWinsWhenSent() {
            VatDeclarationIndicator i = blank();
            i.setPurchAmtVat(bd("7000000"));
            i.setSalesAmt5(bd("100000000"));
            i.setSalesAmt10(bd("200000000"));
            i.setDeductVatAmt(bd("6000000"));    // partial deduction, below [24]
            i.setSalesAmtVat5(bd("4900000"));    // manually corrected
            i.setSalesAmtVat10(bd("19900000"));

            calculator.recompute(i, Set.of("ct25", "ct31", "ct33"));

            assertThat(i.getDeductVatAmt()).isEqualByComparingTo("6000000");
            assertThat(i.getSalesAmtVat5()).isEqualByComparingTo("4900000");
            assertThat(i.getSalesAmtVat10()).isEqualByComparingTo("19900000");
            // and the overrides must propagate into the dependent formula cells
            assertThat(i.getTotSalesAmtVat()).isEqualByComparingTo("24800000"); // [28]
            assertThat(i.getVatIncurred()).isEqualByComparingTo("18800000");    // [36] = [35]-[25]
        }

        @Test
        @DisplayName("overriding [25] leaves [31] to fill from its own driver")
        void partialOverride() {
            VatDeclarationIndicator i = blank();
            i.setPurchAmtVat(bd("7000000"));
            i.setSalesAmt5(bd("100000000"));
            i.setDeductVatAmt(bd("6000000"));    // partial deduction, kept
            i.setSalesAmtVat5(bd("999"));        // stale — [30] is being written

            calculator.recompute(i, Set.of("ct25", "ct30"));

            assertThat(i.getDeductVatAmt()).isEqualByComparingTo("6000000");
            assertThat(i.getSalesAmtVat5()).isEqualByComparingTo("5000000");
        }
    }

    @Nested
    @DisplayName("Guards")
    class Guards {

        @Test
        @DisplayName("[40b] may not exceed [40a]")
        void investOffsetCappedByPayable() {
            VatDeclarationIndicator i = blank();
            i.setSalesAmt10(bd("100000000"));          // [40a] = 10,000,000
            i.setInvestOffsetVatAmt(bd("10000001"));   // one dong over

            assertThatThrownBy(() -> calculator.recompute(i))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBE_VAT_DECL_INVALID_INDICATOR");
        }

        @Test
        @DisplayName("[40b] exactly equal to [40a] is accepted and zeroes [40]")
        void investOffsetEqualToPayableIsAllowed() {
            VatDeclarationIndicator i = blank();
            i.setSalesAmt10(bd("100000000"));
            i.setSalesAmtVat10(bd("10000000"));// [33] dat thang -> [40a] = 10.000.000
            i.setInvestOffsetVatAmt(bd("10000000"));

            calculator.recompute(i, Set.of());

            assertThat(i.getVatPayableNet()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("[42] may not exceed [41]")
        void refundCappedByUndeductedVat() {
            VatDeclarationIndicator i = blank();
            i.setPrevCredit(bd("50000000"));   // [41] = 50,000,000
            i.setVatRefundReq(bd("50000001"));

            assertThatThrownBy(() -> calculator.recompute(i))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBE_VAT_DECL_INVALID_INDICATOR");
        }

        @Test
        @DisplayName("a negative input is rejected")
        void negativeInputRejected() {
            VatDeclarationIndicator i = blank();
            i.setSalesAmt10(bd("-1"));

            assertThatThrownBy(() -> calculator.recompute(i))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBE_VAT_DECL_INVALID_INDICATOR");
        }

        @Test
        @DisplayName("a negative client-supplied source cell is rejected too")
        void negativeSourceCellRejected() {
            VatDeclarationIndicator i = blank();
            i.setSalesAmtVat5(bd("-1"));

            assertThatThrownBy(() -> calculator.recompute(i, Set.of("ct31")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBE_VAT_DECL_INVALID_INDICATOR");
        }

        @Test
        @DisplayName("an all-null indicator computes to zeros rather than NPE-ing")
        void allNullIsTreatedAsZero() {
            VatDeclarationIndicator i = blank();

            assertThatCode(() -> calculator.recompute(i)).doesNotThrowAnyException();

            assertThat(i.getVatPayableNet()).isEqualByComparingTo("0");
            assertThat(i.getCreditNext()).isEqualByComparingTo("0");
        }
    }
}
