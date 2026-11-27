package com.kosign.wecms.domain.weinvoice.dto;

import java.util.List;

/** {@code GET /email/messages/{id}}: the mail, each file and each lookup link with its state. */
public record EmailMessageDetail(EmailMessageItem message, List<FileItem> files, List<LinkItem> links) {

    /** @param status engine file status: matched | unmatched | duplicate | not_ours | not_invoice */
    public record FileItem(String id, String name, String kind, String status, String invoiceId, String note) {
    }

    /** @param status engine link status: new | done | failed | ignored | mail_file */
    public record LinkItem(String id, String host, String url, String code, String status, String invoiceId,
                           int attempts, String lastError) {
    }
}
