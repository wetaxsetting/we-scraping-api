package com.kosign.wecms.domain.setting.contacts.service;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable storage root for Settings &gt; Contacts attachments.
 * Resolves to the path pattern
 * {@code <storage-root>/<biz_key>/contacts/<save_file_nm>}.
 *
 * <p>Default in {@code application-local.yml} is
 * {@code "C:/WECMS PROJECT/File Data"} — shared with ERP Transaction
 * History. Developers on a non-Windows / non-standard layout override
 * via env var {@code WECMS_CONTACTS_STORAGE_ROOT}.
 *
 * <p>JEX parity note: the live system reads from
 * {@code WbStatic.PATH_TO_NAS_ROOT} (NAS) with the legacy
 * {@code trco_0004_001_u002} WSVC subfolder name. The refactor uses
 * the slug {@code contacts} — a deliberate divergence so future
 * tooling can identify "refactor-written" file paths at a glance.
 */
@ConfigurationProperties(prefix = "wecms.contacts")
public record ContactsFileStorageProperties(

        /** Filesystem path where the per-tenant subtree lives. */
        @NotBlank
        String storageRoot

) {
    /** Folder name under each tenant directory — never configurable;
     *  the slug is the API contract per
     *  {@code wecms-api-main/CLAUDE.md §"Refactor identifier naming"}. */
    public static final String SLUG_FOLDER = "contacts";
}
