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

    /** True when {@code rsCd} is the success code {@code "0000"}. */
    public static boolean isSuccess(String rsCd) {
        return SUCC.equals(rsCd);
    }
}
