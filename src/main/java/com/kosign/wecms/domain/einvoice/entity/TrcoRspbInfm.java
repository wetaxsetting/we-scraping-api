package com.kosign.wecms.domain.einvoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Settings &gt; Contacts responsible-person / representative row &mdash;
 * refactor mapping of legacy {@code TRCO_RSPB_INFM}
 * ({@code WECMS DEV/WECMS/src/wabooks/entity/TrcoRspbInfm.java}).
 *
 * <p>Written by the e-Invoice counterparty auto-registrar
 * ({@code nxisas_einvoice_c002_act.jsp} {@code createTrcoRspbInfm}, #39847):
 * one row per counterparty carrying the person-in-charge name resolved from
 * the GDT business-registration lookup (D3 / #40477). The scrape path sets
 * only {@code NAME} + the keys + audit; {@code CLPH_NO} / {@code EML} /
 * {@code TAX_CHRG} / {@code MEMO} stay null (legacy parity — c002 sets only
 * {@code setName(trcoPicName)}).
 *
 * <p><b>Baseline:</b> added to the legacy-table inventory 2026-07-14 (PO
 * decision). <b>PK:</b> {@code RSPB_KEY} alone (verbatim from DEV), drawn from
 * the global {@code sq_rspb_key} sequence (V153) via
 * {@code wb_get_next_val('trco_rspb_infm','rspb_key', …)} (V140 branch 6b).
 * Lives in the {@code einvoice} domain &mdash; no Contacts-domain consumer yet
 * &mdash; the same placement as {@code TrcoActvHstr}. Audit columns follow the
 * legacy {@code VARCHAR(14)} timestamp shape; the service stamps them.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "TRCO_RSPB_INFM")
public class TrcoRspbInfm {

    /** Global PK &mdash; {@code sq_rspb_key} via {@code wb_get_next_val}. */
    @Id
    @Column(name = "RSPB_KEY", length = 20, nullable = false)
    private String rspbKey;

    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Column(name = "TRCO_KEY", length = 20, nullable = false)
    private String trcoKey;

    /** Person-in-charge name (legacy {@code setName(trcoPicName)} = the
     *  accountant from the GDT registration lookup). */
    @Column(name = "NAME", length = 50)
    private String name;

    @Column(name = "CLPH_NO", length = 100)
    private String clphNo;

    @Column(name = "EML", length = 100)
    private String eml;

    /** Tax-in-charge flag {@code 'Y'}/{@code 'N'} &mdash; CHAR(1), unset on scrape. */
    @Column(name = "TAX_CHRG", length = 1)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String taxChrg;

    @Column(name = "MEMO", length = 200)
    private String memo;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100)
    private String modId;

    @Column(name = "MOD_DTM", length = 14)
    private String modDtm;
}
