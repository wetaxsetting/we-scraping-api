package com.kosign.wecms.domain.payment.internaltransfer.service;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable storage root for Internal Transfer Excel uploads.
 * Resolves to the path pattern
 * {@code <storage-root>/<biz_key>/internal-transfer/<save_file_nm>}
 * (slug, NOT the legacy {@code pymn_0009_002_c001} NAS subfolder —
 * the refactor folder name is product-agnostic).
 *
 * <p>Mirrors the ERP Transaction History storage convention
 * ({@link com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryStorageProperties})
 * — both features share the same {@code File Data/} root in
 * developer workspaces, with the slug subfolder picking the
 * feature's per-tenant subtree.
 *
 * <p>Default in {@code application.yml} is
 * {@code "./tmp/internal-transfer"} for unit tests / CI; the local
 * developer profile in {@code application-local.yml} points at the
 * workspace-shared {@code C:/WECMS PROJECT/File Data} folder
 * (verified against the user's {@code File Data/} VS Code explorer
 * tree, 2026-05-13). Per-developer override via env var
 * {@code WECMS_INTERNAL_TRANSFER_STORAGE_ROOT}.
 */
@ConfigurationProperties(prefix = "wecms.internal-transfer")
public record InternalTransferStorageProperties(

        /** Filesystem path where the per-tenant subtree lives. Required
         *  at startup; the path itself does not have to exist (the
         *  service creates the per-tenant + per-slug subtree on first
         *  write). */
        @NotBlank
        String storageRoot

) {
    /** Folder name under each tenant directory — never configurable;
     *  the slug is the API contract per
     *  {@code wecms-api-main/CLAUDE.md §"Refactor identifier naming"}. */
    public static final String SLUG_FOLDER = "internal-transfer";
}
