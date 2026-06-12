package com.kosign.wecms.domain.payment.expensepayment.service;

import com.kosign.wecms.domain.payment.file.PaymentUploadFileResolver;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferStorageProperties;

import org.springframework.stereotype.Component;

/**
 * Resolves the physical path for an Expense Payment batch's uploaded
 * Excel file:
 *
 * <pre>
 * &lt;storage-root&gt;/&lt;biz_key&gt;/expense-payment/&lt;save_file_nm&gt;
 * </pre>
 *
 * <p>Bound to its own {@code wecms.expense-payment.storage-root} (2026-06-12);
 * the default mirrors the other modules' root, so the physical layout is
 * unchanged unless the env var is overridden. Reads fall back to the legacy
 * {@code internal-transfer} subfolder for files written before the per-module
 * folder split.
 */
@Component
public class ExpensePaymentFileResolver extends PaymentUploadFileResolver {

    /** Per-module subfolder — the route slug, never the legacy
     *  {@code pymn_0010_002} WSVC id. */
    public static final String SLUG_FOLDER = "expense-payment";

    public ExpensePaymentFileResolver(ExpensePaymentStorageProperties properties) {
        super(properties.storageRoot(), SLUG_FOLDER, InternalTransferStorageProperties.SLUG_FOLDER);
    }
}
