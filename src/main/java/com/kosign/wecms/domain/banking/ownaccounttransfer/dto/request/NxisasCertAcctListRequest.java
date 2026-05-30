package com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/banking/own-account-transfer/nxisas/cert-acct-list}.
 * Refactor port of legacy JEX {@code nxisas_acctList_r003} (see
 * {@code WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/nxisas/nxisas_acctList_r003_act.jsp}).
 *
 * <p>The bundle calls this via {@code jexjs.createAjaxUtil("nxisas_acctList_r003")}
 * when it needs to resolve the cert payload for one
 * {@code BANK_CERT_SEQ}. The refactor routes the call through
 * {@code jexjs-adapter.ENDPOINT_MAP} to this endpoint. Today the
 * composer pre-populates the cert fields on the source-account row,
 * so the bundle's direct cert lookup is a fallback — the endpoint
 * stays available for parity with the legacy flow.
 *
 * <p>{@code seqNo} is the {@code BANK_CERT_INFO.SEQ_NO} value. Tenant
 * scope rides on the JWT.
 */
public record NxisasCertAcctListRequest(
        @NotBlank String seqNo
) { }
