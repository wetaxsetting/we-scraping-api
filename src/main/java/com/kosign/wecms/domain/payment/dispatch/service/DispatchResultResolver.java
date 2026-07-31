package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTran;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMstId;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTarget;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared resolver that surfaces the per-line dispatch result
 * ({@code PROC_STAT} / {@code RS_CD} / {@code RS_MSG} / {@code PROC_DTTM} /
 * {@code RSLT_DTTM}) on the SCDUL_PYMN family's Result detail pages.
 *
 * <p>Replaces the legacy {@code SCDUL_PYMN_SUMR_R006} IDO query's LEFT
 * JOIN chain with three bulk lookups (one per dispatch path):
 * <ul>
 *   <li><b>API path</b> (when {@code master.payFileSeqNo == null}):
 *       {@code TB_PAY_TARGET} for {@code PROC_STAT}/{@code RSLT_DTTM},
 *       {@code TB_FIRM_TRAN} for {@code RS_CD}/{@code RS_MSG}.</li>
 *   <li><b>File path</b> (when {@code master.payFileSeqNo != null}):
 *       {@code TB_PAY_FILE_MST} for {@code PROC_STAT}/{@code RSLT_DTTM}
 *       (master-level — every line in the batch shares the same status),
 *       {@code TB_PAY_FILE_DATA} for the per-line {@code RS_CD}.
 *       {@code RS_MSG} stays null on the File path (legacy parity).</li>
 * </ul>
 *
 * <h2>The two datetimes are different columns (DRR-1, fixed 2026-07-30)</h2>
 *
 * <table>
 *   <caption>Legacy datetime sources</caption>
 *   <tr><th>Field</th><th>SCDUL family ({@code R006})</th><th>Overseas ({@code R007})</th></tr>
 *   <tr><td>{@code procDttm}</td>
 *       <td>{@code SPS.TRNF_DTM} (`:89`)</td>
 *       <td>{@code SPS.TRNF_DT} (`:75`)</td></tr>
 *   <tr><td>{@code rsltDttm}</td>
 *       <td>{@code TPFM.PROC_DTTM} / {@code TPT.PROC_DTTM} (`:96-100`)</td>
 *       <td>{@code TPFM.PROC_DTTM} (`:77`)</td></tr>
 * </table>
 *
 * <p>{@code procDttm} is the <b>master request</b> datetime — a plain master column, the same
 * value on every line of the batch, present even for a line that has no dispatch row.
 * {@code rsltDttm} is the <b>bank-side result</b> datetime read from the dispatch row. Until
 * 2026-07-30 both fields carried the dispatch row's {@code PROC_DTTM}, so they were always
 * equal and the request datetime was not surfaced per line.
 *
 * <p><b>Read-only mapping change.</b> Nothing about the stored
 * {@code TB_PAY_TARGET.PROC_DTTM} / {@code TB_PAY_FILE_MST.PROC_DTTM} values changed — this
 * resolver never writes. Only which column each wire field projects changed.
 *
 * <p>Returns {@link DispatchResult#EMPTY} for any line whose master /
 * detail shadow keys are null (batch hasn't been dispatched yet) or
 * whose corresponding {@code TB_PAY_*} row is missing (dispatch happened
 * but the bank-side daemon never ran).
 *
 * <p>One call per detail() request — call once with the full line list
 * to avoid N+1 lookups.
 */
@Service
@RequiredArgsConstructor
public class DispatchResultResolver {

    /** Coalesce {@code PROC_STAT} when the source row's column is null —
     *  legacy {@code SCDUL_PYMN_SUMR_R006.xml} coalesces to {@code '3'}
     *  (failed). Mirrors that exactly. */
    private static final String DEFAULT_PROC_STAT_ON_NULL = "3";

    private final TbPayTargetRepository   tbPayTargetRepository;
    private final TbPayFileMstRepository  tbPayFileMstRepository;
    private final TbPayFileDataRepository tbPayFileDataRepository;
    private final TbFirmTranRepository    tbFirmTranRepository;

    /**
     * Resolve dispatch results for every detail line in {@code lines}.
     * Returned map is keyed by {@code SCDUL_PYMN_DETL_NO} for direct
     * lookup inside the caller's {@code toResultLine} mapper.
     */
    @Transactional(readOnly = true)
    public Map<String, DispatchResult> resolve(
            ScdulPymnSumr master,
            List<ScdulPymnSumrDetl> lines) {

        if (lines == null || lines.isEmpty()) {
            return Collections.emptyMap();
        }
        String bizKey = master.getBizKey();
        if (master.getPayFileSeqNo() != null && !master.getPayFileSeqNo().isBlank()) {
            return resolveFilePath(bizKey, master, lines);
        }
        return resolveApiPath(bizKey, master, lines);
    }

    /* ───────────────── API path ───────────────── */

    private Map<String, DispatchResult> resolveApiPath(
            String bizKey, ScdulPymnSumr master, List<ScdulPymnSumrDetl> lines) {

        // DRR-1: PROC_DTTM is the MASTER's request datetime, unconditionally — legacy
        // SCDUL_PYMN_SUMR_R006.xml:89 `SPS.TRNF_DTM AS PROC_DTTM`. It is a plain master column,
        // independent of every join, so it is the same value on every line of the batch.
        String requestDttm = master.getTrnfDtm();

        // Group lines by the (pay_tran_dt, pay_tran_seq) shadow keys.
        // Most batches share one pay_tran_dt across all lines, but
        // historically dispatches can spread across two adjacent dates
        // when a batch crosses the cut-off-time boundary.
        Map<String, List<String>> seqsByDate = new HashMap<>();
        for (ScdulPymnSumrDetl line : lines) {
            String dt  = line.getPayTranDt();
            String seq = line.getPayTranSeq();
            if (dt == null || seq == null) continue;
            seqsByDate.computeIfAbsent(dt, k -> new java.util.ArrayList<>()).add(seq);
        }
        if (seqsByDate.isEmpty()) return Collections.emptyMap();

        // Bulk-load TB_PAY_TARGET + TB_FIRM_TRAN per distinct pay_tran_dt.
        Map<String, TbPayTarget> targetByKey = new HashMap<>();
        Map<String, TbFirmTran>  firmByKey   = new HashMap<>();
        for (Map.Entry<String, List<String>> e : seqsByDate.entrySet()) {
            String tranDt = e.getKey();
            List<String> seqs = e.getValue();
            for (TbPayTarget t : tbPayTargetRepository
                    .findByBizKeyAndTranDtAndTranSeqIn(bizKey, tranDt, seqs)) {
                targetByKey.put(tranDt + "|" + t.getTranSeq(), t);
            }
            for (TbFirmTran f : tbFirmTranRepository
                    .findByBizKeyAndTranDtAndTranSeqIn(bizKey, tranDt, seqs)) {
                firmByKey.put(tranDt + "|" + f.getTranSeq(), f);
            }
        }

        Map<String, DispatchResult> out = new HashMap<>(lines.size());
        for (ScdulPymnSumrDetl line : lines) {
            String dt  = line.getPayTranDt();
            String seq = line.getPayTranSeq();
            if (dt == null || seq == null) {
                // Legacy still emits PROC_DTTM here — it is a master column, not a joined one, so a
                // line with no shadow keys carries the batch's request datetime and nothing else.
                out.put(line.getScdulPymnDetlNo(), new DispatchResult(
                        null, null, requestDttm, null, null, null));
                continue;
            }
            TbPayTarget tpt = targetByKey.get(dt + "|" + seq);
            TbFirmTran  tft = firmByKey.get(dt + "|" + seq);
            String procStat = tpt != null
                    ? coalesceProcStat(tpt.getProcStat())
                    : null;
            // DRR-1: RSLT_DTTM is the API route's dispatch-row PROC_DTTM (legacy R006:99
            // `ELSE TPT.PROC_DTTM END AS RSLT_DTTM`) — a DIFFERENT column from PROC_DTTM above.
            String rsltDttm = tpt != null ? tpt.getProcDttm() : null;
            String rsCd  = tft != null ? trimToNull(tft.getRsCd())  : null;
            String rsMsg = tft != null ? trimToNull(tft.getRsMsg()) : null;
            out.put(line.getScdulPymnDetlNo(), new DispatchResult(
                    procStat,
                    ProcStatKey.forCode(procStat),
                    requestDttm,
                    rsltDttm,
                    rsCd,
                    rsMsg));
        }
        return out;
    }

    /* ───────────────── File path ───────────────── */

    private Map<String, DispatchResult> resolveFilePath(
            String bizKey, ScdulPymnSumr master, List<ScdulPymnSumrDetl> lines) {

        String tranDt    = master.getPayTranDt();
        String fileSeqNo = master.getPayFileSeqNo();
        if (tranDt == null || fileSeqNo == null) {
            return Collections.emptyMap();
        }

        // Master-level PROC_STAT / RSLT_DTTM from TB_PAY_FILE_MST.
        Optional<TbPayFileMst> mstOpt = tbPayFileMstRepository
                .findById(new TbPayFileMstId(tranDt, bizKey, fileSeqNo));
        String procStat = mstOpt
                .map(TbPayFileMst::getProcStat)
                .map(DispatchResultResolver::coalesceProcStat)
                .orElse(null);
        // DRR-1: RSLT_DTTM on the file route is TB_PAY_FILE_MST.PROC_DTTM (legacy R006:98
        // `THEN TPFM.PROC_DTTM`), while PROC_DTTM is the master's TRNF_DTM (legacy R006:89).
        String rsltDttm = mstOpt.map(TbPayFileMst::getProcDttm).orElse(null);
        String requestDttm = master.getTrnfDtm();
        String procStatKey = ProcStatKey.forCode(procStat);

        // Per-line RS_CD from TB_PAY_FILE_DATA. Build a map keyed by SEQ
        // (varchar) so the join against detail.pay_tran_seq is O(1).
        Map<String, String> rsCdBySeq = tbPayFileDataRepository
                .findByBizKeyAndTranDtAndFileSeqNo(bizKey, tranDt, fileSeqNo)
                .stream()
                .collect(Collectors.toMap(
                        d -> d.getSeq().toPlainString(),
                        d -> trimToNull(d.getRsCd()) == null ? "" : d.getRsCd().trim(),
                        (a, b) -> a));

        Map<String, DispatchResult> out = new HashMap<>(lines.size());
        for (ScdulPymnSumrDetl line : lines) {
            String seq  = line.getPayTranSeq();
            String rsCd = seq == null ? null : trimToNull(rsCdBySeq.get(seq));
            out.put(line.getScdulPymnDetlNo(), new DispatchResult(
                    procStat,
                    procStatKey,
                    requestDttm,
                    rsltDttm,
                    rsCd,
                    null));           // rs_msg always null on File path (legacy R006 `THEN NULL`)
        }
        return out;
    }

    /* ───────────────── Overseas (always File path) ───────────────── */

    /**
     * Overseas variant — Overseas Payment uses the same
     * {@code TB_PAY_FILE_MST} / {@code TB_PAY_FILE_DATA} dispatch
     * tables as the SCDUL_PYMN File path but is always SWIFT
     * (no API/TB_PAY_TARGET branch). Result key is the line's
     * {@code OVRS_PYMN_DETL_NO} (the natural detail PK on
     * {@link OvrsPymnSumrDetl}) so the caller can look up using the same
     * map shape as SCDUL_PYMN. The RS_CD join itself still keys on
     * {@code PAY_TRAN_SEQ} (the stamped TB_PAY_FILE_DATA.SEQ).
     *
     * <p><b>DRR-2 (fixed 2026-07-30):</b> unlike the SCDUL File path — where legacy
     * {@code R006} explicitly selects {@code NULL} for {@code RS_MSG} — Overseas
     * {@code R007:80} selects {@code TPFM.RS_MSG}, so a SWIFT rejection message reaches the
     * Result detail. Source precedence is unambiguous: the pay-file master is the only table
     * on this path carrying an {@code RS_MSG} column ({@code TB_PAY_FILE_DATA} has
     * {@code RS_CD} only, and there is no {@code TB_FIRM_TRAN} row without an API rail). A
     * genuinely null master message stays null; a blank one is normalised to null, matching
     * how the API path already treats {@code TFT.RS_MSG}.
     */
    @Transactional(readOnly = true)
    public Map<String, DispatchResult> resolveOverseas(
            OvrsPymnSumr master,
            List<OvrsPymnSumrDetl> lines) {

        if (lines == null || lines.isEmpty()) {
            return Collections.emptyMap();
        }
        String tranDt    = master.getPayTranDt();
        String fileSeqNo = master.getPayFileSeqNo();
        if (tranDt == null || fileSeqNo == null
                || tranDt.isBlank() || fileSeqNo.isBlank()) {
            return Collections.emptyMap();
        }
        String bizKey = master.getBizKey();

        Optional<TbPayFileMst> mstOpt = tbPayFileMstRepository
                .findById(new TbPayFileMstId(tranDt, bizKey, fileSeqNo));
        String procStat = mstOpt
                .map(TbPayFileMst::getProcStat)
                .map(DispatchResultResolver::coalesceProcStat)
                .orElse(null);
        // DRR-1: RSLT_DTTM = TPFM.PROC_DTTM (legacy OVRS_PYMN_SUMR_R007.xml:77), PROC_DTTM =
        // the master's TRNF_DT (legacy R007:75 `SPS.TRNF_DT AS PROC_DTTM`).
        String rsltDttm = mstOpt.map(TbPayFileMst::getProcDttm).orElse(null);
        String requestDttm = master.getTrnfDt();
        // DRR-2: RS_MSG comes from the pay-file MASTER (legacy R007:80 `TPFM.RS_MSG AS RS_MSG`).
        // Single source — Overseas has no API rail (so no TB_FIRM_TRAN) and TB_PAY_FILE_DATA has no
        // RS_MSG column, so there is no precedence to resolve. Absent master row → null.
        String rsMsg = mstOpt.map(TbPayFileMst::getRsMsg).map(DispatchResultResolver::trimToNull).orElse(null);
        String procStatKey = ProcStatKey.forCode(procStat);

        // Per-line RS_CD keyed by SEQ (varchar) — same shape as the
        // SCDUL_PYMN File path.
        Map<String, String> rsCdBySeq = tbPayFileDataRepository
                .findByBizKeyAndTranDtAndFileSeqNo(bizKey, tranDt, fileSeqNo)
                .stream()
                .collect(Collectors.toMap(
                        d -> d.getSeq().toPlainString(),
                        d -> trimToNull(d.getRsCd()) == null ? "" : d.getRsCd().trim(),
                        (a, b) -> a));

        Map<String, DispatchResult> out = new HashMap<>(lines.size());
        for (OvrsPymnSumrDetl line : lines) {
            String seq  = line.getPayTranSeq();
            String rsCd = seq == null ? null : trimToNull(rsCdBySeq.get(seq));
            String resultKey = line.getOvrsPymnDetlNo();
            if (resultKey == null) continue;
            out.put(resultKey, new DispatchResult(
                    procStat,
                    procStatKey,
                    requestDttm,
                    rsltDttm,
                    rsCd,
                    rsMsg));
        }
        return out;
    }

    /* ───────────────── Helpers ───────────────── */

    private static String coalesceProcStat(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT_PROC_STAT_ON_NULL;
        return raw.trim();
    }

    private static String trimToNull(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
