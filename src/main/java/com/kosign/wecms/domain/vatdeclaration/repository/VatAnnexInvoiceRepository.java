package com.kosign.wecms.domain.vatdeclaration.repository;

import com.kosign.wecms.domain.vatdeclaration.dto.AnnexInvoiceProjection;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.Repository;
import com.kosign.wecms.domain.vatdeclaration.entity.VatDeclaration;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * Annex invoice lines, <b>derived per request</b> from the period's real
 * invoices — there is no persisted annex table.
 *
 * <p>Grain is one product line: {@code BUSA_DOC_DETL} (product name, taxable
 * value, tax rate) joined to its header {@code BUSA_DOC_MSTR} (invoice number,
 * symbol, issue date, counterparty). Scope is the declaration's
 * {@code FROM_DT..TO_DT} window over {@code ISSU_DT} plus the direction:
 * {@code DOC_TYPE 'I'} = output/sales (BKBR), {@code 'B'} = input/purchase (BKMV).
 *
 * <p>Native SQL rather than JPQL because the join is cross-module, the rate is
 * a display string ({@code '8%'} / {@code '10%'} / {@code 'KCT'} /
 * {@code 'KKKNT'}) that needs parsing, and VAT / reduction are computed —
 * {@code BUSA_DOC_DETL} stores no VAT amount. Reading the invoice tables
 * through raw SQL keeps this domain free of a dependency on the e-Invoice
 * domain's entities and repositories.
 *
 * <p>Reduction basis: Resolution 204/2025/QH15 lowers 10% to 8%, so the amount
 * reduced on an 8% line is the 2-point difference on its taxable value.
 */
public interface VatAnnexInvoiceRepository extends Repository<VatDeclaration, Long> {

    String FROM_WHERE = """
             from busa_doc_mstr m
             join busa_doc_detl d
               on d.biz_key = m.biz_key and d.doc_id = m.doc_id
            where m.biz_key   = :bizKey
              and m.issu_dt between :fromDt and :toDt
              and m.doc_type in (:docTypes)
              and coalesce(m.einv_invoicestatus, '') not in (:excludedStatuses)
              and (cast(:rate   as text) is null or d.einv_taxrate = cast(:rate as text))
              and (cast(:search as text) is null
                   or lower(coalesce(d.einv_productname, d.item_nm, '')) like lower(concat('%', cast(:search as text), '%'))
                   or lower(coalesce(m.disp_doc_id, m.doc_id))           like lower(concat('%', cast(:search as text), '%')))
            """;

    /** Rate string -> numeric percent; non-numeric codes (KCT / KKKNT) yield 0. */
    String RATE_PCT = """
            (case when d.einv_taxrate ~ '^[0-9]+(\\.[0-9]+)?%$'
                  then replace(d.einv_taxrate, '%', '')::numeric else 0 end)
            """;

    /**
     * Sort weight deciding the SEQUENCE the VAT buckets appear in — internal to
     * ordering, never emitted. Fixed 2026-07-24: KKKNT, then the numeric rates
     * ascending, then KCT; an unrecognised / NULL rate weighs 99 so it lands
     * last instead of merging into an existing bucket.
     */
    String RATE_SEQ = """
            (case d.einv_taxrate
                  when 'KKKNT' then 1
                  when '0%'    then 2
                  when '5%'    then 3
                  when '8%'    then 4
                  when '10%'   then 5
                  when 'KCT'   then 6
                  else 99 end)
            """;

    /**
     * The {@code vat_rate_order} on the wire: a <b>dense, 1-based</b> rank over
     * the VAT rates that actually occur in THIS period's filtered set. A period
     * carrying four rates numbers them 1·2·3·4 — the weights above only decide
     * which comes first, they are not the number itself, so no gaps appear just
     * because some rate is absent.
     *
     * <p>{@code dense_rank()} is evaluated before {@code OFFSET/LIMIT}, so the
     * window spans the whole filtered set and a row keeps the same number no
     * matter which page it is read on.
     */
    String RATE_ORDER = "(dense_rank() over (order by " + RATE_SEQ + "))::int";

    @Query(value = "select m.doc_id                                as srcDocId,"
            + "        case when m.doc_type = 'I' then 'O' else 'I' end as ioDv,"
            + "        coalesce(m.disp_doc_id, m.doc_id)          as invNo,"
            + "        m.einv_invoicesymbol                       as invSymbol,"
            + "        m.issu_dt                                  as invDt,"
            + "        m.trco_tax_id                              as trcoTaxCode,"
            + "        m.trco_cmnm                                as trcoNm,"
            + "        coalesce(d.einv_productname, d.item_nm)    as prodNm,"
            + "        coalesce(d.sply_amt, 0)                    as splyAmt,"
            + "        d.einv_taxrate                             as vatRate,"
            + "        floor(coalesce(d.sply_amt, 0) * " + RATE_PCT + " / 100)  as vatAmt,"
            + "        case when d.einv_taxrate = '8%'"
            + "             then floor(coalesce(d.sply_amt, 0) * 2 / 100) else 0 end as reducedAmt,"
            + "        m.vat_decl_proc_yn                         as vatDeclProcYn,"
            + "        m.vat_decl_sid                             as vatDeclSid,"
            + "        " + RATE_ORDER + "                         as vatRateOrder"
            + FROM_WHERE
            + " order by " + RATE_SEQ + ", m.issu_dt, m.doc_id, d.disp_ord"
            + " offset :offset limit :size",
            nativeQuery = true)
    List<AnnexInvoiceProjection> findRows(@Param("bizKey") String bizKey,
                                          @Param("fromDt") String fromDt,
                                          @Param("toDt") String toDt,
                                          @Param("docTypes") Collection<String> docTypes,
                                          @Param("rate") String rate,
                                          @Param("search") String search,
                                          @Param("excludedStatuses") Collection<String> excludedStatuses,
                                          @Param("offset") long offset,
                                          @Param("size") int size);

