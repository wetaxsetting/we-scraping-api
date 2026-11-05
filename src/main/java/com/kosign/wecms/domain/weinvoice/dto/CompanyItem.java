package com.kosign.wecms.domain.weinvoice.dto;

/**
 * A managed tax code (UI Spec {@code GdtConnection}, screen 06). The password is never part of
 * any response (UI Spec 3.10); {@code has_password} only says whether one is stored.
 *
 * @param status {@code pending} (login test queued), {@code connected}, {@code error},
 *               {@code expired}, {@code need_password} (added from an upload)
 */
public record CompanyItem(
        String taxId,
        String companyName,
        String username,
        String label,
        String status,
        String statusMessage,
        boolean hasPassword,
        boolean defaultCompany,
        String lastSyncAt,
        String createdAt) {
}
