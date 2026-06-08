package com.kosign.wecms.domain.banking.ownaccounttransfer.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.BalanceRefreshRequest;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.EngineConnectionCheckRequest;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.NxisasCertAcctListRequest;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.NxisasDecryptRequest;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.NxisasLoggingRequest;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.ScrapingResultIntakeRequest;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.BalanceRefreshResponse;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.NapasBankInfoRow;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.NxisasCertAcctListResponse;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.NxisasDecryptResponse;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.ScrapingResultResponse;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.SourceAccountListResponse;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.TargetAccountListResponse;
import com.kosign.wecms.domain.banking.ownaccounttransfer.service.OatNxisasService;
import com.kosign.wecms.domain.banking.ownaccounttransfer.service.OwnAccountTransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Banking &gt; Own Account Transfer — REST surface under
 * {@code /api/v1/banking/own-account-transfer}. Path A scope (4
 * endpoints):
 * <ul>
 *   <li>{@code GET  /source-accounts} — source-account picker (port of
 *       legacy {@code trns_0001_001_r001}).</li>
 *   <li>{@code POST /balance-refresh} — post-scrape balance update
 *       (port of legacy {@code trns_0001_001_u001}).</li>
 *   <li>{@code POST /engine-connection-check} — audit-only sink for
 *       the engine-handshake report. Returns {@code 204 No Content}.</li>
 *   <li>{@code GET  /napas-bank-info/{napas_bank_cd}} — NAPAS bank
 *       dictionary lookup (port of legacy {@code trns_0001_001_r003}).</li>
 * </ul>
 *
 * <p>Wire format is lowercase snake_case via the global Jackson naming
 * strategy ({@code config/JacksonConfig}). {@code @Validated} at the
 * class level enables the {@code @NotBlank}/{@code @Pattern} guards on
 * the path / query parameters.
 *
 * <p>Slug is {@code own-account-transfer} — the stable identifier
 * shared with the audit-log {@code WSVC_ID}, log/MDC tag, i18n key
 * prefix, and View Setting {@code WEB_SVC_ID} (V180 seed). Legacy
 * {@code trns_0001_001} appears only as a citation in the migration
 * doc, never in code.
 *
 * <p>Not in this MR — composer / scraping-result feature:
 * {@code /scraping-result}, {@code /target-accounts},
 * {@code /transfer-history}, {@code /recipient-banks}. Those depend
 * on the transfer-execution surface and the {@code NAPAS_BANK_REMIT_INFO}
 * table; ship together in the composer MR.
 */
@RestController
@RequestMapping("/api/v1/banking/own-account-transfer")
@RequiredArgsConstructor
@Validated
@PreAuthorize("@menuAuthz.canAccess('banking-internal-transfer-own')")
public class OwnAccountTransferController {

    private final OwnAccountTransferService service;
    private final OatNxisasService          nxisasService;

    /**
     * Source-account picker. All three params are optional; the
     * service handles unsupported {@code bank_cd} values (coerced to
     * "all"), unknown {@code sortField}/{@code sortDirection} tokens
     * (fall back to default ORDER BY), and the HAENGSUNG sender-bank
     * narrowing (forced VietinBank-only inside the service).
     *
     * <p><b>Query-parameter naming.</b> {@code sortField} /
     * {@code sortDirection} use camelCase to match every other banking
     * list endpoint in this repo ({@code AccountController#listAccounts},
     * Transaction History, Approval Box) and the frontend's shared
     * {@code useTableSort()} hook which emits camelCase params straight
     * into the Axios request. Path A's earlier {@code sort_field} /
     * {@code sort_direction} (snake_case) names silently dropped sort
     * clicks on the wire — the frontend kept sending camelCase, the
     * controller bound null, and the service fell back to default ORDER
     * BY. JSON bodies stay snake_case per CTO 2026-05-08; the wire
     * convention for query-string parameters in this repo is camelCase
     * (intentional difference — query params are URL components, not
     * JSON payload).
     */
    @GetMapping("/source-accounts")
    public ApiResponse<SourceAccountListResponse> listSourceAccounts(
            @RequestParam(name = "bank_cd",       required = false) String bankCd,
            @RequestParam(name = "sortField",     required = false) String sortField,
            @RequestParam(name = "sortDirection", required = false) String sortDirection) {
        return ApiResponse.ok(service.listSourceAccounts(bankCd, sortField, sortDirection));
    }

