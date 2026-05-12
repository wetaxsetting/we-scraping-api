package com.kosign.wecms.domain.setting.erptransactionhistory.service;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable storage root for the local ERP-Transaction-History
 * download endpoint. Resolves to the path pattern
 * {@code <storage-root>/<biz_key>/erp-transaction-history/<save_file_nm>}
 * (slug, NOT the legacy {@code comm_0219_001_c001} NAS subfolder —
 * the refactor folder name is product-agnostic).
 *
 * <p>Default in {@code application-local.yml} is
 * {@code "C:/WECMS PROJECT/File Data"} (OQ-3, resolved 2026-05-11) —
 * developers on a non-Windows / non-standard layout override via
 * env var {@code WECMS_ERP_TRANSACTION_HISTORY_STORAGE_ROOT}.
 *
 * <p>JEX parity note: the live system reads from {@code WbStatic.PATH_TO_NAS_ROOT}
 * with the legacy {@code comm_0219_001_c001} subfolder name. The
 * refactor uses the slug — a deliberate divergence so future tooling
 * can identify "refactor-written" file paths at a glance.
 */
@ConfigurationProperties(prefix = "wecms.erp-transaction-history")
public record ErpTrnsHistoryStorageProperties(

        /** Filesystem path where the per-tenant subtree lives. Required
         *  at startup; the path itself does not have to exist (callers
         *  may seed it lazily on first download). */
        @NotBlank
        String storageRoot

) {
    /** Folder name under each tenant directory — never configurable;
     *  the slug is the API contract per
     *  {@code wecms-api-main/CLAUDE.md §"Refactor identifier naming"}. */
    public static final String SLUG_FOLDER = "erp-transaction-history";
}
