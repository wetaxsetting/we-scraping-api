package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.constant.AntDivChannel;
import com.kosign.wecms.domain.payment.dispatch.constant.BulkTransferConstants;
import com.kosign.wecms.domain.payment.dispatch.constant.FirmBankingResultCd;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTran;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConfId;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.enums.ApiTranTp;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStep;
import com.kosign.wecms.domain.payment.dispatch.file.DepositData;
import com.kosign.wecms.domain.payment.dispatch.file.DepositHeader;
import com.kosign.wecms.domain.payment.dispatch.file.DepositTrailer;
import com.kosign.wecms.domain.payment.dispatch.file.FixedLengthCodec;
import com.kosign.wecms.domain.payment.dispatch.file.VietnameseText;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmBatApiTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmConfRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.BulkValidationCounts;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.sftp.SftpClient;
import com.kosign.wecms.domain.payment.dispatch.sftp.SftpProperties;
import com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs301Request;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs301Response;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;

/**
 * Bulk reception — refactor of legacy {@code FILE_DEPOSIT_TRANSFER_BATCH1}.
 *
 * <p>Confirmed TOBE (Mr. Kang 2026-06-06): BATCH1 becomes a <b>request-time</b>
 * flow. {@link #receive} reproduces the BATCH1 core: validate the pay-file
 * target → resolve {@code TB_FIRM_CONF} → build the fixed-length file
 * ({@link FixedLengthCodec}) → insert {@code TB_FIRM_FILE_MST}
 * ({@code PROC_STEP=0/PROC_STAT=6}) + {@code TB_FIRM_FILE_DATA} → SFTP-upload →
 * audit {@code 00301} (before the call) → {@link #issueReception} (EFS 00301).
 *
 * <h2>Legacy-fidelity guarantees</h2>
 * <ul>
 *   <li><b>Audit before the external call</b> — the {@code TB_FIRM_BAT_API_TRAN}
 *       row is written before {@code issueReception}, then updated with the result.</li>
 *   <li><b>No unsafe state advance</b> — {@code TB_FIRM_FILE_MST} is created at
 *       {@code PROC_STEP=0} and is NEVER advanced here (the {@code 0→1} advance
 *       belongs to the 01301 callback). On any SFTP/API failure the file and the
 *       pay-file row are set to {@code PROC_STAT='3'} (fail); nothing moves
 *       forward.</li>
 *   <li><b>Idempotency / duplicate protection (H-4)</b> — the pay-file target is processed only
 *       from {@code PROC_STAT='9'} (WAIT), and the {@code '9' → '6'} transition is taken by a
 *       <b>single conditional UPDATE</b> ({@link
 *       com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository#claimForReception})
 *       committed in its own transaction <b>before</b> any file is built, uploaded or dispatched. The
 *       database therefore picks exactly one winner per logical file; a concurrent driver moves 0 rows
 *       and returns before the egress boundary. Mirrors legacy {@code R001} selecting only WAIT plus
 *       the {@code R004} fail skip, with the legacy {@code U001} write ({@code PROC_STAT='6'}) made
 *       conditional.
 *       <p><b>Superseded claim — do not restore.</b> This bullet previously stated that reading the
 *       WAIT state was itself the protection. It was not: the read and the write were separate
 *       statements, so two drivers could both observe {@code '9'}, both write {@code '6'} (the same
 *       value, so nothing conflicted), and both reach the SFTP upload and EFS 00301. The
 *       deterministic {@code TB_FIRM_FILE_MST} PK could not compensate — inside
 *       {@code buildAndDispatchFile}'s transaction the insert is deferred to commit, which happens
 *       <i>after</i> both egress calls. See
 *       {@code docs/architecture/bulk-dispatch-concurrency-fix.md}.</li>
 * </ul>
 *
 * <h2>Transaction model (Item 2 / audit M-7 — per-split-file commit)</h2>
 * {@code receive()} is <b>not</b> a single transaction. It runs after the payment commit
 * (via {@link DispatchAutoReceiveRegistrar}) with no outer transaction; the pre-flight
 * validation, the operating-hours gate, and the once-before-loop {@code '6'} mark each
 * commit on their own. <b>Each split file is then built + uploaded + dispatched inside its
 * own {@code REQUIRES_NEW} transaction</b> ({@link #buildAndDispatchFile} via
 * {@link #runInNewTransaction}), exactly like the legacy per-file auto-commit. So an
 * unexpected throw while building split file N can never roll back split files
 * {@code 1..N-1} that were already committed <b>and already SFTP-uploaded / 00301-dispatched
 * to the bank</b> (the corruption the single-transaction design risked). A per-file EFS/SFTP
 * {@link BusinessException} is still caught, recorded as a committed FAIL state, and not
 * rethrown (legacy parity), which trips the sibling fail-stop for the remaining files.
 *
 * <h2>Branch-split into multiple files (C5, ported 2026-06-13)</h2>
 * When {@code TB_FIRM_CONF.BAT_TRAN_BRANCH_DIV='Y'} and the detail count exceeds
 * {@code BAT_TRAN_BRANCH_LIMIT}, the request is split into
 * {@code ceil(reqCnt / limit)} files of at most {@code limit} rows each — the
 * refactor of legacy {@code calculateBatchDivision} + the per-file dispatch loop in
 * {@code FILE_DEPOSIT_TRANSFER_BATCH1}. Each split file is fully independent: its own
 * consecutive file round, {@code FIRM_FILE_NM}, {@code TB_FIRM_FILE_MST} /
 * {@code TB_FIRM_FILE_DATA} rows (line sequence restarts at 1 per file), fixed-length
 * file, SFTP upload, 00301 audit, and EFS 301 call. The non-split path is the special
 * case {@code loopCnt=1} and is byte-for-byte unchanged. A per-file failure marks the
 * shared pay-file FAIL and stops the batch (legacy {@code R004} skip parity — once the
 * pay-file is FAIL, every remaining sibling is skipped). The whole {@code receive()}
 * is one transaction, so a build that throws before the EFS leg rolls back entirely —
 * no partial/oversized files.
 *
 * <h2>Deferred (NOT in this phase — documented blockers)</h2>
 * The full BATCH1 pre-flight is intentionally out of scope: channel-mix validation
 * (EBANK/VCB/CITAD/NAPAS rules), the operating-hours gate
 * ({@code TB_BATCH_SCHEDULE_*}), and Slack alerting ({@code MsgSendUtil}). These port
 * in a later phase.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkReceptionService {

    private static final DateTimeFormatter DTTM14_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter TRAN_TM_FMT = DateTimeFormatter.ofPattern("HHmmss");
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Legacy {@code Deposit_Data} address field is 180 bytes (post-2025.10.29 layout). */
    private static final int RCPT_USE_ADDR_MAX_BYTES = 180;

    /** Legacy {@code WCConst.NON_PROCESSING} — detail rows start un-processed. */
    private static final String NON_PROCESSING = "N";

    /**
     * Cap for a generated {@code TB_PAY_FILE_MST.RS_MSG} (H-3). Matches the mapped column width
     * ({@code TbPayFileMst.rsMsg}, {@code length = 200}); a failure message that overflowed the column
     * would fail its own UPDATE and leave the row in exactly the stranded state it is meant to clear.
     */
    private static final int RS_MSG_MAX = 200;

    /** Legacy no-INFO-row message (FILE_DEPOSIT_TRANSFER_BATCH1.java:289, verbatim). */
    private static final String NO_CRITERIA_MSG =
            "No data to perform bulk transfer batch. Please check TB_BATCH_SCHEDULE_INFO.";

    private final EfsApiClient efsApiClient;
    private final EfsCryptoService efsCryptoService;
    private final VanApiKeyService vanApiKeyService;
    private final EfsProperties efsProperties;
    private final ObjectMapper objectMapper;

    private final TbPayFileMstRepository payFileMstRepository;
    private final TbPayFileDataRepository payFileDataRepository;
    private final TbFirmConfRepository firmConfRepository;
    private final TbFirmFileMstRepository firmFileMstRepository;
    private final TbFirmFileDataRepository firmFileDataRepository;
    private final TbFirmBatApiTranRepository firmBatApiTranRepository;
    private final DispatchKeyGenerator dispatchKeyGenerator;
    private final FixedLengthCodec fixedLengthCodec;
    private final SftpClient sftpClient;
    private final SftpProperties sftpProperties;
    /** BATCH1 pre-flight network/currency validator (legacy
     *  FILE_DEPOSIT_TRANSFER_BATCH1:113-266, Gate A). */
    private final BulkTransferValidator bulkTransferValidator;
    private final OpsAlertService opsAlertService;
    private final OperatingHoursService operatingHoursService;
    /** H-4: owns the atomic pre-egress claim, in its own committed transaction. */
    private final BulkDispatchClaimTxService bulkDispatchClaimTxService;
    /** Dispatch-time blocked-char guard + NAPAS→internal bank-code resolver (audit task-44508, P0). */
    private final com.kosign.wecms.domain.payment.common.PaymentBlockedCharGuard blockedCharGuard;
    private final com.kosign.wecms.domain.payment.common.OutBankCdNormalizer outBankCdNormalizer;
    /** Item 2 (audit M-7): commits each split file in its own REQUIRES_NEW transaction. */
    private final PlatformTransactionManager transactionManager;

    /**
     * Request-time bulk reception of one pay-file target: build + upload the
     * fixed-length file and issue 00301.
     *
     * @param tranDt    transaction date (YYYYMMDD) — pay-file key
     * @param bizKey    tenant business key
     * @param fileSeqNo bulk file sequence within the tenant/date
     */
    public void receive(String tranDt, String bizKey, String fileSeqNo) {
        // 1. Load + validate the pay-file target (legacy R001 selects PROC_STAT='9').
        TbPayFileMst pay = payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(tranDt, bizKey, fileSeqNo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "pay-file target not found: tranDt=" + tranDt + " bizKey=" + bizKey + " fileSeqNo=" + fileSeqNo));

        // Fast-path early-out only — NOT the duplicate guard. Mirrors legacy R001 selecting WAIT
        // rows, and avoids doing validation / operating-hours work for a file that is already done.
        // The authoritative duplicate decision is the atomic claim at step 5; this check is racy by
        // construction and must never be relied on for safety.
        if (!ProcStat.WAIT.code().equals(pay.getProcStat())) {
            log.info("receive() no-op — pay-file not WAIT (procStat={}) tranDt={} bizKey={} fileSeqNo={}",
                    pay.getProcStat(), tranDt, bizKey, fileSeqNo);
            return;
        }

        // 2. Resolve dispatch config (VAN_ORG_CD / FILE_TYPE / branch split) — legacy TB_FIRM_CONF_R001.
        TbFirmConf conf = firmConfRepository
                .findById(new TbFirmConfId(bizKey, pay.getOutBankCd(), pay.getWdrlActNo()))
                .orElseThrow(() -> new IllegalStateException(
                        "TB_FIRM_CONF missing for bizKey=" + bizKey + " outBankCd=" + pay.getOutBankCd()
                                + " wdrlActNo=" + pay.getWdrlActNo()));

        // 3. Load detail rows (legacy R001 orders by SEQ; sort so split windows are deterministic).
        List<TbPayFileData> details = new ArrayList<>(
                payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(bizKey, tranDt, fileSeqNo));
        if (details.isEmpty()) {
            throw new IllegalStateException("pay-file has no detail rows: fileSeqNo=" + fileSeqNo);
        }
        details.sort(Comparator.comparing(TbPayFileData::getSeq));

        int reqCnt = details.size();

        // 3a. BATCH1 pre-flight network/currency validation (legacy
        //     FILE_DEPOSIT_TRANSFER_BATCH1:113-266, Gate A) — runs BEFORE branch-split,
        //     the PROC_STAT='6' mark, and any TB_FIRM_* / SFTP / EFS work. On failure,
        //     the whole file is marked FAIL ('3') with the legacy RS_MSG and skipped
        //     (legacy `continue`). The operating-hours / cut-off gate (legacy :269-447,
        //     TB_BATCH_SCHEDULE_INFO/DTL) runs next in step 3b.
        BulkValidationCounts counts =
                payFileDataRepository.aggregateValidationCounts(bizKey, tranDt, fileSeqNo);
        Optional<String> vrfiMsg = bulkTransferValidator.validate(pay, counts);
        if (vrfiMsg.isPresent()) {
            pay.setProcStat(ProcStat.FAIL.code());
            pay.setRsMsg(vrfiMsg.get());
            payFileMstRepository.save(pay);
            log.warn("00301 pre-flight validation failed — file marked FAIL bizKey={} fileSeqNo={} reason={}",
                    bizKey, fileSeqNo, vrfiMsg.get());
            // Ops alert (legacy FILE_DEPOSIT_TRANSFER_BATCH1:249 "이체파일 Batch1 검증 오류 발생").
            opsAlertService.notify(conf.getVanOrgCd(),
                    "이체파일 Batch1 검증 오류 발생"
                            + "\nTRAN_DT : " + tranDt
                            + "\nBIZ_KEY : " + bizKey
                            + "\nFILE_SEQ_NO : " + fileSeqNo
                            + "\nOUT_BANK_CD : " + pay.getOutBankCd()
                            + "\nBANK_PRTN_ID : " + pay.getBankPrtnId()
                            + "\nREQ_CNT : " + reqCnt
                            + "\nVRFIMSG : " + vrfiMsg.get());
            return;
        }

        // 3b. Operating-hours gate (legacy FILE_DEPOSIT_TRANSFER_BATCH1:269-448,
        //     TB_BATCH_SCHEDULE_INFO/DTL, EXE_DIV). Runs BEFORE the PROC_STAT='6' mark.
        //     CUTOFF / NO_CRITERIA → FAIL the pay-file + ops alert + return (legacy exeErrYN
        //     block :411-443). SKIP ('N' before start, or bank-uses-network with no matching
        //     channel) → leave the file WAIT for a later run, no write, no alert (legacy
        //     silent `continue`). Present channels are the file's distinct 1-char ANT_DIV_CD
        //     mapped to names via the verified R005 classification (E/V/C/N/S).
        OperatingHoursDecision oh = operatingHoursService.evaluateBulk(pay.getOutBankCd(), presentChannels(details));
        if (oh == OperatingHoursDecision.SKIP) {
            log.info("00301 skipped — outside operating window (before start / channel not open) "
                    + "bizKey={} fileSeqNo={}", bizKey, fileSeqNo);
            return;
        }
        if (oh == OperatingHoursDecision.CUTOFF || oh == OperatingHoursDecision.NO_CRITERIA) {
            boolean cutoff = oh == OperatingHoursDecision.CUTOFF;
            // Legacy leaves vrfi_msg empty for cutoff; sets the "No data…" text for no-criteria.
            String rsMsg = cutoff ? "" : NO_CRITERIA_MSG;
            pay.setProcStat(ProcStat.FAIL.code());
            pay.setRsMsg(rsMsg);
            payFileMstRepository.save(pay);
            log.warn("00301 blocked ({}) bizKey={} fileSeqNo={} — pay-file FAIL",
                    cutoff ? "cutoff" : "no-criteria", bizKey, fileSeqNo);
            opsAlertService.notify(conf.getVanOrgCd(),
                    (cutoff ? "대량이체 Batch 거래 마감." : "대량이체 Batch 수행 기준 없음.")
                            + "\nTRAN_DT : " + tranDt
                            + "\nBIZ_KEY : " + bizKey
                            + "\nFILE_SEQ_NO : " + fileSeqNo
                            + "\nMSG : " + rsMsg);
            return;
        }

        // 4. Branch-split division (legacy calculateBatchDivision): split into
        //    ceil(reqCnt/limit) files of `chunkSize` rows when BAT_TRAN_BRANCH_DIV='Y'
        //    and reqCnt > limit; otherwise one file of all rows.
        int[] division = calculateBatchDivision(conf.getBatTranBranchDiv(), reqCnt, conf.getBatTranBranchLimit());
        int loopCnt = division[0];
        int chunkSize = division[1];

        LocalDateTime now = LocalDateTime.now(ICT);
        String dttm14 = now.format(DTTM14_FMT);
        String tranTm = now.format(TRAN_TM_FMT);

        // 5. H-4 · ATOMIC CLAIM — the pre-egress duplicate decision.
        //
        // This is the same legacy U001 transition (PROC_STAT '9' -> '6', legacy
        // FILE_DEPOSIT_TRANSFER_BATCH1:453-454) but expressed as a CONDITIONAL update, and committed
        // in its own short transaction before any file is built, uploaded, or 00301-dispatched.
        //
        // It replaces a read-then-check-then-write that could not stop a double dispatch: the WAIT
        // check at the top of this method and this write were separate statements, so two drivers
        // (the auto-receive after-commit hook and a StrandedRecoveryService sweep, or two nodes)
        // could both read '9', both pass the check, both write '6' — the same value, so nothing
        // conflicted — and both then upload the file and issue EFS 00301. The deterministic
        // TB_FIRM_FILE_MST PK did not help: inside buildAndDispatchFile's transaction the insert is
        // deferred to commit, which happens AFTER the upload and after the 301 call.
        //
        // Now the database picks exactly one winner. The loser sees 0 rows updated and returns here,
        // before the egress boundary. Everything above this line (validation FAIL, cut-off FAIL,
        // operating-hours SKIP) still runs while the row is WAIT and is unaffected — in particular
        // SKIP must NOT claim, so the file stays '9' and a later run can pick it up.
        if (!bulkDispatchClaimTxService.claimForReception(bizKey, tranDt, fileSeqNo, dttm14)) {
            log.info("00301 no-op — pay-file claimed by another driver (concurrent hand-off or "
                    + "recovery sweep) bizKey={} tranDt={} fileSeqNo={}", bizKey, tranDt, fileSeqNo);
            return;
        }
        // Mirror the committed transition onto the in-memory row. `pay` was read before the claim, so
        // without this a later failBoth()/save(pay) would write back the stale '9'.
        pay.setProcStat(ProcStat.REQ.code());
        pay.setProcDttm(dttm14);

        // H-3 / N-3 — everything below this point runs with the claim ALREADY COMMITTED at '6'.
        //
        // Before this guard, any RuntimeException escaping the post-claim region rolled back the
        // per-file transaction (taking its failBoth write with it) but NOT the claim, and then escaped
        // receive() to be swallowed by DispatchAutoReceiveRegistrar's catch-and-log. The pay-file was
        // left at '6' — neither '9' (WAIT) nor '3' (FAIL) — and every recovery and monitoring query
        // selected only '9', so nothing ever saw it again. The class Javadoc's "on any SFTP/API failure
        // the pay-file is set to PROC_STAT='3'" was therefore not true for non-BusinessException
        // failures. H-4's claim made this worse, not better: the '6' is now committed earlier and
        // cannot roll back with the file work.
        //
        // RuntimeException only — never Throwable/Error. An OutOfMemoryError or a ThreadDeath must not
        // be converted into a business FAIL by a handler that may itself be unable to run.
        // The counter is a mutable accumulator, NOT a return value. A return value is only assigned on
        // normal completion, so when buildAndDispatchAllFiles THROWS — which is the whole reason this
        // guard exists — the count of files already at the bank would be lost and the failure would be
        // reported as "0 dispatched". That is worse than silence: it tells the operator nothing left
        // the building when a file already did, which invites a blind re-drive and a double-send.
        // Incremented inside the loop, so it is correct at every throw point.
        AtomicInteger dispatchedFiles = new AtomicInteger();
        try {
            buildAndDispatchAllFiles(
                    pay, conf, tranDt, bizKey, fileSeqNo, details, loopCnt, chunkSize, reqCnt,
                    dttm14, tranTm, dispatchedFiles);
        } catch (RuntimeException e) {
            // The split-file transaction (if one was open) has already rolled back, so this write must
            // come from its own transaction against the pay-file's key — see
            // BulkDispatchClaimTxService.failStrandedPayFile.
            int dispatched = dispatchedFiles.get();
            String rsMsg = strandedFailMessage(e, dispatched);
            log.error("00301 unexpected failure after the claim — recording pay-file FAIL "
                            + "bizKey={} tranDt={} fileSeqNo={} filesAlreadyDispatched={}",
                    bizKey, tranDt, fileSeqNo, dispatched, e);
            bulkDispatchClaimTxService.failStrandedPayFile(
                    bizKey, tranDt, fileSeqNo, rsMsg, nowDttm14());
            opsAlertService.notify(conf.getVanOrgCd(), strandedAlert(
                    tranDt, bizKey, fileSeqNo, pay, reqCnt, dispatched, loopCnt, rsMsg));
            // Not rethrown: the pay-file now carries a terminal state, so there is nothing for the
            // caller to recover and rethrowing would only be re-swallowed by the registrar.
        }
    }

    /**
     * Build + dispatch every split file (legacy per-file loop). Extracted from {@link #receive} so the
     * post-claim region has a single entry point that the H-3 guard can wrap.
     *
     * @param dispatchedFiles accumulator, incremented as each split file is successfully uploaded and
     *                        {@code 00301}-accepted. It is an <b>out-parameter rather than a return
     *                        value</b> on purpose: a return value is only assigned when this method
     *                        completes normally, so on the throw path — the one the caller's guard
     *                        exists for — the count would be lost and a partially-dispatched batch
     *                        would be reported as "0 dispatched". A value {@code > 0} alongside a
     *                        failure means money has already moved for those files and the batch is
     *                        NOT safe to blindly re-drive.
     */
    private void buildAndDispatchAllFiles(TbPayFileMst pay, TbFirmConf conf, String tranDt, String bizKey,
                                          String fileSeqNo, List<TbPayFileData> details,
                                          int loopCnt, int chunkSize, int reqCnt,
                                          String dttm14, String tranTm,
                                          AtomicInteger dispatchedFiles) {
        // 6. Base file round (legacy R002 MAX+1). Computed once; split file i uses base+i so the
        //    rounds stay consecutive without depending on intra-transaction flush visibility.
        int baseRound = nextFileRound(tranDt, pay.getOutBankCd(), pay.getBankPrtnId());

        if (loopCnt > 1) {
            log.info("00301 branch-split bizKey={} fileSeqNo={} reqCnt={} -> {} files of <= {} rows",
                    bizKey, fileSeqNo, reqCnt, loopCnt, chunkSize);
        }
        // ── PHASE A ──────────────────────────────────────────────────────────────────────────────
        // Persist EVERY split file's TB_FIRM_FILE_MST + TB_FIRM_FILE_DATA rows at
        // PROC_STEP='0'/PROC_STAT='6', encode each file's bytes, and COMMIT — all before any
        // external call. Nothing here touches SFTP or the gateway.
        //
        // This restores the legacy ordering. FILE_DEPOSIT_TRANSFER_BATCH1 commits its firm-file row
        // at :696-697 and only THEN uploads (:700) and calls 00301 (:721); the refactor had inverted
        // it, committing last. Three things follow from putting the commit first:
        //
        //   * The sibling barrier sees the complete group, every time. BulkExecutionResultService's
        //     defer-while-any-sibling-is-pending guard counts rows that EXIST
        //     (TbFirmFileMstRepository.findAllSiblings). With the commit last, file 2's row did not
        //     exist while file 1 was being uploaded and dispatched, so the barrier counted a short
        //     group and could pass on an incomplete batch. Legacy never had this hole.
        //   * A 01301 can no longer arrive before its own row is committed. Legacy's callback looks
        //     the row up by FIRM_FILE_NM (API1:151-155) and rejects with CD_DB_ERR when it finds
        //     none; with the commit last that was reachable for a fast gateway.
        //   * No SFTP or HTTP runs inside a transaction. Phase B opens a transaction per write and
        //     closes it before the next external call.
        //
        // Phase A is ONE transaction covering all N files, which is what makes the barrier guarantee
        // hold: the group becomes visible atomically rather than file by file. Legacy commits per
        // file, so this is marginally stronger than legacy — deliberately, and in the direction of
        // the barrier it protects.
        List<PreparedFile> prepared;
        try {
            prepared = runInNewTransaction(status -> persistAllFileRows(
                    pay, conf, tranDt, bizKey, fileSeqNo, details, loopCnt, chunkSize, baseRound, dttm14));
        } catch (BusinessException e) {
            // Blocked character reached dispatch (task-44508). PRE-EGRESS and GROUP-WIDE: the
            // transaction rolled back, so no firm-file row exists and nothing was sent. A terminal
            // '3' on the pay-file is the whole truth.
            //
            // Group-wide is the correction here. The guard used to run inside each split file's own
            // transaction, so file 1 could be dispatched and file 2 rejected — a partially-sent batch
            // over a validation failure that was knowable before the first byte left. That split was
            // accidental, not designed: legacy's Gate A (FILE_DEPOSIT_TRANSFER_BATCH1:113-266)
            // validates the WHOLE pay file BEFORE calculateBatchDivision splits it, and fails the
            // pay-file as a unit. Validating the group in phase A restores that shape.
            recordGroupRejection(pay, conf, tranDt, bizKey, fileSeqNo, reqCnt, e, dttm14);
            return;
        }

        // ── PHASE B ──────────────────────────────────────────────────────────────────────────────
        // Per file: write the staging file, SFTP-upload it, audit 00301, call 00301. No transaction
        // spans any of these; each DB write commits on its own.
        for (PreparedFile f : prepared) {
            if (!dispatchOneFile(pay, conf, tranDt, bizKey, fileSeqNo, f, dttm14, tranTm)) {
                // Legacy parity (FILE_DEPOSIT_TRANSFER_BATCH1:741-768): a phase-B failure marks the
                // firm file AND the pay file FAIL, then throws out of the per-file loop entirely —
                // remaining split files are never built and never sent. The R004 guard at the head of
                // legacy's loop (:478-498) is a second, redundant barrier it never reaches. Breaking
                // here is the same outcome.
                log.warn("00301 branch-split aborted after a failed file — pay-file FAIL bizKey={} "
                        + "fileSeqNo={} filesAlreadyDispatched={}", bizKey, fileSeqNo, dispatchedFiles.get());
                // Ops alert (legacy FILE_DEPOSIT_TRANSFER_BATCH1:751 "이체파일 Batch1 처리중 오류 발생").
                // filesAlreadyDispatched is appended (H-3 requirement 9): with a branch-split batch the
                // operator must be able to see that files 1..N-1 already reached the bank, so the batch
                // reads as partially dispatched rather than as a clean retryable failure.
                opsAlertService.notify(conf.getVanOrgCd(),
                        "이체파일 Batch1 처리중 오류 발생"
                                + "\nTRAN_DT : " + tranDt
                                + "\nBIZ_KEY : " + bizKey
                                + "\nFILE_SEQ_NO : " + fileSeqNo
                                + "\nOUT_BANK_CD : " + pay.getOutBankCd()
                                + "\nBANK_PRTN_ID : " + pay.getBankPrtnId()
                                + "\nREQ_CNT : " + reqCnt
                                + "\nFILES_DISPATCHED : " + dispatchedFiles.get() + " / " + prepared.size());
                break;
            }
            dispatchedFiles.incrementAndGet();
        }
    }

    /**
     * One split file after phase A: its rows are committed at {@code PROC_STEP='0'/PROC_STAT='6'} and
     * its fixed-length bytes are encoded, but nothing has been sent.
     *
     * <p>{@code fileBytes} is carried in memory rather than re-encoded in phase B so the encode — the
     * step that rejects an over-width field ({@link FixedLengthCodec}, the retained
     * overflow-rejects-instead-of-writing-"null" divergence) — happens while the transaction is still
     * open and can still roll back. Encoding is pure local computation with no external effect, so
     * there is no reason to defer it past the commit: doing so would turn "over-width field ⇒ nothing
     * committed" into "over-width field ⇒ committed rows that must now be marked FAIL".
     */
    private record PreparedFile(String fileName, String fileSeq, int reqCnt, BigDecimal reqAmt,
                                byte[] fileBytes, List<FixedLengthCodec.NonAsciiField> nonAscii) {}

    /**
     * Bounded, non-sensitive failure summary for {@code TB_PAY_FILE_MST.RS_MSG} (H-3 requirement 4).
     *
     * <p>Carries the exception <b>type</b> and the operational context only. It deliberately does not
     * include {@code e.getMessage()}: messages on this path can embed a file name, a gateway body, an
     * account number or a serialised payload, and {@code RS_MSG} is read back into the UI. The full
     * exception (with message and stack) goes to the log, which is the correct place for it.
     *
     * <p>Truncated to {@value #RS_MSG_MAX} characters so a long exception class name can never fail the
     * UPDATE on column width — a failed failure-write would put the row straight back into the strand
     * this method exists to prevent.
     */
    private static String strandedFailMessage(RuntimeException e, int dispatchedFiles) {
        String msg = "00301 dispatch failed unexpectedly: " + e.getClass().getSimpleName()
                + " (files dispatched before failure: " + dispatchedFiles + ")";
        return msg.length() <= RS_MSG_MAX ? msg : msg.substring(0, RS_MSG_MAX);
    }

    /** Ops-alert body for an unexpected post-claim failure. Mirrors the legacy Batch1 error alert shape. */
    private static String strandedAlert(String tranDt, String bizKey, String fileSeqNo, TbPayFileMst pay,
                                        int reqCnt, int dispatchedFiles, int loopCnt, String rsMsg) {
        return "이체파일 Batch1 처리중 예외 발생 (pay-file FAIL 처리)"
                + "\nTRAN_DT : " + tranDt
                + "\nBIZ_KEY : " + bizKey
                + "\nFILE_SEQ_NO : " + fileSeqNo
                + "\nOUT_BANK_CD : " + pay.getOutBankCd()
                + "\nBANK_PRTN_ID : " + pay.getBankPrtnId()
                + "\nREQ_CNT : " + reqCnt
                + "\nFILES_DISPATCHED : " + dispatchedFiles + " / " + loopCnt
                + "\nMSG : " + rsMsg;
    }

    /** Current ICT timestamp for a terminal write made outside the main flow's captured {@code dttm14}. */
    private String nowDttm14() {
        return LocalDateTime.now(ICT).format(DTTM14_FMT);
    }

    /**
     * <b>PHASE A</b> — persist and encode every split file, in the caller's single transaction.
     *
     * <p>For each of the {@code loopCnt} windows: insert {@code TB_FIRM_FILE_MST}
     * ({@code PROC_STEP='0'/PROC_STAT='6'}) + one {@code TB_FIRM_FILE_DATA} per row (line sequence
     * restarts at 1 per file), and encode the fixed-length bytes. No SFTP, no gateway, no audit row.
     *
     * <p>A throw from anywhere in here rolls back the whole group — which is the point: at this stage
     * nothing has been sent, so discarding the rows is the correct and complete response. The blocked
     * character guard is allowed to propagate for exactly that reason (see the caller's catch).
     *
     * @return one {@link PreparedFile} per split file, in dispatch order
     */
    private List<PreparedFile> persistAllFileRows(TbPayFileMst pay, TbFirmConf conf, String tranDt,
                                                  String bizKey, String fileSeqNo,
                                                  List<TbPayFileData> details, int loopCnt, int chunkSize,
                                                  int baseRound, String dttm14) {
        List<PreparedFile> prepared = new ArrayList<>(loopCnt);
        for (int i = 0; i < loopCnt; i++) {
            // F — legacy parity (FILE_DEPOSIT_TRANSFER_BATCH1:502-528): partition split files by the
            // SEQ *value* range [startSeq, endSeq] (legacy START_SEQ/END_SEQ, advanced by chunkSize),
            // NOT by list index. The refactor allocates SEQ as a dense 1..N run so this yields the same
            // windows as index slicing; for migrated rows with SEQ gaps or a non-1 start it matches
            // legacy's value-range membership (rows outside every window are simply not sent).
            long startSeq = (long) i * chunkSize + 1;
            long endSeq   = (long) (i + 1) * chunkSize;
            List<TbPayFileData> window = new ArrayList<>();
            for (TbPayFileData d : details) {
                long s = d.getSeq().longValue();
                if (s >= startSeq && s <= endSeq) {
                    window.add(d);
                }
            }
            // M-4: clamp to the bank's legal round range before formatting. Legacy re-runs R002 per
            // file, so each split file is independently wrapped (99 Woori / 999 others).
            String fileSeq = String.format("%03d", wrapFileRound(baseRound + i, pay.getOutBankCd()));
            prepared.add(persistOneFileRows(pay, conf, tranDt, bizKey, fileSeqNo, window, fileSeq, dttm14));
        }
        return prepared;
    }

    /**
     * Phase A for ONE split file: {@code TB_FIRM_FILE_MST} + {@code TB_FIRM_FILE_DATA} rows and the
     * encoded bytes. Shared by the single-file and branch-split paths (the single-file path is just
     * {@code window == all details}).
     *
     * <p>Throws rather than returning a failure flag — every failure reachable here is pre-egress and
     * group-wide, and the caller's rollback is the right handling for all of them.
     */
    private PreparedFile persistOneFileRows(TbPayFileMst pay, TbFirmConf conf, String tranDt, String bizKey,
                                            String fileSeqNo, List<TbPayFileData> window, String fileSeq,
                                            String dttm14) {
        int reqCnt = window.size();
        BigDecimal reqAmt = window.stream()
                .map(TbPayFileData::getTranAmt)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Filename S_{VAN}_{TYPE}_{round}_{date} — the per-file round makes each split file's
        // FIRM_FILE_NM (and therefore its MST/DATA primary keys) unique.
        // G — legacy parity (FILE_DEPOSIT_TRANSFER_BATCH1:533,540): FILE_TYPE is used RAW (no trim).
        // Safe: TB_FIRM_CONF.FILE_TYPE is CHAR(3) with exactly-3-char values (SHB/VCB/WRB) on both
        // DBs, so raw == trimmed for all real data (no padding). FILE_TYPE is NOT in the 1000-byte
        // record (not a DepositHeader field), so byte-identity of the file is unaffected.
        String fileName = "S_" + conf.getVanOrgCd() + "_" + conf.getFileType() + "_" + fileSeq + "_" + tranDt;

        // Insert TB_FIRM_FILE_MST at PROC_STEP=0 / PROC_STAT=6.
        TbFirmFileMst mst = new TbFirmFileMst();
        mst.setTranDt(tranDt);
        mst.setBizKey(bizKey);
        mst.setFileSeqNo(fileSeqNo);
        mst.setFirmFileNm(fileName);
        mst.setOutBankCd(pay.getOutBankCd());
        mst.setBankPrtnId(conf.getBankPrtnId());
        mst.setVanOrgCd(conf.getVanOrgCd());
        mst.setFileType(conf.getFileType());   // G — raw FILE_TYPE (legacy BATCH1:548), no trim
        mst.setWdrlActNo(pay.getWdrlActNo());
        mst.setFundTp(pay.getFundTp());
        mst.setTranTp(pay.getTranTp());
        mst.setFileSeq(fileSeq);
        mst.setProcStep(ProcStep.REQ.code());
        mst.setProcStat(ProcStat.REQ.code());
        mst.setReqCnt(BigDecimal.valueOf(reqCnt));
        mst.setReqAmt(reqAmt);
        mst.setRegDttm(dttm14);
        firmFileMstRepository.save(mst);

        // P0 (audit task-44508): final blocked-char safety boundary before the
        // bank file is built. Defense-in-depth on top of the save-path guard.
        // Rules are keyed on the internal BANK_CD, so resolve the stored NAPAS
        // OUT_BANK_CD back first.
        String internalBankCd = outBankCdNormalizer.toInternalBankCd(pay.getOutBankCd());

        // Insert TB_FIRM_FILE_DATA rows + build the fixed-length D records (line seq restarts at 1).
        //
        // X-1: the blocked-char guard below throws BusinessException, and it is now allowed to
        // propagate out of phase A. The caller catches it once for the whole group, rolls back every
        // file's rows, and records a single terminal '3' on the pay-file — matching legacy Gate A
        // (FILE_DEPOSIT_TRANSFER_BATCH1:113-266), which validates the entire pay file BEFORE
        // calculateBatchDivision splits it and fails the pay-file as a unit. Catching it per file here
        // (the previous shape) let file 1 dispatch and file 2 be rejected over a condition that was
        // knowable before the first byte left.
        List<DepositData> dataLines = new ArrayList<>(reqCnt);
        int seq = 0;
        for (TbPayFileData d : window) {
            seq++;
            BigDecimal lineSeq = BigDecimal.valueOf(seq);

            TbFirmFileData fd = new TbFirmFileData();
            fd.setTranDt(tranDt);
            fd.setFileSeqNo(fileSeqNo);
            fd.setFirmFileNm(fileName);
            fd.setSeq(lineSeq);
            fd.setBizKey(bizKey);
            fd.setFileSeq(fileSeq);
            fd.setOrgSeq(d.getSeq());
            fd.setTarBankCd(d.getTarBankCd());
            fd.setFincMdaDiv(d.getFincMdaDiv());
            fd.setFincMdaNo(d.getFincMdaNo());
            fd.setTrscCcyCd(d.getTrscCcyCd());
            fd.setTranAmt(d.getTranAmt());
            fd.setBankChnlDiv(d.getBankChnlDiv());
            fd.setAntDivCd(d.getAntDivCd());
            fd.setSenderNm(VietnameseText.replaceTone(d.getSenderNm()));
            fd.setFincMdaOwnerNm(VietnameseText.replaceTone(d.getFincMdaOwnerNm()));
            fd.setTarAcctRmrk(VietnameseText.replaceTone(d.getTarAcctRmrk()));
            fd.setWdrlAcctRmrk(VietnameseText.replaceTone(d.getWdrlActRmrk()));
            // P0 (audit task-44508): final blocked-char check on the bank-bound
            // recipient name + remarks (already tone-stripped above).
            blockedCharGuard.assertCleanForDispatch(internalBankCd, java.util.List.of(
                    com.kosign.wecms.domain.payment.common.PaymentBlockedCharGuard.field(
                            com.kosign.wecms.domain.payment.validation.FieldCd.RECIPIENT_NAME, fd.getFincMdaOwnerNm()),
                    com.kosign.wecms.domain.payment.common.PaymentBlockedCharGuard.field(
                            com.kosign.wecms.domain.payment.validation.FieldCd.REMARKS, fd.getTarAcctRmrk()),
                    com.kosign.wecms.domain.payment.common.PaymentBlockedCharGuard.field(
                            com.kosign.wecms.domain.payment.validation.FieldCd.REMARKS, fd.getWdrlAcctRmrk())));
            fd.setProcRsltDiv(NON_PROCESSING);
            fd.setRcptBankCntryCd(d.getRcptBankCntryCd());
            fd.setRemitCcyCd(d.getRemitCcyCd());
            fd.setRcptUseAddr(VietnameseText.replaceTone(byteTruncate(d.getRcptUseAddr(), RCPT_USE_ADDR_MAX_BYTES)));
            fd.setFeeBrdnClsCd(d.getFeeBrdnClsCd());
            // Town name (RCPT_USE_ADDR_SI): DB-only carry from the pay-file line — raw (already
            // ≤35 chars), NOT tone-stripped/truncated (it is not in the fixed-length file). Audit M-11/E1.
            fd.setRcptUseAddrSi(d.getRcptUseAddrSi());
            firmFileDataRepository.save(fd);

            dataLines.add(new DepositData(
                    String.valueOf(seq),                                  // seq
                    d.getFincMdaNo(),                                     // depositAccountNumber
                    d.getTrscCcyCd(),                                     // currencyCode
                    plain(d.getTranAmt()),                                // transferAmount (verbatim)
                    d.getBankChnlDiv(),                                   // sameBankFlag
                    d.getAntDivCd(),                                      // otherBankNetworkCode
                    "C".equals(trim(d.getFincMdaDiv())) ? "N" : "Y",      // cardAccountType (legacy mapping)
                    d.getTarBankCd(),                                     // depositBankCode
                    VietnameseText.replaceTone(d.getSenderNm()),          // senderName
                    VietnameseText.replaceTone(d.getFincMdaOwnerNm()),    // receiverName
                    VietnameseText.replaceTone(d.getTarAcctRmrk()),       // depositAccountMemo
                    VietnameseText.replaceTone(d.getWdrlActRmrk()),       // withdrawalAccountMemo
                    "",                                                   // processingResult (send file: blank)
                    "",                                                   // responseCode (send file: blank)
                    d.getRcptBankCntryCd(),                               // receiptBankCountryCode
                    d.getRemitCcyCd(),                                    // remittanceCurrencyCode
                    d.getFeeBrdnClsCd(),                                  // feeBrdnClsCd
                    VietnameseText.replaceTone(byteTruncate(d.getRcptUseAddr(), RCPT_USE_ADDR_MAX_BYTES)) // rcptUseAddr
            ));
        }

        DepositHeader header = new DepositHeader(
                pay.getOutBankCd(), conf.getBankPrtnId(), tranDt, pay.getWdrlActNo(),
                pay.getFundTp(), pay.getTranTp(), fileSeq);
        DepositTrailer trailer = new DepositTrailer(
                String.valueOf(reqCnt), plain(reqAmt), "0", "0", "0", "0");

        // ENCODE IN PHASE A — deliberately, and this is the reason the bytes are carried forward in
        // PreparedFile rather than re-encoded later.
        //
        // The realistic throw is fixedLengthCodec.encode raising IllegalArgumentException on an
        // over-width field (e.g. a recipient name longer than its 150-byte slot, which the codec
        // correctly REFUSES rather than truncating — the retained divergence from legacy, which writes
        // a short misaligned file to the bank with only a log line). Encoding is pure local
        // computation with no external effect, so keeping it inside the transaction preserves the
        // strongest available property: an over-width field means NOTHING was committed. Deferring it
        // to phase B would weaken that to "committed rows that must now be marked FAIL" for no gain.
        //
        // C-13: collect every field that puts a non-ASCII byte on the wire, so ONE alert can be raised
        // per file rather than one per field. The codec logs each finding at WARN; the list is carried
        // to phase B because raising the alert is itself an outbound call and must not run inside this
        // transaction (and because the codec has no VAN_ORG_CD, which OpsAlertService needs).
        List<FixedLengthCodec.NonAsciiField> nonAscii = new ArrayList<>();
        byte[] fileBytes = fixedLengthCodec.encode(header, dataLines, trailer, nonAscii);

        return new PreparedFile(fileName, fileSeq, reqCnt, reqAmt, fileBytes, nonAscii);
    }

    /**
     * <b>PHASE B</b> for ONE split file: write the staging file, SFTP-upload it, audit the 00301 leg
     * before the call, then issue EFS 301.
     *
     * <p>Runs with the file's rows ALREADY COMMITTED at {@code PROC_STEP='0'/PROC_STAT='6'} (phase A)
     * and with <b>no transaction open across any external call</b> — each DB write goes through
     * {@link BulkDispatchClaimTxService} in its own committed transaction. This is the legacy
     * ordering: {@code FILE_DEPOSIT_TRANSFER_BATCH1} commits at {@code :696-697}, uploads at
     * {@code :700}, audits at {@code :718} and calls 00301 at {@code :721}.
     *
     * <p>On any failure both the firm file and the pay-file are marked FAIL (no advance) and
     * {@code false} is returned; the EFS error is NOT rethrown (legacy parity — the FAIL state
     * commits). Because phase A already committed, a failure here can no longer be undone by a
     * rollback, so every failure path MUST write its terminal state explicitly — that is what the
     * {@code failFirmFileAndPayFile} calls below are for.
     *
     * @return {@code true} when the file was uploaded and 00301 accepted; {@code false} on any
     *         failure (caller stops the batch, legacy {@code :741-768})
     */
    private boolean dispatchOneFile(TbPayFileMst pay, TbFirmConf conf, String tranDt, String bizKey,
                                    String fileSeqNo, PreparedFile f, String dttm14, String tranTm) {
        String fileName = f.fileName();

        // Write the local staging file. Still pre-egress (an unwritable staging path is a local
        // fault), but no longer rollback-able — phase A has committed — so it records FAIL.
        String localPath;
        try {
            localPath = writeLocalFile(fileName, f.fileBytes());
        } catch (RuntimeException e) {
            log.error("00301 staging write failed (pre-egress, nothing sent) fileNm={} — marking FAIL",
                    fileName, e);
            bulkDispatchClaimTxService.failFirmFileAndPayFile(bizKey, tranDt, fileSeqNo, fileName,
                    FirmBankingResultCd.ETC_ERR,
                    "file build failed: " + e.getClass().getSimpleName(), dttm14);
            return false;
        }

        List<FixedLengthCodec.NonAsciiField> nonAscii = f.nonAscii();
        // C-13 — ONE alert per file, never one per field. Fires only when the file actually carries
        // non-ASCII bytes, which for a healthy tenant is never: all six UAT samples are pure ASCII and
        // tone-stripping is meant to leave nothing behind. So this alert firing at all is the signal.
        //
        // NOT a failure. The file is structurally valid and IS dispatched — the bank's accepted charset
        // is unconfirmed (K3 / C-13), so refusing here would block payments over an unproven premise.
        // See FixedLengthCodec.encode's TODO for what has to be established before this becomes a
        // reject. Field names / offsets / counts only; never the values (payer + payee names, memos).
        if (!nonAscii.isEmpty()) {
            log.warn("00301 file carries non-ASCII bytes in {} field(s) — dispatched anyway (bank charset "
                            + "unconfirmed, K3/C-13). fileNm={} bizKey={} fields={}",
                    nonAscii.size(), fileName, bizKey, nonAscii);
            opsAlertService.notify(conf.getVanOrgCd(),
                    "대량이체 파일 비ASCII 문자 포함 (non-ASCII bytes on the wire)."
                            + "\nTRAN_DT : " + tranDt
                            + "\nBIZ_KEY : " + bizKey
                            + "\nFIRM_FILE_NM : " + fileName
                            + "\nFIELD_COUNT : " + nonAscii.size()
                            + "\nFIELDS : " + nonAscii
                            + "\nThe file was still sent — the bank's accepted charset on this channel "
                            + "is unconfirmed (K3 / C-13). Every real sample file is pure ASCII, so this "
                            + "most likely means a value reached dispatch without tone-stripping or "
                            + "without entry validation (e.g. ACCT_MSTR.ACCT_NM -> SENDER_NM, an ERP "
                            + "passthrough row, or a legacy-JEX-written row). Verify the bank accepted "
                            + "the batch, then trace the field back to its writer.");
        }

        // SFTP upload — on failure mark FAIL (no advance) and stop the batch.
        // No transaction is open here (legacy uploads at :700, AFTER its :696 commit).
        String remotePath = joinPath(sftpProperties.getRemoteUploadPath(), fileName);
        try {
            sftpClient.upload(localPath, remotePath);
        } catch (RuntimeException e) {
            log.error("00301 SFTP upload failed fileNm={} — marking FAIL", fileName, e);
            bulkDispatchClaimTxService.failFirmFileAndPayFile(bizKey, tranDt, fileSeqNo, fileName,
                    FirmBankingResultCd.ETC_ERR, "SFTP upload failed", dttm14);
            return false;
        }

        // Audit the 00301 leg BEFORE the call (legacy createApiTran :706-718, then apiCall :721).
        int reqCnt = f.reqCnt();
        BigDecimal reqAmt = f.reqAmt();
        String[] amt = splitAmount(reqAmt);
        TbFirmBatApiTran audit = writeApiTran(bizKey, pay.getOutBankCd(), conf.getBankPrtnId(), fileName,
                tranDt, tranTm, ApiTranTp.BULK_RECEPTION.code(),
                BigDecimal.valueOf(reqCnt), reqAmt);

        // Issue EFS 00301.
        try {
            issueReception(new BulkReceptionApiCommand(
                    conf.getVanOrgCd(), pay.getOutBankCd(), conf.getBankPrtnId(),
                    fileName, String.valueOf(reqCnt), amt[0], amt[1], audit.getTranSeq()));

            audit.setRsCd(FirmBankingResultCd.SUCC);
            audit.setRsMsg(FirmBankingResultCd.MSG_SUCC);
            audit.setRsDttm(dttm14);
            firmBatApiTranRepository.save(audit);
            // Success: TB_FIRM_FILE_MST stays at PROC_STEP=0/PROC_STAT=6 — the 0→1 advance
            // is the 01301 callback's job. No unsafe advance here.
            log.info("00301 reception requested fileNm={} bizKey={} cnt={}", fileName, bizKey, reqCnt);
            return true;
        } catch (BusinessException e) {
            String rc = e.getArgs() != null && e.getArgs().length > 0 && e.getArgs()[0] != null
                    ? e.getArgs()[0].toString() : FirmBankingResultCd.ETC_ERR;
            audit.setRsCd(rc);
            audit.setRsMsg(e.getCode());
            audit.setRsDttm(dttm14);
            firmBatApiTranRepository.save(audit);
            bulkDispatchClaimTxService.failFirmFileAndPayFile(bizKey, tranDt, fileSeqNo, fileName, rc,
                    "EFS 00301 failed: " + e.getCode(), dttm14);
            log.warn("00301 reception failed (rc={}) fileNm={} — FAIL recorded, no advance", rc, fileName);
            // Do NOT rethrow — commit the FAIL state (legacy parity).
            return false;
        } catch (RuntimeException e) {
            // H-3 (4.1) — the smallest correct boundary around issueReception for a NON-business
            // runtime failure. The realistic sources are all inside this leg:
            //   * VanApiKeyService.getApiKey        → IllegalStateException when TB_VAN_API_INFO has no
            //                                         API_KEY for the VAN (the B2 provisioning gap)
            //   * EfsCryptoService encrypt / MAC    → IllegalStateException / IllegalArgumentException
            //   * serialize / deserialize           → IllegalStateException on a payload problem
            //   * RestClientEfsApiClient            → IllegalStateException on an unparseable envelope,
            //                                         and RestClient's own transport exceptions
            // None is a BusinessException, so before this catch every one of them escaped, rolled back
            // this transaction (discarding the firm-file row AND any terminal write) and left the
            // committed pay-file claim at '6' with only a log line — the H-3 strand. Under the
            // two-phase split there is no transaction here to roll back at all, which is why the
            // terminal write must be explicit.
            //
            // Recorded as ETC_ERR / G999, matching what the gateway itself returns for a general error
            // and what the SFTP-failure branch above already uses. RuntimeException only — an Error is
            // deliberately allowed to propagate untouched.
            //
            // NOTE on egress ambiguity: unlike the pre-egress build guard, the request MAY have reached
            // the gateway before the failure (e.g. a response that failed to parse). '3' here matches
            // the BusinessException branch directly above, which has the same ambiguity and is the
            // legacy-parity behaviour; the audit row written before the call is what an operator
            // reconciles against, and the 4.3 monitor sweep surfaces the file for exactly that.
            audit.setRsCd(FirmBankingResultCd.ETC_ERR);
            audit.setRsMsg(e.getClass().getSimpleName());
            audit.setRsDttm(dttm14);
            firmBatApiTranRepository.save(audit);
            bulkDispatchClaimTxService.failFirmFileAndPayFile(bizKey, tranDt, fileSeqNo, fileName,
                    FirmBankingResultCd.ETC_ERR,
                    "EFS 00301 failed unexpectedly: " + e.getClass().getSimpleName(), dttm14);
            log.error("00301 reception failed unexpectedly fileNm={} — FAIL recorded, no advance",
                    fileName, e);
            return false;
        }
    }

    /**
     * Record a group-wide phase-A rejection: the whole batch was refused before any file was built,
     * so the pay-file carries the terminal state and no firm-file row exists at all (phase A rolled
     * back). Mirrors legacy Gate A's outcome (FILE_DEPOSIT_TRANSFER_BATCH1:231-265) — pay-file FAIL
     * with the validation message, one alert, and the request skipped.
     */
    private void recordGroupRejection(TbPayFileMst pay, TbFirmConf conf, String tranDt, String bizKey,
                                      String fileSeqNo, int reqCnt, BusinessException e, String dttm14) {
        // The validator's message already names the field and the offending character(s).
        String detail = (e.getArgs() != null && e.getArgs().length > 0)
                ? String.valueOf(e.getArgs()[0])
                : e.getCode();
        String rsMsg = detail.length() <= RS_MSG_MAX ? detail : detail.substring(0, RS_MSG_MAX);
        log.warn("00301 blocked characters at dispatch — whole request marked FAIL bizKey={} "
                + "fileSeqNo={} reason={}", bizKey, fileSeqNo, detail);
        bulkDispatchClaimTxService.failStrandedPayFile(bizKey, tranDt, fileSeqNo, rsMsg, dttm14);
        opsAlertService.notify(conf.getVanOrgCd(),
                "이체파일 Batch1 금칙문자 오류 발생 (blocked characters at dispatch)"
                        + "\nTRAN_DT : " + tranDt
                        + "\nBIZ_KEY : " + bizKey
                        + "\nFILE_SEQ_NO : " + fileSeqNo
                        + "\nOUT_BANK_CD : " + pay.getOutBankCd()
                        + "\nBANK_PRTN_ID : " + pay.getBankPrtnId()
                        + "\nREQ_CNT : " + reqCnt
                        + "\nAPI RM : " + detail);
    }

    /**
     * Branch-split division — refactor of legacy
     * {@code FILE_DEPOSIT_TRANSFER_BATCH1.calculateBatchDivision}.
     *
     * <p>Returns {@code [loopCnt, chunkSize]}. When {@code BAT_TRAN_BRANCH_DIV='Y'} and
     * {@code reqCnt > limit}, the request splits into {@code ceil(reqCnt / limit)} files
     * of at most {@code limit} rows each. Otherwise a single file of all {@code reqCnt}
     * rows.
     *
     * <p><b>Defensive deviation from legacy:</b> a null or non-positive limit is treated
     * as "no split" rather than reaching {@code ceil(reqCnt / 0)} (legacy
     * {@code Math.ceil(reqCnt / 0.0)} would overflow to a huge {@code int}). A
     * misconfigured limit therefore degrades to the safe single-file path instead of
     * producing an absurd file count.
     */
    /**
     * The set of operating-hours channel names present in the file — the distinct
     * 1-char {@code ANT_DIV_CD} of the detail rows mapped via
     * {@link AntDivChannel#fromStorageCode(String)}
     * (E/V/C/N/S → EBANK/VCB/CITAD/NAPAS/SWIFT). Unmapped / blank codes are ignored
     * (legacy {@code ETC_CNT} — not operating-hours-gated). Mirrors the legacy per-channel
     * {@code count > 0} guards (FILE_DEPOSIT_TRANSFER_BATCH1.java:299/319/339/359/379).
     */
    private Set<String> presentChannels(List<TbPayFileData> details) {
        Set<String> channels = new LinkedHashSet<>();
        for (TbPayFileData d : details) {
            // null / blank / unrecognised = legacy ETC — deliberately NOT gated.
            String name = AntDivChannel.fromStorageCode(d.getAntDivCd());
            if (name != null) {
                channels.add(name);
            }
        }
        return channels;
    }

    private int[] calculateBatchDivision(String branchDiv, int reqCnt, BigDecimal branchLimit) {
        if ("Y".equals(trim(branchDiv)) && branchLimit != null) {
            int limit = branchLimit.intValue();
            if (limit > 0 && reqCnt > limit) {
                int loopCnt = (int) Math.ceil((double) reqCnt / limit);
                return new int[] {loopCnt, limit};
            }
        }
        return new int[] {1, reqCnt};
    }

    /**
     * Run {@code work} in a fresh {@code REQUIRES_NEW} transaction that commits immediately.
     * {@code receive()} holds no outer transaction, so this stands alone.
     *
     * <p>Used for phase A (all N files in one committed unit). Phase B holds no transaction at all —
     * its writes go through {@link BulkDispatchClaimTxService}, one committed transaction each, so
     * nothing spans an SFTP transfer or a gateway call.
     */
    private <T> T runInNewTransaction(TransactionCallback<T> work) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx.execute(work);
    }

    /**
     * Insert a {@code TB_FIRM_BAT_API_TRAN} audit row (00301), result columns left null.
     *
     * <p>Takes the key fields explicitly rather than a {@code TbFirmFileMst}: phase A committed and
     * closed its transaction, so the entity the caller would otherwise hold is detached, and reading
     * it back purely to copy four columns onto the audit row would be a query for nothing.
     */
    private TbFirmBatApiTran writeApiTran(String bizKey, String outBankCd, String bankPrtnId,
                                          String firmFileNm, String tranDt, String tranTm,
                                          String apiTranTp, BigDecimal batCnt, BigDecimal batAmt) {
        TbFirmBatApiTran row = new TbFirmBatApiTran();
        row.setTranDt(tranDt);
        row.setBizKey(bizKey);
        row.setTranSeq(dispatchKeyGenerator.nextTranSeq(bizKey, tranDt));
        row.setApiTranTp(apiTranTp);
        row.setOutBankCd(outBankCd);
        row.setBankPrtnId(bankPrtnId);
        row.setTranTm(tranTm);
        row.setFirmFileNm(firmFileNm);
        row.setBatCnt(batCnt == null ? BigDecimal.ZERO : batCnt);
        row.setBatAmt(batAmt == null ? BigDecimal.ZERO : batAmt);
        return firmBatApiTranRepository.save(row);
    }

    /**
     * Wrap a file round to the bank's legal range, mirroring legacy
     * {@code IDO.TB_FIRM_FILE_MST_R002.xml:34-49} (audit <b>M-4</b>):
     * <pre>
     * WHEN OUT_BANK_CD = '970457' THEN (MAX_SEQ + 1 &gt; 99  ? 1 : MAX_SEQ + 1)
     * ELSE                             (MAX_SEQ + 1 &gt; 999 ? 1 : MAX_SEQ + 1)
     * </pre>
     *
     * <p>Applied <b>per split file</b> ({@code base + i}), not just to the base. Legacy re-runs R002
     * inside its per-file loop ({@code FILE_DEPOSIT_TRANSFER_BATCH1:520-532}), so every file is
     * independently clamped; computing the base once and adding {@code i} would otherwise sail past
     * the ceiling on a multi-split request.
     *
     * <p>Note this is a <b>reset to 1</b>, not a modulo — exactly as legacy. Once a bank/partner group
     * saturates its range for the day, legacy emits {@code 001} repeatedly (its recomputed {@code MAX}
     * still sees the pre-wrap high value), and so does this. That collision behaviour is legacy's, is
     * reproduced deliberately, and is bounded by the same precondition: it takes 99 (Woori) or 999
     * files for one bank+partner in a single day.
     *
     * <p>The hard invariant this guarantees is the formatting one: the result is always in
     * {@code 1..999}, so {@code String.format("%03d", …)} can never emit a 4th character into the
     * 3-byte {@code FILE_SEQ} header field.
     */
    static int wrapFileRound(int round, String outBankCd) {
        int ceiling = BulkTransferConstants.BANK_CD_WOORI.equals(outBankCd)
                ? BulkTransferConstants.FILE_ROUND_MAX_WOORI
                : BulkTransferConstants.FILE_ROUND_MAX_DEFAULT;
        return round > ceiling ? 1 : round;
    }

    /**
     * Next file round (MAX existing + 1) for the bank/partner group (legacy R002 MAX+1).
     *
     * <p>Returns the <b>unclamped</b> MAX+1; the per-bank ceiling is applied by
     * {@link #wrapFileRound(int, String)} at the point of use, so it covers both this base value and
     * every {@code base + i} split file.
     */
    private int nextFileRound(String tranDt, String outBankCd, String bankPrtnId) {
        int max = firmFileMstRepository.findByTranDtAndOutBankCdAndBankPrtnId(tranDt, outBankCd, bankPrtnId).stream()
                .map(TbFirmFileMst::getFileSeq)
                .filter(StringUtils::hasText)
                .mapToInt(s -> {
                    try {
                        return Integer.parseInt(s.trim());
                    } catch (NumberFormatException nfe) {
                        return 0;
                    }
                })
                .max().orElse(0);
        return max + 1;
    }

    /** Write the generated file to the local staging dir (or the JVM temp dir if unset). */
    private String writeLocalFile(String fileName, byte[] bytes) {
        try {
            String dir = StringUtils.hasText(sftpProperties.getLocalUploadPath())
                    ? sftpProperties.getLocalUploadPath()
                    : System.getProperty("java.io.tmpdir");
            Path target = Path.of(dir, fileName);
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.write(target, bytes);
            return target.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write local dispatch file " + fileName, e);
        }
    }

    private static String joinPath(String dir, String name) {
        if (!StringUtils.hasText(dir)) {
            return name;
        }
        return dir.endsWith("/") ? dir + name : dir + "/" + name;
    }

    /** Truncate to {@code maxBytes} UTF-8 bytes without splitting a character (legacy {@code getStrByteMaxLen}). */
    private static String byteTruncate(String value, int maxBytes) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int total = 0;
        int cut = 0;
        for (int i = 0; i < value.length(); i++) {
            int len = String.valueOf(value.charAt(i)).getBytes(FixedLengthCodec.CHARSET).length;
            if (total + len > maxBytes) {
                break;
            }
            total += len;
            cut = i + 1;
        }
        return value.substring(0, cut);
    }

    /**
     * Split the amount into [integer, decimal-portion] for the 00301 request, reproducing
     * legacy {@code StrUtil.BigDecPaser} exactly (see {@link com.kosign.wecms.domain.payment.dispatch.van.AmountSplitter}).
     */
    private static String[] splitAmount(BigDecimal amt) {
        return com.kosign.wecms.domain.payment.dispatch.van.AmountSplitter.split(amt);
    }

    private static String plain(BigDecimal v) {
        return v == null ? "0" : v.toPlainString();
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }

    // ------------------------------------------------------------------ 00301 API leg (unchanged)

    /**
     * The {@code WGVN_EFS_301} bulk-reception <b>API request leg</b> — build →
     * encrypt → POST {@code EFS_301} → parse. Called from {@link #receive} after
     * the file is built + uploaded; also directly callable for the leg in isolation.
     *
     * @return the echoed header totals on {@code RC=0000}
     * @throws IllegalArgumentException on missing required input
     * @throws IllegalStateException    when the API key is not provisioned (B2,
     *                                  surfaced from {@link VanApiKeyService})
     * @throws BusinessException        ({@code WBE_EFS_GATEWAY_ERROR}) on a
     *                                  non-{@code 0000} gateway result
     */
    public BulkReceptionApiResult issueReception(BulkReceptionApiCommand cmd) {
        validate(cmd);

        // Resolve the per-VAN key first — a missing key (B2) must surface here,
        // before any envelope is built or any request is sent.
        String apiKey = vanApiKeyService.getApiKey(cmd.vanOrgCd());

        Efs301Request request = new Efs301Request(
                cmd.bankCd(), cmd.prtnId(), cmd.fileNm(), cmd.dataCnt(), cmd.dataAmt(), cmd.dataDcmAmt());
        String businessJson = serialize(request, "301");

        // A1 (audit X-2): the envelope TNO is the allocated TB_FIRM_BAT_API_TRAN.TRAN_SEQ (legacy
        // BATCH1 `seq`), NOT a UUID — the gateway record correlates 1:1 to the audit row.
        String rqDtime = LocalDateTime.now(ICT).format(DTTM14_FMT);
        String tno = cmd.tno();
        String ev = efsCryptoService.encryptEv(businessJson, apiKey, rqDtime);
        String vv = efsCryptoService.buildVv(businessJson, apiKey);
        // D-2 (legacy parity): the envelope ID is ALWAYS TB_FIRM_CONF.VAN_ORG_CD — never a
        // configured partner id. Per-leg legacy evidence in EfsProperties' class Javadoc.
        String id = cmd.vanOrgCd();

        EfsEnvelope requestEnvelope = new EfsEnvelope(
                id, rqDtime, null, tno, ev, vv,
                EfsEnvelope.EM_AES, EfsEnvelope.VM_HMAC_SHA256, null, null);

        EfsEnvelope response = efsApiClient.send(EfsApiClient.EFS_301, requestEnvelope);

        String rc = response == null ? null : response.rc();
        if (response == null || !FirmBankingResultCd.isSuccess(rc)) {
            log.warn("EFS 301 reception failed vanOrgCd={} fileNm={} dataCnt={} rc={}",
                    cmd.vanOrgCd(), cmd.fileNm(), cmd.dataCnt(), rc);
            throw new BusinessException("WBE_EFS_GATEWAY_ERROR", rc);
        }

        String responseJson = efsCryptoService.decryptEv(response.ev(), apiKey);
        Efs301Response parsed = deserialize(responseJson, Efs301Response.class, "301");

        log.debug("EFS 301 reception ok vanOrgCd={} fileNm={} dataCnt={} rc={}",
                cmd.vanOrgCd(), cmd.fileNm(), cmd.dataCnt(), rc);
        return BulkReceptionApiResult.from(parsed);
    }

    private void validate(BulkReceptionApiCommand cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("BulkReceptionApiCommand is required");
        }
        if (!StringUtils.hasText(cmd.vanOrgCd())) {
            throw new IllegalArgumentException("vanOrgCd is required");
        }
        if (!StringUtils.hasText(cmd.fileNm())) {
            throw new IllegalArgumentException("fileNm is required");
        }
        if (!StringUtils.hasText(cmd.dataCnt())) {
            throw new IllegalArgumentException("dataCnt is required");
        }
        if (!StringUtils.hasText(cmd.tno())) {
            throw new IllegalArgumentException("tno (TRAN_SEQ) is required");
        }
    }

    private String serialize(Object value, String svc) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise EFS " + svc + " request", e);
        }
    }

    private <T> T deserialize(String json, Class<T> type, String svc) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse EFS " + svc + " response", e);
        }
    }
}
