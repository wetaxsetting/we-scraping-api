package com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/banking/own-account-transfer/nxisas/cert-acct-list}.
 * Refactor port of legacy JEX {@code nxisas_acctList_r003} (see
 * {@code WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/nxisas/nxisas_acctList_r003_act.jsp}).
 *
 * <p>The bundle calls this via {@code jexjs.createAjaxUtil("nxisas_acctList_r003")}
 * when it needs to resolve the cert payload for one {@code BANK_CERT_SEQ}. The
 * refactor routes the call through {@code jexjs-adapter.ENDPOINT_MAP} to this
 * endpoint. Today the composer pre-populates the cert fields on the
 * source-account row, so the bundle's direct cert lookup is a fallback — the
 * endpoint stays available for parity with the legacy flow.
 *
 * <p>{@code SEQ_NO} is the {@code BANK_CERT_INFO.SEQ_NO} value. Tenant scope
 * rides on the JWT.
 *
 * <p><b>The UPPER_SNAKE wire name is pinned with {@code @JsonProperty}</b>
 * because the caller is the vendor bundle, which posts
 * {@code input.SEQ_NO = seq} ({@code nxisas_v2.js:1630}) — an external contract,
 * exempt from the lowercase snake_case rule like the {@code nxisas/gateway/}
 * DTOs.
 *
 * <p>Fixed 2026-08-18: the component was {@code seqNo} with no
 * {@code @JsonProperty}, so the global {@code SNAKE_CASE} strategy expected
 * {@code seq_no} while the bundle sent {@code SEQ_NO} — the field bound
 * {@code null} and {@code @NotBlank} rejected every call with
 * {@code WBE_VALIDATION}.
 *
 * <p><b>Known residual limitation (not fixed here):</b> the bundle requests this
 * call with {@code async: false} and reads the return value on the same tick
 * ({@code nxisas_v2.js:1634-1645}), which modern browsers cannot honour — the
 * adapter runs it async, so {@code getCertInfo(seq)} returns {@code null}
 * regardless of this binding. That is a structural sync-XHR problem in the
 * vendor bundle's contract, out of scope for the wire-contract fix; the
 * composer's pre-population is what makes the flow work today.
 */
public record NxisasCertAcctListRequest(
        @JsonProperty("SEQ_NO")
        @NotBlank String seqNo
) { }
