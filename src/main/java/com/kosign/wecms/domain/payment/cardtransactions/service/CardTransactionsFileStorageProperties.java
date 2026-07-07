package com.kosign.wecms.domain.payment.cardtransactions.service;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable storage root for Payment &gt; Card Transactions receipt /
 * e-Invoice attachments (audit G3/G5). Path pattern
 * {@code <storage-root>/<biz_key>/card-transactions/<save_file_nm>} — same
 * shape as Contacts / ERP Transaction History, different slug folder.
 *
 * <p>Set via env var {@code WECMS_CARD_TRANSACTIONS_STORAGE_ROOT}
 * (shares the common {@code File Data} root in local dev).
 */
@ConfigurationProperties(prefix = "wecms.card-transactions")
public record CardTransactionsFileStorageProperties(

        @NotBlank
        String storageRoot

) {
    /** Folder name under each tenant directory — the refactor slug (never the
     *  legacy WSVC id), per {@code wecms-api/CLAUDE.md §"Refactor identifier naming"}. */
    public static final String SLUG_FOLDER = "card-transactions";
}
