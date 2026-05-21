package com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response;

/**
 * Response body for
 * {@code GET /api/v1/banking/own-account-transfer/napas-bank-info}.
 * Mirrors legacy {@code WSVC.trns_0001_001_r003.xml} record — one row
 * for a single requested NAPAS code.
 *
 * <p>Path A scope: the entity {@code NapasBankInfo} mapped on the
 * refactor today carries only {@code napas_bank_cd} and the internal
 * {@code bank_cd}. The legacy WSVC also returns {@code short_name} /
 * {@code long_name_en} / {@code long_name_vn} — those columns exist
 * on the DEV table but the entity does not declare them (per
 * "no speculative entity creation" — the existing entity Javadoc
 * notes this). The fields ride the response as {@code null} so the
 * wire shape matches the legacy contract; the composer MR widens the
 * entity projection when the dropdown actually consumes the bank-name
 * fields.
 *
 * <p>Wire format is lowercase snake_case via the global Jackson naming
 * strategy.
 */
public record NapasBankInfoRow(
        String napasBankCd,
        String shortName,
        String longNameEn,
        String longNameVn,
        String bankCd
) { }
