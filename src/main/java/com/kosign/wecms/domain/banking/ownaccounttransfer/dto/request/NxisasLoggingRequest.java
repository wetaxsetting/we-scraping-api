package com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for {@code POST /api/v1/banking/own-account-transfer/nxisas/logging}.
 * Refactor port of legacy JEX {@code nxisas_logging} action (see
 * {@code WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/nxisas/nxisas_logging_act.jsp}).
 *
 * <p>The browser bundle posts a serialised JSON array (the {@code orgObj} the
 * bundle accumulates across job calls) as {@code JSON_STR}
 * ({@code nxisas_v2.js:2310} — {@code _input['JSON_STR'] =
 * JSON.stringify(_v2nxisas.orgObj)}). The server walks the array and writes one
 * {@code NXSIAS_MODULE_TRAN_LOG_HIS} row per terminal job (LOGIN / BALANCE /
 * TRANSFER / OTP / QNA / CAPTCHA / ACCT_LIST) so the compliance trail mirrors
 * live JEX behaviour.
 *
 * <p><b>The UPPER_SNAKE wire name is vendor-driven and pinned with
 * {@code @JsonProperty}</b> — this endpoint's caller is the vendor bundle
 * itself (via the JEX adapter, which posts the bundle's payload verbatim), not
 * refactor code, so the key is an external contract rather than a WeCMS wire
 * name. It is therefore exempt from the lowercase snake_case rule for the same
 * reason the {@code nxisas/gateway/} DTOs are.
 *
 * <p>Fixed 2026-08-18: the component previously carried no {@code @JsonProperty},
 * so the global {@code PropertyNamingStrategies.SNAKE_CASE} strategy
 * ({@code JacksonConfig:25}) exposed it as {@code json_str} while the bundle
 * sent {@code JSON_STR}. It bound {@code null}, {@code recordLogging} hit its
 * null guard and returned, and <b>no audit row was ever written from this
 * screen</b>. Do not "normalise" this to a camelCase component — that would
 * re-break the binding.
 */
public record NxisasLoggingRequest(
        @JsonProperty("JSON_STR")
        String JSON_STR
) { }
