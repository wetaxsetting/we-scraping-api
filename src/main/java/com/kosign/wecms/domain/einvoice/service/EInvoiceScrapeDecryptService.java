package com.kosign.wecms.domain.einvoice.service;

import com.kosign.wecms.common.nxisas.SeedCbcDecryptService;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceDecryptRequest;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceDecryptResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Decrypts the GDT e-Invoice engine callback payload server-side — the
 * refactor port of the legacy {@code nxisas_decrypt} action for the
 * e-Invoice scrape flow (legacy {@code nxisas_einvoice_c001/c002_act.jsp}
 * decrypt {@code ENC_STR.Result} inline; the refactor exposes it as a
 * standalone step so the browser can parse the phase-1 list before it
 * builds the phase-2 INVOICE_DETAIL jobs).
 *
 * <p>Unlike {@code OatNxisasService}, decryption is <b>unconditional</b>
 * here — the GDT engine always returns SEED-CBC ciphertext (the `Result`
 * blobs are never plaintext), so gating it behind a feature flag would
 * only re-break the scrape. The shared {@link SeedCbcDecryptService} does
 * the actual cipher with the licensed Coocon {@code ISASSeedCBC}
 * ({@code libs/isas1.0.jar}).
 */
@Service
@RequiredArgsConstructor
public class EInvoiceScrapeDecryptService {

    private final SeedCbcDecryptService seedCbc;

    /**
     * Decrypt every {@code Output.Result} in the posted engine callback
     * array and return the array with each result replaced by its parsed
     * plaintext JSON.
     */
    public EInvoiceDecryptResponse decrypt(EInvoiceDecryptRequest request) {
        String decrypted = seedCbc.decryptNxisasData(
                request.nxisasData(), request.nxisasUid(), request.nxisasAction());
        return new EInvoiceDecryptResponse(decrypted);
    }
}