    /**
     * Post-scrape balance update. The frontend POSTs the structured
     * outcome the local NXiSAS engine reported back to the browser.
     * Spring never invokes the engine; it persists what the engine
     * reported, audits success / failure equally, and returns the
     * canonical post-patch balance.
     *
     * <p>Errors:
     * {@code WBE_OWN_ACCOUNT_TRANSFER_ACCOUNT_NOT_FOUND} (no row /
     * wrong tenant), {@code WBE_OWN_ACCOUNT_TRANSFER_ACCOUNT_INACTIVE},
     * {@code WBE_OWN_ACCOUNT_TRANSFER_INVALID_CCY},
     * {@code WBE_OWN_ACCOUNT_TRANSFER_FORBIDDEN} (non-admin without
     * W-grant).
     */
    @PostMapping("/balance-refresh")
    public ApiResponse<BalanceRefreshResponse> refreshBalance(
            @Valid @RequestBody BalanceRefreshRequest req) {
        return ApiResponse.ok(service.applyBalanceRefresh(req));
    }

    /**
     * Audit-only sink for the engine-handshake report. The browser
     * bundle POSTs the outcome of its {@code _v2nxisas.openNxisas(...)}
     * probe at page mount; the row IS the only artifact (no domain
     * effect). Returns {@code 204 No Content} per the finalized plan §2.
     */
    @PostMapping("/engine-connection-check")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordEngineConnectionCheck(
            @Valid @RequestBody EngineConnectionCheckRequest req) {
        service.recordEngineConnectionCheck(req);
    }

    /**
     * NAPAS bank-code lookup. {@code napas_bank_cd} is a 6-to-10 digit
     * public NAPAS code (e.g. {@code 970424} Shinhan / {@code 970436}
     * Vietcombank). Returns {@code WBE_OWN_ACCOUNT_TRANSFER_NAPAS_NOT_FOUND}
     * when the code is not in the dictionary.
     */
    @GetMapping("/napas-bank-info/{napas_bank_cd}")
    public ApiResponse<NapasBankInfoRow> lookupNapasBankInfo(
            @PathVariable("napas_bank_cd")
            @NotBlank
            @Pattern(regexp = "\\d{6,10}",
                     message = "{validation.own_account_transfer.napas_bank_cd}")
            String napasBankCd) {
        return ApiResponse.ok(service.lookupNapasBankInfo(napasBankCd));
    }

    /**
     * Target-account picker for the composer. Returns the tenant's own
     * VND accounts the user can transfer TO, excluding the row they
     * picked as the source (when {@code source_bank_cd} +
     * {@code source_acct_no} are supplied).
     *
     * <p>Per OQ-A (PO 2026-05-18) the scope is own-only — external
     * NAPAS recipients are deferred. The response carries the NAPAS
     * routing code for each row so the composer can drive the engine's
     * cross-bank ({@code NAPAS}) job when the source and target banks
     * differ.
     */
    @GetMapping("/target-accounts")
    public ApiResponse<TargetAccountListResponse> listTargetAccounts(
            @RequestParam(name = "source_bank_cd", required = false) String sourceBankCd,
            @RequestParam(name = "source_acct_no", required = false) String sourceAcctNo) {
        return ApiResponse.ok(service.listTargetAccounts(sourceBankCd, sourceAcctNo));
    }

