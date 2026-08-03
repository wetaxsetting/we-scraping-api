package com.kosign.wecms.domain.payment.dispatch.constant;

/**
 * EFS (전자금융서비스) gateway response codes for the firm-banking bulk flow.
 *
 * <p>Ported verbatim from {@code consts.WCConst.CD_*}
 * ({@code WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH/src/consts/WCConst.java}).
 * These are the codes the legacy batch wrote into
 * {@code TB_FIRM_BAT_API_TRAN.RS_CD} / {@code REG_RSLT_CD} and compared against
 * when advancing the file state machine. Kept as {@code String} constants
 * (not an enum) because they are matched as raw wire values from the gateway.
 *
 * <p>The English {@code MSG_*} strings below are the exact legacy messages
 * (the legacy file already localised them to English).
 *
 * <p><b>TODO(K3) / TODO(B5):</b> the file-reception/execution result also
 * carries gateway-specific status tokens (e.g. a no-response / retry code) used
 * by the removed BATCH2 retry loop. Those tokens are NOT defined in
 * {@code WCConst} and are not invented here — confirm the full result-code set
 * against a real EFS sample (B5) before wiring retry logic (see TODO(K9) in
 * {@link com.kosign.wecms.domain.payment.dispatch.callback.service.BulkReceptionResultService}).
 */
public final class FirmBankingResultCd {

    private FirmBankingResultCd() {
    }

    /** {@code 0000} — 정상 / success. */
    public static final String SUCC = "0000";
    public static final String MSG_SUCC = "정상";

    /** {@code 0001} — 원거래 없음 / original transaction not found. */
    public static final String NO_FOUND = "0001";
    public static final String MSG_NO_FOUND = "No original transaction";

    /** {@code 9999} — 금액 데이터 상이 / reception result amount mismatch. */
    public static final String AMT_ERR = "9999";
    public static final String MSG_AMT_ERR = "Reception result amount data difference";

    /** {@code G001} — 공통부 필수 입력값 누락 / common header required value missing. */
    public static final String BASE_NONE_INPUT_DATA = "G001";
    public static final String MSG_BASE_NONE_INPUT_DATA = "Basic information is not registered.";

    /** {@code G002} — 데이터부 필수 입력값 누락 / data section required value missing. */
    public static final String DETAIL_NONE_INPUT_DATA = "G002";
    public static final String MSG_DETAIL_NONE_INPUT_DATA = "Basic information (bank details) not registered.";

    /** {@code G003} — 암복호화 검증오류 / encryption verification error. */
    public static final String ENC_VRFY_ERR = "G003";
    public static final String MSG_ENC_VRFY_ERR = "An error occurred while checking encrypted data.";

    /** {@code G004} — DB처리 오류 / DB processing error. */
    public static final String DB_ERR = "G004";
    public static final String MSG_DB_ERR = "An error occurred during DB communication.";

    /** {@code G998} — 이체대상 원장 상태 실패 / transfer-target ledger status failure. */
    public static final String TARGET_STAT_ERR = "G998";
    public static final String MSG_TARGET_STAT_ERR =
            "Unable to process the status of the transfer target (TB_PAY_FILE_MST) transaction as failed.";

    /** {@code G999} — 기타에러 / general error. */
    public static final String ETC_ERR = "G999";
    public static final String MSG_ETC_ERR = "General Error";

    // ── 00302 bulk-execution leg only ────────────────────────────────────────
    // These three codes are NOT in legacy consts.WCConst — they appear as string
    // literals inside FILE_DEPOSIT_TRANSFER_BATCH2 only, and they apply to the
    // 00302 execution-instruction leg exclusively. They are deliberately kept out
    // of isSuccess(String) so the 201/202/203/00301 legs keep their existing
    // "0000-only" contract unchanged.

    /**
     * {@code BT00} — 대량이체 실행지시 정상 접수 / bulk execution accepted.
     *
     * <p>Legacy treats this as <b>success</b> alongside {@code 0000}:
     * {@code FILE_DEPOSIT_TRANSFER_BATCH2.java:242}
     * — {@code if("0000".equals(RC) || "BT00".equals(RC))} → {@code PROC_STEP='2'}.
     */
    public static final String BULK_EXEC_ACCEPTED = "BT00";

    /**
     * {@code BT01} — 대량이체 실행지시 무응답 / bulk execution no-response.
     * Legacy {@code FILE_DEPOSIT_TRANSFER_BATCH2.java:232} → {@code continue}
     * (row stays {@code PROC_STEP='1'}, retried on the next 60 s poll).
     */
    public static final String BULK_EXEC_NO_RESPONSE = "BT01";

    /**
     * {@code C999} — 통신 오류 / communication error.
     * Legacy {@code FILE_DEPOSIT_TRANSFER_BATCH2.java:232} → {@code continue} (retryable).
     */
    public static final String COMMS_ERR = "C999";

    /** True when {@code rsCd} is the success code {@code "0000"}. */
    public static boolean isSuccess(String rsCd) {
        return SUCC.equals(rsCd);
    }

    /**
     * Success test for the <b>00302 bulk-execution leg only</b> — {@code 0000} or {@code BT00}.
     *
     * <p>Legacy parity: {@code FILE_DEPOSIT_TRANSFER_BATCH2.java:242} accepts both. The generic
     * {@link #isSuccess(String)} accepts only {@code 0000}, which would misclassify a legacy
     * {@code BT00} success as a gateway failure on this leg.
     */
    public static boolean isBulkExecutionSuccess(String rsCd) {
        return SUCC.equals(rsCd) || BULK_EXEC_ACCEPTED.equals(rsCd);
    }

    /**
     * Transient-vs-permanent classification for the <b>00302 bulk-execution leg only</b>.
     *
     * <p><b>Naming note.</b> "Retryable" describes what <i>legacy</i> did with these codes, not what
     * the refactor does. The approved TO-BE has <b>no automatic retry</b> — the sole consumer is
     * {@code BulkReceptionResultService.issueExecutionInline}, which uses this to decide whether a
     * failure is <b>permanent enough to raise an ops alert</b> (legacy alerted only on the permanent
     * branch, {@code :264-280}). A transient code leaves the file at {@code PROC_STEP='1'} for
     * monitoring + manual recovery, exactly as the approved design prescribes.
     *
     * <p>Derived verbatim from {@code FILE_DEPOSIT_TRANSFER_BATCH2.java:232}:
     * <pre>if("BT01".equals(RC) || "G999".equals(RC) || "C999".equals(RC)) { continue; }</pre>
     * plus the earlier <b>empty-body</b> branch at {@code :202-206}, where legacy performs
     * {@code continue} <i>before</i> the audit update — leaving {@code TB_FIRM_BAT_API_TRAN.RS_CD}
     * {@code NULL}. A {@code null}/blank code therefore means "request sent, nothing came back",
     * which is transient rather than a bank rejection.
     *
     * <p>Every other code (including {@code G001}/{@code G002}/{@code G003}/{@code G004}/
     * {@code G998}) falls through to legacy's {@code else} branch at {@code :264}, which marks the
     * file {@code PROC_STAT='3'} permanently and alerts.
     */
    public static boolean isBulkExecutionRetryable(String rsCd) {
        if (rsCd == null || rsCd.isBlank()) {
            return true;
        }
        String rc = rsCd.trim();
        return BULK_EXEC_NO_RESPONSE.equals(rc) || ETC_ERR.equals(rc) || COMMS_ERR.equals(rc);
    }
}
