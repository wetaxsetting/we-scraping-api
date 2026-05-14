package com.kosign.wecms.domain.setting.logincredentials.dto;

import java.util.List;

/**
 * One row of the Login Credentials list — either a bank (one or more
 * accounts grouped by BANK_CD) or the single GDT row.
 *
 * <p>The legacy JEX list query at {@code IDO.BANK_CERT_INFO_R007.xml}
 * UNIONs BANK_CERT_INFO with GDT_CERT_INFO and groups bank rows by
 * BANK_CD via {@code json_agg}. The refactor produces the same shape
 * server-side from two simple per-tenant queries — easier to evolve
 * than a 90-line native UNION.
 */
public record LoginCredentialGroup(
        /** "BANK" or "GDT". */
        String                       type,
        /** Bank code for BANK rows; {@code null} for GDT. */
        String                       bankCd,
        /** Display name — bank registry lookup for BANK rows;
         *  the literal "GDT" for GDT. */
        String                       institution,
        /** Last MOD_ID across the group's accounts. */
        String                       modifiedBy,
        /** Last MOD_DTM (legacy yyyyMMddHHmmss) across the group. */
        String                       modifiedAt,
        List<LoginCredentialAccount> accounts
) { }
