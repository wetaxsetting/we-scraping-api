package com.kosign.wecms.domain.setting.logincredentials.dto;

/**
 * Decrypted GDT credential handed to the client-side NXiSAS engine for
 * the e-Invoice "Real-Time Online Inquire" scrape.
 *
 * <p>This is the GDT analogue of the bank
 * {@link com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService.DecryptedCredential}
 * scraping surface. The e-Invoice engine runs on the user's PC and logs
 * into the GDT tax portal itself, so — unlike bank/card sync (which
 * decrypts server-side and never exposes the secret) — the plaintext
 * must reach the browser. It is returned ONLY by the dedicated
 * scraping-credential endpoint (JWT-authenticated + tenant-scoped),
 * never by the credential-list path (which masks the password).
 *
 * <p>Legacy parity: mirrors {@code gdtc_0001_001_r001} + the legacy
 * {@code nxisas_einvoice.js getCertInfo()} call, which returned
 * {@code CERT_INFO_CON} + {@code CERT_INFO_DETL_CON} straight to the
 * engine with no password re-entry. Distinct from the reveal path
 * ({@link RevealResponse}), which gates on the credential's own password
 * because it is the credential-management "view password" feature.
 */
public record GdtScrapingCredentialResponse(
        String loginId,
        String password
) { }
