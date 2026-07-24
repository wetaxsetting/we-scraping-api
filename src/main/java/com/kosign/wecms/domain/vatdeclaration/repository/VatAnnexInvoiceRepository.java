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
            + "        m.vat_decl_sid                             as vatDeclSid"
            + FROM_WHERE
            + " order by m.issu_dt, m.doc_id, d.disp_ord"
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
     */
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
}
