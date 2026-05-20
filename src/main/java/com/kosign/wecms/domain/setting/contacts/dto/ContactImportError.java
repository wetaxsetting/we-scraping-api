package com.kosign.wecms.domain.setting.contacts.dto;

/**
 * Per-row error entry inside {@link ContactBulkImportResponse}.
 *
 * @param index       zero-based row index in the original request list
 * @param handTrcoKey the contact code from that row (for display in the UI)
 * @param code        backend error code (e.g. {@code WBE_CONTACTS_HAND_KEY_DUP})
 * @param message     localized error message
 */
public record ContactImportError(
        int index,
        String handTrcoKey,
        String code,
        String message) { }
