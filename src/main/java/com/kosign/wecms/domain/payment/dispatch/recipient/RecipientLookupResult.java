package com.kosign.wecms.domain.payment.dispatch.recipient;

/**
 * Result of a successful ({@code RC=0000}) {@code WGVN_EFS_201} inquiry —
 * the decrypted {@code Efs201Response} mapped to the two business fields callers
 * need (REST API guide ver 1.2.2 §5.3).
 *
 * @param ownerName resolved account-holder name ({@code finc_mda_owner_nm});
 *                  may be blank even on success — the legacy frontend treats a
 *                  blank name as {@code WBE1143} "Recipient Name not found"
 *                  (that decision stays with the caller)
 * @param antDivCd  resolved network ({@code ant_div_cd}) — {@code INTER} / {@code CITAD} / {@code NAPAS}
 */
public record RecipientLookupResult(
        String ownerName,
        String antDivCd
) {
}
