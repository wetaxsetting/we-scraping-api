package com.kosign.wecms.domain.weinvoice.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** GDT code ↔ WeInvoice / WeCMS vocabulary (UI Spec 02 §3.1, §3.3; Partner API). */
class InvoiceCodesTest {

    /** Phrases WeCMS {@code EInvoiceScrapeService} accepts — anything else makes it skip the row. */
    private static final Set<String> WECMS_CHECK_RESULTS = Set.of(
            "Đã cấp mã hóa đơn",
            "Hóa đơn không đủ điều kiện cấp mã",
            "Tổng cục Thuế đã nhận",
            "Cục thuế đã nhận không mã",
            "Cục Thuế đã nhận hóa đơn có mã khởi tạo từ máy tính tiền",
            "Cục Thuế đã nhận");

    @Test
    void nullTthaiWithCancelledExcelWordingIsCancelled() {
        // The 19 rows the engine stored with tthai NULL (wording missing from its mapping).
        assertThat(InvoiceCodes.effectiveTthai(null, "Hóa đơn đã bị xóa bỏ/hủy bỏ")).isEqualTo(6);
        assertThat(InvoiceCodes.effectiveTthai(null, "Hóa đơn đã bị huỷ")).isEqualTo(6);
        assertThat(InvoiceCodes.effectiveTthai(null, "Hóa đơn mới")).isEqualTo(1);
        assertThat(InvoiceCodes.effectiveTthai(null, null)).isEqualTo(1);
        assertThat(InvoiceCodes.effectiveTthai(4, "Hóa đơn đã bị hủy")).isEqualTo(4);
    }

    @ParameterizedTest
    @CsvSource({"1,original", "2,replacement", "3,adjustment", "4,replaced", "5,adjusted", "6,cancelled"})
    void statusRoundTrip(int tthai, String status) {
        assertThat(InvoiceCodes.status(tthai)).isEqualTo(status);
        assertThat(InvoiceCodes.tthai(status)).isEqualTo(tthai);
    }

    @Test
    void unknownStatusIsAValidationError() {
        assertThatThrownBy(() -> InvoiceCodes.tthai("deleted")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"K26TEV,no_code", "C26MVM,cash_register", "C26TAA,normal", "K26MAA,no_code", "c26mvm,cash_register"})
    void kindFromSymbol(String symbol, String kind) {
        assertThat(InvoiceCodes.kind(symbol)).isEqualTo(kind);
    }

    @Test
    void currencyNormalisation() {
        assertThat(InvoiceCodes.currency("VNĐ")).isEqualTo("VND");
        assertThat(InvoiceCodes.currency(" vnd ")).isEqualTo("VND");
        assertThat(InvoiceCodes.currency(null)).isEqualTo("VND");
        assertThat(InvoiceCodes.currency("")).isEqualTo("VND");
        assertThat(InvoiceCodes.currency("usd")).isEqualTo("USD");
        assertThat(InvoiceCodes.currency("KRW")).isEqualTo("KRW");
    }

    @Test
    void vatRateUsesUiSpecNumbers() {
        assertThat(InvoiceCodes.vatRate("10%")).isEqualByComparingTo("10");
        assertThat(InvoiceCodes.vatRate("8%")).isEqualByComparingTo("8");
        assertThat(InvoiceCodes.vatRate("0%")).isEqualByComparingTo("0");
        assertThat(InvoiceCodes.vatRate("KCT")).isEqualByComparingTo(BigDecimal.valueOf(-1));
        assertThat(InvoiceCodes.vatRate("KKKNT")).isEqualByComparingTo(BigDecimal.valueOf(-2));
        assertThat(InvoiceCodes.vatRate("KHAC:5,26%")).isEqualByComparingTo("5.26");
        assertThat(InvoiceCodes.vatRate(null)).isNull();
        assertThat(InvoiceCodes.vatRate("n/a")).isNull();
    }

    @Test
    void checkResultForKnownCodesIsAPhraseWeCmsAccepts() {
        for (int code : new int[]{0, 4, 5, 6, 8}) {
            assertThat(WECMS_CHECK_RESULTS).contains(InvoiceCodes.checkResultText(code, null, "mua"));
        }
        // Old GDT wording from the Excel file is rewritten to the phrase WeCMS accepts.
        assertThat(InvoiceCodes.checkResultText(6, "Tổng cục Thuế đã nhận không mã", "mua-khma"))
                .isEqualTo("Cục thuế đã nhận không mã");
    }

    @Test
    void checkResultFallsBackToTextThenListType() {
        assertThat(InvoiceCodes.checkResultText(null, "Hóa đơn đủ điều kiện cấp mã", "ban"))
                .isEqualTo("Hóa đơn đủ điều kiện cấp mã");
        assertThat(InvoiceCodes.checkResultText(null, null, "mua-mtt"))
                .isEqualTo("Cục Thuế đã nhận hóa đơn có mã khởi tạo từ máy tính tiền");
        assertThat(InvoiceCodes.checkResultText(null, null, "mua-khma")).isEqualTo("Cục thuế đã nhận không mã");
        assertThat(InvoiceCodes.checkResultText(null, null, "mua")).isEqualTo("Đã cấp mã hóa đơn");
    }

    @Test
    void invoiceStatusTextMatchesWeCmsVoidSets() {
        assertThat(InvoiceCodes.invoiceStatusText(4)).isEqualTo("Hóa đơn đã bị thay thế");
        assertThat(InvoiceCodes.invoiceStatusText(5)).isEqualTo("Hóa đơn đã bị điều chỉnh");
        assertThat(InvoiceCodes.invoiceStatusText(6)).isEqualTo("Hóa đơn đã bị hủy");
    }

    @Test
    void testResultCodes() {
        assertThat(InvoiceCodes.testResult("C26TAA")).isEmpty();
        assertThat(InvoiceCodes.testResult("K26TEV")).isEqualTo("1");
        assertThat(InvoiceCodes.testResult("C26MVM")).isEqualTo("2");
    }

    @Test
    void directionMapping() {
        assertThat(InvoiceCodes.engineDirection("purchase")).isEqualTo("mua");
        assertThat(InvoiceCodes.engineDirection("SALES")).isEqualTo("ban");
        assertThat(InvoiceCodes.direction("ban")).isEqualTo("sales");
        assertThat(InvoiceCodes.direction("mua")).isEqualTo("purchase");
        assertThatThrownBy(() -> InvoiceCodes.engineDirection("both")).isInstanceOf(IllegalArgumentException.class);
    }
}
