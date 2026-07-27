package com.kosign.wecms.domain.setting.contacts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Read-only projection over the Shinhan-provided ISO 20022 SWIFT BIC
 * directory master ({@code SWIFT_BIC_MST}, ~99,060 rows). Backs the
 * overseas-contact BIC search typeahead + Town Name auto-fill.
 *
 * <p>Mirrors legacy {@code IDO.SWIFT_BIC_MST_R002} (WSVC
 * {@code comm_1007_001} "Search Swift Bic Code"): the popup searches by
 * BIC prefix and, on selection, auto-fills the read-only Town Name from
 * {@code PSTL_ADR_TWN_NM}. The SWIFT file carries no bank name, so search
 * is BIC-only (per the requirement clarification).
 *
 * <p><b>Column subset.</b> Only the two columns the feature consumes are
 * mapped ({@code BIC_CD} PK + {@code PSTL_ADR_TWN_NM}); the remaining
 * postal-address columns exist in the DDL but are unused here. Hibernate
 * {@code validate} ignores unmapped columns. This is a reference
 * dictionary — never written by the refactor (no {@code BaseEntity}).
 *
 * <p><b>Baseline note.</b> {@code SWIFT_BIC_MST} was added to the refactor
 * table-inventory baseline by PO (Mr. Jung) decision 2026-07-21 (same
 * widening precedent as {@code TRCO_RSPB_INFM}, 2026-07-14). The table
 * already exists in the shared dev DB; its 99,060 rows are loaded by a
 * DBA-run seed script (Flyway disabled).
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "SWIFT_BIC_MST")
public class SwiftBicMst {

    /** ISO 20022 BIC code (8 or 11 chars). Primary key. */
    @Id
    @Column(name = "BIC_CD", length = 11, nullable = false)
    private String bicCd;

    /** Postal-address town / city name — the read-only Town Name source. */
    @Column(name = "PSTL_ADR_TWN_NM", length = 35)
    private String pstlAdrTwnNm;
}
