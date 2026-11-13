package com.kosign.wecms.domain.weinvoice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * {@code POST /mailboxes} and {@code PUT /mailboxes/{id}}.
 *
 * @param kind         {@code GMAIL | IMAP} (Microsoft mailboxes are connected through
 *                     {@code /mailboxes/microsoft/authorize}; a {@code PUT} on one sends {@code MICROSOFT})
 * @param password     Gmail: the 16-letter app password (spaces ignored); IMAP: the mailbox password.
 *                     Required on {@code POST}; on {@code PUT} blank keeps the stored secret
 * @param imapHost     IMAP only (required); ignored for Gmail / Microsoft
 * @param imapPort     IMAP only, default 993
 * @param tlsMode      IMAP only, {@code SSL} (default) or {@code STARTTLS}
 * @param loginId      IMAP login when it differs from the address; blank = the address
 * @param folders      folders to scan; empty = {@code INBOX}
 * @param companyScope {@code ONE} (default) or {@code ALL}
 * @param companyMst   required when {@code company_scope = ONE}; must be one of the user's tax codes
 */
public record MailboxSaveRequest(
        @NotBlank @Size(max = 10) String kind,
        @NotBlank @Size(max = 320) String address,
        @Size(max = 500) String password,
        @Size(max = 255) String imapHost,
        @Min(1) @Max(65535) Integer imapPort,
        @Size(max = 10) String tlsMode,
        @Size(max = 320) String loginId,
        @Size(max = 20) List<@Size(max = 100) String> folders,
        @Size(max = 5) String companyScope,
        @Size(max = 14) String companyMst) {

    /** Never print the password. */
    @Override
    public String toString() {
        return "MailboxSaveRequest[kind=" + kind + ", address=" + address + ", password="
                + (password == null || password.isBlank() ? "<none>" : "***") + ", imapHost=" + imapHost
                + ", imapPort=" + imapPort + ", tlsMode=" + tlsMode + ", loginId=" + loginId + ", folders=" + folders
                + ", companyScope=" + companyScope + ", companyMst=" + companyMst + "]";
    }
}
