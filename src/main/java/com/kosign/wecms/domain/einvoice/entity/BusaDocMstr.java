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

    /** Use date for manually-registered receipts — paired with
     *  {@link #handRegYn}={@code 'Y'} rows. Legacy {@code card_0001_002_r002}
     *  filters Receipt-tab rows on this column instead of
     *  {@code ISSU_DT}. {@code YYYYMMDD}. */
    @Column(name = "USE_DT", length = 14)
    private String useDt;

    /** Usage / Purpose code on manually-registered receipts (legacy
     *  Receipt tab "Purpose" column). Resolves to a friendly name via
     *  a future JOIN to {@code USAG_INFM}; for now the wire ships the
     *  code as-is. */
    @Column(name = "BUSA_USAG_CD", length = 20)
    private String busaUsagCd;

    /** Free-form description / note (legacy Receipt tab "Description"
     *  column; same column as the e-Invoice's "Note" field — it's a
     *  shared text column on the master row). */
    @Column(name = "CONT", columnDefinition = "TEXT")
    private String cont;

    /** GDT invoice number as scraped (legacy {@code HAND_DOC_ID}); the
     *  {@code DISP_DOC_ID} composes {@code numericSymbol+invoiceSymbol-invoiceNo}
     *  while this stores the raw {@code invoiceNo}. Written by the scrape
     *  intake (legacy {@code nxisas_einvoice_c001_act.jsp:175}). */
    @Column(name = "HAND_DOC_ID", length = 50)
    private String handDocId;

    /** Full GDT invoice info blob (legacy {@code EINV_INVOICEINFO}). */
    @Column(name = "EINV_INVOICEINFO", length = 500)
    private String einvInvoiceinfo;

    /** Total discount amount (legacy {@code TOTL_DIS_AMT} ← engine
     *  {@code totalDiscount}). */
    @Column(name = "TOTL_DIS_AMT")
    private BigDecimal totlDisAmt;

    /** GDT check-result phrase (legacy {@code EINV_CHECKRESULTS} ←
     *  engine {@code checkResult}); drives the accept/skip + branch
     *  selection at scrape time. */
    @Column(name = "EINV_CHECKRESULTS", length = 100)
    private String einvCheckresults;

    /** First numeric symbol char of the invoice (legacy
     *  {@code EINV_NUMERICSYMBOL} ← engine {@code numericSymbol}). */
    @Column(name = "EINV_NUMERICSYMBOL", length = 5)
    private String einvNumericsymbol;

    /** Symbol-type marker — {@code ''} (C / coded), {@code '1'} (K /
     *  no-code), {@code '2'} (POS) — legacy {@code EINV_TESTRESULTS}.
     *  DEV-parity {@code char(1)} so mapped with {@link SqlTypes#CHAR}
     *  like {@link #provStts}. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "EINV_TESTRESULTS", length = 1)
    private String einvTestresults;

    /** Owning user id stamped at scrape time (legacy {@code USER_ID}). */
    @Column(name = "USER_ID", length = 100)
    private String userId;

    /* ------------------------------------------------------------------
     *  Full master enrichment (Scope 3) — populated by the phase-2
     *  detail-save from the engine INVOICE_DETAIL payload (legacy
     *  nxisas_einvoice_c002_act.jsp:264-687). Every column below is
     *  present in the local DB via V23 (verified). Issuer (ISSU_*) =
     *  our own company on the invoice; the EINV_* fields are the GDT
     *  invoice's seller/buyer-side data (stored verbatim, not type-
     *  swapped, matching legacy).
     * ------------------------------------------------------------------ */

    /** Invoice title — legacy {@code EINV_TITLE}. */
    @Column(name = "EINV_TITLE", length = 300)
    private String einvTitle;

    /** Merchant/category code — legacy {@code EINV_MCCQT}. */
    @Column(name = "EINV_MCCQT", length = 50)
    private String einvMccqt;

    /** Original-invoice relation text (replacement/adjustment) —
     *  legacy {@code EINV_ORIGINALINVOICE}. */
    @Column(name = "EINV_ORIGINALINVOICE", length = 200)
    private String einvOriginalInvoice;

    @Column(name = "EINV_BUYERCITIZENID", length = 12)
    private String einvBuyercitizenid;

    @Column(name = "EINV_SELLERBANKACC", length = 100)
    private String einvSellerbankacc;

    @Column(name = "EINV_SELLERBANKNAME", length = 200)
    private String einvSellerbankname;

    @Column(name = "EINV_BUYERBANKACC", length = 100)
    private String einvBuyerbankacc;

    @Column(name = "EINV_BUYERBANKNAME", length = 200)
    private String einvBuyerbankname;

    @Column(name = "EINV_PAYMENTTYPE", length = 50)
    private String einvPaymenttype;

    @Column(name = "EINV_LISTNUMBER", length = 50)
    private String einvListnumber;

    @Column(name = "EINV_LISTDATE", length = 50)
    private String einvListdate;

    /** Total fee amount — legacy {@code TOTL_FEE_AMT}. */
    @Column(name = "TOTL_FEE_AMT")
    private BigDecimal totlFeeAmt;

    @Column(name = "EINV_TOTALAMTINLETTER", length = 200)
    private String einvTotalamtinletter;

    @Column(name = "EINV_BUYERSIGNBY", length = 200)
    private String einvBuyersignby;

    /** Buyer signature date {@code YYYYMMDD} — legacy {@code EINV_BUYERSIGNDATE}
     *  (stored as the raw 8-digit string; column is {@code VARCHAR(8)}). */
    @Column(name = "EINV_BUYERSIGNDATE", length = 8)
    private String einvBuyersigndate;

    /** Buyer signature time {@code HHmmss} — legacy {@code EINV_BUYERSIGNTIME}
     *  (raw 6-digit string; column is {@code VARCHAR(6)}). */
    @Column(name = "EINV_BUYERSIGNTIME", length = 6)
    private String einvBuyersigntime;

    @Column(name = "EINV_SELLERSIGNBY", length = 200)
    private String einvSellersignby;

    @Column(name = "EINV_SELLERSIGNDATE", length = 8)
    private String einvSellersigndate;

    @Column(name = "EINV_SELLERSIGNTIME", length = 6)
    private String einvSellersigntime;

    @Column(name = "EINV_QRCODE", length = 1000)
    private String einvQrcode;

    /** Applied exchange rate — legacy {@code EINV_RATE}. */
    @Column(name = "EINV_RATE")
    private BigDecimal einvRate;

    /** Issuer (own-company) name — legacy {@code ISSU_CMNM}. */
    @Column(name = "ISSU_CMNM", length = 200)
    private String issuCmnm;

    @Column(name = "ISSU_CMENM", length = 200)
    private String issuCmenm;

    @Column(name = "ISSU_BSUN_POST_ADRS", length = 200)
    private String issuBsunPostAdrs;

    @Column(name = "ISSU_BSUN_DTL_ADRS", length = 200)
    private String issuBsunDtlAdrs;

    @Column(name = "ISSU_BSUN_CITY", length = 200)
    private String issuBsunCity;

    @Column(name = "ISSU_BSUN_ST", length = 200)
    private String issuBsunSt;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100, nullable = false)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;
}
