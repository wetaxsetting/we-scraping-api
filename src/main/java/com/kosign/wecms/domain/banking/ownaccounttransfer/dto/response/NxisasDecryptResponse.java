package com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response;

/**
 * Response body for {@code POST /api/v1/banking/own-account-transfer/nxisas/decrypt}
 * (and {@code /nxisas/encrypt}). Carries the transformed job array as a JSON
 * string, mirroring legacy {@code nxisas_decrypt_act.jsp}, which returns the
 * decrypted payload under the same single field the request used.
 *
 * <p>Wire name is {@code nxisas_data} (global {@code SNAKE_CASE} strategy).
 * See {@link com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.NxisasDecryptRequest}
 * for why this contract is lowercase rather than the legacy UPPER_SNAKE form.
 */
public record NxisasDecryptResponse(
        String nxisasData
) { }
