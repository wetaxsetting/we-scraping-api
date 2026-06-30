package com.kosign.wecms.domain.einvoice.repository;

import com.kosign.wecms.domain.einvoice.entity.BusaDocMstr;
import com.kosign.wecms.domain.einvoice.entity.BusaDocMstrId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
 *   <li>Optional {@code status} — exact match against
 *       {@code EINV_INVOICESTATUS} (the stored GDT Vietnamese status
 *       string). Mirrors legacy {@code einv_0001_001_r001_act.jsp:88-92}:
 *       the dropdown's {@code dv00x} values are the verbatim stored
 *       strings, so legacy does {@code WHERE einv_invoiceStatus = ?}.
 *       Blank / {@code null} = no status filter.</li>
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
               AND (:status IS NULL OR m.einvInvoicestatus = :status)
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
            @Param("status")         String status,
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
               AND (:status IS NULL OR m.einvInvoicestatus = :status)
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
            @Param("status")         String status,
            @Param("keywordPattern") String keywordPattern);

    /**
     * Card-link list — every BUSA_DOC_MSTR row eligible to be linked to
     * a card transaction in the
     * "Link Card Payments with the Expenses" popup
     * (legacy {@code card_0001_002_r001_act.jsp}).
     *
     * <p>Filter chain (legacy parity):
     * <ul>
     *   <li>{@code BIZ_KEY = :bizKey}</li>
     *   <li>{@code DOC_TYPE = 'B'} (Bill / e-Invoice)</li>
     *   <li>{@code HAND_REG_YN = 'N'} (synced from GDT, NOT manually
     *       entered — receipts go through the {@code r002} sibling)</li>
     *   <li>{@code PROV_STTS NOT IN ('EE','VD')} (hides Rejected /
     *       Voided)</li>
     *   <li>{@code ISSU_DT BETWEEN :fromDt AND :toDt}</li>
     *   <li>{@code NOT EXISTS} on {@code BUSA_DOC_RCV_PAY_HIS} — excludes
     *       documents already linked to ANY receive / payment record
     *       (any payment method, not just CARD; mirrors legacy
     *       {@code (BIZ_KEY, DOC_ID) NOT IN (SELECT BIZ_KEY, DOC_ID FROM
     *       BUSA_DOC_RCV_PAY_HIS)} subquery)</li>
     *   <li>Optional search on {@code TRCO_CMNM} / {@code TRCO_CMENM} /
     *       {@code DISP_DOC_ID} (legacy `srchWd`)</li>
     * </ul>
     *
     * <p>Returns most-recent issue date first, deterministic by
     * {@code DOC_ID} secondary key. Caller paginates via the supplied
     * {@link org.springframework.data.domain.Pageable}.
     *
     * <p>Native query because {@code BUSA_DOC_RCV_PAY_HIS} has no JPA
     * entity yet (only its V13 schema). Adding a one-shot entity for a
     * single NOT EXISTS clause is overkill; the native form keeps the
     * surface narrow.
     */
    @Query(value = """
            SELECT m.* FROM BUSA_DOC_MSTR m
             WHERE m.BIZ_KEY      = :bizKey
               AND m.DOC_TYPE     = 'B'
               AND m.HAND_REG_YN  = 'N'
               AND COALESCE(m.PROV_STTS, '') NOT IN ('EE', 'VD')
               AND m.ISSU_DT BETWEEN :fromDt AND :toDt
               AND NOT EXISTS (
                   SELECT 1 FROM BUSA_DOC_RCV_PAY_HIS h
                    WHERE h.BIZ_KEY = m.BIZ_KEY
                      AND h.DOC_ID  = m.DOC_ID)
               AND (:keywordPattern IS NULL
                    OR LOWER(COALESCE(m.TRCO_CMNM,    '')) LIKE :keywordPattern ESCAPE '!'
                    OR LOWER(COALESCE(m.TRCO_CMENM,   '')) LIKE :keywordPattern ESCAPE '!'
                    OR LOWER(COALESCE(m.DISP_DOC_ID,  '')) LIKE :keywordPattern ESCAPE '!')
             ORDER BY m.ISSU_DT DESC, m.DOC_ID DESC
            """,
            countQuery = """
                    SELECT COUNT(*) FROM BUSA_DOC_MSTR m
                     WHERE m.BIZ_KEY      = :bizKey
                       AND m.DOC_TYPE     = 'B'
                       AND m.HAND_REG_YN  = 'N'
                       AND COALESCE(m.PROV_STTS, '') NOT IN ('EE', 'VD')
                       AND m.ISSU_DT BETWEEN :fromDt AND :toDt
                       AND NOT EXISTS (
                           SELECT 1 FROM BUSA_DOC_RCV_PAY_HIS h
                            WHERE h.BIZ_KEY = m.BIZ_KEY
                              AND h.DOC_ID  = m.DOC_ID)
                       AND (:keywordPattern IS NULL
                            OR LOWER(COALESCE(m.TRCO_CMNM,    '')) LIKE :keywordPattern ESCAPE '!'
                            OR LOWER(COALESCE(m.TRCO_CMENM,   '')) LIKE :keywordPattern ESCAPE '!'
                            OR LOWER(COALESCE(m.DISP_DOC_ID,  '')) LIKE :keywordPattern ESCAPE '!')
                    """,
            nativeQuery = true)
    Page<BusaDocMstr> findLinkableEInvoices(
            @Param("bizKey")         String bizKey,
            @Param("fromDt")         String fromDt,
            @Param("toDt")           String toDt,
            @Param("keywordPattern") String keywordPattern,
            Pageable pageable);

    /**
     * Card-link "Receipt" tab list — manually-registered expenses
     * eligible to be linked to a card transaction. Mirrors legacy
     * {@code card_0001_002_r002_act.jsp}.
     *
     * <p>Filter chain — same as {@link #findLinkableEInvoices} except:
     * <ul>
     *   <li>{@code DOC_TYPE='E'} (Expense — manual receipts)</li>
     *   <li>{@code HAND_REG_YN='Y'} (hand-registered — distinguishes
     *       manual receipts from GDT-synced bills)</li>
     *   <li>Date range applies to {@code USE_DT} (not {@code ISSU_DT}) —
     *       legacy receipts file the "when was this used" date, while
     *       e-Invoices file the "when was this issued by GDT" date</li>
     *   <li>Search additionally matches the {@code BUSA_USAG_CD}
     *       (Purpose) column</li>
     * </ul>
     */
    @Query(value = """
            SELECT m.* FROM BUSA_DOC_MSTR m
             WHERE m.BIZ_KEY      = :bizKey
               AND m.DOC_TYPE     = 'E'
               AND m.HAND_REG_YN  = 'Y'
               AND COALESCE(m.PROV_STTS, '') NOT IN ('EE', 'VD')
               AND COALESCE(m.USE_DT, m.ISSU_DT) BETWEEN :fromDt AND :toDt
               AND NOT EXISTS (
                   SELECT 1 FROM BUSA_DOC_RCV_PAY_HIS h
                    WHERE h.BIZ_KEY = m.BIZ_KEY
                      AND h.DOC_ID  = m.DOC_ID)
               AND (:keywordPattern IS NULL
                    OR LOWER(COALESCE(m.TRCO_CMNM,     '')) LIKE :keywordPattern ESCAPE '!'
                    OR LOWER(COALESCE(m.TRCO_CMENM,    '')) LIKE :keywordPattern ESCAPE '!'
                    OR LOWER(COALESCE(m.DISP_DOC_ID,   '')) LIKE :keywordPattern ESCAPE '!'
                    OR LOWER(COALESCE(m.BUSA_USAG_CD,  '')) LIKE :keywordPattern ESCAPE '!')
             ORDER BY COALESCE(m.USE_DT, m.ISSU_DT) DESC, m.DOC_ID DESC
            """,
            countQuery = """
                    SELECT COUNT(*) FROM BUSA_DOC_MSTR m
                     WHERE m.BIZ_KEY      = :bizKey
                       AND m.DOC_TYPE     = 'E'
                       AND m.HAND_REG_YN  = 'Y'
                       AND COALESCE(m.PROV_STTS, '') NOT IN ('EE', 'VD')
                       AND COALESCE(m.USE_DT, m.ISSU_DT) BETWEEN :fromDt AND :toDt
                       AND NOT EXISTS (
                           SELECT 1 FROM BUSA_DOC_RCV_PAY_HIS h
                            WHERE h.BIZ_KEY = m.BIZ_KEY
                              AND h.DOC_ID  = m.DOC_ID)
                       AND (:keywordPattern IS NULL
                            OR LOWER(COALESCE(m.TRCO_CMNM,     '')) LIKE :keywordPattern ESCAPE '!'
                            OR LOWER(COALESCE(m.TRCO_CMENM,    '')) LIKE :keywordPattern ESCAPE '!'
                            OR LOWER(COALESCE(m.DISP_DOC_ID,   '')) LIKE :keywordPattern ESCAPE '!'
                            OR LOWER(COALESCE(m.BUSA_USAG_CD,  '')) LIKE :keywordPattern ESCAPE '!')
                    """,
            nativeQuery = true)
    Page<BusaDocMstr> findLinkableReceipts(
            @Param("bizKey")         String bizKey,
            @Param("fromDt")         String fromDt,
            @Param("toDt")           String toDt,
            @Param("keywordPattern") String keywordPattern,
            Pageable pageable);

    /**
     * Fetch the linkable docs for an in-progress link write. Used by
     * the link create path to validate every requested {@code doc_id}
     * (a) exists, (b) belongs to the caller's tenant, (c) is still
     * eligible (not already linked / not Rejected / not Voided) before
     * inserting the {@code BUSA_DOC_RCV_PAY_HIS} rows.
     */
    @Query(value = """
            SELECT m.* FROM BUSA_DOC_MSTR m
             WHERE m.BIZ_KEY = :bizKey
               AND m.DOC_ID IN (:docIds)
               AND m.DOC_TYPE IN ('B', 'E')
               AND COALESCE(m.PROV_STTS, '') NOT IN ('EE', 'VD')
               AND NOT EXISTS (
                   SELECT 1 FROM BUSA_DOC_RCV_PAY_HIS h
                    WHERE h.BIZ_KEY = m.BIZ_KEY
                      AND h.DOC_ID  = m.DOC_ID)
            """,
            nativeQuery = true)
    List<BusaDocMstr> findLinkableByDocIds(
            @Param("bizKey") String bizKey,
            @Param("docIds") List<String> docIds);

    /**
     * Detail-history popup query — refactor port of
     * {@code nxisas_einvhis_dt_r001_act.jsp:57-79}. Lists every invoice a
     * single GDT inquiry batch produced, filtered exactly as legacy:
     * <ul>
     *   <li>{@code DOC_TYPE = :docType} (Sales {@code 'I'} only — the
     *       legacy action rejects Expense)</li>
     *   <li>{@code BIZ_KEY = :bizKey} — tenant scope</li>
     *   <li>{@code ISSU_DT BETWEEN :fromDt AND :toDt}</li>
     *   <li>{@code MOD_DTM = :modDtm} — the exact batch timestamp</li>
     *   <li>{@code HAND_REG_YN = 'N'} — scraped (not hand-registered) rows</li>
     * </ul>
     * Ordering comes from the caller-supplied {@link Sort} (default
     * {@code issu_dt DESC} per the legacy {@code SortHelper} whitelist).
     */
    @Query("""
            SELECT m FROM BusaDocMstr m
             WHERE m.docType = :docType
               AND m.bizKey = :bizKey
               AND m.issuDt BETWEEN :fromDt AND :toDt
               AND m.modDtm = :modDtm
               AND m.handRegYn = 'N'
            """)
    List<BusaDocMstr> findDetailHistory(
            @Param("bizKey")  String bizKey,
            @Param("docType") String docType,
            @Param("fromDt")  String fromDt,
            @Param("toDt")    String toDt,
            @Param("modDtm")  String modDtm,
            Sort sort);

    /**
     * Operation Status Inquiry <b>seed</b> batch ({@code OperationStatusBatchService.seedTenant},
     * legacy {@code bcs.jex.wabooks.oprt.OPRT_0001_001}) — every distinct, non-blank
     * counterparty tax id used on this tenant's documents, restricted to
     * {@code HAND_REG_YN = 'N'} (synced-from-GDT rows, not manually entered).
     *
     * <p>Mirrors legacy {@code IDO.BUSA_DOC_MSTR_R035}:
     * {@code SELECT DISTINCT TRCO_TAX_ID FROM BUSA_DOC_MSTR
     * WHERE NULLIF(TRIM(TRCO_TAX_ID),'') IS NOT NULL} + dynamic
     * {@code biz_key = ? AND hand_reg_yn = 'N'}. The seed batch is the only
     * consumer; read-only — it never writes {@code BUSA_DOC_MSTR}.
     */
    @Query("""
            SELECT DISTINCT m.trcoTaxId FROM BusaDocMstr m
             WHERE m.bizKey = :bizKey
               AND m.handRegYn = :handRegYn
               AND m.trcoTaxId IS NOT NULL
               AND TRIM(m.trcoTaxId) <> ''
            """)
    List<String> findDistinctTrcoTaxIdByBizKeyAndHandRegYn(
            @Param("bizKey")    String bizKey,
            @Param("handRegYn") String handRegYn);
}
