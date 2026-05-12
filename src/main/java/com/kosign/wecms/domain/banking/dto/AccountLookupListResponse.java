package com.kosign.wecms.domain.banking.dto;

import java.util.List;

/**
 * Wire shape for {@code GET /api/v1/banking/accounts/by-bank}.
 * Single field {@code list} — keeps room for future top-level
 * metadata (e.g. {@code total_count}) without breaking the frontend
 * consumer.
 *
 * <p>The legacy {@code comm_1004_001} returns the rows under
 * {@code REC}; the refactor uses {@code list} per the
 * frontend's existing list-response convention
 * ({@code FirmBankingSettingListResponse},
 * {@code FirmBankingAuditListResponse} etc.).
 */
public record AccountLookupListResponse(List<AccountLookupRow> list) { }