    /** Unpaged row count over the same filter — feeds {@code total_count}. */
    @Query(value = "select count(*)" + FROM_WHERE, nativeQuery = true)
    long countRows(@Param("bizKey") String bizKey,
                   @Param("fromDt") String fromDt,
                   @Param("toDt") String toDt,
                   @Param("docTypes") Collection<String> docTypes,
                   @Param("rate") String rate,
                   @Param("search") String search,
                   @Param("excludedStatuses") Collection<String> excludedStatuses);

    /**
     * Stamp every invoice of the period as closed into this declaration
     * (PO decision 2026-07-24 — marker lives on the invoice row, see V253).
     * Only rows not already claimed by another declaration are touched, so a
     * later period can never steal an earlier period's invoices.
     *
     * @return how many invoices were stamped
     **/
    @Modifying
    @Query(value = """
            update busa_doc_mstr
               set vat_decl_proc_yn = 'Y', vat_decl_sid = :sid
             where biz_key = :bizKey
               and issu_dt between :fromDt and :toDt
               and doc_type in (:docTypes)
               and coalesce(einv_invoicestatus, '') not in (:excludedStatuses)
               and (vat_decl_sid is null or vat_decl_sid = :sid)
            """, nativeQuery = true)
    int markProcessed(@Param("bizKey") String bizKey,
                      @Param("fromDt") String fromDt,
                      @Param("toDt") String toDt,
                      @Param("docTypes") Collection<String> docTypes,
                      @Param("excludedStatuses") Collection<String> excludedStatuses,
                      @Param("sid") Long sid);

    /** Release every invoice this declaration had claimed (unlock). */
    @Modifying
    @Query(value = """
            update busa_doc_mstr
               set vat_decl_proc_yn = 'N', vat_decl_sid = null
             where biz_key = :bizKey
               and vat_decl_sid = :sid
            """, nativeQuery = true)
    int clearProcessed(@Param("bizKey") String bizKey, @Param("sid") Long sid);

    /** Reduction total over the whole filtered set (not just the page). */
    @Query(value = "select coalesce(sum(case when d.einv_taxrate = '8%'"
            + "                              then floor(coalesce(d.sply_amt, 0) * 2 / 100) else 0 end), 0)"
            + FROM_WHERE,
            nativeQuery = true)
    BigDecimal sumReducedAmt(@Param("bizKey") String bizKey,
                             @Param("fromDt") String fromDt,
                             @Param("toDt") String toDt,
                             @Param("docTypes") Collection<String> docTypes,
                             @Param("rate") String rate,
                             @Param("search") String search,
                             @Param("excludedStatuses") Collection<String> excludedStatuses);

    /**
     * Per-VAT-rate subtotals over the whole filtered set — one row per
     * {@code EINV_TAXRATE} bucket, ordered into the fixed 01-1/GTGT section
     * sequence ({@link #RATE_SEQ}). Same {@code FROM_WHERE} chain as the row
     * list, so the section footers and the rows always reconcile. The grand
     * total is the caller's sum of these rows — no separate query.
     */
    @Query(value = "select d.einv_taxrate                                       as groupCd,"
            + "        count(*)                                                 as rowCount,"
            + "        coalesce(sum(coalesce(d.sply_amt, 0)), 0)                as totalSplyAmt,"
            + "        coalesce(sum(floor(coalesce(d.sply_amt, 0) * " + RATE_PCT + " / 100)), 0) as totalVatAmt,"
            + "        coalesce(sum(case when d.einv_taxrate = '8%'"
            + "                          then floor(coalesce(d.sply_amt, 0) * 2 / 100) else 0 end), 0) as totalReducedAmt"
            + FROM_WHERE
            + " group by d.einv_taxrate"
            + " order by " + RATE_SEQ,
            nativeQuery = true)
    List<AnnexTaxGroupProjection> groupTotals(@Param("bizKey") String bizKey,
                                              @Param("fromDt") String fromDt,
                                              @Param("toDt") String toDt,
                                              @Param("docTypes") Collection<String> docTypes,
                                              @Param("rate") String rate,
                                              @Param("search") String search,
                                              @Param("excludedStatuses") Collection<String> excludedStatuses);
}
