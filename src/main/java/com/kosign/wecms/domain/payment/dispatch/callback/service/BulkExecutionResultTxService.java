package com.kosign.wecms.domain.payment.dispatch.callback.service;

import com.kosign.wecms.domain.payment.dispatch.callback.event.PaymentDispatchResultFinalizedEvent;
import com.kosign.wecms.domain.payment.dispatch.constant.FirmBankingResultCd;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMstId;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileRslt;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.file.DepositData;
import com.kosign.wecms.domain.payment.dispatch.file.DepositTrailer;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmBatApiTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileRsltRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * C-4 · The <b>write half</b> of the 01302 execution-result callback — everything legacy
 * {@code FILE_DEPOSIT_TRANSFER_API2_act.jsp} does from {@code idoCon.beginTransaction()} at
 * {@code :291} onward, and nothing it does before.
 *
 * <h2>Why this is a separate bean</h2>
 * {@link BulkExecutionResultService#handle} is deliberately <b>not</b> {@code @Transactional} any more,
 * so it can SFTP-download and parse the result file without a database connection pinned to a network
 * round trip on a callback thread. Annotating this method on that class instead would not work: the
 * call would be a self-invocation, Spring's proxy would never see it, and the whole apply would silently
 * run outside a transaction — losing the all-or-nothing guarantee that makes a partially-applied result
 * file impossible. Same reason {@link DispatchApiTranAuditWriter} and {@link BulkExecutionInlineTxService}
 * are their own beans.
 *
 * <h2>What legacy does, and where the transaction sits</h2>
 * Legacy opens its transaction at {@code API2_act.jsp:291} — after the SFTP download ({@code :234}),
 * the remote archive move ({@code :235}), the disconnect ({@code :236}), the empty-file check
 * ({@code :249-254}) and the entire parse loop ({@code :262-290}). The 01302 audit row
 * ({@code :181-202}) and the {@code R006} file lookup ({@code :144-153}) are outside it too. Everything
 * from {@code :291} to the end is one transaction:
 * <ul>
 *   <li>{@code :293-331} — one {@code TB_FIRM_FILE_RSLT} insert per {@code D} line, each checked, a
 *       single failure rolling back every prior line. <b>Reproduced verbatim below</b>; the per-line
 *       loop belongs inside the transaction and stays there.</li>
 *   <li>{@code :334-348} — reflect each result onto {@code TB_FIRM_FILE_DATA} ({@code U001}).</li>
 *   <li>{@code :351-372} — advance {@code TB_FIRM_FILE_MST} {@code PROC_STEP 2→3 / PROC_STAT='0'} with
 *       the trailer aggregates ({@code U003}). Hoisted to the top here as the atomic claim — see below.</li>
 *   <li>{@code :375-387} — reflect onto {@code TB_PAY_FILE_DATA} ({@code U001}).</li>
 *   <li>{@code :389-446} — sibling gate ({@code R003}) then the final {@code TB_PAY_FILE_MST} status
 *       ({@code R005} + {@code U002}).</li>
 *   <li>{@code :451-462} — the audit row's success {@code RS_CD}/{@code RS_MSG}/{@code RS_DTTM}.</li>
 * </ul>
 *
 * <h2>The one deliberate divergence: an atomic claim</h2>
 * Legacy's {@code R006} lookup is outside its transaction, so two concurrent 01302 callbacks for the
 * same file both pass it and both apply — legacy has no claim. Matching the boundary without matching
 * the race is the point of
 * {@link TbFirmFileMstRepository#claimExecutionResult}: the {@code 2→3} advance runs <b>first</b>,
 * conditionally on the row still being at {@code '2'/'6'}, so the loser of the race writes nothing.
 * This is the same answer {@code H-4} (bulk reception) and {@code G2} (real-time dispatch) already give
 * on their {@code '9'→'6'} transitions. Documented as an improvement on legacy, not as parity.
 *
 * <h2>Rollback surface is unchanged by C-4</h2>
 * Before C-4 nothing was written inside the transaction ahead of the parse either — the only earlier
 * write is the audit row, and that already committed in its own {@code REQUIRES_NEW}. So moving the
 * download and parse out removed <i>zero</i> writes from the rollback set. A malformed file, a missing
 * download or a failed insert still leaves {@code TB_FIRM_FILE_MST} at {@code PROC_STEP='2'} and
 * replayable, and still leaves the audit trail intact.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkExecutionResultTxService {

    private final TbFirmFileMstRepository firmFileMstRepository;
    private final TbFirmFileDataRepository firmFileDataRepository;
    private final TbFirmFileRsltRepository firmFileRsltRepository;
    private final TbFirmBatApiTranRepository firmBatApiTranRepository;
    private final TbPayFileMstRepository payFileMstRepository;
    private final TbPayFileDataRepository payFileDataRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Apply one decoded execution-result file, atomically.
     *
     * @return {@code true} when this call claimed the file and applied the result; {@code false} when
     *         the file was no longer at {@code PROC_STEP='2'/PROC_STAT='6'} — a concurrent callback won
     *         the claim and this one must write nothing
     */
    @Transactional
    public boolean applyResult(BulkExecutionResultCommand cmd) {
        TbFirmFileMstId fileId = cmd.fileId();
        DepositTrailer t = cmd.parsed().trailer();

        // ── 1. ATOMIC CLAIM — MUST STAY THE FIRST STATEMENT ───────────────────────────────────────
        // Legacy's U003 advance (API2_act.jsp:351-372) hoisted to the front and made conditional. A
        // second concurrent 01302 for the same file updates 0 rows here and applies nothing; without
        // it, moving the R006 lookup outside the transaction (C-4) would import legacy's TOCTOU race.
        // Reordering is safe because this is one transaction — a later failure rolls the advance back
        // with everything else and the row returns to '2'/'6', replayable.
        int claimed = firmFileMstRepository.claimExecutionResult(
                fileId.getTranDt(), fileId.getBizKey(), fileId.getFileSeqNo(), fileId.getFirmFileNm(),
                count(t.succProcessingCnt()), amount(t.succProcessingAmt()),
                count(t.nonProcessingCnt()), amount(t.nonProcessingAmt()),
                cmd.dttm14());
        if (claimed == 0) {
            log.warn("01302 apply SKIPPED — TB_FIRM_FILE_MST is no longer at PROC_STEP='2'/PROC_STAT='6' "
                            + "for fileNm={} bizKey={} fileSeqNo={}; a concurrent callback claimed it "
                            + "first and has applied (or is applying) this result. Nothing written by "
                            + "this call — including the audit RS columns, which stay blank precisely so "
                            + "this callback is not recorded as having completed an apply it did not do.",
                    fileId.getFirmFileNm(), fileId.getBizKey(), fileId.getFileSeqNo());
            return false;
        }

        // ── 2. Re-read by PK, inside the transaction ──────────────────────────────────────────────
        // The orchestrator's copy was loaded without a transaction and is detached; save()-ing it would
        // be a merge that writes every column and blind-overwrites whatever changed in between. Read
        // the committed row instead — post-claim, so this is the state this transaction owns.
        TbFirmFileMst mst = firmFileMstRepository.findById(fileId).orElse(null);
        if (mst == null) {
            // Unreachable in practice: the claim above updated this exact PK in this transaction.
            // Defensive, because silently continuing would key every TB_FIRM_FILE_RSLT row on nulls.
            log.error("01302 apply ABORTED — TB_FIRM_FILE_MST vanished after a successful claim "
                    + "fileNm={} bizKey={}", fileId.getFirmFileNm(), fileId.getBizKey());
            return false;
        }
        String bizKey = mst.getBizKey();
        String fileSeqNo = mst.getFileSeqNo();
        String tranDt = cmd.tranDt();

        // ── 3. TB_FIRM_FILE_RSLT per D line + reflect onto the detail rows (legacy :293-348) ──────
        // H-1: the firm-data index is keyed on FILE_SEQ + prefix-stripped name (legacy U001), NOT the
        // exact callback name — resultFileNm is the bank's R_ name, the stored rows carry the sent S_ one.
        Map<Integer, TbFirmFileData> firmDataBySeq =
                indexFirmDataBySeq(tranDt, mst.getFileSeq(), cmd.resultFileNm());
        Map<Integer, TbPayFileData> payDataBySeq = indexPayDataBySeq(bizKey, tranDt, fileSeqNo);

        for (DepositData line : cmd.parsed().lines()) {
            int seq = parseInt(line.seq());
            String procRsltDiv = blankTo(line.processingResult(), "N");
            String rsCd = trim(line.responseCode());
            TbFirmFileData fd = firmDataBySeq.get(seq);

            TbFirmFileRslt rslt = new TbFirmFileRslt();
            rslt.setTranDt(tranDt);
            rslt.setBizKey(bizKey);
            rslt.setFirmFileNm(cmd.resultFileNm());
            rslt.setSeq(BigDecimal.valueOf(seq));
            rslt.setFileSeq(mst.getFileSeq());
            rslt.setTarBankCd(trim(line.depositBankCode()));
            rslt.setFincMdaDiv(blankTo(line.cardAccountType(), "Y"));
            rslt.setFincMdaNo(trim(line.depositAccountNumber()));
            rslt.setTrscCcyCd(blankTo(line.currencyCode(), "VND"));
            rslt.setTranAmt(amount(line.transferAmount()));
            rslt.setBankChnlDiv(trimOrNull(line.sameBankFlag()));
            rslt.setAntDivCd(trimOrNull(line.otherBankNetworkCode()));
            rslt.setSenderNm(trim(line.senderName()));
            rslt.setFincMdaOwnerNm(trim(line.receiverName()));
            rslt.setTarAcctRmrk(trim(line.depositAccountMemo()));
            rslt.setWdrlAcctRmrk(trim(line.withdrawalAccountMemo()));
            rslt.setProcRsltDiv(procRsltDiv);
            rslt.setRsCd(rsCd);
            rslt.setRcptBankCntryCd(trimOrNull(line.receiptBankCountryCode()));
            rslt.setRemitCcyCd(trimOrNull(line.remittanceCurrencyCode()));
            rslt.setRcptUseAddr(trim(line.rcptUseAddr()));
            rslt.setFeeBrdnClsCd(trimOrNull(line.feeBrdnClsCd()));
            // Town name (RCPT_USE_ADDR_SI): not present in the result file — carry from the matching
            // TB_FIRM_FILE_DATA row (audit M-11/E1). Null when the line has no firm-data match.
            rslt.setRcptUseAddrSi(fd != null ? fd.getRcptUseAddrSi() : null);
            firmFileRsltRepository.save(rslt);

            // Reflect onto TB_FIRM_FILE_DATA + then TB_PAY_FILE_DATA (legacy U001 chain via ORG_SEQ).
            if (fd != null) {
                fd.setProcRsltDiv(procRsltDiv);
                fd.setRsCd(rsCd);
                firmFileDataRepository.save(fd);

                TbPayFileData pd = payDataBySeq.get(intValue(fd.getOrgSeq()));
                if (pd != null) {
                    pd.setProcRsltDiv(procRsltDiv);
                    pd.setRsCd(rsCd);
                    payFileDataRepository.save(pd);
                }
            } else {
                log.warn("01302 result line seq={} has no TB_FIRM_FILE_DATA row fileNm={}",
                        seq, cmd.resultFileNm());
            }
        }

        // ── 4. Sibling gate + final pay-file status (legacy R003/R005 + PAY U002) ─────────────────
        // The claim in step 1 already advanced THIS file, so the sibling scan below sees it terminal.
        finalizePayFileStatus(tranDt, bizKey, fileSeqNo, cmd.dttm14());

        // ── 5. Audit success (legacy :451-462) ────────────────────────────────────────────────────
        // Re-read rather than merge the orchestrator's detached copy — same reasoning as step 2.
        firmBatApiTranRepository.findById(cmd.auditId()).ifPresentOrElse(audit -> {
            audit.setRsCd(FirmBankingResultCd.SUCC);
            audit.setRsMsg(FirmBankingResultCd.MSG_SUCC);
            audit.setRsDttm(cmd.dttm14());
            firmBatApiTranRepository.save(audit);
        }, () -> log.warn("01302 audit row not found for RS update tranSeq={} bizKey={} — the result "
                        + "WAS applied; only the audit outcome columns are missing",
                cmd.auditId().getTranSeq(), cmd.auditId().getBizKey()));

        log.info("01302 execution result applied fileNm={} bizKey={} lines={}",
                cmd.resultFileNm(), bizKey, cmd.parsed().lines().size());
        return true;
    }

    /**
     * Finalize {@code TB_PAY_FILE_MST} only when every sibling file of the same
     * {@code FILE_SEQ_NO} has reported (none still at {@code PROC_STAT='6'}):
     * {@code 0} all-success / {@code 3} all-fail / {@code 1} partial.
     */
    private void finalizePayFileStatus(String tranDt, String bizKey, String fileSeqNo, String dttm14) {
        List<TbFirmFileMst> siblings = firmFileMstRepository
                .findByTranDtAndBizKeyAndFileSeqNo(tranDt, bizKey, fileSeqNo);

        // ── Guard 1 (Option A) — defer while ANY sibling is still non-terminal ────────────────────
        // Legacy's barrier (IDO.TB_FIRM_FILE_MST_R003.xml:26-30, called at API2_act.jsp:389-405) tests
        // only PROC_STEP='0' AND PROC_STAT='6' — reception-pending. A sibling mid-EXECUTION ('1'/'6' or
        // '2'/'6') does not block it, and R005 then counts only terminal rows into TOT, so an
        // in-flight sibling is invisible to the tot==succ ratio and the parent finalises without it.
        //
        // DELIBERATE DIVERGENCE FROM LEGACY (approved 2026-07-31, same class as the ANT_DIV_CD
        // canonicalisation): defer on PROC_STAT='6' at ANY step. A file that has not reported cannot
        // be counted as success or failure, so the only correct action is to wait.
        boolean awaitingSiblings = siblings.stream()
                .anyMatch(f -> ProcStat.REQ.code().equals(f.getProcStat()));
        if (awaitingSiblings) {
            log.info("01302 — deferring pay-file finalize, sibling files still pending fileSeqNo={} states={}",
                    fileSeqNo, siblingStates(siblings));
            return;
        }

        long tot = siblings.stream().filter(f ->
                ProcStat.SUCC.code().equals(f.getProcStat()) || ProcStat.FAIL.code().equals(f.getProcStat())).count();
        long succ = siblings.stream().filter(f -> ProcStat.SUCC.code().equals(f.getProcStat())).count();
        long fail = siblings.stream().filter(f -> ProcStat.FAIL.code().equals(f.getProcStat())).count();

        // ── Guard 2 — never finalise on an empty terminal set ─────────────────────────────────────
        // THIS is the arithmetic that produced the two observed PROD false-"all success" rows, and it
        // needs no split batch at all: for a SINGLE file stranded at '2'/'6', tot=0 and succ=0, so
        // `tot == succ` is 0 == 0 — TRUE — and the parent is finalised to '0' (ALL SUCCESS) while its
        // only file never reported. An empty count satisfies the equality.
        //
        // Guard 1 already prevents reaching this line (a '6' sibling defers first), so this is
        // belt-and-braces — but the equality is the actual defect, it is one comparison, and any future
        // change to Guard 1's predicate would silently re-expose it. Keep both.
        if (tot == 0) {
            log.warn("01302 — refusing to finalize pay-file with NO terminal sibling fileSeqNo={} "
                            + "bizKey={} siblings={} states={} — parent left at PROC_STAT='6' "
                            + "(finalising here would report '0'/all-success for a file that never reported)",
                    fileSeqNo, bizKey, siblings.size(), siblingStates(siblings));
            return;
        }

        // Legacy partial status is the literal '1' (NOT a WCConst PROC_ST_* — see API2_act.jsp:421-429).
        String stat;
        if (tot == succ) {
            stat = ProcStat.SUCC.code();   // "0" — all success
        } else if (tot == fail) {
            stat = ProcStat.FAIL.code();   // "3" — all fail
        } else {
            stat = "1";                    // partial (mixed) — verbatim legacy value
        }

        payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(tranDt, bizKey, fileSeqNo)
                .ifPresent(pay -> {
                    pay.setProcStat(stat);
                    pay.setProcDttm(dttm14);
                    payFileMstRepository.save(pay);

                    // Single trigger for the HAENGSUNG Oracle dispatch-result
                    // write-back (Step 5). Fires once, only on actual pay-file
                    // finalization (all siblings reported). Deferred to
                    // AFTER_COMMIT by the subscriber, which filters to
                    // HAENGSUNG / ERP batches and is a no-op otherwise — so it
                    // is harmless for every other tenant/module.
                    eventPublisher.publishEvent(
                            new PaymentDispatchResultFinalizedEvent(bizKey, tranDt, fileSeqNo));
                });
    }

    /** {@code PROC_STEP/PROC_STAT} of each sibling, for the deferral logs. */
    private static String siblingStates(List<TbFirmFileMst> siblings) {
        return siblings.stream()
                .map(f -> f.getFirmFileNm() + "=" + f.getProcStep() + "/" + f.getProcStat())
                .collect(java.util.stream.Collectors.joining(" "));
    }

    /**
     * Index the sent file's detail lines by line {@code SEQ}, matching the way legacy
     * {@code TB_FIRM_FILE_DATA_U001} joins — prefix-stripped {@code FIRM_FILE_NM} + {@code FILE_SEQ}.
     *
     * <p><b>H-1:</b> {@code fileNm} here is the bank's {@code R_…} result name, while the stored rows
     * carry the sent {@code S_…} name, so an exact-name lookup returns nothing. See
     * {@link TbFirmFileDataRepository#findForExecutionResult}.
     */
    private Map<Integer, TbFirmFileData> indexFirmDataBySeq(String tranDt, String fileSeq, String fileNm) {
        Map<Integer, TbFirmFileData> map = new HashMap<>();
        for (TbFirmFileData fd : firmFileDataRepository.findForExecutionResult(tranDt, fileSeq, fileNm)) {
            map.put(intValue(fd.getSeq()), fd);
        }
        return map;
    }

    private Map<Integer, TbPayFileData> indexPayDataBySeq(String bizKey, String tranDt, String fileSeqNo) {
        Map<Integer, TbPayFileData> map = new HashMap<>();
        for (TbPayFileData pd : payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(bizKey, tranDt, fileSeqNo)) {
            map.put(intValue(pd.getSeq()), pd);
        }
        return map;
    }

    // ------------------------------------------------------------------ helpers

    /** Parse a fixed-width numeric/amount field (leading zeros, optional decimal point). */
    private static BigDecimal amount(String raw) {
        String s = trim(raw);
        return s.isEmpty() ? BigDecimal.ZERO : new BigDecimal(s);
    }

    private static BigDecimal count(String raw) {
        String s = trim(raw);
        return s.isEmpty() ? BigDecimal.ZERO : new BigDecimal(s);
    }

    private static int parseInt(String raw) {
        String s = trim(raw);
        return s.isEmpty() ? 0 : Integer.parseInt(s);
    }

    private static int intValue(BigDecimal v) {
        return v == null ? 0 : v.intValue();
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }

    private static String trimOrNull(String v) {
        String t = trim(v);
        return t.isEmpty() ? null : t;
    }

    private static String blankTo(String v, String dflt) {
        String t = trim(v);
        return t.isEmpty() ? dflt : t;
    }
}
