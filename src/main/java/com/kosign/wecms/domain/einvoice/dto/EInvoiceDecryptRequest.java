package com.kosign.wecms.domain.einvoice.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for the GDT e-Invoice engine-payload decrypt endpoint
 * ({@code POST /api/v1/addon/einvoices/decrypt}).
 *
 * <p>Refactor port of the legacy {@code nxisas_decrypt} action's inputs
 * ({@code nxisas_v2.js:1674-1676}). The browser drives the local NXiSAS
 * engine with {@code nonDecrypt:true} (mirroring legacy), so each job's
 * {@code Output.Result} comes back as Coocon SEED-CBC ciphertext. The
 * frontend serialises the raw engine callback array and posts it here to
 * be decrypted server-side (the refactor never bundled a client-side
 * cipher — OQ-S2), then parses the returned plaintext to build the next
 * scrape phase / the persist payload.
 *
 * @param nxisasData   the {@code NXISAS_DATA} — a JSON array string of the
 *                     engine callback (one element per job, each carrying
 *                     {@code Output.Result} ciphertext).
 * @param nxisasUid    the engine session {@code NXISAS_UID}
 *                     ({@code CooconiSASNX.OpenUid}) — SEED key material;
 *                     may be blank, in which case the fixed-key cipher is used.
 * @param nxisasAction the engine session {@code NXISAS_ACTION}
 *                     ({@code CooconiSASNX.OpenAction}) — SEED IV material;
 *                     may be blank, as above.
 */
public record EInvoiceDecryptRequest(
        @NotBlank String nxisasData,
        String nxisasUid,
        String nxisasAction
) { }
