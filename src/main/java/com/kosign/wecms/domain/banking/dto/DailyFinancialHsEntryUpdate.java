package com.kosign.wecms.domain.banking.dto;

import jakarta.validation.constraints.Size;

/**
 * Partial update for a single HAENGSUNG Daily Financial ledger row
 * (legacy {@code rept_0017_001_u001} — per-field auto-save on blur/Enter).
 *
 * <p>Only the user-editable columns are exposed; the field whitelist IS this
 * record's shape (a caller cannot patch any other {@code REPT_TRNS_HSTR}
 * column). The frontend saves one field at a time, so a request normally
 * carries a single non-null property — but any subset is accepted.
 *
 * <p>Semantics mirror legacy {@code FIELD_VAL}: a {@code null} property is "not
 * provided" (left unchanged); an empty string is "clear this field". The
 * {@code acct_seq_no} row key travels in the path, not the body — the tenant
 * ({@code BIZ_KEY}) is always resolved server-side from the JWT.
 */
public record DailyFinancialHsEntryUpdate(
        @Size(max = 20) String reptCd,
        @Size(max = 20) String pymnCd,
        @Size(max = 255) String descNmEn,
        @Size(max = 255) String descNmKo,
        @Size(max = 500) String memo
) {
    /** True when the request carries at least one field to apply. */
    public boolean hasAnyField() {
        return reptCd != null || pymnCd != null || descNmEn != null
                || descNmKo != null || memo != null;
    }
}
