package com.kosign.wecms.domain.payment.internaltransfer.repository;

import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link ScdulPymnSumr}. Every query is
 * tenant-scoped — callers pass {@code bizKey} from
 * {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()};
 * a method without a {@code bizKey} parameter does not exist on this
 * interface, so cross-tenant leakage is impossible by construction.
 *
 * <p>List queries filter on {@code SCDUL_PYMN_TYPE_CD = '03'} so the
 * Internal Transfer endpoints only see their own batches (other Payment
 * modules share the same physical table — see legacy
 * {@code PymnHelper.pymn_0001_001_r001} which is parameterised by
 * payment type).
 *
 * <p>Date filters: the Service layer converts the wire {@code yyyyMMdd}
 * date filters to {@code yyyyMMddHHmmss} bounds ({@code yyyyMMdd000000} /
 * {@code yyyyMMdd235959}). The two list queries deliberately constrain
 * <em>different</em> physical columns, matching legacy:
 * <ul>
 *   <li>{@link #search} (Scheduled-Transfer list, legacy {@code pymn_0009_001})
 *       constrains {@code REG_DTM} (batch creation) — legacy
 *       {@code PymnHelper.getDynamic} uses {@code LEFT(sps.reg_dtm,8)}.</li>
 *   <li>{@link #searchDispatched} (Transfer Result list, legacy
 *       {@code pymn_0010_001}) constrains {@code TRNF_DTM} (dispatch /
 *       execution) — legacy {@code PymnHelper.getPymentResult} uses
 *       {@code LEFT(sps.trnf_dtm,8)}. The legacy IDO
 *       {@code SCDUL_PYMN_SUMR_R007} aliases {@code TRNF_DTM AS REG_DTM} on
 *       output, which is purely a wire-field name — the filtered/sorted/
 *       displayed source column is {@code TRNF_DTM}, never the real
 *       {@code REG_DTM}.</li>
 * </ul>
 */
public interface ScdulPymnSumrRepository extends JpaRepository<ScdulPymnSumr, ScdulPymnSumrId> {

    /**
     * Detail read — single row by composite PK + tenant + payment type.
     * Type-filtering at the repository layer prevents Internal Transfer
     * endpoints from accidentally returning a Vendor / Expense batch
     * that happens to share a generated SCDUL_PYMN_NO.
     */
    Optional<ScdulPymnSumr> findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(
            String bizKey, String scdulPymnNo, String scdulPymnTypeCd);

    /**
     * Fallback detail read — single row by tenant + approval sequence number +
     * payment type. Used when the caller supplies a DOC_NO (VO_YYMMDD_NNN)
     * instead of a SCDUL_PYMN_NO: the service resolves DOC_NO → APPR_SEQ_NO
     * via EXPT_RESOL_MSTR, then calls this to get the SCDUL_PYMN_SUMR row.
     * Type discriminator keeps Vendor / Expense / Payroll isolated even though
     * the APPR_SEQ_NO namespace is shared.
     */
    Optional<ScdulPymnSumr> findByBizKeyAndApprSeqNoAndScdulPymnTypeCd(
            String bizKey, String apprSeqNo, String scdulPymnTypeCd);

    /**
     * List read — paged, filtered by tenant + payment type + date range
     * (inclusive on REG_DTM) + optional title / doc-no LIKE + optional
     * {@code TRNF_YN} (used by the Transfer Result list to drop drafts).
     *
     * <p>Reason for JPQL over a derived method name: the derived form
     * would be 30+ characters and harder to read than the explicit JPQL.
     * Tenant + type are non-optional in every variant.
     */
    /**
     * <p>{@code srchWdPattern} is the pre-built lowercase {@code "%foo%"}
     * LIKE pattern (or {@code null} to skip the predicate). Building
     * the pattern in the service rather than inside the JPQL avoids a
     * PostgreSQL {@code function lower(bytea) does not exist} error
     * that fires when a null {@code :srchWd} parameter inside
     * {@code CONCAT(...)} leaves the param type un-inferable — the
     * driver then sends it as {@code bytea}. Mirrors the precedent
     * established by {@code AcctMstrRepository#findFiltered}.
     */
    /**
     * Filter dispatch table for the legacy {@code APPR_TYPE_CD}
     * codes surfaced by the Approval Status dropdown on every
     * Scheduled Payment list (Vendor / Expense / Payroll /
     * Overseas / Internal Transfer). Mirrors the legacy
     * {@code wabooks.enums.ApprSts} mapping used by
     * {@code comm_0195_001} and the frontend's
     * {@code deriveStatus} helper:
     *
     * <p>Approval status is derived by calling the legacy DB function
     * {@code wb_get_appr_stts(...)} on the {@code (BIZ_KEY, APPR_SEQ_NO)}-joined
     * {@code EXPT_RESOL_MSTR} row, mirroring {@code IDO.SCDUL_PYMN_SUMR_R002.xml:68}
     * (binding {@code (ERM.BIZ_KEY, ERM.REG_ID, ERM.REG_ID, SPS.APPR_SEQ_NO,
     * ERM.APPR_TYPE_CD)}). {@code SCDUL_PYMN_SUMR} has no {@code APPR_STTS} column
     * in the legacy JEX schema, so the function — not a stored column — is the
     * source of truth. The function maps {@code '00'→P, '01'→C, '02'→R, '03'→I}
     * (admin/registrant branch; per-approver ELSE branch is unreachable for the
     * {@code _apprvr_id = _reg_id} binding). The tabs filter on that result:
     *
     * <ul>
     *   <li>{@code null} / blank → All (no filter).</li>
     *   <li>{@code "00"} Not Requested — never submitted ({@code APPR_SEQ_NO}
     *       null/blank) OR an orphaned back-stamp whose {@code EXPT_RESOL_MSTR}
     *       row is gone ({@code erm.apprTypeCd IS NULL} via the LEFT JOIN miss).
     *       The {@code erm.apprTypeCd IS NULL} leg restores parity with legacy
     *       {@code pymn_0009_001} ({@code ERM.APPR_TYPE_CD IS NULL} over the
     *       LEFT JOIN), which cannot distinguish a true draft from a dangling
     *       reference and buckets both as Not Requested. The refactor itself
     *       never creates orphans (withdraw clears {@code APPR_SEQ_NO} in the
     *       same tx); this only matches pre-existing dirty live-JEX data. F-2.</li>
     *   <li>{@code "01"} Approved — status {@code 'C'} OR {@code TRNF_YN = 'Y'}
     *       (dispatched batches are implicitly approved).</li>
     *   <li>{@code "02"} Rejected — status {@code 'R'}.</li>
     *   <li>{@code "03"} In Progress — status {@code 'I'}.</li>
     *   <li>{@code "04"} Pending — status {@code 'P'}.</li>
     * </ul>
     *
     * <p>Other / unknown codes pass-through as no-op (the OR
     * chain falls through and the whole {@code apprType} clause
     * evaluates true). Same defensive degradation Spring uses
     * elsewhere for unmapped enum values.
     */
    @Query("""
            SELECT s FROM ScdulPymnSumr s
            LEFT JOIN ExptResolMstr erm
                   ON erm.bizKey = s.bizKey AND erm.apprSeqNo = s.apprSeqNo
            WHERE s.bizKey             = :bizKey
              AND s.scdulPymnTypeCd    = :scdulPymnTypeCd
              AND s.regDtm BETWEEN :fromDtm AND :toDtm
              AND (:srchWdPattern IS NULL
                   OR LOWER(s.titl) LIKE :srchWdPattern
                   OR LOWER(COALESCE(s.apprSeqNo, '')) LIKE :srchWdPattern)
              AND (:trnfYn IS NULL OR s.trnfYn = :trnfYn)
              AND (
                    :apprType IS NULL
                 OR :apprType = ''
                 OR (:apprType = '00' AND (s.apprSeqNo IS NULL OR s.apprSeqNo = '' OR erm.apprTypeCd IS NULL))
                 OR (:apprType = '01' AND (s.trnfYn = 'Y'
                        OR FUNCTION('wb_get_appr_stts', erm.bizKey, erm.regId, erm.regId,
                                    s.apprSeqNo, erm.apprTypeCd) = 'C'))
                 OR (:apprType = '02'
                        AND FUNCTION('wb_get_appr_stts', erm.bizKey, erm.regId, erm.regId,
                                     s.apprSeqNo, erm.apprTypeCd) = 'R')
                 OR (:apprType = '03'
                        AND FUNCTION('wb_get_appr_stts', erm.bizKey, erm.regId, erm.regId,
                                     s.apprSeqNo, erm.apprTypeCd) = 'I')
                 OR (:apprType = '04'
                        AND FUNCTION('wb_get_appr_stts', erm.bizKey, erm.regId, erm.regId,
                                     s.apprSeqNo, erm.apprTypeCd) = 'P')
                 OR :apprType NOT IN ('00','01','02','03','04')
              )
            """)
    Page<ScdulPymnSumr> search(
            @Param("bizKey")           String bizKey,
            @Param("scdulPymnTypeCd")  String scdulPymnTypeCd,
            @Param("fromDtm")          String fromDtm,
            @Param("toDtm")            String toDtm,
            @Param("srchWdPattern")    String srchWdPattern,
            @Param("trnfYn")           String trnfYn,
            @Param("apprType")         String apprType,
            Pageable pageable);

    /**
     * Footer currency totals for the Scheduled-Transfer list, aggregated
     * across the <em>entire filtered result set</em> (no pagination) and
     * grouped by currency — the legacy {@code SCDUL_PYMN_SUMR_R003}
     * behaviour. The WHERE clause is a <strong>verbatim copy</strong> of
     * {@link #search}'s predicate (same tenant / type / date-range / search /
     * status filters, including the F-2 "Not Requested" leg that accepts both
     * true drafts and orphaned approval references {@code erm.apprTypeCd IS
     * NULL}), so the footer always sums exactly the rows the list filters —
     * never just the current page (F-1).
     *
     * <p>Returns {@code Object[]} rows {@code [ccyCd, sumAmount, docCount,
     * sumTransactionCount]} — {@code COUNT} yields {@code Long} which does not
     * fit the all-{@code BigDecimal} {@code CurrencyTotal} record, so the
     * service does the small Long→BigDecimal projection. {@code CCY_CD} is
     * coalesced to {@code ''} (matching the prior in-memory grouping) so a
     * null-currency batch lands in a single deterministic bucket.
     */
    @Query("""
            SELECT COALESCE(s.ccyCd, ''),
                   SUM(COALESCE(s.totalTranAmt, 0)),
                   COUNT(s),
                   SUM(COALESCE(s.totTrnsCnt, 0))
            FROM ScdulPymnSumr s
            LEFT JOIN ExptResolMstr erm
                   ON erm.bizKey = s.bizKey AND erm.apprSeqNo = s.apprSeqNo
            WHERE s.bizKey             = :bizKey
              AND s.scdulPymnTypeCd    = :scdulPymnTypeCd
              AND s.regDtm BETWEEN :fromDtm AND :toDtm
              AND (:srchWdPattern IS NULL
                   OR LOWER(s.titl) LIKE :srchWdPattern
                   OR LOWER(COALESCE(s.apprSeqNo, '')) LIKE :srchWdPattern)
              AND (:trnfYn IS NULL OR s.trnfYn = :trnfYn)
              AND (
                    :apprType IS NULL
                 OR :apprType = ''
                 OR (:apprType = '00' AND (s.apprSeqNo IS NULL OR s.apprSeqNo = '' OR erm.apprTypeCd IS NULL))
                 OR (:apprType = '01' AND (s.trnfYn = 'Y'
                        OR FUNCTION('wb_get_appr_stts', erm.bizKey, erm.regId, erm.regId,
                                    s.apprSeqNo, erm.apprTypeCd) = 'C'))
                 OR (:apprType = '02'
                        AND FUNCTION('wb_get_appr_stts', erm.bizKey, erm.regId, erm.regId,
                                     s.apprSeqNo, erm.apprTypeCd) = 'R')
                 OR (:apprType = '03'
                        AND FUNCTION('wb_get_appr_stts', erm.bizKey, erm.regId, erm.regId,
                                     s.apprSeqNo, erm.apprTypeCd) = 'I')
                 OR (:apprType = '04'
                        AND FUNCTION('wb_get_appr_stts', erm.bizKey, erm.regId, erm.regId,
                                     s.apprSeqNo, erm.apprTypeCd) = 'P')
                 OR :apprType NOT IN ('00','01','02','03','04')
              )
            GROUP BY COALESCE(s.ccyCd, '')
            ORDER BY COALESCE(s.ccyCd, '')
            """)
    List<Object[]> searchCurrencyTotals(
            @Param("bizKey")           String bizKey,
            @Param("scdulPymnTypeCd")  String scdulPymnTypeCd,
            @Param("fromDtm")          String fromDtm,
            @Param("toDtm")            String toDtm,
            @Param("srchWdPattern")    String srchWdPattern,
            @Param("trnfYn")           String trnfYn,
            @Param("apprType")         String apprType);

    /**
     * Same shape as {@link #search} but scoped to dispatched batches —
     * the Result list. Uses {@code trnf_yn = 'Y'} as the dispatched flag,
     * matching the legacy marker and the corrected Overseas Result query
     * ({@code OverseasPaymentRepository.buildResultSql}). Un-dispatched
     * batches still in the approval flow carry {@code trnf_yn} NULL/<>'Y'
     * and are excluded.
     *
     * <p>History: this previously gated on {@code trnf_dtm IS NOT NULL},
     * but {@code TRNF_DTM} is written only by the runtime dispatch path
     * ({@code InternalTransferDispatchService.dispatch} and the per-module
     * dispatch flows) and is never populated by demo seeds — so every
     * seeded {@code TRNF_YN='Y'} batch was silently filtered out and the
     * Result lists read empty. {@code trnf_yn = 'Y'} restores parity with
     * Overseas and the legacy behaviour.
     *
     * <p><b>F-1 fix (legacy parity):</b> the date range constrains
     * {@code TRNF_DTM} (transfer / dispatch datetime), NOT {@code REG_DTM}
     * (batch creation). Legacy {@code PymnHelper.getPymentResult} /
     * {@code getTotPymentResult} / {@code getTotRecPymentResult} all build
     * {@code AND LEFT(sps.trnf_dtm, 8) BETWEEN ? AND ?}. The earlier
     * {@code s.regDtm BETWEEN} predicate was a regression caused by the
     * legacy IDO's {@code TRNF_DTM AS REG_DTM} output alias being mistaken
     * for a genuine {@code REG_DTM} filter; against the live JEX DB it
     * dropped batches created before but dispatched inside the window (e.g.
     * a row registered 2025-12-18 but transferred 2026-01-07 vanished from a
     * January search) and skewed the currency-footer totals.
     *
     * <p><b>C2 fix (legacy status filter parity).</b> The Result list's
     * "Completed" / "In Progress" checkboxes drive {@code :procStts} (legacy
     * codes {@code 'C'} / {@code 'P'}), NOT the old {@code TRNF_YN}-shaped
     * {@code 'Y'} / {@code 'N'}. Legacy {@code PymnHelper.getPymentResult}
     * filters on the dispatch result via route-aware EXISTS over
     * {@code TB_PAY_FILE_MST.PROC_STAT} (file route — batch carries
     * {@code PAY_FILE_SEQ_NO}) / {@code TB_PAY_TARGET.PROC_STAT} (api route —
     * batch carries {@code PAY_TRAN_SEQ}), joined to
     * {@code SCDUL_PYMN_SUMR_DETL} by the shadow keys:
     * <ul>
     *   <li>{@code 'P'} In&nbsp;Progress — the batch has at least one line whose
     *       dispatch {@code PROC_STAT IN ('6','9')} (legacy P).</li>
     *   <li>{@code 'C'} Completed — the batch has at least one settled-success
     *       line ({@code PROC_STAT = '0'}, legacy C) AND no line still
     *       processing ({@code NOT} the {@code 'P'} predicate). A fully-failed
     *       batch (all {@code '3'}, no {@code '0'}) matches neither checkbox —
     *       legacy filter parity (legacy has no "Failed" checkbox).</li>
     *   <li>{@code null} / blank / any other value &rarr; no status filter
     *       (defensive degradation for a stale client, e.g. the legacy
     *       {@code 'Y'} / {@code 'N'} a not-yet-migrated module still sends).</li>
     * </ul>
     * The base {@code s.trnfYn = 'Y'} dispatched gate is unchanged. The footer
     * aggregate {@link #searchDispatchedCurrencyTotals} is deliberately NOT
     * status-filtered — legacy {@code SCDUL_PYMN_SUMR_R008} sums all dispatched
     * rows regardless of the checkboxes; callers pass {@code null} to it.
     */
    @Query(value = """
            SELECT s FROM ScdulPymnSumr s
            WHERE s.bizKey             = :bizKey
              AND s.scdulPymnTypeCd    = :scdulPymnTypeCd
              AND s.trnfDtm BETWEEN :fromDtm AND :toDtm
              AND s.trnfYn = 'Y'
              AND (:srchWdPattern IS NULL
                   OR LOWER(s.titl) LIKE :srchWdPattern
                   OR LOWER(COALESCE(s.apprSeqNo, '')) LIKE :srchWdPattern)
              AND (
                    :procStts IS NULL OR :procStts = ''
                 OR (:procStts = 'P' AND (
                        EXISTS (SELECT 1 FROM ScdulPymnSumrDetl pd, TbPayFileMst pm
                                 WHERE pd.bizKey = s.bizKey AND pd.scdulPymnNo = s.scdulPymnNo
                                   AND pm.bizKey = pd.bizKey AND pm.tranDt = pd.payTranDt
                                   AND pm.fileSeqNo = pd.payFileSeqNo
                                   AND pm.procStat IN ('6','9'))
                     OR EXISTS (SELECT 1 FROM ScdulPymnSumrDetl pd2, TbPayTarget pt
                                 WHERE pd2.bizKey = s.bizKey AND pd2.scdulPymnNo = s.scdulPymnNo
                                   AND pt.bizKey = pd2.bizKey AND pt.tranDt = pd2.payTranDt
                                   AND pt.tranSeq = pd2.payTranSeq
                                   AND pt.procStat IN ('6','9'))))
                 OR (:procStts = 'C'
                     AND (
                        EXISTS (SELECT 1 FROM ScdulPymnSumrDetl sd, TbPayFileMst sm
                                 WHERE sd.bizKey = s.bizKey AND sd.scdulPymnNo = s.scdulPymnNo
                                   AND sm.bizKey = sd.bizKey AND sm.tranDt = sd.payTranDt
                                   AND sm.fileSeqNo = sd.payFileSeqNo
                                   AND sm.procStat = '0')
                     OR EXISTS (SELECT 1 FROM ScdulPymnSumrDetl sd2, TbPayTarget st
                                 WHERE sd2.bizKey = s.bizKey AND sd2.scdulPymnNo = s.scdulPymnNo
                                   AND st.bizKey = sd2.bizKey AND st.tranDt = sd2.payTranDt
                                   AND st.tranSeq = sd2.payTranSeq
                                   AND st.procStat = '0'))
                     AND NOT (
                        EXISTS (SELECT 1 FROM ScdulPymnSumrDetl nd, TbPayFileMst nm
                                 WHERE nd.bizKey = s.bizKey AND nd.scdulPymnNo = s.scdulPymnNo
                                   AND nm.bizKey = nd.bizKey AND nm.tranDt = nd.payTranDt
                                   AND nm.fileSeqNo = nd.payFileSeqNo
                                   AND nm.procStat IN ('6','9'))
                     OR EXISTS (SELECT 1 FROM ScdulPymnSumrDetl nd2, TbPayTarget nt
                                 WHERE nd2.bizKey = s.bizKey AND nd2.scdulPymnNo = s.scdulPymnNo
                                   AND nt.bizKey = nd2.bizKey AND nt.tranDt = nd2.payTranDt
                                   AND nt.tranSeq = nd2.payTranSeq
                                   AND nt.procStat IN ('6','9'))))
                 OR :procStts NOT IN ('P','C')
              )
            """,
            countQuery = """
            SELECT COUNT(s) FROM ScdulPymnSumr s
            WHERE s.bizKey             = :bizKey
              AND s.scdulPymnTypeCd    = :scdulPymnTypeCd
              AND s.trnfDtm BETWEEN :fromDtm AND :toDtm
              AND s.trnfYn = 'Y'
              AND (:srchWdPattern IS NULL
                   OR LOWER(s.titl) LIKE :srchWdPattern
                   OR LOWER(COALESCE(s.apprSeqNo, '')) LIKE :srchWdPattern)
              AND (
                    :procStts IS NULL OR :procStts = ''
                 OR (:procStts = 'P' AND (
                        EXISTS (SELECT 1 FROM ScdulPymnSumrDetl pd, TbPayFileMst pm
                                 WHERE pd.bizKey = s.bizKey AND pd.scdulPymnNo = s.scdulPymnNo
                                   AND pm.bizKey = pd.bizKey AND pm.tranDt = pd.payTranDt
                                   AND pm.fileSeqNo = pd.payFileSeqNo
                                   AND pm.procStat IN ('6','9'))
                     OR EXISTS (SELECT 1 FROM ScdulPymnSumrDetl pd2, TbPayTarget pt
                                 WHERE pd2.bizKey = s.bizKey AND pd2.scdulPymnNo = s.scdulPymnNo
                                   AND pt.bizKey = pd2.bizKey AND pt.tranDt = pd2.payTranDt
                                   AND pt.tranSeq = pd2.payTranSeq
                                   AND pt.procStat IN ('6','9'))))
                 OR (:procStts = 'C'
                     AND (
                        EXISTS (SELECT 1 FROM ScdulPymnSumrDetl sd, TbPayFileMst sm
                                 WHERE sd.bizKey = s.bizKey AND sd.scdulPymnNo = s.scdulPymnNo
                                   AND sm.bizKey = sd.bizKey AND sm.tranDt = sd.payTranDt
                                   AND sm.fileSeqNo = sd.payFileSeqNo
                                   AND sm.procStat = '0')
                     OR EXISTS (SELECT 1 FROM ScdulPymnSumrDetl sd2, TbPayTarget st
                                 WHERE sd2.bizKey = s.bizKey AND sd2.scdulPymnNo = s.scdulPymnNo
                                   AND st.bizKey = sd2.bizKey AND st.tranDt = sd2.payTranDt
                                   AND st.tranSeq = sd2.payTranSeq
                                   AND st.procStat = '0'))
                     AND NOT (
                        EXISTS (SELECT 1 FROM ScdulPymnSumrDetl nd, TbPayFileMst nm
                                 WHERE nd.bizKey = s.bizKey AND nd.scdulPymnNo = s.scdulPymnNo
                                   AND nm.bizKey = nd.bizKey AND nm.tranDt = nd.payTranDt
                                   AND nm.fileSeqNo = nd.payFileSeqNo
                                   AND nm.procStat IN ('6','9'))
                     OR EXISTS (SELECT 1 FROM ScdulPymnSumrDetl nd2, TbPayTarget nt
                                 WHERE nd2.bizKey = s.bizKey AND nd2.scdulPymnNo = s.scdulPymnNo
                                   AND nt.bizKey = nd2.bizKey AND nt.tranDt = nd2.payTranDt
                                   AND nt.tranSeq = nd2.payTranSeq
                                   AND nt.procStat IN ('6','9'))))
                 OR :procStts NOT IN ('P','C')
              )
            """)
    Page<ScdulPymnSumr> searchDispatched(
            @Param("bizKey")           String bizKey,
            @Param("scdulPymnTypeCd")  String scdulPymnTypeCd,
            @Param("fromDtm")          String fromDtm,
            @Param("toDtm")            String toDtm,
            @Param("srchWdPattern")    String srchWdPattern,
            @Param("procStts")         String procStts,
            Pageable pageable);

    /**
     * Footer currency totals for the Payment RESULT list — aggregated across
     * the <em>entire filtered, dispatched result set</em> (no pagination) and
     * grouped by currency. The WHERE clause is a <strong>verbatim copy</strong>
     * of {@link #searchDispatched}'s predicate (same tenant / type /
     * {@code TRNF_DTM} window / {@code trnf_yn='Y'} dispatched gate / search),
     * so the footer always sums exactly the dispatched rows the Result list
     * draws from — never just the current page (F-1).
     *
     * <p><b>C2:</b> the {@code :trnfYn} parameter is retained for signature
     * stability but Result-list callers now pass {@code null} — the footer is
     * deliberately NOT status-filtered (legacy {@code SCDUL_PYMN_SUMR_R008} sums
     * all dispatched rows regardless of the Completed / In&nbsp;Progress
     * checkboxes). The "Completed/In Progress" filter applies to the row query
     * ({@link #searchDispatched}) only.
     *
     * <p>Legacy parity: IDO {@code SCDUL_PYMN_SUMR_R008}
     * ({@code PymnHelper.getTotRecPymentResult}), which runs
     * {@code SELECT SUM(TOTAL_TRAN_AMT), CCY_CD ... GROUP BY CCY_CD} over the
     * same dispatched predicate with no pagination. Shared by all four
     * {@code SCDUL_PYMN} Result services (Internal Transfer / Vendor / Expense /
     * Payroll) — they all call {@link #searchDispatched} for the page and this
     * for the footer.
     *
     * <p>Returns {@code Object[]} rows {@code [ccyCd, SUM(totalTranAmt),
     * COUNT(docs), SUM(totTrnsCnt)]} — {@code COUNT} yields {@code Long} which
     * does not fit the all-{@code BigDecimal} {@link
     * com.kosign.wecms.domain.payment.internaltransfer.dto.CurrencyTotal}
     * record, so the service does the small Long→BigDecimal projection (same
     * shape as the Scheduled {@link #searchCurrencyTotals}). {@code CCY_CD} is
     * coalesced to {@code ''} so a null-currency batch lands in a single
     * deterministic bucket, matching the prior in-memory grouping.
     */
    @Query("""
            SELECT COALESCE(s.ccyCd, ''),
                   SUM(COALESCE(s.totalTranAmt, 0)),
                   COUNT(s),
                   SUM(COALESCE(s.totTrnsCnt, 0))
            FROM ScdulPymnSumr s
            WHERE s.bizKey             = :bizKey
              AND s.scdulPymnTypeCd    = :scdulPymnTypeCd
              AND s.trnfDtm BETWEEN :fromDtm AND :toDtm
              AND s.trnfYn = 'Y'
              AND (:srchWdPattern IS NULL
                   OR LOWER(s.titl) LIKE :srchWdPattern
                   OR LOWER(COALESCE(s.apprSeqNo, '')) LIKE :srchWdPattern)
              AND (:trnfYn IS NULL OR s.trnfYn = :trnfYn)
            GROUP BY COALESCE(s.ccyCd, '')
            ORDER BY COALESCE(s.ccyCd, '')
            """)
    List<Object[]> searchDispatchedCurrencyTotals(
            @Param("bizKey")           String bizKey,
            @Param("scdulPymnTypeCd")  String scdulPymnTypeCd,
            @Param("fromDtm")          String fromDtm,
            @Param("toDtm")            String toDtm,
            @Param("srchWdPattern")    String srchWdPattern,
            @Param("trnfYn")           String trnfYn);

    // APPR_SEQ_NO is now allocated through the single
    // LegacyFunctionRepository.nextApprSeqNo funnel (V140 #1 — the GLOBAL
    // sq_appr_seq_no sequence shared across all Payment modules), replacing the
    // former per-tenant cross-table MAX+1 finder.

    /** Batch-fetch (apprSeqNo → ccyCd) pairs for the Approval Box list.
     *  Mirrors the equivalent query on {@code OvrsPymnSumrRepository} — both
     *  are merged by {@code ApprovalBoxService} so the correct amount column
     *  is picked regardless of payment type. */
    @Query("SELECT s.apprSeqNo, s.ccyCd FROM ScdulPymnSumr s " +
           "WHERE s.bizKey = :bizKey AND s.apprSeqNo IN :apprSeqNos")
    List<Object[]> findCcyCdByBizKeyAndApprSeqNos(
            @Param("bizKey")      String bizKey,
            @Param("apprSeqNos") Collection<String> apprSeqNos);

    /** Batch-fetch (apprSeqNo → ORG_FILE_NM) pairs for the Approval Box
     *  "File Name" column. Legacy {@code IDO.EXPT_RESOL_MSTR_R003.xml:146}
     *  reads {@code COALESCE(SCDUL_PYMN_SUMR.TITL, OVRS_PYMN_SUMR.TITL)}
     *  because legacy stored the uploaded file name in {@code TITL}; the
     *  refactor moved the file name to {@code ORG_FILE_NM} (TITL now holds
     *  the typed document title — see
     *  {@code InternalTransferService.requestApproval}), so the refactor
     *  equivalent is {@code COALESCE(SCDUL.ORG_FILE_NM, OVRS.ORG_FILE_NM)}.
     *  Matches the {@code orgFileNm} source already used by the Scheduled
     *  Transfer / Transfer Result list pages. The service merges this with
     *  the OVRS variant (SCDUL takes precedence). Separate from the CCY_CD
     *  fetch so the proven currency resolution is untouched. */
    @Query("SELECT s.apprSeqNo, s.orgFileNm FROM ScdulPymnSumr s " +
           "WHERE s.bizKey = :bizKey AND s.apprSeqNo IN :apprSeqNos")
    List<Object[]> findOrgFileNmByBizKeyAndApprSeqNos(
            @Param("bizKey")      String bizKey,
            @Param("apprSeqNos") Collection<String> apprSeqNos);

    // updateApprStts removed 2026-06-10: SCDUL_PYMN_SUMR has no APPR_STTS column
    // in the legacy JEX schema. The Approval Box no longer mirrors status onto the
    // payment batch — status is derived on read via the legacy wb_get_appr_stts(...)
    // function (LegacyFunctionRepository.getApprStts / apprSttsByApprSeqNos).

    /** Clear the approval stamp on the payment batch when its Approval Box
     *  request is withdrawn — nulls {@code APPR_SEQ_NO} so the row returns to
     *  Draft ({@code apprSeqNo IS NULL} → tab '00' in {@link #search}). Called
     *  by {@code ApprovalBoxService.withdraw} after
     *  {@code ApprovalBoxFanoutService.withdrawApproval} deletes the master.
     *  Locator is (bizKey, apprSeqNo). */
    @Modifying
    @Query("""
            UPDATE ScdulPymnSumr s
               SET s.apprSeqNo = null,
                   s.modId     = :modId,
                   s.modDtm    = :modDtm
             WHERE s.bizKey      = :bizKey
               AND s.apprSeqNo   = :apprSeqNo
            """)
    int clearApprSeqNo(@Param("bizKey")    String bizKey,
                       @Param("apprSeqNo") String apprSeqNo,
                       @Param("modId")     String modId,
                       @Param("modDtm")    String modDtm);
}
