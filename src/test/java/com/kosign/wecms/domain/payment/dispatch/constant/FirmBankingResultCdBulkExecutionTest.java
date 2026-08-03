package com.kosign.wecms.domain.payment.dispatch.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code 00302} retry-code mapping, pinned against legacy
 * {@code FILE_DEPOSIT_TRANSFER_BATCH2}.
 *
 * <p>This table <b>is</b> the retry policy — getting a code into the wrong bucket either re-sends a
 * transfer the bank already rejected, or abandons one that only timed out. Legacy source:
 * <pre>
 *   :202-206  if (respData == null || respData.length() == 0) { continue; }   // no body, no audit update
 *   :232-234  if("BT01".equals(RC) || "G999".equals(RC) || "C999".equals(RC)) { continue; }
 *   :242      if("0000".equals(RC) || "BT00".equals(RC)) { ...PROC_STEP='2'... }
 *   :264      else { ...alert + PROC_STAT='3' on both tables... }             // everything else
 * </pre>
 */
class FirmBankingResultCdBulkExecutionTest {

    @Nested
    @DisplayName("success — legacy :242")
    class Success {

        @ParameterizedTest(name = "rc={0} is a 00302 success")
        @ValueSource(strings = {"0000", "BT00"})
        void successCodes(String rc) {
            assertThat(FirmBankingResultCd.isBulkExecutionSuccess(rc)).isTrue();
        }

        @Test
        @DisplayName("BT00 is accepted ONLY on the 00302 leg — the generic isSuccess is unchanged")
        void bt00IsScopedToTheBulkExecutionLeg() {
            // Guards the deliberate scoping decision: the 201/202/203/00301 legs keep their
            // "0000-only" contract, so widening isSuccess() would be a silent cross-leg change.
            assertThat(FirmBankingResultCd.isBulkExecutionSuccess("BT00")).isTrue();
            assertThat(FirmBankingResultCd.isSuccess("BT00")).isFalse();
            assertThat(FirmBankingResultCd.isSuccess("0000")).isTrue();
        }

        @ParameterizedTest(name = "rc={0} is NOT a success")
        @ValueSource(strings = {"BT01", "C999", "G001", "G002", "G003", "G004", "G998", "G999", "9999"})
        void nonSuccessCodes(String rc) {
            assertThat(FirmBankingResultCd.isBulkExecutionSuccess(rc)).isFalse();
        }
    }

    @Nested
    @DisplayName("retryable — legacy :202-206 + :232-234")
    class Retryable {

        @ParameterizedTest(name = "rc={0} is retryable")
        @ValueSource(strings = {"BT01", "G999", "C999"})
        void transientCodes(String rc) {
            assertThat(FirmBankingResultCd.isBulkExecutionRetryable(rc)).isTrue();
        }

        @ParameterizedTest(name = "blank rc [{0}] is retryable (no response body)")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void noResponseIsRetryable(String rc) {
            // Legacy :202-206 does `continue` BEFORE the audit update, so RS_CD stays NULL. A null
            // therefore means "request sent, nothing came back" — exactly the case legacy retried.
            assertThat(FirmBankingResultCd.isBulkExecutionRetryable(rc)).isTrue();
        }

        @Test
        @DisplayName("codes are trimmed before matching (bpchar padding from the DB)")
        void codesAreTrimmed() {
            assertThat(FirmBankingResultCd.isBulkExecutionRetryable(" BT01 ")).isTrue();
        }
    }

    @Nested
    @DisplayName("permanent — legacy :264 else branch")
    class Permanent {

        @ParameterizedTest(name = "rc={0} is NOT retryable")
        @ValueSource(strings = {"0000", "BT00", "G001", "G002", "G003", "G004", "G998", "9999", "0001"})
        void permanentCodes(String rc) {
            assertThat(FirmBankingResultCd.isBulkExecutionRetryable(rc)).isFalse();
        }

        @Test
        @DisplayName("G998 (target-status error) is permanent — it is a WeCMS-side verdict, not a timeout")
        void targetStatusErrorIsPermanent() {
            assertThat(FirmBankingResultCd.isBulkExecutionRetryable(FirmBankingResultCd.TARGET_STAT_ERR))
                    .isFalse();
        }
    }

    @Test
    @DisplayName("success and retryable are mutually exclusive for every known code")
    void successAndRetryableNeverOverlap() {
        for (String rc : new String[]{"0000", "BT00", "BT01", "C999", "G001", "G002", "G003",
                                      "G004", "G998", "G999", "9999", "0001"}) {
            boolean success = FirmBankingResultCd.isBulkExecutionSuccess(rc);
            boolean retryable = FirmBankingResultCd.isBulkExecutionRetryable(rc);
            assertThat(success && retryable)
                    .as("rc=%s must not be both a success and retryable", rc)
                    .isFalse();
        }
    }
}
