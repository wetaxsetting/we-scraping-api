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
 * Settings &gt; Contacts &mdash; address row. Each {@link TrcoInfm}
 * has 0..N addresses; the {@code DISP_ORD} column is the current /
 * previous discriminator (OQ-8 resolved 2026-05-12 &mdash; never
 * compare {@code TRCO_ADDR_KEY} to literal {@code "1"} / {@code "2"}
 * for business meaning).
 *
 * <p><b>Convention</b>: {@code DISP_ORD = 1} &rArr; current address;
 * {@code DISP_ORD = 2} &rArr; previous address. The form-side enforces
 * the {1, 2} domain; the backend validator enforces it again on upsert.
 *
 * <p>Audit columns follow the legacy {@code VARCHAR(14)} timestamp
 * shape, NOT {@code BaseEntity}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "TRCO_ADDR_INFM")
@IdClass(TrcoAddrInfmId.class)
public class TrcoAddrInfm {

    @Id
    @Column(name = "TRCO_KEY", length = 20, nullable = false)
    private String trcoKey;

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "TRCO_ADDR_KEY", length = 20, nullable = false)
    private String trcoAddrKey;

    @Column(name = "TRCO_BSUN_POST_ADRS", length = 400)
    private String trcoBsunPostAdrs;

    /** Address discriminator. {@code 1} = current, {@code 2} = previous
     *  (OQ-8 resolved 2026-05-12). DEV stores this as
     *  {@code NUMERIC(10,0)}; the local column was converted to
     *  {@code INTEGER} by V89 because Hibernate 6's
     *  {@code ddl-auto: validate} rejects NUMERIC against a Java
     *  {@code Integer} mapping. Same precedent as
     *  {@code SysScrOptionWecms.dispOrd} (V35 &rarr; V36 hotfix).
     *  Documented refactor-only deviation from DEV. */
    @Column(name = "DISP_ORD", nullable = false)
    private Integer dispOrd;

    /** Issue date as {@code yyyyMMdd}. */
    @Column(name = "ISSU_DT", length = 8, nullable = false)
    private String issuDt;

    /** Due date as {@code yyyyMMdd}. */
    @Column(name = "DUE_DT", length = 8, nullable = false)
    private String dueDt;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100, nullable = false)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;
}
