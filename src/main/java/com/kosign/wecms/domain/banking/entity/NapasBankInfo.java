package com.kosign.wecms.domain.banking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * NAPAS bank dictionary (V68) — bridges the public NAPAS bank code
 * (e.g. {@code 970424} Shinhan) to the internal {@code BANK_CD} used
 * across {@code ACCT_MSTR}, {@code ACCT_TRNS_HSTR} and the rest of
 * the banking schema (e.g. {@code 11704088}). Mirrors legacy
 * {@code wabooks.entity.NapasBankInfo}.
 *
 * <p>Sole consumer today: the firm-banking-settings Create dialog's
 * Withdrawal Account autocomplete, which calls
 * {@code GET /api/v1/banking/accounts/by-bank?out_bank_cd=<NAPAS>}
 * and needs the internal code to filter {@code ACCT_MSTR}. JEX
 * does the same bridge in
 * {@code comm_1004_001_act.jsp:56-66} — refactor mirrors the
 * resolve-then-query shape exactly.
 *
 * <p>Sprint scope: only the columns the lookup needs are mapped
 * ({@code NAPAS_BANK_CD} PK + {@code BANK_CD}); the dictionary's
 * other columns ({@code SHORT_NAME}, {@code LONG_NAME_EN},
 * {@code LONG_NAME_VN}) live in the table but are not surfaced as
 * entity fields until a feature actually consumes them. Hibernate
 * {@code ddl-auto = validate} only complains about declared columns
 * missing from the DB, never the reverse.
 *
 * <p>The dictionary is global (NOT tenant-scoped — there is no
 * {@code BIZ_KEY} column on the table).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "NAPAS_BANK_INFO")
public class NapasBankInfo {

    /** Public NAPAS bank code (e.g. {@code 970424} Shinhan /
     *  {@code 970436} Vietcombank / {@code 970457} Woori). PK. */
    @Id
    @Column(name = "NAPAS_BANK_CD", length = 10, nullable = false)
    private String napasBankCd;

    /** Internal {@code BANK_CD} used across {@code ACCT_MSTR}
     *  (e.g. {@code 11704088} for Shinhan, {@code 11704004} for
     *  Vietcombank, {@code 11704020} for Woori). The single piece
     *  of information the firm-banking lookup needs from this row. */
    @Column(name = "BANK_CD", length = 20)
    private String bankCd;
}
