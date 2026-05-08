package com.kosign.wecms.domain.banking.dto;

import java.util.List;

/**
 * Sprint 1 Transfer Own Accounts response — same row shape as Account
 * Inquiry. Transfer execution is intentionally NOT implemented here;
 * the static UI's transfer dialog is mock-only and the legacy 10-step
 * wizard ports in Sprint 2+.
 *
 * Wire names auto-derived from camelCase Java fields via the global
 * SNAKE_CASE Jackson naming strategy.
 */
public record OwnAccountListResponse(
        List<AccountRow> accounts,
        int    totalCount,
        String balTotal,
        String availTotal
) { }
