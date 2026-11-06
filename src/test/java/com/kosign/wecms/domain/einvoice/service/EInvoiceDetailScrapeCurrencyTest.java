package com.kosign.wecms.domain.einvoice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link EInvoiceDetailScrapeService#resolveMasterCcy} to the legacy
 * currency clamp in {@code nxisas_einvoice_c002_act.jsp:239-260} + {@code :605-611}.
 *
 * <p>Regression guard for the {@code busa_doc_mstr_chk07} violation: a K
 * e-Invoice reporting {@code KRW} used to be written verbatim, and the column
 * only accepts {@code VND} / {@code USD} / {@code 999} / {@code 998}.
 */
class EInvoiceDetailScrapeCurrencyTest {

    /** Convenience wrapper — only the four discriminators matter here. */
    private static String resolve(String ccy, String testResults, String symbol, String totalAmt) {
        return EInvoiceDetailScrapeService.resolveMasterCcy(ccy, testResults, symbol, totalAmt, "1");
    }

    @Nested
    @DisplayName("K e-Invoice (einv_testResults='1' or invoiceSymbol starts with K)")
    class KInvoice {

        @Test
        @DisplayName("foreign currency folds to VND — the chk07 regression case")
        void foreignCurrencyFoldsToVnd() {
            // The exact failing invoice: symbol K26TTT, einv_testResults '1', KRW @ 17.93.
            assertThat(resolve("KRW", "1", "K26TTT", "76676400")).isEqualTo("VND");
            assertThat(resolve("KHR", "1", "K26TTT", "76676400")).isEqualTo("VND");
            // Symbol-only discriminator (einv_testResults absent).
            assertThat(resolve("KRW", "", "K26TTT", "76676400")).isEqualTo("VND");
        }

        @Test
        @DisplayName("VND / USD pass through")
        void vndAndUsdPassThrough() {
            assertThat(resolve("VND", "1", "K26TTT", "76676400")).isEqualTo("VND");
            assertThat(resolve("USD", "1", "K26TTT", "1200.50")).isEqualTo("USD");
            assertThat(resolve("VNĐ", "1", "K26TTT", "76676400")).isEqualTo("VND");
        }

        @Test
        @DisplayName("blank currency: no separator and >= 1000 reads as whole VND")
        void blankCurrencyAmountHeuristic() {
            assertThat(resolve("", "1", "K26TTT", "76676400")).isEqualTo("VND");
            assertThat(resolve(null, "1", "K26TTT", "1000")).isEqualTo("VND");
            // Below 1000, or carrying a thousands separator → legacy 999, which
            // collapses back to the VND default on the master.
            assertThat(resolve("", "1", "K26TTT", "999")).isEqualTo("VND");
            assertThat(resolve("", "1", "K26TTT", "76,676,400")).isEqualTo("VND");
            assertThat(resolve("", "1", "K26TTT", "not-a-number")).isEqualTo("VND");
        }
    }

    @Nested
    @DisplayName("POS e-Invoice (einv_testResults='2')")
    class PosInvoice {

        @Test
        @DisplayName("blank currency defaults to VND")
        void blankDefaultsToVnd() {
            assertThat(resolve("", "2", "C26TAA", "76676400")).isEqualTo("VND");
        }

        @Test
        @DisplayName("VND / USD pass through untouched")
        void knownCurrenciesPassThrough() {
            assertThat(resolve("USD", "2", "C26TAA", "1200")).isEqualTo("USD");
            assertThat(resolve("VND", "2", "C26TAA", "76676400")).isEqualTo("VND");
        }
    }

    @Nested
    @DisplayName("C e-Invoice (neither K nor POS)")
    class CInvoice {

        @Test
        @DisplayName("blank currency leaves the phase-1 value untouched")
        void blankLeavesExistingValue() {
            assertThat(resolve("", "", "C26TAA", "76676400")).isNull();
            assertThat(resolve(null, "", "C26TAA", "76676400")).isNull();
        }

        @Test
        @DisplayName("VND / USD pass through untouched")
        void knownCurrenciesPassThrough() {
            assertThat(resolve("VND", "", "C26TAA", "76676400")).isEqualTo("VND");
            assertThat(resolve("USD", "", "C26TAA", "1200")).isEqualTo("USD");
        }

        @Test
        @DisplayName("a residual unstorable code is clamped to 998 (CcyCd.ETC)")
        void residualCodeClampedToEtc() {
            // Legacy leaves this raw on the C branch and dies on chk07; the
            // refactor stores CcyCd.ETC ("Others") so the batch survives.
            assertThat(resolve("KRW", "", "C26TAA", "76676400")).isEqualTo("998");
            assertThat(resolve("998", "", "C26TAA", "76676400")).isEqualTo("998");
        }
    }
}
