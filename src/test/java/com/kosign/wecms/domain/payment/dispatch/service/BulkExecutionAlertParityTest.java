package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.domain.payment.dispatch.constant.FirmBankingResultCd;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G-2 — the alert-classification contract for the {@code 00302} leg.
 *
 * <h2>Legacy evidence (verified from source)</h2>
 * {@code FILE_DEPOSIT_TRANSFER_BATCH2.java} contains <b>exactly one</b> {@code MsgSendUtil.SendMsg}
 * call — line {@code :280}, inside the {@code :264} {@code else} branch, i.e. <b>permanent failure
 * only</b>. Every other path reaches a {@code continue} before the alert code:
 *
 * <table border="1">
 *   <caption>Legacy alert behaviour per response code</caption>
 *   <tr><th>Response</th><th>Legacy line</th><th>Alert?</th></tr>
 *   <tr><td>{@code 0000} / {@code BT00} (success)</td><td>{@code :242-263}</td><td>no</td></tr>
 *   <tr><td>empty response body</td><td>{@code :202-206} {@code continue}</td><td>no</td></tr>
 *   <tr><td>{@code BT01} / {@code G999} / {@code C999}</td><td>{@code :232-234} {@code continue}</td><td>no</td></tr>
 *   <tr><td>{@code G998} target-fail gate</td><td>{@code :83-104} {@code continue}</td><td>no</td></tr>
 *   <tr><td>sibling pending</td><td>{@code :121-125} {@code continue}</td><td>no</td></tr>
 *   <tr><td>any other rc (permanent)</td><td>{@code :264-280}</td><td><b>yes</b></td></tr>
 * </table>
 *
 * <h2>Why this test is now classification-only</h2>
 * An earlier revision of this class also exercised a scheduled retry sweep's alert de-duplication.
 * That sweep ({@code BulkExecutionRetryJob} / {@code …Service} / {@code …TxService}) was <b>removed</b>
 * on 2026-07-29: it was K9 <b>Option A</b> ("Recovery Scheduler (reconciliation)"), which Mr. Kang was
 * offered and did not select. With no retry sweep there is no repeating alert to de-duplicate — the
 * only alerting consumer is {@code BulkReceptionResultService.issueExecutionInline}, whose behavioural
 * coverage lives in {@code BulkReceptionResultServiceTest}.
 *
 * <p>What remains here is the part that matters and is easy to regress silently: <b>which codes count
 * as permanent</b>. Get that wrong and the inline path either alerts on a routine timeout (noise) or
 * stays silent on a real bank rejection (missed incident).
 *
 * @see com.kosign.wecms.domain.payment.dispatch.constant.FirmBankingResultCdBulkExecutionTest
 */
class BulkExecutionAlertParityTest {

    // ── permanent ⇒ ALERT (legacy :264-280) ─────────────────────────────────

    @ParameterizedTest(name = "rc={0} is permanent ⇒ the inline path must alert")
    @ValueSource(strings = {"G001", "G002", "G003", "G004", "G998", "9999", "0001"})
    @DisplayName("permanent codes are classified non-retryable, which is what triggers the ops alert")
    void permanentCodesAlert(String rc) {
        assertThat(FirmBankingResultCd.isBulkExecutionRetryable(rc))
                .as("rc=%s must be treated as permanent so issueExecutionInline raises the legacy alert", rc)
                .isFalse();
    }

    @Test
    @DisplayName("G998 (target-status error) is permanent — a WeCMS-side verdict, not a timeout")
    void targetStatusErrorIsPermanent() {
        assertThat(FirmBankingResultCd.isBulkExecutionRetryable(FirmBankingResultCd.TARGET_STAT_ERR))
                .isFalse();
    }

    // ── transient ⇒ NO ALERT (legacy :202-206 / :232-234) ───────────────────

    @ParameterizedTest(name = "rc={0} is transient ⇒ no alert, no state change")
    @ValueSource(strings = {"BT01", "G999", "C999"})
    @DisplayName("transient codes stay silent — legacy passed over them with continue")
    void transientCodesDoNotAlert(String rc) {
        assertThat(FirmBankingResultCd.isBulkExecutionRetryable(rc))
                .as("rc=%s is the normal no-confirmation path; alerting on it would flood ops", rc)
                .isTrue();
    }

    @Test
    @DisplayName("an empty gateway body (null rc) is transient — legacy :202-206 left RS_CD NULL")
    void emptyBodyIsTransient() {
        // Legacy `continue`s BEFORE the audit update, so RS_CD stays NULL. A null therefore means
        // "request sent, nothing came back" — not a bank rejection.
        assertThat(FirmBankingResultCd.isBulkExecutionRetryable(null)).isTrue();
        assertThat(FirmBankingResultCd.isBulkExecutionRetryable("   ")).isTrue();
    }

    // ── success ⇒ NO ALERT (legacy :242) ───────────────────────────────────

    @Test
    @DisplayName("BT00 remains a success on the 00302 leg and never reaches an alert path")
    void bt00RemainsSuccess() {
        // Legacy :242 — if("0000".equals(RC) || "BT00".equals(RC)). The generic isSuccess() accepts
        // only "0000" and is deliberately left alone for the 201/202/203/00301 legs.
        assertThat(FirmBankingResultCd.isBulkExecutionSuccess("BT00")).isTrue();
        assertThat(FirmBankingResultCd.isBulkExecutionSuccess("0000")).isTrue();
        assertThat(FirmBankingResultCd.isBulkExecutionRetryable("BT00")).isFalse();
        assertThat(FirmBankingResultCd.isSuccess("BT00")).isFalse();
    }

    @Test
    @DisplayName("no code is both a success and transient — the two classifications cannot overlap")
    void successAndTransientNeverOverlap() {
        for (String rc : new String[]{"0000", "BT00", "BT01", "C999", "G001", "G002", "G003",
                                      "G004", "G998", "G999", "9999", "0001"}) {
            assertThat(FirmBankingResultCd.isBulkExecutionSuccess(rc)
                    && FirmBankingResultCd.isBulkExecutionRetryable(rc))
                    .as("rc=%s must not be both a success and transient", rc)
                    .isFalse();
        }
    }
}
