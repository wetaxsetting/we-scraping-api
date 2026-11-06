package com.kosign.wecms.domain.weinvoice.dto;

import java.util.List;

/**
 * A processed mail (UI Spec {@code EmailMessage}, screen 07).
 *
 * @param result       {@code matched} (a file or link landed on an invoice), {@code duplicate},
 *                     {@code unmatched} (XML / PDF waiting for its invoice), {@code no_invoice}, {@code error}
 * @param engineStatus engine status of the mail as stored
 * @param invoiceRef   id of the first invoice the mail fed
 */
public record EmailMessageItem(
        String id,
        String receivedAt,
        String from,
        String subject,
        List<String> attachments,
        String result,
        String engineStatus,
        String invoiceRef) {
}
