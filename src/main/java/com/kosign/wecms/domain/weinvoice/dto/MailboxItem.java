package com.kosign.wecms.domain.weinvoice.dto;

import java.util.List;

/**
 * A connected mailbox ({@code GET /mailboxes}, and the answer of every write). No secret is ever part
 * of it — the password, app password or Microsoft refresh token stays encrypted in the database.
 *
 * <p>Codes travel exactly as stored, upper case, the same vocabulary as the request:
 *
 * @param kind             {@code GMAIL | MICROSOFT | IMAP}
 * @param authType         {@code APP_PASSWORD | PASSWORD | OAUTH_MS}
 * @param tlsMode          {@code SSL | STARTTLS}
 * @param companyScope     {@code ONE} ({@code company_mst} only) or {@code ALL} (every tax code of the user)
 * @param companyMst       set when {@code company_scope = ONE}, {@code null} for {@code ALL}
 * @param status           {@code CONNECTED | ERROR | EXPIRED}
 * @param statusMessage    last error reported by the engine (mail server text), {@code null} when connected
 * @param connectedAt      when this address was connected (ISO instant)
 * @param lastScanAt       end of the last engine scan (ISO instant), {@code null} before the first scan
 * @param lastScanInvoices invoices that got a file from the last scan
 */
public record MailboxItem(
        String id,
        String kind,
        String address,
        String authType,
        String imapHost,
        Integer imapPort,
        String tlsMode,
        String loginId,
        List<String> folders,
        String companyScope,
        String companyMst,
        String status,
        String statusMessage,
        String connectedAt,
        String lastScanAt,
        Integer lastScanInvoices) {
}
