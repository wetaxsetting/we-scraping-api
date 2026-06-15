package com.kosign.wecms.domain.payment.vendorpayment.service;

import com.kosign.wecms.domain.payment.file.PaymentUploadFileResolver;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferStorageProperties;

import org.springframework.stereotype.Component;

/**
 * Resolves the physical path for a Vendor Payment batch's uploaded Excel
 * file:
 *
 * <pre>
 * &lt;storage-root&gt;/&lt;biz_key&gt;/vendor-payment/&lt;save_file_nm&gt;
 * </pre>
 *
 * <p>Bound to its own {@code wecms.vendor-payment.storage-root} (2026-06-12);
 * the default mirrors the other modules' root, so the physical layout is
 * unchanged unless the env var is overridden. Reads fall back to the legacy
 * {@code internal-transfer} subfolder for files written before the per-module
 * folder split.
 */
@Component
public class VendorPaymentFileResolver extends PaymentUploadFileResolver {

    /** Per-module subfolder — the route slug, never the legacy
     *  {@code pymn_0009_002} WSVC id. */
    public static final String SLUG_FOLDER = "vendor-payment";

    public VendorPaymentFileResolver(VendorPaymentStorageProperties properties) {
        super(properties.storageRoot(), SLUG_FOLDER, InternalTransferStorageProperties.SLUG_FOLDER);
    }
}
