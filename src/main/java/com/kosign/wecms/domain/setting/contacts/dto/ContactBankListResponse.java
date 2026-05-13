package com.kosign.wecms.domain.setting.contacts.dto;

import java.util.List;

/**
 * Wire envelope for {@code GET /api/v1/settings/contacts/banks}.
 * Returns the NAPAS + CITAD bank rows for the contacts Add / Edit
 * dialog's bank-picker autocomplete (the popup consumes the same
 * shape legacy {@code comm_0109_001_r002} produced).
 *
 * <p>Single-list envelope rather than paged — the dictionary has
 * under ~100 rows and the popup loads it once on mount.
 */
public record ContactBankListResponse(List<ContactBankRow> rows) { }
