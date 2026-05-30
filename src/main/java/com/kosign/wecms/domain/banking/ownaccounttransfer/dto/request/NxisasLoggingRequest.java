package com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request;

/**
 * Request body for {@code POST /api/v1/banking/own-account-transfer/nxisas/logging}.
 * Refactor port of legacy JEX {@code nxisas_logging} action (see
 * {@code WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/nxisas/nxisas_logging_act.jsp}).
 *
 * <p>The browser bundle posts a serialised JSON array (the
 * {@code _threadArray} the bundle accumulates across job calls) as
 * {@code JSON_STR}. The server walks the array and writes one
 * {@code NXSIAS_MODULE_TRAN_LOG_HIS} row per terminal job (LOGIN /
 * BALANCE / TRANSFER / OTP / QNA / CAPTCHA / ACCT_LIST) so the
 * compliance trail mirrors live JEX behaviour.
 *
 * <p>UPPER_SNAKE field name preserved verbatim — the legacy bundle
 * sends {@code {"JSON_STR": "..."}}.
 */
public record NxisasLoggingRequest(
        String JSON_STR
) { }
