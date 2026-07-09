package com.kosign.wecms.domain.payment.expensepayment.service;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable storage root for Expense Payment Excel uploads. Files live
 * in a per-tenant subtree
 * {@code <storage-root>/<biz_key>/expense-payment/<save_file_nm>}.
 *
 * <p>Expense Payment used to reuse {@code wecms.internal-transfer.storage-root};
 * as of 2026-06-12 it has its own root key so it can be pointed at a separate
 * volume without affecting Internal Transfer. The default still mirrors the
 * other modules' root, so the physical layout is unchanged unless the env var
 * is overridden. Per-developer / per-environment override via env var
 * {@code WECMS_EXPENSE_PAYMENT_STORAGE_ROOT}.
 */
@ConfigurationProperties(prefix = "wecms.expense-payment")
public record ExpensePaymentStorageProperties(

        /** Filesystem root under which each tenant's {@code expense-payment}
         *  subtree lives. */
        @NotBlank
        String storageRoot

) {
}
