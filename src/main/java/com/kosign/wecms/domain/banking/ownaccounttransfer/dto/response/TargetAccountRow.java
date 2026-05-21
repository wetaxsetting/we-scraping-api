package com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response;

/**
 * One row in the target-account picker for Own Account Transfer.
 * Per OQ-A (PO 2026-05-18) the OAT scope is own-only VND — every row
 * the composer offers is a row from the caller's own {@code ACCT_MSTR}
 * (the same source-account universe minus the row the user is
 * transferring from).
 *
 * <p>{@code kind} is always {@code "OWN_ACCOUNT"} under the current
 * scope. The literal is preserved on the wire so a future scope
 * expansion (FCY / external NAPAS recipients) can add additional kinds
 * without renaming this field.
 *
 * <p>{@code napasBankCd} is the NAPAS routing code resolved through the
 * {@code NAPAS_BANK_INFO} dictionary — populated when the source and
 * target bank differ (cross-bank own→own routing); null when the row's
 * bank is not in the NAPAS dictionary (still listable for
 * {@code SAME_BANK} same-bank transfers).
 *
 * <p>Wire shape is lowercase snake_case via the global Jackson naming
 * strategy.
 */
public record TargetAccountRow(
        String kind,
        String bankCd,
        String bankNm,
        String acctNo,
        String acctNickNm,
        String acctTypeCd,
        String ccyCd,
        String napasBankCd
) {
    /** Discriminator value for own-account rows. */
    public static final String KIND_OWN_ACCOUNT = "OWN_ACCOUNT";
}
