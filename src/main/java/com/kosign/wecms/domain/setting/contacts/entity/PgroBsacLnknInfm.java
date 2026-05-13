package com.kosign.wecms.domain.setting.contacts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Settings &gt; Contacts &mdash; per-tenant group-membership row.
 * On contact create, the service seeds two rows with {@code USER_ID = 'ALL'}
 * and {@code GRP_CD &isin; { 'B' (buy), 'S' (sale) }} &mdash; verbatim
 * with the legacy {@code comm_0184_002_c001_act.jsp:131-147} pattern.
 *
 * <p>Other {@code GRP_CD} values (custom user groups) are written by
 * the legacy Payment / Sales screens, which are out of refactor scope.
 * The refactor only reads this table; it never writes non-default
 * group codes.
 *
 * <p><b>No audit columns</b>: DEV's {@code PGRO_BSAC_LNKN_INFM} carries
 * only {@code REG_DTM} (no {@code REG_ID} / {@code MOD_ID} / {@code MOD_DTM}).
 * Preserved verbatim.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "PGRO_BSAC_LNKN_INFM")
@IdClass(PgroBsacLnknInfmId.class)
public class PgroBsacLnknInfm {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "USER_ID", length = 100, nullable = false)
    private String userId;

    @Id
    @Column(name = "GRP_CD", length = 10, nullable = false)
    private String grpCd;

    @Id
    @Column(name = "TRCO_KEY", length = 20, nullable = false)
    private String trcoKey;

    @Column(name = "TAX_ID", length = 40)
    private String taxId;

    @Column(name = "REG_DTM", length = 14)
    private String regDtm;
}