    /**
     * Post-scrape intake. The browser bundle (local NXiSAS engine)
     * runs the bank scrape, captures the engine's output, and POSTs
     * the structured result here. Spring re-validates ownership,
     * amount tolerance (exact match for VND), bank tx id format,
     * and idempotency, then persists the canonical row to
     * {@code NAPAS_REMIT_TRNS_HSTR} — the same legacy contract
     * {@code trns_0002_001_c001_act.jsp} writes.
     *
     * <p>A second POST with the same {@code idempotency_key} returns
     * the previously recorded row with {@code already_recorded=true};
     * clients should treat that as success.
     *
     * <p>Errors:
     * {@code WBE_OAT_IDEMPOTENCY_CONFLICT},
     * {@code WBE_OWN_ACCOUNT_TRANSFER_INVALID_CCY},
     * {@code WBE_OAT_TARGET_NAPAS_REQUIRED},
     * {@code WBE_OAT_TARGET_BANK_REQUIRED},
     * {@code WBE_OAT_SAME_BANK_MISMATCH},
     * {@code WBE_OAT_AMOUNT_MISMATCH},
     * {@code WBE_OAT_SOURCE_NOT_FOUND},
     * {@code WBE_OAT_SOURCE_FORBIDDEN},
     * {@code WBE_OAT_TARGET_NAPAS_UNKNOWN},
     * {@code WBE_OAT_TARGET_BANK_NAPAS_MISMATCH},
     * {@code WBE_OAT_TARGET_NOT_FOUND}.
     */
    @PostMapping("/scraping-result")
    public ApiResponse<ScrapingResultResponse> recordScrapingResult(
            @Valid @RequestBody ScrapingResultIntakeRequest req) {
        return ApiResponse.ok(service.recordScrapingResult(req));
    }

    /* =================================================================
     *  Phase B — bundle-facing NXiSAS endpoints
     *  -----------------------------------------------------------------
     *  These four endpoints port the legacy JEX
     *  {@code nxisas_acctList_r003} / {@code nxisas_decrypt} /
     *  {@code nxisas_encrypt} / {@code nxisas_logging} actions so the
     *  browser bundle's {@code jexjs.createAjaxUtil(...)} calls resolve
     *  to real Spring REST endpoints (routed through the frontend
     *  {@code jexjs-adapter.ts ENDPOINT_MAP}). Tenant scope rides on
     *  the JWT — none of these endpoints accept a wire {@code biz_key}.
     *
     *  All four URLs live UNDER the OAT controller because OAT is the
     *  first (today only) consumer. If a future feature (E-Invoice,
     *  Operation Status) needs them, lift the service + DTOs to a
     *  shared {@code domain/banking/nxisas/} module and add per-feature
     *  controller endpoints alongside.
     * ================================================================= */

    /**
     * Refactor port of legacy {@code nxisas_acctList_r003}. Resolves
     * the five cert payload fields the bundle's
     * {@code _v2nxisas.getCertInfo(seqNo)} consumes. Today the OAT
     * composer pre-populates these on the source-account row, so the
     * endpoint is a fallback — but the bundle calls it directly during
     * 2nd-cert flows.
     */
    @PostMapping("/nxisas/cert-acct-list")
    public ApiResponse<NxisasCertAcctListResponse> nxisasCertAcctList(
            @Valid @RequestBody NxisasCertAcctListRequest req) {
        return ApiResponse.ok(nxisasService.certAcctList(req));
    }

    /**
     * Refactor port of legacy {@code nxisas_decrypt}. Decrypts the engine
     * {@code Output.Result} via the Coocon {@code ISASSeedCBC} cipher when
     * {@code wecms.oat.seed-decrypt.enabled} is on (B1); otherwise returns
     * {@code NXISAS_DATA} unchanged. See {@link OatNxisasService#decrypt}.
     */
    @PostMapping("/nxisas/decrypt")
    public ApiResponse<NxisasDecryptResponse> nxisasDecrypt(
            @RequestBody NxisasDecryptRequest req) {
        return ApiResponse.ok(nxisasService.decrypt(req));
    }

    /**
     * Refactor port of legacy {@code nxisas_encrypt} — inverse of
     * {@code /nxisas/decrypt}, used by the 2nd-factor re-send path. Same
     * {@code wecms.oat.seed-decrypt.enabled} gate.
     */
    @PostMapping("/nxisas/encrypt")
    public ApiResponse<NxisasDecryptResponse> nxisasEncrypt(
            @RequestBody NxisasDecryptRequest req) {
        return ApiResponse.ok(nxisasService.encrypt(req));
    }

    /**
     * Refactor port of legacy {@code nxisas_logging}. The bundle posts
     * a serialised {@code _threadArray} as {@code JSON_STR}; the
     * service writes one {@code NXSIAS_MODULE_TRAN_LOG_HIS} row per
     * terminal job. Returns {@code 204 No Content} — the row IS the
     * artifact.
     */
    @PostMapping("/nxisas/logging")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void nxisasLogging(@RequestBody NxisasLoggingRequest req) {
        nxisasService.recordLogging(req);
    }
}
