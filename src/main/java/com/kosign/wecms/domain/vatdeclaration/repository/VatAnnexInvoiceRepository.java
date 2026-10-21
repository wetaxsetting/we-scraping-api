package com.kosign.wecms.domain.vatdeclaration.repository;

import com.kosign.wecms.domain.vatdeclaration.dto.AnnexInvoiceProjection;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.Repository;
import com.kosign.wecms.domain.vatdeclaration.entity.VatDeclaration;

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
             left join trco_infm t
               on t.biz_key = m.biz_key and t.trco_key = m.trco_key
            where m.biz_key   = :bizKey
              and m.issu_dt between :fromDt and :toDt
              and m.doc_type in (:docTypes)
              and coalesce(m.einv_invoicestatus, '') not in (:excludedStatuses)
              and m.einv_numericsymbol = '1'
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

    /** {@link #RATE_SEQ} over the per-(invoice,rate) subquery alias {@code p.rate}. */
    String RATE_SEQ_P = """
            (case p.rate
                  when 'KKKNT' then 1
                  when '0%'    then 2
                  when '5%'    then 3
                  when '8%'    then 4
                  when '10%'   then 5
                  when 'KCT'   then 6
                  else 99 end)
            """;

    /**
     * The period's invoice lines folded to <b>one row per (invoice, VAT rate)</b>
     * — the shared base for {@link #findRows} and {@link #groupTotals}. Base
     * ({@code sply}), the per-line floor VAT ({@code floor_vat}) and the 8%
     * reduction come from {@code BUSA_DOC_DETL}; the counterparty tax code is
     * resolved via the {@code trco_infm} join in {@link #FROM_WHERE}. The real
     * output VAT is grafted on by the callers (they LEFT JOIN {@link #INV_TAX}),
     * never summed here — folding first, then joining 1:1, is what keeps the VAT
     * from being multiplied by the invoice's product-line count.
     */
    String PER_INV_RATE = "  select m.doc_id, m.doc_type,"
            + "         coalesce(m.disp_doc_id, m.doc_id)          as inv_no,"
            + "         m.einv_invoicesymbol                       as inv_symbol,"
            + "         m.issu_dt, m.hand_doc_id, m.vat_decl_proc_yn, m.vat_decl_sid,"
            + "         d.einv_taxrate                             as rate,"
            + "         case when nullif(t.trco_tax_id, '') is not null then t.trco_tax_id"
            + "              when m.trco_tax_id is not null and m.trco_tax_id <> m.issu_tax_id then m.trco_tax_id"
            + "              else null end                         as trco_tax_code,"
            + "         m.trco_cmnm                                as trco_nm,"
            + "         string_agg(distinct coalesce(d.einv_productname, d.item_nm), ', ') as prod_nm,"
            + "         coalesce(sum(coalesce(d.sply_amt, 0)), 0)  as sply,"
            + "         coalesce(sum(floor(coalesce(d.sply_amt, 0) * " + RATE_PCT + " / 100)), 0) as floor_vat,"
            + "         coalesce(sum(case when d.einv_taxrate = '8%'"
            + "                           then floor(coalesce(d.sply_amt, 0) * 2 / 100) else 0 end), 0) as reduced,"
            + "         'D' as src"
            + FROM_WHERE
            + "  group by m.doc_id, m.doc_type, m.disp_doc_id, m.einv_invoicesymbol, m.issu_dt,"
            + "           m.hand_doc_id, m.vat_decl_proc_yn, m.vat_decl_sid, d.einv_taxrate,"
            + "           t.trco_tax_id, m.trco_tax_id, m.issu_tax_id, m.trco_cmnm";

    /**
     * Effective VAT-rate bucket for a <b>header-only</b> invoice (no
     * {@code BUSA_DOC_DETL}, no {@code EINV_TAX_DETL} — only the master header
     * exists, e.g. a scrape that captured the header but no line items). The rate
     * is <b>derived</b> from {@code round(TAX_AMT_SUM / SPLY_AMT_SUM × 100)} and
     * snapped to the nearest legal bucket; no base or zero VAT → {@code KCT}.
     * This is an approximation (a header-only invoice could in principle span
     * several rates) accepted by the PO 2026-08-22 so the invoice still appears
     * on BKBR/BKMV and contributes to [23]/[24] instead of vanishing.
     */
    String HDR_RATE = "(case when coalesce(m.tax_amt_sum, 0) = 0 or coalesce(m.sply_amt_sum, 0) = 0 then 'KCT'"
            + "            when round(m.tax_amt_sum / m.sply_amt_sum * 100) < 3 then '0%'"
            + "            when round(m.tax_amt_sum / m.sply_amt_sum * 100) < 7 then '5%'"
            + "            when round(m.tax_amt_sum / m.sply_amt_sum * 100) < 9 then '8%'"
            + "            else '10%' end)";

    /**
     * Header-only fallback branch — one row per invoice that has <b>no</b>
     * {@code BUSA_DOC_DETL} line at all, built from the {@code BUSA_DOC_MSTR}
     * header ({@code SPLY_AMT_SUM} / {@code TAX_AMT_SUM}) with the rate derived by
     * {@link #HDR_RATE}. UNION-ALL'd with {@link #PER_INV_RATE} (mutually
     * exclusive via the {@code NOT EXISTS busa_doc_detl} guard) so these invoices
     * still list on the annex and feed the indicators. {@code src = 'H'} tells the
     * callers to take the header VAT verbatim (never the {@link #INV_TAX} join).
     * Column list matches {@link #PER_INV_RATE} exactly for the UNION.
     */
    String PER_INV_RATE_HDR = "  select m.doc_id, m.doc_type,"
            + "         coalesce(m.disp_doc_id, m.doc_id)          as inv_no,"
            + "         m.einv_invoicesymbol                       as inv_symbol,"
            + "         m.issu_dt, m.hand_doc_id, m.vat_decl_proc_yn, m.vat_decl_sid,"
            + "         " + HDR_RATE + "                           as rate,"
            + "         case when nullif(t.trco_tax_id, '') is not null then t.trco_tax_id"
            + "              when m.trco_tax_id is not null and m.trco_tax_id <> m.issu_tax_id then m.trco_tax_id"
            + "              else null end                         as trco_tax_code,"
            + "         m.trco_cmnm                                as trco_nm,"
            + "         null                                       as prod_nm,"
            + "         coalesce(m.sply_amt_sum, 0)                as sply,"
            + "         floor(coalesce(m.tax_amt_sum, 0))          as floor_vat,"
            + "         case when " + HDR_RATE + " = '8%' then floor(coalesce(m.sply_amt_sum, 0) * 2 / 100) else 0 end as reduced,"
            + "         'H' as src"
            + "    from busa_doc_mstr m"
            + "    left join trco_infm t on t.biz_key = m.biz_key and t.trco_key = m.trco_key"
            + "   where m.biz_key = :bizKey"
            + "     and m.issu_dt between :fromDt and :toDt"
            + "     and m.doc_type in (:docTypes)"
            + "     and coalesce(m.einv_invoicestatus, '') not in (:excludedStatuses)"
            + "     and m.einv_numericsymbol = '1'"
            + "     and not exists (select 1 from busa_doc_detl d where d.biz_key = m.biz_key and d.doc_id = m.doc_id)"
            + "     and (cast(:rate as text) is null or " + HDR_RATE + " = cast(:rate as text))"
            + "     and (cast(:search as text) is null"
            + "          or lower(coalesce(m.disp_doc_id, m.doc_id)) like lower(concat('%', cast(:search as text), '%')))";

    /**
     * Real output VAT per {@code (doc_id, rate)} from {@code EINV_TAX_DETL} — the
     * VAT actually issued on the invoice, which the Sales e-Invoice list sums
     * (header {@code TAX_AMT_SUM}). Callers LEFT JOIN this 1:1 onto
     * {@link #PER_INV_RATE} and use it for OUTPUT (sales) rows instead of the
     * {@code floor(SPLY_AMT × rate%)} recompute, which drifts by a few đồng of
     * rounding. Verified on DEV Q1/2026: 8%+10% sums to 57,656,821 = the
     * e-invoice total (floor gave 57,656,833). Purchase VAT keeps {@code floor_vat}.
     */
    String INV_TAX = "  select e.doc_id, e.einv_taxrate as rate,"
            + "         coalesce(sum(coalesce(e.tax_amt, 0)), 0) as tax"
            + "    from einv_tax_detl e"
            + "   where e.biz_key = :bizKey"
            + "   group by e.doc_id, e.einv_taxrate";

    /**
     * One row per <b>(invoice, VAT rate)</b> — the product lines of an invoice
     * that share a rate are folded into a single annex line (To-Be 2026-08-22:
     * 01-1/GTGT lists one line per rate per invoice, not one line per product).
     * An invoice carrying two rates therefore appears twice, once in each rate
     * section. {@code splyAmt} / {@code vatAmt} / {@code reducedAmt} are the
     * summed line values so the section footers ({@link #groupTotals}) still
     * reconcile to the đồng (VAT summed per line then aggregated, identical to
     * the footer formula). {@code prodNm} — not shown on the 01-1/GTGT grid —
     * carries the distinct product names of the folded lines for the export.
     */
    @Query(value = "select p.doc_id                               as srcDocId,"
            + "        case when p.doc_type = 'I' then 'O' else 'I' end as ioDv,"
            + "        p.inv_no                                   as invNo,"
            + "        p.inv_symbol                               as invSymbol,"
            + "        p.issu_dt                                  as invDt,"
            + "        p.trco_tax_code                            as trcoTaxCode,"
            + "        p.trco_nm                                  as trcoNm,"
            + "        p.prod_nm                                  as prodNm,"
            + "        p.sply                                     as splyAmt,"
            + "        p.rate                                     as vatRate,"
            // VAT = real issued VAT (EINV_TAX_DETL) for OUTPUT/sales; per-line
            // floor for input/purchase. Sales floor drifts by a few đồng of
            // rounding vs the invoice's actual VAT — see INV_TAX.
            + "        case when p.src = 'H' then p.floor_vat"
            + "             when p.doc_type = 'I' then coalesce(it.tax, 0)"
            + "             else p.floor_vat end                  as vatAmt,"
            + "        p.reduced                                  as reducedAmt,"
            + "        p.vat_decl_proc_yn                         as vatDeclProcYn,"
            + "        p.vat_decl_sid                             as vatDeclSid,"
            + "        (dense_rank() over (order by " + RATE_SEQ_P + "))::int as vatRateOrder"
            + "   from ( " + PER_INV_RATE + " union all " + PER_INV_RATE_HDR + " ) p"
            + "   left join ( " + INV_TAX + " ) it on it.doc_id = p.doc_id and it.rate = p.rate"
            // Order by invoice NUMBER numerically, not lexically: doc_id sorts
            // '...-1','...-10','...-2' as strings. hand_doc_id is the bare invoice
            // no ('1','2','10'); cast to bigint when numeric (nulls last for any
            // non-numeric hand number), doc_id as the final stable tiebreaker.
            + "  order by " + RATE_SEQ_P + ", p.issu_dt,"
            + "          (case when p.hand_doc_id ~ '^[0-9]+$' then p.hand_doc_id::bigint else null end) nulls last,"
            + "          p.doc_id"
            + "  offset :offset limit :size",
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
               and einv_numericsymbol = '1'
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

    /**
     * Per-VAT-rate subtotals over the whole filtered set — one row per
     * {@code EINV_TAXRATE} bucket, ordered into the fixed 01-1/GTGT section
     * sequence ({@link #RATE_SEQ}). Same {@code FROM_WHERE} chain as the row
     * list, so the section footers and the rows always reconcile. The grand
     * total is the caller's sum of these rows — no separate query.
     *
     * <p>{@code rowCount} is {@code count(distinct doc_id)} — the number of
     * <b>invoices</b> in the section, which equals the number of grouped rows
     * {@link #findRows} emits for that rate (rows are folded per invoice+rate),
     * so it doubles as the paging {@code total_count}.
     */
    @Query(value = "select p.rate                                             as groupCd,"
            + "        count(*)                                                 as rowCount,"
            + "        coalesce(sum(p.sply), 0)                                 as totalSplyAmt,"
            + "        coalesce(sum(case when p.src = 'H' then p.floor_vat"
            + "                          when p.doc_type = 'I' then coalesce(it.tax, 0)"
            + "                          else p.floor_vat end), 0)             as totalVatAmt,"
            + "        coalesce(sum(p.reduced), 0)                              as totalReducedAmt"
            + "   from ( " + PER_INV_RATE + " union all " + PER_INV_RATE_HDR + " ) p"
            + "   left join ( " + INV_TAX + " ) it on it.doc_id = p.doc_id and it.rate = p.rate"
            + "  group by p.rate"
            + "  order by " + RATE_SEQ_P,
            nativeQuery = true)
    List<AnnexTaxGroupProjection> groupTotals(@Param("bizKey") String bizKey,
                                              @Param("fromDt") String fromDt,
                                              @Param("toDt") String toDt,
                                              @Param("docTypes") Collection<String> docTypes,
                                              @Param("rate") String rate,
                                              @Param("search") String search,
                                              @Param("excludedStatuses") Collection<String> excludedStatuses);

    /**
     * Sync aggregation: the period's invoice lines summed by
     * {@code (DOC_TYPE, EINV_TAXRATE)} — the source of the invoice-derived
     * indicator buckets ([23]/[24], [26]/[29]/[30]/[31]/[32]/[33]/[32a]).
     *
     * <p>Reads {@code BUSA_DOC_DETL} (the same line-item source as the annex
     * {@link #groupTotals}), <b>not</b> {@code EINV_TAX_DETL}. Verified on DEV
     * 2026-08-06: {@code EINV_TAX_DETL.AMT} is 0 for every output (sales)
     * invoice — only purchase rows carry a base there — so the previous
     * {@code EINV_TAX_DETL} source left the sales base cells ([26]/[30]/[32]/…)
     * at 0 even when the annex clearly listed the invoices. Sourcing base from
     * {@code SPLY_AMT} and VAT from {@code floor(SPLY_AMT * rate% / 100)} makes
     * the declaration reconcile to the đồng with BKBR/BKMV (the annex is the
     * supporting schedule for these lines and already computes VAT this way).
     * Same period + excluded-status predicate as the annex list. Consumed by
     * {@code VatDeclarationService.sync} / {@code create}.
     */
    @Query(value = "select u.doc_type                            as docType,"
            + "        u.rate                                    as taxRate,"
            + "        coalesce(sum(u.amt), 0)                   as amt,"
            + "        coalesce(sum(u.tax), 0)                   as taxAmt"
            + "   from ("
            // detail branch — one row per BUSA_DOC_DETL line
            + "     select m.doc_type, d.einv_taxrate as rate,"
            + "            coalesce(d.sply_amt, 0) as amt,"
            + "            floor(coalesce(d.sply_amt, 0) * " + RATE_PCT + " / 100) as tax"
            + "       from busa_doc_mstr m"
            + "       join busa_doc_detl d on d.biz_key = m.biz_key and d.doc_id = m.doc_id"
            + "      where m.biz_key = :bizKey and m.issu_dt between :fromDt and :toDt"
            + "        and m.doc_type in (:docTypes)"
            + "        and coalesce(m.einv_invoicestatus, '') not in (:excludedStatuses)"
            + "        and m.einv_numericsymbol = '1'"
            + "     union all"
            // header-only branch — one row per invoice with no detail line (see HDR_RATE)
            + "     select m.doc_type, " + HDR_RATE + " as rate,"
            + "            coalesce(m.sply_amt_sum, 0) as amt,"
            + "            floor(coalesce(m.tax_amt_sum, 0)) as tax"
            + "       from busa_doc_mstr m"
            + "      where m.biz_key = :bizKey and m.issu_dt between :fromDt and :toDt"
            + "        and m.doc_type in (:docTypes)"
            + "        and coalesce(m.einv_invoicestatus, '') not in (:excludedStatuses)"
            + "        and m.einv_numericsymbol = '1'"
            + "        and not exists (select 1 from busa_doc_detl d where d.biz_key = m.biz_key and d.doc_id = m.doc_id)"
            + "   ) u"
            + "  group by u.doc_type, u.rate",
            nativeQuery = true)
    List<VatSyncBucketProjection> syncBuckets(@Param("bizKey") String bizKey,
                                              @Param("fromDt") String fromDt,
                                              @Param("toDt") String toDt,
                                              @Param("docTypes") Collection<String> docTypes,
                                              @Param("excludedStatuses") Collection<String> excludedStatuses);

    /**
     * The period's <b>real output (sales) VAT</b> per rate — {@code SUM(EINV_TAX_DETL.TAX_AMT)}
     * grouped by {@code EINV_TAXRATE}, output invoices only.
     *
     * <p>Used to pin {@code [31]} / {@code [33]} to the VAT actually issued on the
     * invoices instead of recomputing {@code floor(SPLY_AMT × rate% / 100)} per
     * line — the per-line floor drifts by a few đồng of rounding, so the
     * declaration disagreed with the Sales e-Invoice list (which sums the header
     * {@code TAX_AMT_SUM}). Verified on DEV Q1/2026: {@code EINV_TAX_DETL.TAX_AMT}
     * for 8%+10% sums to 57,656,821 = the e-invoice total, whereas the floor
     * recompute gave 57,656,833. Base cells ({@code [30]}/{@code [32]}) still come
     * from {@code BUSA_DOC_DETL.SPLY_AMT} (they already reconcile with {@code [34]}
     * and the e-invoice pre-tax total). Sales only — purchase VAT {@code [24]}
     * keeps its {@link #syncBuckets} source. Same period + excluded-status
     * predicate as {@code syncBuckets}.
     */
    @Query(value = "select 'I'                                        as docType,"
            + "        u.rate                                         as taxRate,"
            + "        cast(0 as numeric)                             as amt,"
            + "        coalesce(sum(u.tax), 0)                        as taxAmt"
            + "   from ("
            // real per-rate VAT from EINV_TAX_DETL (invoices with tax detail)
            + "     select e.einv_taxrate as rate, coalesce(e.tax_amt, 0) as tax"
            + "       from busa_doc_mstr m"
            + "       join einv_tax_detl e on e.biz_key = m.biz_key and e.doc_id = m.doc_id"
            + "      where m.biz_key = :bizKey and m.issu_dt between :fromDt and :toDt"
            + "        and m.doc_type = 'I'"
            + "        and coalesce(m.einv_invoicestatus, '') not in (:excludedStatuses)"
            + "        and m.einv_numericsymbol = '1'"
            + "     union all"
            // header-only sales fallback — no detail AND no einv_tax_detl (avoid double)
            + "     select " + HDR_RATE + " as rate, floor(coalesce(m.tax_amt_sum, 0)) as tax"
            + "       from busa_doc_mstr m"
            + "      where m.biz_key = :bizKey and m.issu_dt between :fromDt and :toDt"
            + "        and m.doc_type = 'I'"
            + "        and coalesce(m.einv_invoicestatus, '') not in (:excludedStatuses)"
            + "        and m.einv_numericsymbol = '1'"
            + "        and not exists (select 1 from busa_doc_detl d where d.biz_key = m.biz_key and d.doc_id = m.doc_id)"
            + "        and not exists (select 1 from einv_tax_detl e where e.biz_key = m.biz_key and e.doc_id = m.doc_id)"
            + "   ) u"
            + "  group by u.rate",
            nativeQuery = true)
    List<VatSyncBucketProjection> salesVatByRate(@Param("bizKey") String bizKey,
                                                 @Param("fromDt") String fromDt,
                                                 @Param("toDt") String toDt,
                                                 @Param("excludedStatuses") Collection<String> excludedStatuses);
}
