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
 * <p>Consumers: (1) the firm-banking-settings Create dialog's
 * Withdrawal Account autocomplete, which calls
 * {@code GET /api/v1/banking/accounts/by-bank?out_bank_cd=<NAPAS>}
 * and needs the internal code to filter {@code ACCT_MSTR} (JEX
 * does the same bridge in {@code comm_1004_001_act.jsp:56-66});
 * (2) Own Account Transfer's NAPAS lookup
 * ({@code trns_0001_001_r003}), which returns the bank display
 * names so the composer / recipient list / history can label the
 * recipient bank.
 *
 * <p>Mapped columns: {@code NAPAS_BANK_CD} (PK) + {@code BANK_CD}
 * (internal code) + the three display-name columns
 * ({@code SHORT_NAME}, {@code LONG_NAME_EN}, {@code LONG_NAME_VN}).
 * The display names were added to the entity 2026-06-04 to close
 * gap B9 (Own Account Transfer parity) — legacy
 * {@code trns_0001_001_r003} returns them and the refactor was
 * shipping them null. All five columns exist on both the refactor
 * and JEX DEV tables (verified column-for-column), so Hibernate
 * {@code ddl-auto = validate} accepts them.
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

    /** Short bank name (e.g. {@code Shinhan}). Returned by the OAT
     *  NAPAS lookup ({@code trns_0001_001_r003}) so the recipient
     *  bank can be labelled. */
    @Column(name = "SHORT_NAME", length = 100)
    private String shortName;

    /** Full English bank name (e.g.
     *  {@code Shinhan Bank Vietnam Limited}). */
    @Column(name = "LONG_NAME_EN", length = 500)
    private String longNameEn;

    /** Full Vietnamese bank name. */
    @Column(name = "LONG_NAME_VN", length = 500)
    private String longNameVn;
}
