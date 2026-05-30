package com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response;

/**
 * Response body for {@code POST /api/v1/banking/own-account-transfer/nxisas/decrypt}
 * (and {@code /nxisas/encrypt}). Pass-through shape — {@code NXISAS_DATA}
 * returned verbatim. See {@code NxisasDecryptRequest} Javadoc for the
 * Coocon-license rationale.
 */
public record NxisasDecryptResponse(
        String NXISAS_DATA
) { }
