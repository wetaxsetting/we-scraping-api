package com.kosign.wecms.domain.setting.contacts.dto;

import java.util.List;

/**
 * Wire envelope for {@code GET /api/v1/settings/contacts/swift-bic}.
 * Returns up to 50 SWIFT BIC directory rows matching the search prefix
 * (legacy {@code comm_1007_001} parity), for the overseas-contact BIC
 * typeahead.
 */
public record SwiftBicListResponse(List<SwiftBicRow> rows) { }
