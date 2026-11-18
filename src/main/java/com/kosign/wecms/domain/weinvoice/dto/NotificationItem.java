package com.kosign.wecms.domain.weinvoice.dto;

import java.util.List;

/**
 * One bell notification ({@code GET /notifications}). Fields that do not apply to the type are {@code null}.
 * Record: docs/features/addon-service/weinvoice/weinvoice-notifications-2026-11-12.md.
 *
 * @param id           {@code <TYPE>|<mst or ->|<direction or ->|<bucket>} — send it back to {@code POST /notifications/read}
 * @param type         {@code NEW_INVOICES | XML_READY | PDF_READY | GDT_LOGIN_ERROR | MAILBOX_ERROR}
 * @param companyMst   the tax code; {@code null} for a mailbox that serves every tax code of the user
 * @param direction    {@code purchase | sales} for the three count types
 * @param count        invoices in the bucket (count types)
 * @param fromProvider PDFs obtained from the provider's site ({@code PDF_READY})
 * @param fromEmail    PDFs taken from mail ({@code PDF_READY})
 * @param months       issue months {@code yyyy-MM} of the new invoices, newest first, at most 3 ({@code NEW_INVOICES})
 * @param mailboxId    the mailbox ({@code MAILBOX_ERROR})
 * @param address      the mailbox address ({@code MAILBOX_ERROR})
 * @param status       connection status {@code ERROR | EXPIRED} (error types)
 * @param message      the GDT portal's / mail server's message (error types)
 * @param at           latest event of the bucket, ISO-8601 UTC
 * @param read         read by the user
 * @param action       {@code true} for the two error types (filter "Cần xử lý")
 */
public record NotificationItem(
        String id,
        String type,
        String companyMst,
        String companyName,
        String direction,
        Long count,
        Long fromProvider,
        Long fromEmail,
        List<String> months,
        String mailboxId,
        String address,
        String status,
        String message,
        String at,
        boolean read,
        boolean action) {
}
