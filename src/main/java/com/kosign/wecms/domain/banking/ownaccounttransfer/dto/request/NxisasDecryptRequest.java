package com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request;

/**
 * Request body for {@code POST /api/v1/banking/own-account-transfer/nxisas/decrypt}
 * (and {@code /nxisas/encrypt}). Refactor port of legacy JEX
 * {@code nxisas_decrypt} / {@code nxisas_encrypt} actions.
 *
 * <p>The legacy action runs Coocon SEED-CBC ({@code ISASSeedCBC}) on
 * the engine's output {@code Result} payloads. The refactor does NOT
 * bundle the Coocon ISAS library — the endpoint is a structured
 * pass-through that returns {@code NXISAS_DATA} unchanged so the
 * bundle's response-chain doesn't crash on the missing endpoint. Most
 * VND own-account-transfer paths (Vietin / Vietcom plaintext JSON)
 * never need actual decrypt; banks that do need it would fall back to
 * the engine's own decoder on the user's machine.
 *
 * <p>The bundle posts UPPER_SNAKE-keyed JSON because it predates the
 * lowercase snake_case wire convention. The endpoint accepts the
 * UPPER_SNAKE form verbatim.
 */
public record NxisasDecryptRequest(
        String NXISAS_DATA,
        String NXISAS_UID,
        String NXISAS_ACTION
) { }
