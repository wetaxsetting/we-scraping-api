package com.kosign.wecms.domain.setting.contacts.dto;

/**
 * Response for {@code POST /api/v1/settings/contacts/lookup-recipient}.
 *
 * <p>Stub today — returns a deterministic mock name keyed off the
 * last 4 digits of the account number so the popup's verified-recipient
 * UX flow exercises end-to-end without bank-scraping integration.
 * When real bank lookup lands, the same shape will carry the
 * actual account-holder name from the firm-banking module.
 */
public record ContactLookupRecipientResponse(String recipientName) { }
