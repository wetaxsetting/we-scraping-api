package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService;
import com.kosign.wecms.domain.payment.dispatch.constant.FirmBankingResultCd;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTran;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmConfRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmTranRepository;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs203Request;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs203Response;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConfId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * {@code WGVN_EFS_203} — processing-result inquiry (REST API guide ver 1.2.2
 * §5.5). Refactor of legacy {@code PROCESSING_RESULT_CHECK_BATCH}.
 *
 * <p>Two callers:
 * <ul>
 *   <li>{@link RealtimeTransferService} — invokes {@link #inquire} when a 202
 *       transfer returns {@code RC=G999} (no definitive response) to confirm the
 *       true outcome (§5.4 recovery rule).</li>
 *   <li>The scheduled poll ({@link com.kosign.wecms.domain.payment.dispatch.scheduler.ResultCheckJob})
 *       — calls {@link #inquire} for aged {@code TRAN_TP='202'}/{@code PROC_STAT='6'}
 *       rows. <b>Double-gated (D4/B7)</b>, like every sibling sweep: {@link #runOnce()}
 *       is a no-op unless {@code wecms.dispatch.result-check.enabled=true}, <b>and</b>
 *       the job's cron defaults to "never fire". Both default OFF, so the refactor never
 *       co-processes live rows on the shared dev DB. When both gates are open the body
 *       does real work — it is <b>not</b> inert.</li>
 * </ul>
 *
 * <p>Flow (mirrors {@link com.kosign.wecms.domain.payment.dispatch.recipient.RecipientLookupService}):
 * build {@link Efs203Request} → serialise (global SNAKE_CASE) → resolve key by
 * {@code vanOrgCd} ({@link VanApiKeyService}) → build {@code EV}/{@code VV}
 * ({@link EfsCryptoService}) → POST via {@link EfsApiClient#send EfsApiClient.EFS_203}
 * → on {@code RC=0000}, decrypt the response {@code EV} and map the 203 fields.
 *
 * <p><b>No DB status writes in this phase (D3).</b> {@code PROC_STAT} is not
 * mutated here; the caller owns that decision once the {@code TB_FIRM_TRAN} seam
 * is wired. Logging never emits {@code API_KEY}, {@code EV}, {@code VV}, the
 * decrypted payload, or full account numbers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResultCheckService {

    private static final DateTimeFormatter RQ_DTIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DT8 = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TM6 = DateTimeFormatter.ofPattern("HHmmss");
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");
    /**
     * D-1: the zone {@code TB_FIRM_TRAN.TRAN_DT} lives in. NOT a business zone — it is the zone
     * legacy stamps the originating {@code TB_PAY_TARGET.TRAN_DT} in
     * ({@code DateTimeUtils.utcNow()}, legacy {@code PymnHelper:2197}), which the refactor's 202
     * write seam reuses as the firm-tran PK date. Do not "correct" this to ICT without changing
     * the writers — reader and writer must share one clock.
     */
    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final int TNO_MAX = 50;

    /** Real-time transfer leg ({@code TB_FIRM_TRAN.TRAN_TP}) the poll re-checks. */
    private static final String TRAN_TP_TRANSFER = "202";
    /** Processing-result-inquiry leg ({@code WCConst.TRAN_TP_PROCESS_INQ}) — the 203 audit row. */
    private static final String TRAN_TP_PROCESS_INQ = "203";
    /** Legacy {@code WCConst.ORG_ST_OK} — connecting-org state "거래가능 / available". */
    private static final String ORG_ST_OK = "1";
    /** Legacy re-check window: only transfers older than 2 minutes are inquired. */
    private static final int RESULT_CHECK_DELAY_MINUTES = 2;

    /** Fallback used when {@code batch-size} is configured non-positive. */
    static final int DEFAULT_BATCH_SIZE = 100;

    /** Fallback used when {@code pass-deadline} is configured non-positive. */
    static final Duration DEFAULT_PASS_DEADLINE = Duration.ofMinutes(10);

    /**
     * Master gate — OFF by default, matching {@code StrandedRecoveryService.enabled} and
     * {@code StuckPayFileMonitorService.enabled}.
     *
     * <p>Before this existed, {@code result-check.cron} was the <b>only</b> gate on this body: a single
     * cron variable started outbound EFS 203 traffic and {@code TB_FIRM_TRAN} / {@code TB_PAY_TARGET}
     * {@code PROC_STAT} writes against whatever database the instance points at. Every sibling sweep
     * that mutates payment state is double-gated; this one was not, and it is the sweep with the
     * strictest coexistence precondition (see {@code ResultCheckJob}'s class Javadoc — it must not run
     * while the legacy {@code PROCESSING_RESULT_CHECK_BATCH} is live on the same DB).
     *
     * <p><b>This gate also governs the {@code StrandedRecoveryService} delegation.</b> That sweep calls
     * {@link #runOnce()} for its aged-{@code '6'} strand class, so with this flag false the delegation
     * is a no-op even when {@code wecms.dispatch.recovery.enabled=true}. That is deliberate: the flag
     * answers "may this instance drive 203 traffic and finalize payment rows?", and the answer cannot
     * depend on which entry point asked.
     */
    @Value("${wecms.dispatch.result-check.enabled:false}")
    private boolean enabled;

    /**
     * Whether {@link #runOnce()} will do anything — for callers that delegate into it and need to say so
     * when it will not.
     *
     * <p>{@code StrandedRecoveryService} covers four strand classes and this method owns one of them
     * (aged {@code 202}/{@code '6'} confirmation). A recovery sweep that silently completes three of four
     * is the trap this accessor exists to close: the sweep logs a WARN naming the flag instead of
     * reporting success.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Candidate rows fetched per scheduled pass (H-2).
     *
     * <p>Bounds memory <b>and</b> the number of sequential EFS 203 round-trips one pass can make,
     * which in turn bounds how long the job holds its ShedLock. Legacy had no limit — it processed
     * every eligible row in one run — so this is an operational bound, not a parity change: the same
     * rows are still processed, just across successive passes when the volume is large.
     *
     * <p>A non-positive value falls back to {@link #DEFAULT_BATCH_SIZE} rather than failing startup;
     * a scheduler that refuses to boot over a mistyped batch size is worse than one that runs with a
     * sane default and says so. See {@link #effectiveBatchSize()}.
     */
    @Value("${wecms.dispatch.result-check.batch-size:100}")
    private int batchSize = DEFAULT_BATCH_SIZE;

    /**
     * Wall-clock budget for one {@link #runOnce()} pass, checked at the <b>top of each row
     * iteration</b>. When the budget is spent the loop breaks and the remaining rows are left at
     * {@code PROC_STAT='6'} for the next pass, which resumes from the oldest of them (the candidate
     * query is ordered {@code TRAN_DT, TRAN_TM, TRAN_SEQ} ascending, so a deferred row sorts to the
     * front rather than starving).
     *
     * <p><b>Why a deadline rather than a bigger {@code lockAtMostFor}.</b> Growing the lock ceiling to
     * track the worst-case pass is unbounded in principle — every timeout or batch-size increase pushes
     * it up again — and each increase buys concurrency safety by paying for it one-for-one in
     * crash-recovery latency. The deadline caps the pass instead: a pass cannot exceed
     * {@code deadline + one in-flight row} regardless of gateway health, which makes the lock ceiling a
     * fixed crash-recovery constant decoupled from {@code EfsProperties.readTimeoutMs}. A dead gateway
     * now stops the sweep in ~10 minutes instead of grinding through 100 timeouts for nearly two hours.
     *
     * <p><b>The check is at the top of the iteration, never inside a row.</b> A row that has already
     * begun runs to completion — its 203 audit row is written, its inquiry is issued, its outcome is
     * finalized and its pace is paid. Interrupting mid-row would be the one thing this budget must not
     * do: it would leave a 203 audit row at {@code '6'} with an inquiry already at the gateway. The
     * overrun is therefore bounded by exactly one worst-case row (66&nbsp;s: 3&nbsp;s connect +
     * 60&nbsp;s read + 3000&nbsp;ms Woori pace).
     *
     * <p>A non-positive / unparseable value falls back to {@link #DEFAULT_PASS_DEADLINE} rather than
     * failing startup — same stance as {@link #effectiveBatchSize()}.
     */
    @Value("${wecms.dispatch.result-check.pass-deadline:PT10M}")
    private Duration passDeadline = DEFAULT_PASS_DEADLINE;

    private final EfsApiClient efsApiClient;
    private final EfsCryptoService efsCryptoService;
    private final VanApiKeyService vanApiKeyService;
    private final EfsProperties efsProperties;
    private final ObjectMapper objectMapper;
    private final TbFirmTranRepository tbFirmTranRepository;
    private final OpsAlertService opsAlertService;
    /** B3 (audit M-4): allocates the fresh {@code TRAN_SEQ} for each 203 audit row (firm_seq_no funnel). */
    private final LegacyFunctionRepository legacyFunctions;
    /** B4 (audit M-6): source of the connecting-org state ({@code ORG_STAT}) gate. */
    private final TbFirmConfRepository firmConfRepository;
    /** B5 (audit M-5): per-bank inter-request pacing for the scan loop. */
    private final DispatchPacer dispatchPacer;
    /** Item 1 (audit M-7): atomic original-firm-tran + pay-target finalize (one REQUIRES_NEW tx per row). */
    private final DispatchResultTxService dispatchResultTxService;

    /**
     * Confirm the true outcome of an original transaction via {@code WGVN_EFS_203}.
     *
     * @return the decrypted 203 result on {@code RC=0000}
     * @throws IllegalArgumentException on missing required input
     * @throws IllegalStateException    when the API key is not provisioned (B2,
     *                                  surfaced from {@link VanApiKeyService})
     * @throws BusinessException        ({@code WBE_EFS_GATEWAY_ERROR}) on a
     *                                  non-{@code 0000} gateway result
     */
    public ResultCheckInquiryResult inquire(ResultCheckInquiryCommand cmd) {
        return inquire(cmd, null);
    }

    /**
     * As {@link #inquire(ResultCheckInquiryCommand)} but with a caller-allocated envelope
     * {@code TNO} — the fresh {@code TB_FIRM_TRAN.TRAN_SEQ} of the {@code TRAN_TP='203'}
     * audit row the scheduled poll creates (B3 / audit X-2). Sending the inquiry's own
     * allocated sequence as the gateway {@code TNO} (never a UUID) keeps the 203 request
     * correlated 1:1 to its ledger row, exactly as legacy
     * {@code PROCESSING_RESULT_CHECK_BATCH} (envelope {@code TNO = tran_seq}). A blank
     * {@code inquiryTno} falls back to a generated one (the direct-call convenience path).
     */
    public ResultCheckInquiryResult inquire(ResultCheckInquiryCommand cmd, String inquiryTno) {
        validate(cmd);

        // Resolve the per-VAN key first — a missing key (B2) must surface here,
        // before any envelope is built or any request is sent.
        String apiKey = vanApiKeyService.getApiKey(cmd.vanOrgCd());

        Efs203Request request = new Efs203Request(
                cmd.bankCd(), cmd.prtnId(), cmd.orgTranDt(), cmd.orgTno(), cmd.macctNo());
        String businessJson = serialize(request);

        String rqDtime = LocalDateTime.now(ICT).format(RQ_DTIME_FMT);
        String tno = StringUtils.hasText(inquiryTno) ? inquiryTno : newTno(rqDtime);
        String ev = efsCryptoService.encryptEv(businessJson, apiKey, rqDtime);
        String vv = efsCryptoService.buildVv(businessJson, apiKey);
        // D-2 (legacy parity): the envelope ID is ALWAYS TB_FIRM_CONF.VAN_ORG_CD — never a
        // configured partner id. Per-leg legacy evidence in EfsProperties' class Javadoc.
        String id = cmd.vanOrgCd();

        EfsEnvelope requestEnvelope = new EfsEnvelope(
                id, rqDtime, null, tno, ev, vv,
                EfsEnvelope.EM_AES, EfsEnvelope.VM_HMAC_SHA256, null, null);

        EfsEnvelope response = efsApiClient.send(EfsApiClient.EFS_203, requestEnvelope);

        String rc = response == null ? null : response.rc();

        // B1 (audit H-1): RC=0001 "원거래 없음 / original transaction not found" is a DEFINITIVE
        // outcome, not a transient gateway error. Legacy PROCESSING_RESULT_CHECK accepts 0001
        // alongside 0000 and marks the original FAIL. Return a not-found result (no EV payload to
        // decrypt) so reflectOutcome finalizes PROC_STAT='3' — never re-inquiring the row forever.
        if (response != null && FirmBankingResultCd.NO_FOUND.equals(rc)) {
            log.info("EFS 203 inquiry: original not found (0001) vanOrgCd={} orgTno={} macct={}",
                    cmd.vanOrgCd(), cmd.orgTno(), mask(cmd.macctNo()));
            return ResultCheckInquiryResult.notFound(cmd.orgTranDt(), cmd.orgTno());
        }

        if (response == null || !FirmBankingResultCd.isSuccess(rc)) {
            // G999 / other / no-response = transient: throw so the caller leaves PROC_STAT='6'
            // and the next pass retries (never a definitive result).
            log.warn("EFS 203 inquiry failed vanOrgCd={} orgTno={} macct={} rc={}",
                    cmd.vanOrgCd(), cmd.orgTno(), mask(cmd.macctNo()), rc);
            throw new BusinessException("WBE_EFS_GATEWAY_ERROR", rc);
        }

        // A3 (audit H-5): verify the response VV before decrypting/trusting EV (legacy
        // SecurityUtil.VerifyMac → G003). A tampered/corrupt envelope must not be processed;
        // surface G003 as a gateway error so the row stays '6' and is retried, never mis-mapped.
        //
        // D-E GATE (2026-08-01): an ABSENT VV is not a verification failure — it is accepted with a
        // WARN. This leg is the one place legacy does NOT verify (PRCB:240 decrypts straight from rEV,
        // while API1:102 / API2:109 / NAPAS:369 / WB_00000100:152 all call VerifyMac), and the refactor
        // has never run a 203 against the live gateway. If WGVN_EFS_203 emits no VV, a strict check
        // fails 100% of rows and strands the ENTIRE result-check rail at PROC_STAT='6' on first run —
        // a far worse outcome than legacy's, which at least completes. Treating null/blank as pass
        // keeps the tamper defence (a PRESENT but wrong VV still throws) while making a missing field
        // unable to strand the rail. Remove this branch once the VAN Center confirms VV is emitted;
        // the answer is recorded in docs/deployment/wecms-batch-activation-runbook.md §5.1a-2.
        if (!StringUtils.hasText(response.vv())) {
            log.warn("EFS 203 response carries NO VV — accepting unverified (D-E gate) vanOrgCd={} "
                            + "orgTno={}. Legacy PROCESSING_RESULT_CHECK_BATCH never verified this leg "
                            + "either. Record whether WGVN_EFS_203 emits VV in the activation runbook "
                            + "§5.1a-2 before enabling result-check in UAT.",
                    cmd.vanOrgCd(), cmd.orgTno());
        } else if (!efsCryptoService.verifyVv(response.ev(), response.vv(), apiKey)) {
            log.warn("EFS 203 response VV verification failed vanOrgCd={} orgTno={}",
                    cmd.vanOrgCd(), cmd.orgTno());
            throw new BusinessException("WBE_EFS_GATEWAY_ERROR", FirmBankingResultCd.ENC_VRFY_ERR);
        }

        String responseJson = efsCryptoService.decryptEv(response.ev(), apiKey);
        Efs203Response parsed = deserialize(responseJson, Efs203Response.class);

        log.debug("EFS 203 inquiry ok vanOrgCd={} orgTno={} macct={} orgRespCd={}",
                cmd.vanOrgCd(), cmd.orgTno(), mask(cmd.macctNo()), parsed.orgRespCd());
        return ResultCheckInquiryResult.from(parsed);
    }

    /**
     * One scheduled result-check pass — refactor of legacy
     * {@code PROCESSING_RESULT_CHECK_BATCH}. Selects no-response real-time transfers
     * ({@code TRAN_TP='202' / PROC_STAT='6'}) older than {@value #RESULT_CHECK_DELAY_MINUTES}
     * minutes, issues a {@code WGVN_EFS_203} inquiry for each, and reflects the
     * confirmed outcome onto the {@code TB_FIRM_TRAN} row ({@code PROC_STAT 6→0/3}).
     *
     * <p>Each row is processed independently: a gateway error or no-response on one
     * row is logged and the row is left at {@code PROC_STAT='6'} for the next pass
     * (legacy parity — never abandons a transfer on a single failed inquiry, never
     * blocks the rest of the batch).
     *
     * <p><b>Bounded twice.</b> A pass is capped by row count ({@link #batchSize}) <i>and</i> by wall
     * clock ({@link #passDeadline}, default {@code PT10M}, checked at the top of each iteration). The
     * second bound is what makes the duration of a pass independent of gateway health: without it a
     * dead gateway turns a full batch into {@code batchSize × (connect + read + pace)} — nearly two
     * hours at the defaults — and the ShedLock ceiling has to grow to match. Rows not reached stay at
     * {@code '6'} and the next pass resumes from the oldest of them.
     *
     * <p><b>Operational gating (D4/B7):</b> double-gated. This body runs only when an environment
     * that owns the 202 traffic sets <i>both</i> {@code wecms.dispatch.result-check.enabled=true}
     * <i>and</i> {@code wecms.dispatch.result-check.cron} (the cron defaults to "never fire"), so the
     * refactor never co-processes live rows on the shared dev DB by accident. See {@link #enabled} for
     * why one gate was not enough, and note that the flag also silences the
     * {@code StrandedRecoveryService} delegation into this method.
     *
     * <p><b>Scope note (C4, 2026-06-13; atomicity Item 1, 2026-07-21):</b> the 203-confirmed
     * status is propagated to the originating {@code TB_PAY_TARGET} row as well as the
     * {@code TB_FIRM_TRAN} ledger, now atomically via {@link DispatchResultTxService}
     * (one {@code REQUIRES_NEW} transaction per row). The link is the shared
     * {@code (BIZ_KEY, TRAN_DT, TRAN_SEQ)} triple the C2 write seam established (the 202
     * firm-tran row reuses the pay-target {@code TRAN_SEQ}).
     *
     * <p><b>D-1 — which zone {@code TRAN_DT} is evaluated in (legacy-verified 2026-07-29)</b></p>
     *
     * <p>This filter used to compute "today" in {@code Asia/Ho_Chi_Minh} while {@code TRAN_DT} is
     * written in <b>UTC</b>. Between 00:00 and 07:00 ICT the two disagree by one day, so every row
     * dispatched in that window failed the date filter, was never re-checked on any later pass
     * (the filter was today-only) and stayed at {@code PROC_STAT='6'} permanently — a payment sent
     * to the bank with no result confirmation, 7 hours out of every 24.
     *
     * <p>The write side is <b>legacy-correct and unchanged</b>:
     * <ul>
     *   <li>Legacy stamps {@code TB_PAY_TARGET.TRAN_DT} / {@code REG_TM} from
     *       {@code DateTimeUtils.utcNow()} — {@code PymnHelper.pymn_0001_001_c001:2197,2214}
     *       ({@code utcNow()} = {@code now(TimeZone.getTimeZone("UTC"))},
     *       {@code DateTimeUtils:84-86}). The refactor's API-rail executors do the same
     *       ({@code ExpenseDispatchExecutor:150-152} and peers).</li>
     *   <li>The 202 firm-tran row reuses that same date + {@code TRAN_SEQ}, so one triple keys
     *       <b>both</b> tables — mirroring legacy, which finalises {@code TB_FIRM_TRAN} and
     *       {@code TB_PAY_TARGET} with a single {@code org_proc_dt}
     *       ({@code PROCESSING_RESULT_CHECK_BATCH:301,319}). Moving the firm-tran date to ICT
     *       would break that shared key and with it {@link DispatchResultTxService}.</li>
     * </ul>
     *
     * <p>So the <b>reader</b> was wrong, not the writer: the date filter is now evaluated in
     * {@link #UTC}, the same clock the value is written in.
     *
     * <p><b>Not a legacy-parity argument, though</b> — an earlier revision of this paragraph claimed
     * this "is exactly legacy's invariant (legacy reads {@code TRAN_DT = TO_CHAR(now(),'YYYYMMDD')} in
     * the DB's own zone, the same clock its writer used)". Legacy is <b>not</b> reader==writer: its
     * reader is PostgreSQL {@code now()} evaluated in the DB session's zone (Asia/Seoul on the JEX DB
     * — see {@code project_batch_schedule_tz_antdiv_fixes}), while its writer is
     * {@code DateTimeUtils.utcNow()}. Legacy therefore has the same two-clock mismatch this code was
     * fixed to remove; it is simply a 9-hour offset instead of 7, and it strands rows for part of every
     * day exactly as the pre-fix refactor did. Aligning the reader to the writer is the <b>right</b>
     * choice and stays — but it is a deliberate improvement on legacy, not a reproduction of it, and
     * should not be defended as parity.
     *
     * <p><b>Deliberate divergence from legacy (documented TO-BE, not parity):</b> legacy selects
     * <i>today only</i>. This pass also accepts the <b>previous</b> UTC date, so a row dispatched
     * shortly before midnight cannot be orphaned by the rollover before it becomes eligible.
     * Legacy's own answer to that edge is the guard {@code … < '235800'} in
     * {@code IDO.TB_FIRM_TRAN_R001.xml} — it stops the batch running in the last two minutes of the
     * day, which avoids the wrap without ever recovering the affected row. Widening by one day is
     * strictly safer, cannot double-process (each row is claimed via {@code PROC_STAT}), and is
     * what makes "no dispatched transaction is permanently stranded by a date boundary" provable.
     * See {@link #isAged} for the matching aging change.
     */
    public void runOnce() {
        if (!enabled) {
            log.debug("result-check disabled (wecms.dispatch.result-check.enabled=false) — no-op");
            return;
        }
        // H-2: bounded + deterministically ordered. Previously this called
        // findByTranTpAndProcStat(...), which loaded EVERY 202/'6' row — all dates, all tenants —
        // into memory before filtering in Java.
        //
        // N-1 (2026-07-29): the eligible TRAN_DT set is now a SQL predicate, applied BEFORE the
        // LIMIT. That is also legacy's own shape (the batch-side IDO.TB_FIRM_TRAN_R001 carries
        // TRAN_DT = TO_CHAR(now(),'YYYYMMDD')); moving it into Java was the regression. With the
        // filter after the limit, rows parked at 202/'6' from older dates sorted to the front of
        // every oldest-first page, consumed the whole window and were then discarded — so once such
        // rows reached the batch size the sweep processed nothing, permanently.
        //
        // Only the AGE comparison stays in Java (selectAged): the tiered RQ_DTTM-first rule is the
        // D-1 fix and cannot be reduced to one SQL compare without reintroducing the UTC/ICT
        // boundary bug. The date bound carries none of that subtlety.
        int limit = effectiveBatchSize();

        // C-10 — TWO CLOCKS IN ONE QUERY PATH, AND BOTH ARE CORRECT. Read this before "fixing" it.
        //
        // The date bound is evaluated in UTC and the age bound in ICT — seven hours apart. That is not
        // a defect: they compare against columns stamped by DIFFERENT writers, in different zones, and
        // each reader matches its own writer.
        //
        //   TRAN_DT      -> UTC. Written by DateTimeUtils.utcNow() on the pay-target (legacy
        //                   PymnHelper.pymn_0001_001_c001:2197,2214) and reused verbatim as the
        //                   firm-tran PK date; the refactor's API-rail executors do the same
        //                   (ExpenseDispatchExecutor:150-152 and peers). Read here via LocalDate.now(UTC)
        //                   — see eligibleTranDts + the D-1 paragraph in this method's Javadoc.
        //   RQ_DTTM /    -> ICT. Both stamped from LocalDateTime.now(ICT) at dispatch
        //   TRAN_TM         (RealtimeDispatchService:146-148 -> :240,:251). Read here via
        //                   LocalDateTime.now(ICT) — see isAged.
        //
        // Aligning them to ONE zone would break whichever column lost its writer's clock: forcing the
        // age compare to UTC would make every row look 7 hours older than it is and defeat the
        // 2-minute delay entirely; forcing the date compare to ICT is the D-1 defect this method was
        // fixed to remove (rows dispatched 00:00-07:00 ICT stranded at PROC_STAT='6' forever).
        //
        // What IS aligned here: both are now derived from a SINGLE instant. Previously they were two
        // independent now() reads a few statements apart, so a pass that straddled a UTC midnight
        // could bound the date on one day and the age on the other.
        Instant now = Instant.now();
        LocalDate utcToday = LocalDate.ofInstant(now, UTC);
        LocalDateTime nowIct = LocalDateTime.ofInstant(now, ICT);
        List<String> tranDts = eligibleTranDts(utcToday);

        List<TbFirmTran> candidates = tbFirmTranRepository.findResultCheckCandidates(
                TRAN_TP_TRANSFER, ProcStat.REQ.code(), tranDts, PageRequest.of(0, limit));

        // Retained as a redundant in-memory guard. It is now a no-op for any row the query above can
        // return, and exists so a future change to the query cannot silently widen the date scope
        // past what selectAged/isAged were reasoned about.
        List<TbFirmTran> aged = selectAged(candidates, nowIct, utcToday);

        // N-1: the cap check is evaluated BEFORE the empty-return. Previously it sat after it, so a
        // sweep whose entire page was discarded logged only "no aged transfers" at DEBUG — a total
        // stall was indistinguishable from a healthy idle tick.
        boolean capReached = candidates.size() >= limit;
        if (capReached) {
            log.warn("result-check: candidate batch cap reached ({} of limit {}, dates {}) — more "
                            + "eligible rows may remain; the next scheduled pass continues from the "
                            + "oldest remaining row", candidates.size(), limit, tranDts);
        }
        if (aged.isEmpty()) {
            if (capReached) {
                // A full page where nothing passed the age window. Not the N-1 starvation shape any
                // more (the date bound is in SQL), but still worth surfacing: it means the whole
                // window is younger than the delay, i.e. a dispatch burst — or, if it persists, that
                // something is re-filling '6' faster than the sweep drains it.
                log.warn("result-check: full candidate page ({}) but no row passed the {}-minute age "
                                + "window — sweep did no work this pass; investigate if this repeats",
                        candidates.size(), RESULT_CHECK_DELAY_MINUTES);
            } else {
                log.debug("result-check: no aged 202/PROC_STAT=6 transfers "
                        + "(scanned {} candidate(s), limit {})", candidates.size(), limit);
            }
            return;
        }
        Duration deadline = effectivePassDeadline();
        log.info("result-check: {} aged 202 transfer(s) to inquire (of {} candidate(s), limit {}, "
                        + "pass deadline {})", aged.size(), candidates.size(), limit, deadline);
        int processed = 0;
        for (TbFirmTran row : aged) {
            // Per-pass deadline — evaluated BEFORE the row is touched, so a row that starts always
            // finishes (see the passDeadline field Javadoc for why mid-row interruption is unsafe).
            // `now` is the same instant the date/age bounds were derived from, so the budget covers
            // the whole pass including candidate selection, not just the loop.
            Duration elapsed = Duration.between(now, Instant.now());
            if (elapsed.compareTo(deadline) >= 0) {
                log.warn("result-check: pass deadline {} exceeded after {} (processed {} of {} aged "
                                + "row(s)) — stopping; {} row(s) stay at PROC_STAT='6' and the next "
                                + "pass resumes from the oldest of them",
                        deadline, elapsed, processed, aged.size(), aged.size() - processed);
                break;
            }
            processed++;
            boolean pace;
            try {
                pace = reflectOutcome(row);
            } catch (Exception e) {
                // No-response / gateway error: leave at PROC_STAT='6' for the next pass.
                // Legacy paces on the comms-fail branch (PROCESSING_RESULT_CHECK_BATCH:234).
                log.warn("result-check: 203 inquiry deferred for tranSeq={} ({}) — left pending",
                        row.getTranSeq(), e.getMessage());
                pace = true;
            }
            // B5 (audit M-5): per-bank inter-request pacing (Woori 3000ms, else 100ms), keyed on
            // the aged row's OUT_BANK_CD. Legacy PROCESSING_RESULT_CHECK_BATCH sleeps on the
            // ORG_STAT-skip (:108), comms-fail (:234) and original-fail (:393) branches, but NOT
            // after a clean success (6-1 has no Thread.sleep) — reflectOutcome returns that decision.
            // Scan loop only (the event-driven legs are unpaced by design).
            if (pace) {
                dispatchPacer.pause(row.getOutBankCd());
            }
        }
    }

    /**
     * The batch size actually used, guarding against a misconfigured non-positive value.
     *
     * <p>{@code PageRequest.of(0, n)} throws on {@code n < 1}, and that exception would surface from
     * inside the scheduler thread on every fire. Clamping to the documented default keeps the sweep
     * running and logs the correction once per pass instead.
     */
    private int effectiveBatchSize() {
        if (batchSize > 0) {
            return batchSize;
        }
        log.warn("result-check: wecms.dispatch.result-check.batch-size={} is not positive — "
                + "falling back to {}", batchSize, DEFAULT_BATCH_SIZE);
        return DEFAULT_BATCH_SIZE;
    }

    /**
     * The per-pass deadline actually used, guarding against a null / non-positive configured value.
     *
     * <p>A zero or negative budget would break the loop before the first row and the sweep — the only
     * thing that finalizes a transfer already sent to the bank — would silently never run. Clamping to
     * the documented default and saying so once per pass is the safe failure.
     */
    private Duration effectivePassDeadline() {
        if (passDeadline != null && !passDeadline.isZero() && !passDeadline.isNegative()) {
            return passDeadline;
        }
        log.warn("result-check: wecms.dispatch.result-check.pass-deadline={} is not positive — "
                + "falling back to {}", passDeadline, DEFAULT_PASS_DEADLINE);
        return DEFAULT_PASS_DEADLINE;
    }

    /**
     * N-1 — the eligible {@code TRAN_DT} values, as a SQL predicate rather than a post-limit filter.
     *
     * <p>Legacy selects <b>today only</b> ({@code TRAN_DT = TO_CHAR(now(),'YYYYMMDD')}, batch-side
     * {@code IDO.TB_FIRM_TRAN_R001}). This adds the <b>previous</b> UTC date, which is the same
     * deliberate widening {@link #selectAged} already applied — a row dispatched shortly before UTC
     * midnight must not be orphaned by the rollover. Legacy's own answer to that edge was the
     * {@code … < '235800'} guard, which skips the last two minutes of the day and never recovers the
     * affected row.
     *
     * <p>Evaluated in {@link #UTC}, because {@code TRAN_DT} is <b>written</b> in UTC
     * ({@code PymnHelper.pymn_0001_001_c001:2197} via {@code DateTimeUtils.utcNow()}). Comparing it
     * against an ICT date is the D-1 defect and must not be reintroduced here.
     *
     * <p>Ordered newest-first purely for log readability; the query's own {@code ORDER BY} governs row
     * order and is unaffected by the order of this set.
     *
     * @param utcToday current date in {@link #UTC}
     * @return exactly two dates, {@code [today, yesterday]}, never empty
     */
    static List<String> eligibleTranDts(LocalDate utcToday) {
        return List.of(utcToday.format(DT8), utcToday.minusDays(1).format(DT8));
    }

    /**
     * D-1 — the aged-row selection, as a pure function of the candidate rows and the two clocks
     * involved, so every date/time boundary is testable without waiting for one.
     *
     * <p>Two clocks, deliberately:
     * <ul>
     *   <li>{@code nowUtcDate} bounds {@code TRAN_DT}, because {@code TRAN_DT} is written in UTC
     *       (legacy {@code PymnHelper:2197} {@code DateTimeUtils.utcNow()}). Both today and
     *       yesterday are accepted so the midnight rollover cannot orphan a row.</li>
     *   <li>{@code nowIct} bounds the aging comparison, because {@code RQ_DTTM} / {@code TRAN_TM}
     *       are stamped in ICT at dispatch ({@code RealtimeDispatchService:146-148}).</li>
     * </ul>
     *
     * <p>Mixing the two is intentional and is what the row itself looks like; see {@link #isAged}.
     *
     * @param candidates all {@code TRAN_TP='202'} / {@code PROC_STAT='6'} rows
     * @param nowIct     current time in {@link #ICT} (aging clock)
     * @param nowUtcDate current date in {@link #UTC} ({@code TRAN_DT} clock)
     */
    static List<TbFirmTran> selectAged(List<TbFirmTran> candidates, LocalDateTime nowIct, LocalDate nowUtcDate) {
        String utcTodayDt = nowUtcDate.format(DT8);
        String utcYesterdayDt = nowUtcDate.minusDays(1).format(DT8);
        String agedBeforeDttm = nowIct.minusMinutes(RESULT_CHECK_DELAY_MINUTES).format(RQ_DTIME_FMT);
        String cutoffTm = nowIct.minusMinutes(RESULT_CHECK_DELAY_MINUTES).format(TM6);

        return candidates.stream()
                .filter(t -> {
                    String dt = t.getTranDt() == null ? "" : t.getTranDt().trim();
                    return utcTodayDt.equals(dt) || utcYesterdayDt.equals(dt);
                })
                .filter(t -> isAged(t, utcTodayDt, agedBeforeDttm, cutoffTm))
                .toList();
    }

    /**
     * D-1 aging gate — "has this 202 row been pending long enough to re-check?".
     *
     * <p>Legacy asks this with a wrapping wall-clock compare:
     * {@code TRAN_TM <= TO_CHAR(now() - INTERVAL '2 minute','hh24miss')}, plus the guard
     * {@code TO_CHAR(now() - INTERVAL '2 minute','hh24miss') < '235800'}
     * ({@code IDO.TB_FIRM_TRAN_R001.xml}). That guard exists only because an {@code HHmmss}
     * compare is meaningless once the clock rolls past midnight — legacy simply stops selecting
     * anything in the last two minutes of the day rather than handle the wrap.
     *
     * <p>This implementation removes the wrap instead of papering over it, in three tiers — and the
     * order is load-bearing:
     * <ol>
     *   <li><b>{@code RQ_DTTM}</b> — the full 14-char request timestamp stamped at dispatch
     *       ({@code RealtimeDispatchService:240}); a monotonic string compare against
     *       {@code now - delay} in the same zone, which cannot wrap. Every refactor-written row
     *       carries it, so this is the normal path and it is exact at every boundary.</li>
     *   <li><b>Earlier UTC date</b> — only for rows with no {@code RQ_DTTM}
     *       (legacy-JEX-originated): a previous {@code TRAN_DT} means a previous UTC day, so treat
     *       the row as aged. <b>Deliberately not tier 1:</b> in the two minutes after UTC midnight a
     *       row already carries the previous {@code TRAN_DT} while being seconds old, so letting
     *       this shortcut pre-empt {@code RQ_DTTM} would re-check a row before its delay had
     *       elapsed — which {@code ResultCheckSelectionBoundaryTest} caught at 07:00 ICT.</li>
     *   <li><b>{@code TRAN_TM}</b> — the legacy same-day {@code HHmmss} compare, unchanged, for
     *       same-date rows with no {@code RQ_DTTM}.</li>
     * </ol>
     *
     * @param utcTodayDt        today's date in the {@code TRAN_DT} zone ({@link #UTC})
     * @param agedBeforeDttm    {@code now - delay} as {@code yyyyMMddHHmmss}, same zone as {@code RQ_DTTM}
     * @param cutoffTm          {@code now - delay} as {@code HHmmss} (legacy fallback compare)
     */
    private static boolean isAged(TbFirmTran t, String utcTodayDt, String agedBeforeDttm, String cutoffTm) {
        // Tier 1 — RQ_DTTM is the authority whenever it is present, and it is checked FIRST.
        // Ordering matters: the tier-2 date shortcut below is not safe on its own around UTC
        // midnight (a row written one minute before it already carries the previous TRAN_DT while
        // being one minute old), so a date-based shortcut must never pre-empt a real timestamp.
        String rqDttm = t.getRqDttm() == null ? "" : t.getRqDttm().trim();
        if (rqDttm.length() == 14) {
            return rqDttm.compareTo(agedBeforeDttm) <= 0;
        }
        // Tier 2 — no RQ_DTTM (legacy-JEX-written rows): an earlier TRAN_DT means the row is from a
        // previous UTC day, so treat it as aged. Within the two minutes after UTC midnight this can
        // be up to the delay window early; accepted for legacy rows only, because the alternative is
        // reconstructing an instant from a UTC date plus an ICT time-of-day, which is ill-defined.
        // Refactor-written rows never reach here — they always carry RQ_DTTM.
        String tranDt = t.getTranDt() == null ? "" : t.getTranDt().trim();
        if (!utcTodayDt.equals(tranDt)) {
            return true;
        }
        // Tier 3 — same UTC date, no RQ_DTTM: the legacy HHmmss compare, unchanged.
        String tranTm = t.getTranTm() == null ? "" : t.getTranTm().trim();
        return !tranTm.isEmpty() && tranTm.compareTo(cutoffTm) <= 0;
    }

    /**
     * Process one aged 202 row through the 203 processing-result inquiry, mirroring legacy
     * {@code PROCESSING_RESULT_CHECK_BATCH} row body end-to-end:
     * <ol>
     *   <li><b>B4 (audit M-6) — {@code ORG_STAT} gate.</b> Load {@code TB_FIRM_CONF}
     *       ({@code R001}); when the connecting-org state is not {@code '1'} (available),
     *       skip the row (legacy {@code continue}). A missing conf row is skipped too
     *       (defensive — one anomalous row must not abort the scan).</li>
     *   <li><b>B3 (audit M-4) — 203 audit row.</b> Allocate a fresh {@code TRAN_SEQ}
     *       (firm_seq_no funnel) and insert a {@code TRAN_TP='203'} {@code TB_FIRM_TRAN}
     *       row at {@code PROC_STAT='6'} (legacy {@code C001}), pointing back at the
     *       original via {@code ORG_TRAN_DT}/{@code ORG_TRAN_SEQ}. That fresh sequence is
     *       also the inquiry's gateway {@code TNO}.</li>
     *   <li>Issue the 203 inquiry. On a transport failure the 203 row is finalized FAIL
     *       ({@code U001}) + a failure alert fires, and the exception is rethrown so the
     *       <b>original</b> 202 row is left at {@code '6'} for the next pass (legacy
     *       {@code 5-1 실패} branch — never finalizes the original on a comms failure).</li>
     *   <li>On a definitive result, the 203 row is finalized SUCC ({@code U003}) with the
     *       confirmed detail (curren/amt/owner/remarks/org_proc_tm), then the original 202
     *       row ({@code U004}) + the originating {@code TB_PAY_TARGET} ({@code U001}) are
     *       set to {@code '0'}/{@code '3'} from {@code org_resp_cd}. When the original
     *       transaction failed, a {@code "원거래오류"} failure alert fires (legacy {@code 6-2}).</li>
     * </ol>
     *
     * <p><b>Alerts fire on failure only</b> (H2 / audit M-3): legacy sends a Slack alert in
     * the {@code 5-1} (comms fail) and {@code 6-2} (original-tx fail) branches, never on a
     * clean success. A missing pay-target row is logged and skipped, never failing the
     * firm-tran writes or the rest of the scan.
     *
     * @return whether the caller should apply the per-bank pace after this row — {@code true}
     *         on the ORG_STAT-skip / original-fail branches (legacy sleeps), {@code false} on a
     *         clean success (legacy 6-1 has no sleep) or a missing-conf skip. The comms-fail
     *         branch rethrows, and {@link #runOnce()} paces on the caught exception (legacy :234).
     */
    private boolean reflectOutcome(TbFirmTran row) {
        // B4 (audit M-6): connecting-org state gate (legacy TB_FIRM_CONF_R001 + ORG_STAT check).
        TbFirmConf conf = firmConfRepository
                .findById(new TbFirmConfId(row.getBizKey(), row.getOutBankCd(), row.getWdrlActNo()))
                .orElse(null);
        if (conf == null) {
            log.warn("result-check: no TB_FIRM_CONF for bizKey={} outBankCd={} wdrlActNo={} — row skipped",
                    row.getBizKey(), row.getOutBankCd(), row.getWdrlActNo());
            return false; // no legacy equivalent (legacy would abort on a conf error); do not pace
        }
        if (!ORG_ST_OK.equals(conf.getOrgStat())) {
            log.info("result-check: ORG_STAT={} (not '1') for bizKey={} outBankCd={} — inquiry skipped",
                    conf.getOrgStat(), row.getBizKey(), row.getOutBankCd());
            return true; // legacy PROCESSING_RESULT_CHECK_BATCH:108 paces on the ORG_STAT skip
        }

        String vanOrgCd = conf.getVanOrgCd();
        String bankPrtnId = conf.getBankPrtnId();
        LocalDateTime now = LocalDateTime.now(ICT);
        String tranDt = now.format(DT8);
        String tranTm = now.format(TM6);
        String dttm14 = now.format(RQ_DTIME_FMT);

        // B3 (audit M-4): insert the TRAN_TP='203' inquiry audit row at PROC_STAT='6' (legacy C001),
        // with a fresh firm_seq_no as both its PK and the inquiry's gateway TNO.
        String inqSeq = legacyFunctions.nextTbFirmTranSeq();
        TbFirmTran inq = new TbFirmTran();
        inq.setTranDt(tranDt);
        inq.setBizKey(row.getBizKey());
        inq.setTranSeq(inqSeq);
        inq.setOutBankCd(row.getOutBankCd());
        inq.setBankPrtnId(bankPrtnId);
        inq.setTranTp(TRAN_TP_PROCESS_INQ);
        inq.setTranTm(tranTm);
        inq.setVanOrgCd(vanOrgCd);
        inq.setWdrlActNo(row.getWdrlActNo());
        inq.setTarBankCd(row.getTarBankCd());
        inq.setProcStat(ProcStat.REQ.code());
        inq.setOrgTranDt(row.getTranDt());
        inq.setOrgTranSeq(row.getTranSeq());
        inq.setRqDttm(dttm14);
        tbFirmTranRepository.save(inq);

        ResultCheckInquiryResult res;
        try {
            res = inquire(new ResultCheckInquiryCommand(
                    vanOrgCd, row.getOutBankCd(), bankPrtnId,
                    row.getTranDt(), row.getTranSeq(), row.getWdrlActNo()), inqSeq);
        } catch (BusinessException e) {
            // Legacy 5-1 실패: comms failure → finalize the 203 row FAIL (U001) + failure alert,
            // and leave the ORIGINAL 202 row at '6' by rethrowing (runOnce logs + moves on).
            String rc = e.getArgs() != null && e.getArgs().length > 0 && e.getArgs()[0] != null
                    ? e.getArgs()[0].toString() : FirmBankingResultCd.ETC_ERR;
            inq.setProcStat(ProcStat.FAIL.code());
            inq.setRsCd(rc);
            inq.setRsMsg(e.getCode());
            inq.setRsDttm(LocalDateTime.now(ICT).format(RQ_DTIME_FMT));
            tbFirmTranRepository.save(inq);
            failureAlert(vanOrgCd, "처리결과조회 Batch", row.getOutBankCd(), bankPrtnId,
                    inqSeq, row.getTranSeq(), rc, e.getCode(), null, null);
            throw e;
        }

        boolean success = FirmBankingResultCd.isSuccess(res.orgRespCd());
        String procStat = success ? ProcStat.SUCC.code() : ProcStat.FAIL.code();
        String rsDttm = LocalDateTime.now(ICT).format(RQ_DTIME_FMT);

        // 203 row → SUCC (legacy U003): the inquiry itself succeeded; carry the confirmed detail.
        inq.setProcStat(ProcStat.SUCC.code());
        inq.setRsCd(res.orgRespCd());
        inq.setRsMsg(res.orgRespMsg());
        inq.setTrscCcyCd(res.currenCd());
        inq.setTranAmt(combineAmount(res.amt(), res.dcmAmt()));
        inq.setFincMdaNo(res.tarFincMdaNo());
        inq.setFincMdaOwnerNm(res.fincMdaOwnerNm());
        inq.setWdrlAcctRmrk(res.withdAcctRmrk());
        inq.setTarAcctRmrk(res.tarAcctRmrk());
        inq.setOrgProcTm(res.orgProcTm());
        inq.setOrgRespCd(res.orgRespCd());
        inq.setOrgRespMsg(res.orgRespMsg());
        inq.setRsDttm(rsDttm);
        tbFirmTranRepository.save(inq);

        // Item 1 (audit M-7): finalize the original 202 firm-tran (U004) + the originating
        // TB_PAY_TARGET (U001) atomically in ONE REQUIRES_NEW transaction (legacy one-tx pair).
        // A failure here rolls BOTH back (never firm-tran '0'/'3' while pay-target stays '6') and
        // propagates so runOnce logs it and moves on — one failed row never blocks the others.
        dispatchResultTxService.finalizeOriginalAndPayTarget(
                row.getBizKey(), row.getTranDt(), row.getTranSeq(),
                procStat, res.orgRespCd(), res.orgRespMsg(), rsDttm, res.fincMdaOwnerNm());

        // H2 (audit M-3): failure-only alert. Legacy fires "처리결과조회 Batch 원거래오류" (with the
        // [ORG Result] block) only in the 6-2 original-transaction-failed branch (:391), never on success.
        if (!success) {
            String commsRc = res.orgProcTm() == null && FirmBankingResultCd.NO_FOUND.equals(res.orgRespCd())
                    ? FirmBankingResultCd.NO_FOUND : FirmBankingResultCd.SUCC;
            failureAlert(vanOrgCd, "처리결과조회 Batch 원거래오류", row.getOutBankCd(), bankPrtnId,
                    inqSeq, row.getTranSeq(), commsRc, "", res.orgRespCd(), res.orgRespMsg());
        }

        log.info("result-check: tranSeq={} confirmed orgRespCd={} -> PROC_STAT={} (203 row tranSeq={})",
                row.getTranSeq(), res.orgRespCd(), procStat, inqSeq);

        // Legacy paces on the original-fail branch (:393) but NOT on a clean success (6-1).
        return !success;
    }

    /**
     * Build + send a failure ops alert (legacy {@code PROCESSING_RESULT_CHECK_BATCH:222-232}
     * comms-fail and {@code :378-391} original-tx-fail). The {@code [ORG Result]} block is
     * appended only when {@code orgRc} is supplied (the original-tx-fail case). The
     * {@code [ENV][WAS_NO]} server marker is attached by {@link OpsAlertService}.
     */
    private void failureAlert(String vanOrgCd, String header, String bankCd, String prtnId,
                              String tno, String orgTno, String rc, String rm,
                              String orgRc, String orgRm) {
        StringBuilder sb = new StringBuilder(header)
                .append("\nID : ").append(vanOrgCd)
                .append("\nbank_cd : ").append(bankCd)
                .append("\nprtn_id : ").append(prtnId)
                .append("\nTNO : ").append(tno)
                .append("\nORG TNO : ").append(orgTno)
                .append("\n[Result]")
                .append("\nRC : ").append(rc == null ? "" : rc)
                .append("\nRM : ").append(rm == null ? "" : rm);
        if (orgRc != null) {
            sb.append("\n[ORG Result]")
                    .append("\nORG RC : ").append(orgRc)
                    .append("\nORG RM : ").append(orgRm == null ? "" : orgRm);
        }
        opsAlertService.notify(vanOrgCd, sb.toString());
    }

    /** Combine an integer + decimal-portion amount pair into one {@link BigDecimal} (blank itg → null). */
    private static BigDecimal combineAmount(String itg, String dcm) {
        if (!StringUtils.hasText(itg)) {
            return null;
        }
        String d = StringUtils.hasText(dcm) ? dcm.trim() : "0";
        return new BigDecimal(itg.trim() + "." + d);
    }

    private void validate(ResultCheckInquiryCommand cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("ResultCheckInquiryCommand is required");
        }
        if (!StringUtils.hasText(cmd.vanOrgCd())) {
            throw new IllegalArgumentException("vanOrgCd is required");
        }
        if (!StringUtils.hasText(cmd.orgTranDt())) {
            throw new IllegalArgumentException("orgTranDt is required");
        }
        if (!StringUtils.hasText(cmd.orgTno())) {
            throw new IllegalArgumentException("orgTno is required");
        }
        if (!StringUtils.hasText(cmd.macctNo())) {
            throw new IllegalArgumentException("macctNo is required");
        }
    }

    private String newTno(String rqDtime) {
        String tno = rqDtime + UUID.randomUUID().toString().replace("-", "");
        return tno.length() > TNO_MAX ? tno.substring(0, TNO_MAX) : tno;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            // Never include the payload in the message (it carries account data).
            throw new IllegalStateException("Failed to serialise EFS 203 request", e);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse EFS 203 response", e);
        }
    }

    /** Mask all but the last 4 chars of an account number for logs. */
    private String mask(String acct) {
        if (acct == null || acct.length() <= 4) {
            return "****";
        }
        return "****" + acct.substring(acct.length() - 4);
    }
}
