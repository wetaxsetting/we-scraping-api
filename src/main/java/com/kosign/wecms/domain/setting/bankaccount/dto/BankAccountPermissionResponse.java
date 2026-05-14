package com.kosign.wecms.domain.setting.bankaccount.dto;

import java.util.List;

/**
 * Response body for {@code GET /api/v1/settings/bank-account/{bankCd}/{acctNo}/permissions?type=…}.
 *
 * <p>{@code kind} is echoed from the query parameter so the dialog can
 * be confident the response matches its open state. {@code accountLabel}
 * is composed server-side ({@code "<BANK_NM> / <ACCT_NO> (<CCY>)"}) so
 * every dialog instance reads identically.
 *
 * <p>{@code members} is sorted admin-first then by display name so the
 * locked row anchors the top of the table. {@code totalSelected}
 * mirrors the server-side count for the title-row "+ N more" label.
 */
public record BankAccountPermissionResponse(
        String                              kind,
        String                              accountLabel,
        List<BankAccountPermissionMember>   members,
        int                                 totalSelected
) { }
