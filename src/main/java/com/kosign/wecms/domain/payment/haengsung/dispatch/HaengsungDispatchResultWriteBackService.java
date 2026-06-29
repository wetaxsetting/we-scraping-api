package com.kosign.wecms.domain.payment.haengsung.dispatch;

import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.payment.dispatch.callback.event.PaymentDispatchResultFinalizedEvent;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResult;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl;
import com.kosign.wecms.domain.payment.haengsung.oracle.HaengsungErpOracleRepository;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnDtlsRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnSumrRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HAENGSUNG Oracle dispatch-result write-back ({@code haengsung-dispatch-result-writeback},
 * Step 5 — refactor of legacy {@code ERP_0100_004}). After the firm-banking
 * dispatch engine produces the final per-transaction result in WeCMS, this
 * pushes that result back to the HAENGSUNG Oracle ERP so it can reconcile what
 * happened: the per-line success/fail ({@code TB_TRNS_DETAIL.END_GB} /
 * {@code PROC_STS} / {@code PROC_STS_MSG} / {@code TR_DATE}) and the closed
 * batch ({@code TB_TRNS_MAST.LAST_STATUS='52'}, {@code END_GB='1'}, counts +
 * amounts).
 *
 * <p><b>Single trigger.</b> {@link #onDispatchResultFinalized} is the ONE place
 * that triggers the Oracle dispatch-result sync. The dispatch/result lifecycle is
 * reused unchanged — {@code BulkExecutionResultService} merely publishes a generic
 * {@link PaymentDispatchResultFinalizedEvent} at its single finalization point
 * (when every sibling pay-file of a {@code FILE_SEQ_NO} has reported). No dispatch
 * logic is duplicated and no new dispatch flow is created.
 *
 * <p><b>Transaction model (no XA).</b> The listener runs {@code AFTER_COMMIT} of
 * the WeCMS dispatch-result transaction, so the result is already durably
 * committed before Oracle is touched. The order is, per batch:
 * <pre>
 *   WeCMS dispatch/result commit  (already done — AFTER_COMMIT)
 *        ↓
 *   Oracle result write-back  (TB_TRNS_MAST + TB_TRNS_DETAIL)
 *        ↓
 *   WeCMS summary ERP_PROC_CMPL_YN='Y'  (only after Oracle succeeds)
 * </pre>
 * Any Oracle failure is caught per batch and logged — the WeCMS dispatch result
 * is NEVER rolled back, and {@code ERP_PROC_CMPL_YN} stays {@code 'N'} so the
 * batch is retried later (a retry scheduler is deliberately out of Step-5 scope;
 * {@link #writeBackForFile} is idempotent and safe to re-invoke).
 *
 * <p><b>Idempotency / retry safety.</b> A re-run skips batches already pushed
 * ({@code ERP_PROC_CMPL_YN='Y'}); the Oracle master update is guarded
 * {@code WHERE LAST_STATUS='51'} so a second run finds {@code '52'} and updates
 * zero rows; detail/flag writes are value-stable. A batch with any
 * not-yet-complete line ({@code isAllDetailsComplete} fails) is skipped whole and
 * retried on a later trigger — exactly legacy {@code ERP_0100_004}.
 *
 * <p><b>Gating &amp; filtering.</b> The bean exists only when
 * {@code haengsung.oracle.enabled=true} — otherwise the event has no subscriber
 * (Oracle disabled → skipped). It additionally no-ops for non-HAENGSUNG tenants
 * and for batches not imported from ERP (blank {@code ERP_FILE_*}). The Oracle
 * lookup uses ONLY the imported ERP reference ({@code ERP_FILE_DATE} /
 * {@code ERP_FILE_NUM} / per-line {@code ERP_FILE_SEQ}) — never amount or account.
 *
 * <p>Supported payment types: Vendor / Expense / Payroll (all
 * {@code SCDUL_PYMN_SUMR}) + Overseas ({@code OVRS_PYMN_SUMR}). The two share the
 * classification + Oracle-write helpers; only the entity reads + amount column
 * ({@code TRAN_AMT} vs {@code TRNF_AMT}) differ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "haengsung.oracle", name = "enabled", havingValue = "true")
public class HaengsungDispatchResultWriteBackService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DTM_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** Legacy {@code ERP_0100_004} marks the WeCMS summary with user "SYSTEM". */
    private static final String SYSTEM_USER = "SYSTEM";

    private final HaengsungErpOracleRepository oracle;
    private final DispatchResultResolver dispatchResultResolver;
    private final ScdulPymnSumrRepository scdulSumrRepo;
    private final ScdulPymnSumrDetlRepository scdulDetlRepo;
    private final OvrsPymnSumrRepository ovrsSumrRepo;
    private final OvrsPymnDtlsRepository ovrsDetlRepo;
    private final BusiInfmRepository busiInfmRepository;

    /**
     * The single Oracle dispatch-result-sync trigger. Runs after the WeCMS
     * dispatch-result transaction commits (no XA); swallows any failure so the
     * committed result is never rolled back and a later trigger / retry can
     * re-sync.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDispatchResultFinalized(PaymentDispatchResultFinalizedEvent event) {
        try {
            writeBackForFile(event.bizKey(), event.tranDt(), event.fileSeqNo());
        } catch (RuntimeException ex) {
            log.warn("feature=haengsung-dispatch-writeback Oracle sync failed (dispatch result kept) "
                    + "bizKey={} tranDt={} fileSeqNo={}", event.bizKey(), event.tranDt(), event.fileSeqNo(), ex);
        }
    }

    /**
     * Push the final dispatch result of every HAENGSUNG ERP batch bound to the
     * just-finalized pay-file {@code (bizKey, tranDt, fileSeqNo)} back to Oracle.
     * Package-public + idempotent for direct unit testing and future retry use.
     * Per-batch failures are isolated (one bad batch never blocks the others) —
     * the legacy per-section rollback-and-continue model.
     */
    public void writeBackForFile(String bizKey, String tranDt, String fileSeqNo) {
        if (!isHaengsung(bizKey)) {
            log.debug("feature=haengsung-dispatch-writeback skip — non-HAENGSUNG tenant bizKey={}", bizKey);
            return;
        }

        for (ScdulPymnSumr s : scdulSumrRepo.findByBizKeyAndPayTranDtAndPayFileSeqNo(bizKey, tranDt, fileSeqNo)) {
            try {
                processScheduled(bizKey, s);
            } catch (RuntimeException ex) {
                log.warn("feature=haengsung-dispatch-writeback Oracle sync failed (batch kept) "
                        + "bizKey={} scdulPymnNo={}", bizKey, s.getScdulPymnNo(), ex);
            }
        }

        for (OvrsPymnSumr o : ovrsSumrRepo.findByBizKeyAndPayTranDtAndPayFileSeqNo(bizKey, tranDt, fileSeqNo)) {
            try {
                processOverseas(bizKey, o);
            } catch (RuntimeException ex) {
                log.warn("feature=haengsung-dispatch-writeback Oracle sync failed (batch kept) "
                        + "bizKey={} ovrsPymnNo={}", bizKey, o.getOvrsPymnNo(), ex);
            }
        }
    }

    /* ===================== Scheduled (Vendor / Expense / Payroll) ===================== */

    private void processScheduled(String bizKey, ScdulPymnSumr s) {
        if (!eligible(s.getErpFileDate(), s.getErpFileNum(), s.getTrnfYn(), s.getErpProcCmplYn())) {
            return;
        }
        List<ScdulPymnSumrDetl> details =
                scdulDetlRepo.findByBizKeyAndScdulPymnNo(bizKey, s.getScdulPymnNo(), Sort.unsorted());
        if (details.isEmpty()) {
            return;
        }
        Map<String, DispatchResult> results = dispatchResultResolver.resolve(s, details);

        List<Line> lines = new ArrayList<>(details.size());
        for (ScdulPymnSumrDetl d : details) {
            lines.add(new Line(d.getErpFileSeq(), d.getTranAmt(),
                    results.getOrDefault(d.getScdulPymnDetlNo(), DispatchResult.EMPTY)));
        }
        if (!isAllDetailsComplete(lines)) {
            log.info("feature=haengsung-dispatch-writeback skip — incomplete details, retry later "
                    + "bizKey={} scdulPymnNo={}", bizKey, s.getScdulPymnNo());
            return;
        }

        pushToOracle(s.getErpFileDate(), s.getErpFileNum(), s.getPayTranDt(), lines);
        scdulSumrRepo.markErpProcComplete(bizKey, s.getScdulPymnNo(), SYSTEM_USER, utcNow());
        log.info("feature=haengsung-dispatch-writeback synced (scheduled) bizKey={} scdulPymnNo={} "
                + "erpFileDate={} erpFileNum={}", bizKey, s.getScdulPymnNo(), s.getErpFileDate(), s.getErpFileNum());
    }

    /* ================================ Overseas ================================ */

    private void processOverseas(String bizKey, OvrsPymnSumr o) {
        if (!eligible(o.getErpFileDate(), o.getErpFileNum(), o.getTrnfYn(), o.getErpProcCmplYn())) {
            return;
        }
        List<OvrsPymnSumrDetl> details =
                ovrsDetlRepo.findByBizKeyAndOvrsPymnNoOrderByOvrsPymnDetlNoAsc(bizKey, o.getOvrsPymnNo());
        if (details.isEmpty()) {
            return;
        }
        Map<String, DispatchResult> results = dispatchResultResolver.resolveOverseas(o, details);

        List<Line> lines = new ArrayList<>(details.size());
        for (OvrsPymnSumrDetl d : details) {
            lines.add(new Line(d.getErpFileSeq(), d.getTrnfAmt(),
                    results.getOrDefault(d.getOvrsPymnDetlNo(), DispatchResult.EMPTY)));
        }
        if (!isAllDetailsComplete(lines)) {
            log.info("feature=haengsung-dispatch-writeback skip — incomplete details, retry later "
                    + "bizKey={} ovrsPymnNo={}", bizKey, o.getOvrsPymnNo());
            return;
        }

        pushToOracle(o.getErpFileDate(), o.getErpFileNum(), o.getPayTranDt(), lines);
        ovrsSumrRepo.markErpProcComplete(bizKey, o.getOvrsPymnNo(), SYSTEM_USER, utcNow());
        log.info("feature=haengsung-dispatch-writeback synced (overseas) bizKey={} ovrsPymnNo={} "
                + "erpFileDate={} erpFileNum={}", bizKey, o.getOvrsPymnNo(), o.getErpFileDate(), o.getErpFileNum());
    }

    /* ============================== Shared Oracle write ============================== */

    /**
     * Write the master close-out + per-line result for one batch to Oracle
     * (legacy {@code ERP_0100_004} STEPs 3 + 4). Throws on any Oracle failure so
     * the caller skips the {@code ERP_PROC_CMPL_YN} flip and the batch is retried.
     */
    private void pushToOracle(String erpFileDate, String erpFileNum, String payTranDt, List<Line> lines) {
        String nowVn = ZonedDateTime.now(VIETNAM_ZONE).format(DTM_FMT);

        long norCnt = 0;
        long errCnt = 0;
        BigDecimal norAmt = BigDecimal.ZERO;
        BigDecimal errAmt = BigDecimal.ZERO;
        String procDttm = null;
        for (Line l : lines) {
            if (procDttm == null && l.result().procDttm() != null) {
                procDttm = l.result().procDttm();
            }
            switch (countClassify(l.result().procStat(), l.result().rsCd())) {
                case SUCCESS -> { norCnt++; norAmt = norAmt.add(nz(l.amount())); }
                case ERROR   -> { errCnt++; errAmt = errAmt.add(nz(l.amount())); }
                case NEITHER -> { /* legacy R015 counts neither (e.g. partial-file PROC_STAT='1') */ }
            }
        }

        // END_DATETIME = the dispatch processing datetime (legacy END_DATETIME =
        // PROC_DTTM); fall back to now(VN) when the result carried none.
        String endDatetime = procDttm != null ? procDttm : nowVn;
        oracle.updateMasterDispatchResult(erpFileDate, erpFileNum, nowVn, endDatetime,
                BigDecimal.valueOf(norCnt), norAmt, BigDecimal.valueOf(errCnt), errAmt);

        // TR_DATE = the file batch transfer date (legacy R003/R004 file-path
        // TRAN_DT = the summary PAY_TRAN_DT), shared by every line of the file.
        for (Line l : lines) {
            if (l.erpFileSeq() == null) {
                continue; // defensive — an ERP-imported line always carries its FILE_SEQ
            }
            DetailResult dr = detailResult(l.result().procStat(), l.result().rsCd());
            oracle.updateDetailDispatchResult(erpFileDate, erpFileNum, l.erpFileSeq(),
                    dr.endGb(), dr.procSts(), l.result().rsMsg(), nowVn, payTranDt);
        }
    }

    /* ================================== Helpers ================================== */

    /** Shared eligibility gate — ERP batch (keys present) + dispatched + not yet pushed. */
    private static boolean eligible(String erpFileDate, String erpFileNum, String trnfYn, String erpProcCmplYn) {
        if (isBlank(erpFileDate) || isBlank(erpFileNum)) {
            return false;                       // non-ERP / Excel-upload batch
        }
        if (!"Y".equalsIgnoreCase(trim(trnfYn))) {
            return false;                       // not dispatched (TRNF_YN != 'Y')
        }
        return !"Y".equalsIgnoreCase(trim(erpProcCmplYn)); // already pushed → skip
    }

    /**
     * Legacy {@code isAllDetailsComplete}: every line must carry BOTH a
     * {@code PROC_STAT} and an {@code RS_CD} — otherwise the batch's dispatch
     * result is not yet finalized and the whole batch is skipped (retried later).
     */
    static boolean isAllDetailsComplete(List<Line> lines) {
        if (lines.isEmpty()) {
            return false;
        }
        for (Line l : lines) {
            if (isBlank(l.result().procStat()) || isBlank(l.result().rsCd())) {
                return false;
            }
        }
        return true;
    }

    /** Count classification — legacy {@code SCDUL_PYMN_SUMR_R015} per-line rule. */
    enum Cls { SUCCESS, ERROR, NEITHER }

    static Cls countClassify(String procStat, String rsCd) {
        if ("0".equals(procStat) && "0000".equals(rsCd)) {
            return Cls.SUCCESS;
        }
        if ("3".equals(procStat) || ("0".equals(procStat) && !"0000".equals(rsCd))) {
            return Cls.ERROR;
        }
        return Cls.NEITHER;
    }

    /** Per-line result mapping — legacy {@code ERP_0100_004} detail loop. */
    record DetailResult(String endGb, String procSts) { }

    static DetailResult detailResult(String procStat, String rsCd) {
        if ("3".equals(procStat)) {
            return new DetailResult("5", "0999");                 // failed (rejected by bank)
        }
        if ("0".equals(procStat) && "0000".equals(rsCd)) {
            return new DetailResult("8", rsCd);                   // success / complete
        }
        return new DetailResult("5", rsCd);                       // failed (RS_CD != 0000 / partial)
    }

    private boolean isHaengsung(String bizKey) {
        return busiInfmRepository.findById(bizKey)
                .filter(b -> "H".equals(b.getPrdtType()) && !"Y".equals(b.getDelYn()))
                .isPresent();
    }

    private static String utcNow() {
        return ZonedDateTime.now(ZoneOffset.UTC).format(DTM_FMT);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    /** One detail line's ERP key + amount + resolved dispatch result. */
    record Line(BigDecimal erpFileSeq, BigDecimal amount, DispatchResult result) { }
}
