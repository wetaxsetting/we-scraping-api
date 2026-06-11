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
 *       {@code TB_PAY_TARGET} for {@code PROC_STAT}/{@code PROC_DTTM},
 *       {@code TB_FIRM_TRAN} for {@code RS_CD}/{@code RS_MSG}.</li>
 *   <li><b>File path</b> (when {@code master.payFileSeqNo != null}):
 *       {@code TB_PAY_FILE_MST} for {@code PROC_STAT}/{@code PROC_DTTM}
 *       (master-level — every line in the batch shares the same status),
 *       {@code TB_PAY_FILE_DATA} for the per-line {@code RS_CD}.
 *       {@code RS_MSG} stays null on the File path (legacy parity).</li>
 * </ul>
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
        return resolveApiPath(bizKey, lines);
    }

    /* ───────────────── API path ───────────────── */

    private Map<String, DispatchResult> resolveApiPath(
            String bizKey, List<ScdulPymnSumrDetl> lines) {

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
                out.put(line.getScdulPymnDetlNo(), DispatchResult.EMPTY);
                continue;
            }
            TbPayTarget tpt = targetByKey.get(dt + "|" + seq);
            TbFirmTran  tft = firmByKey.get(dt + "|" + seq);
            String procStat = tpt != null
                    ? coalesceProcStat(tpt.getProcStat())
                    : null;
            String procDttm = tpt != null ? tpt.getProcDttm() : null;
            String rsCd  = tft != null ? trimToNull(tft.getRsCd())  : null;
            String rsMsg = tft != null ? trimToNull(tft.getRsMsg()) : null;
            out.put(line.getScdulPymnDetlNo(), new DispatchResult(
                    procStat,
                    ProcStatKey.forCode(procStat),
                    procDttm,
                    procDttm,                    // rsltDttm = procDttm on API path
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

        // Master-level PROC_STAT / PROC_DTTM from TB_PAY_FILE_MST.
        Optional<TbPayFileMst> mstOpt = tbPayFileMstRepository
                .findById(new TbPayFileMstId(tranDt, bizKey, fileSeqNo));
        String procStat = mstOpt
                .map(TbPayFileMst::getProcStat)
                .map(DispatchResultResolver::coalesceProcStat)
                .orElse(null);
        String procDttm = mstOpt.map(TbPayFileMst::getProcDttm).orElse(null);
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
                    procDttm,
                    procDttm,        // rsltDttm = procDttm on File path
                    rsCd,
                    null));           // rs_msg always null on File path
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
        String procDttm = mstOpt.map(TbPayFileMst::getProcDttm).orElse(null);
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
                    procDttm,
                    procDttm,
                    rsCd,
                    null));
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
