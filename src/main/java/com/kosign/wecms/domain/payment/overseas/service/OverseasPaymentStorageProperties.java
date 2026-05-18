package com.kosign.wecms.domain.payment.overseas.service;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable storage root for Overseas Payment Excel uploads.
 * Resolves to the path pattern
 * {@code <storage-root>/<biz_key>/overseas-payment/<save_file_nm>}.
 *
 * <p>Mirrors the Internal Transfer storage convention
 * ({@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferStorageProperties}).
 *
 * <p>Default in {@code application.yml} is
 * {@code "./tmp/overseas-payment"} for unit tests / CI; the local
 * developer profile in {@code application-local.yml} points at the
 * workspace-shared {@code C:/WECMS PROJECT/File Data} folder.
 * Per-developer override via env var
 * {@code WECMS_OVERSEAS_PAYMENT_STORAGE_ROOT}.
 */
@ConfigurationProperties(prefix = "wecms.overseas-payment")
public record OverseasPaymentStorageProperties(

        @NotBlank
        String storageRoot

) {
    /** Folder name under each tenant directory — the API slug, never
     *  the legacy {@code pymn_0007_001} WSVC id. */
    public static final String SLUG_FOLDER = "overseas-payment";
}
