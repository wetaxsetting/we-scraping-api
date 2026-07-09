package com.kosign.wecms.domain.banking.nxisas;

import java.util.Set;

/**
 * Shared Coocon/NXISAS result-code policy for the scraping layer. One
 * source of truth for "did the bank adapter / gateway succeed?", consumed
 * by both {@link BankScrapingService} (Login Credentials setup flow) and
 * {@link BankTransactionSyncService} (Account Inquiry &gt; Update sync flow).
 *
 * <p>Two distinct codes ride on every scrape response:
 * <ul>
 *   <li><b>Gateway envelope</b> — {@code Common.Result_cd}. {@code "00000000"}
 *       (8 zeros) means "Coocon routed the call"; legacy short forms
 *       {@code "0000"} / {@code "OK"} are tolerated. See
 *       {@link #isGatewayError(String)}.</li>
 *   <li><b>Bank adapter</b> — {@code Output.ErrorCode}. The adapter can still
 *       reject a routed call with its own non-zero code. See
 *       {@link #isBankAdapterSuccess(String)}.</li>
 * </ul>
 *
 * <p>The bank-adapter success whitelist mirrors legacy
 * {@code nxisas_api.js isSuccess()} (lines 2445-2455): besides the plain
 * {@code 00000000}, the legacy engine treats {@code 42110000}
 * ("거래내역을 찾을 수 없음" — no transactions in window) and the two
 * redmine-#36034 edge codes {@code 42120011} / {@code 42120111} as
 * non-fatal success. Reference only — {@code nxisas_api.js} is never loaded
 * or executed by the refactor.
 */
public final class NxisasResultCodes {

    private NxisasResultCodes() { }

    /** Coocon "routed / succeeded" code (8 zeros). */
    public static final String OK = "00000000";
    /** Bank adapter "no transactions in the requested window" — terminal
     *  success with zero rows, NOT an error. */
    public static final String OK_NO_TRANSACTIONS = "42110000";
    /** redmine #36034 edge case — bank adapter returns this on an otherwise
     *  successful inquiry; legacy {@code isSuccess()} whitelists it. */
    public static final String OK_NO_DATA_36034 = "42120011";
    /** Sibling of {@link #OK_NO_DATA_36034} ("Similar case as #36034" in the
     *  legacy comment). */
    public static final String OK_NO_DATA_36034_ALT = "42120111";

    /** Legacy {@code nxisas_api.js isSuccess()} whitelist — the bank-adapter
     *  {@code Output.ErrorCode} values treated as non-fatal success. */
    private static final Set<String> BANK_ADAPTER_SUCCESS = Set.of(
            OK, OK_NO_TRANSACTIONS, OK_NO_DATA_36034, OK_NO_DATA_36034_ALT);

    /**
     * Bank-adapter ({@code Output.ErrorCode}) success test. A {@code null} /
     * blank code is treated as success — there is no adapter verdict to act
     * on, matching the existing {@code code != null && !code.isBlank()}
     * guard the sync service applied inline before this util existed. Any
     * non-blank code outside the legacy whitelist is a failure.
     */
    public static boolean isBankAdapterSuccess(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) return true;
        return BANK_ADAPTER_SUCCESS.contains(errorCode);
    }

    /**
     * Gateway-envelope ({@code Common.Result_cd}) error test. Mirrors the
     * former private {@code BankScrapingService.isGatewayError}: success on
     * {@code "00000000"} and the legacy short forms {@code "0000"} /
     * {@code "OK"}; {@code null} / blank → not an error (no envelope info).
     */
    public static boolean isGatewayError(String resultCd) {
        if (resultCd == null || resultCd.isBlank()) return false;
        if (OK.equals(resultCd)) return false;
        if ("0000".equalsIgnoreCase(resultCd)) return false;
        if ("OK".equalsIgnoreCase(resultCd)) return false;
        return true;
    }
}
