package com.kosign.wecms.domain.einvoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * Mirrors legacy wecmsvndev.BUSA_DOC_MSTR for the Sprint 1 column subset
 * (Sales / Bill e-Invoice list). Legacy carries 100 columns; Sprint 1
 * maps only ~16. Add columns here AND in V15 (ALTER TABLE ADD COLUMN
 * IF NOT EXISTS) when a feature needs them — do not silently widen the
 * entity.
 *
 * <p>DOC_TYPE distinguishes Sales ({@code 'I'}, {@link
 * com.kosign.wecms.domain.einvoice.service.EInvoiceService} maps wire
 * {@code SALES} → {@code 'I'}) from Bill / Expense ({@code 'B'}, wire
 * {@code EXPENSE} → {@code 'B'}). Verified against legacy
 * {@code einv_0001_001_r001_act.jsp:268-284} and
 * {@code wabooks.enums.BusaDocType}: the listing service rejects
 * {@code 'E'} ({@code BusaDocType.EXPENSE}) and {@code 'S'}
 * ({@code BusaDocType.PAYROLLBOOK}) — those constants belong to other
 * flows. Refactor docs: see {@code docs/refactor-wecms/features/addon/
 * sales-e-invoices.md} (rule IR-1).
 *
 * <p>{@code INV_TYPE} stays mapped here because it's a real DB column,
 * but it is intentionally NOT exposed on the wire (legacy WSVC drops
 * it; refactor follows IR-7). The legacy IDO computes
 * {@code CASE WHEN INV_TYPE = 'C' THEN 'Commercial' ELSE 'Tax' END} —
 * a Commercial / Tax classifier, NOT an Original / Replacement /
 * Adjustment status (which belongs to {@code EINV_INVOICESTATUS}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "BUSA_DOC_MSTR")
@IdClass(BusaDocMstrId.class)
public class BusaDocMstr {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "DOC_ID", length = 50, nullable = false)
    private String docId;

    @Column(name = "DOC_TYPE", length = 1, nullable = false)
    private String docType;

    @Column(name = "INV_TYPE", length = 1)
    private String invType;

    @Column(name = "ISSU_DT", length = 8, nullable = false)
    private String issuDt;

    @Column(name = "ISSU_TAX_ID", length = 40, nullable = false)
    private String issuTaxId;

    @Column(name = "TRCO_KEY", length = 20, nullable = false)
    private String trcoKey;

    @Column(name = "TRCO_TAX_ID", length = 40)
    private String trcoTaxId;

    @Column(name = "TRCO_CMNM", length = 200)
    private String trcoCmnm;

    @Column(name = "TRCO_CMENM", length = 200)
    private String trcoCmenm;

    @Column(name = "TRCO_TRSC_CCY_CD", length = 3)
    private String trcoTrscCcyCd;

    @Column(name = "TOTL_AMT")
    private BigDecimal totlAmt;

    @Column(name = "SPLY_AMT_SUM")
    private BigDecimal splyAmtSum;

    @Column(name = "TAX_AMT_SUM")
    private BigDecimal taxAmtSum;

    @Column(name = "EINV_INVOICESTATUS", length = 100)
    private String einvInvoicestatus;

    @Column(name = "EINV_INVOICESYMBOL", length = 10)
    private String einvInvoicesymbol;

    /** Prior {@code EINV_INVOICESTATUS} when the row was replaced /
     *  adjusted (legacy WSVC field {@code einv_invoicestatus_bef}).
     *  Useful for tooltip / detail-page diff. Stored verbatim — same
     *  localisation caveat as {@link #einvInvoicestatus}. */
    @Column(name = "EINV_INVOICESTATUS_BEF", length = 100)
    private String einvInvoicestatusBef;

    /** Due date in {@code YYYYMMDD} form. Paired with
     *  {@link #overDueDays} on the wire. */
    @Column(name = "DUE_DT", length = 8)
    private String dueDt;

    /** Days past due — legacy column is {@code VARCHAR(3)}; the IDO
     *  {@code BUSA_DOC_MSTR_R022} computes a runtime value via
     *  {@code CURRENT_DATE - ISSU_DT - TRCO_PAYM_TERM} when the row
     *  is unpaid and overrides the stored value, but for Phase 2 we
     *  expose the stored column as-is. Refactor docs note the
     *  computation deferral. */
    @Column(name = "OVER_DUE_DAYS", length = 3)
    private String overDueDays;

    /** Display-friendly document id (vs the internal {@link #docId}). */
    @Column(name = "DISP_DOC_ID", length = 50)
    private String dispDocId;

    /** Local payment / settlement status (legacy {@code wabooks.enums.ProvStts}).
     *  6-value enum: {@code OP} / {@code PP} / {@code PA} / {@code OD}
     *  / {@code VD} / {@code EE}. Listing service applies the rule IR-2
     *  baseline filter {@code NOT IN ('VD','EE')} server-side; the field
     *  is mapped here so that filter can be expressed in JPQL.
     *  Column is DEV-parity {@code bpchar(2)} — see V23 schema parity. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "PROV_STTS", length = 2)
    private String provStts;

    /** Hand-registered flag — {@code 'Y'} for rows entered manually
     *  (not synced from GDT), {@code 'N'} for synced rows. Listing
     *  service applies the rule IR-2 baseline filter
     *  {@code <> 'Y'} server-side; the field is mapped here so
     *  that filter can be expressed in JPQL. Column is DEV-parity
     *  {@code varchar(1) NOT NULL DEFAULT 'Y'} — see V23. */
    @Column(name = "HAND_REG_YN", length = 1, nullable = false)
    private String handRegYn;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100, nullable = false)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;
}
