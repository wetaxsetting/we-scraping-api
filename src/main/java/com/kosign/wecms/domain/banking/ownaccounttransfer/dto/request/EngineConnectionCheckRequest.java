package com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body for
 * {@code POST /api/v1/banking/own-account-transfer/engine-connection-check}.
 * Posted once at page mount when the browser bundle's
 * {@code _v2nxisas.openNxisas(...)} returns — the frontend echoes the
 * engine vendor / version it observed so compliance has a record of
 * which engine answered for which user / session.
 *
 * <p>Audit-only sink — no domain effect. The service writes one
 * {@code AUDIT_LOGS_INFM} row and the controller returns
 * {@code 204 No Content}.
 *
 * <p>Field shape mirrors the finalized backend plan §3.4a. Wire format
 * is lowercase snake_case via the global Jackson naming strategy.
 */
public record EngineConnectionCheckRequest(

        @NotBlank
        @Pattern(regexp = "OK|FAIL|TIMEOUT|NOT_INSTALLED",
                 message = "{validation.own_account_transfer.engine_result}")
        String result,

        @NotBlank @Size(max = 50)
        String engineVendor,

        @NotBlank @Size(max = 50)
        String engineVersion,

        @Size(max = 255)  String userAgent,
        @Size(max = 100)  String osPlatform,
        @Size(max = 100)  String scrapeSessionId,
        @Size(max = 20)   String errorCode,
        @Size(max = 255)  String errorMessage

) { }
