package com.kosign.wecms.domain.einvoice.repository;

import com.kosign.wecms.domain.einvoice.entity.BusaDocMstr;
import com.kosign.wecms.domain.einvoice.entity.BusaDocMstrId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Sales / Bill e-Invoice list queries. Runs over {@code BUSA_DOC_MSTR}
 * with the legacy-parity baseline filters baked in (rule IR-2).
 *
 * <p>Two queries share the same WHERE chain so the aggregation totals
 * stay stable across pagination (rule IR-6). The shared chain is:
 * <ul>
 *   <li>{@code BIZ_KEY = :bizKey} — tenant scope</li>
 *   <li>{@code DOC_TYPE = :docType} — {@code 'I'} Sales / {@code 'B'} Bill</li>
 *   <li>{@code PROV_STTS NOT IN ('VD','EE')} — hides drafts + cancelled</li>
 *   <li>{@code HAND_REG_YN <> 'Y'} — hides hand-registered rows</li>
 *   <li>{@code ISSU_DT BETWEEN :fromDt AND :toDt} — required range (IR-3)</li>
 *   <li>Optional {@code keyword} — case-insensitive partial match across
 *       {@code DOC_ID}, {@code TRCO_CMNM}, {@code TRCO_TAX_ID},
 *       {@code EINV_INVOICESYMBOL}, {@code DISP_DOC_ID} (rule IR-5).</li>
 * </ul>
 *
 * <p>{@link #findFiltered} accepts a {@link Pageable} and returns a
 * {@link Page}; {@link #aggregateByCurrency} returns one row per
 * distinct {@code TRCO_TRSC_CCY_CD} with count + total / tax / pretax
 * sums.
 *
 * <p><b>Legacy parity gap — deferred:</b> the legacy action JSP also
 * applies {@code (DOC_ID, BIZ_KEY) IN (SELECT DOC_ID, BIZ_KEY FROM
 * BUSA_DOC_DETL)} so invoices with no line items are filtered out.
 * {@code BUSA_DOC_DETL} is NOT yet in the local Flyway schema; the
 * fourth baseline filter is held back until the table is ported in
 * a follow-up MR.
 *
 * <p><b>Keyword search scope (IR-5):</b> Phase 2 ships a fixed 5-column
 * subset; the legacy 16-column View-Setting-driven walk and {@code
 * wb_get_translate_text} accent folding are deferred. {@code DOC_NO}
 * lives on a JOINed table ({@code EXPT_RESOL_MSTR}) and is not in the
 * {@code BUSA_DOC_MSTR}-only Phase 2 query — substituted with
 * {@code DISP_DOC_ID} (display-friendly document id stored on the
 * master row itself) for the Phase 2 search subset.
 */
public interface BusaDocMstrRepository extends JpaRepository<BusaDocMstr, BusaDocMstrId> {

    @Query("""
            SELECT m FROM BusaDocMstr m
             WHERE m.bizKey = :bizKey
               AND m.docType = :docType
               AND m.provStts NOT IN ('VD', 'EE')
               AND m.handRegYn <> 'Y'
               AND m.issuDt BETWEEN :fromDt AND :toDt
               AND (:keywordPattern IS NULL
                    OR LOWER(COALESCE(m.docId, ''))             LIKE :keywordPattern ESCAPE '!'
                    OR LOWER(COALESCE(m.trcoCmnm, ''))          LIKE :keywordPattern ESCAPE '!'
                    OR LOWER(COALESCE(m.trcoTaxId, ''))         LIKE :keywordPattern ESCAPE '!'
                    OR LOWER(COALESCE(m.einvInvoicesymbol, '')) LIKE :keywordPattern ESCAPE '!'
                    OR LOWER(COALESCE(m.dispDocId, ''))         LIKE :keywordPattern ESCAPE '!')
            """)
    Page<BusaDocMstr> findFiltered(
            @Param("bizKey")         String bizKey,
            @Param("docType")        String docType,
            @Param("fromDt")         String fromDt,
            @Param("toDt")           String toDt,
            @Param("keywordPattern") String keywordPattern,
            Pageable pageable);

    @Query("""
            SELECT new com.kosign.wecms.domain.einvoice.repository.EInvoiceCcyAggregate(
                    m.trcoTrscCcyCd,
                    COUNT(m),
                    COALESCE(SUM(m.totlAmt),     0),
                    COALESCE(SUM(m.taxAmtSum),   0),
                    COALESCE(SUM(m.splyAmtSum),  0))
              FROM BusaDocMstr m
             WHERE m.bizKey = :bizKey
               AND m.docType = :docType
               AND m.provStts NOT IN ('VD', 'EE')
               AND m.handRegYn <> 'Y'
               AND m.issuDt BETWEEN :fromDt AND :toDt
               AND (:keywordPattern IS NULL
                    OR LOWER(COALESCE(m.docId, ''))             LIKE :keywordPattern ESCAPE '!'
                    OR LOWER(COALESCE(m.trcoCmnm, ''))          LIKE :keywordPattern ESCAPE '!'
                    OR LOWER(COALESCE(m.trcoTaxId, ''))         LIKE :keywordPattern ESCAPE '!'
                    OR LOWER(COALESCE(m.einvInvoicesymbol, '')) LIKE :keywordPattern ESCAPE '!'
                    OR LOWER(COALESCE(m.dispDocId, ''))         LIKE :keywordPattern ESCAPE '!')
             GROUP BY m.trcoTrscCcyCd
             ORDER BY m.trcoTrscCcyCd
            """)
    List<EInvoiceCcyAggregate> aggregateByCurrency(
            @Param("bizKey")         String bizKey,
            @Param("docType")        String docType,
            @Param("fromDt")         String fromDt,
            @Param("toDt")           String toDt,
            @Param("keywordPattern") String keywordPattern);
}
