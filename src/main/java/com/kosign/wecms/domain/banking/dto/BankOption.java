package com.kosign.wecms.domain.banking.dto;

/**
 * Single entry in the {@code banks_available} list returned by Account
 * Inquiry. The frontend bank-filter dropdown consumes this list as its
 * canonical option set — independent of the current bank/keyword/page
 * filter so selecting one bank does not narrow the dropdown to that
 * bank only.
 *
 * <p>Sourced from a {@code SELECT DISTINCT BANK_CD ... FROM ACCT_MSTR}
 * scoped to the tenant and the wire {@code currency} (so the VND page
 * sees only banks where the tenant has VND accounts; FCY similarly).
 * Bank-name resolution flows through {@code BankCdResolver}.
 *
 * Wire names auto-derived from camelCase Java fields via the global
 * SNAKE_CASE Jackson naming strategy.
 */
public record BankOption(
        String bankCd,
        String bankNm
) { }
