package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.banking.entity.BankExchRateInfm;
import com.kosign.wecms.domain.banking.nxisas.NxisasGatewayClient;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasApiOut;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasForeignExchangeEntry;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasForeignExchangeJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasForeignExchangeJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasOutput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasResult;
import com.kosign.wecms.common.enums.PrdtType;
import com.kosign.wecms.domain.banking.repository.BankExchRateInfmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Exchange Rate batch — port of legacy BCS jobs
 * {@code bcs.jex.wabooks.exch.EXCH_0001_001} /
 * {@code EXCH_0010_001} / {@code EXCH_0020_001}. Each legacy job has
 * distinct timing AND distinct persistence semantics; the refactor
 * keeps them as three {@code @Transactional} trigger methods
 * ({@link #runShinhanFirstTime()}, {@link #runShinhanLastTime()},
 * {@link #runWoori()}) + one shared on-demand entry point
 * ({@link #runOnDemand()}).
 *
 * <h2>Scheduling — OFF BY DEFAULT (G0)</h2>
 * <p>This service holds NO {@code @Scheduled} annotations. In the
 * live-JEX-DB coexistence model the LEGACY JEX batch owns FX collection
 * into {@code BANK_EXCH_RATE_INFM}; the refactor must not auto-collect or
 * it would double-write / race the legacy batch against the shared DB.
 * The {@code @Scheduled} + {@code @SchedulerLock} triggers live on the
 * gated {@link ExchangeRateBatchScheduler}, which is created ONLY when
 * {@code wecms.exchange-rate.batch.enabled=true} (absent/false → no FX
 * job fires). This service bean is always present so
 * {@link ExchangeRateService#runOnDemand the on-demand path} and DI keep
 * working regardless of the gate.
 *
 * <h2>Schedule when enabled — confirmed against JEX Studio
 * (all anchored to {@code Asia/Ho_Chi_Minh})</h2>
 * <table>
 *   <tr><th>Job</th><th>Cron (ICT)</th><th>Fires/day</th><th>Window</th></tr>
 *   <tr><td>{@link #runShinhanFirstTime()} (EXCH_0001_001)</td>
 *       <td>{@code 0 30 8 * * ?}</td><td>1</td><td>08:30 once</td></tr>
 *   <tr><td>{@link #runShinhanLastTime()} (EXCH_0010_001)</td>
 *       <td>{@code 0 30 8-18 * * ?}</td><td>11</td><td>08:30–18:30 hourly</td></tr>
 *   <tr><td>{@link #runWoori()} (EXCH_0020_001)</td>
 *       <td>{@code 0 30 8 * * ?} + {@code 0 0,30 9-20 * * ?}</td><td>25</td><td>08:30–20:30 every 30 min</td></tr>
 * </table>
 *
 * <p>JEX Studio start/end windows: EXCH_0001_001 08:30:00–08:59:59 (once);
 * EXCH_0010_001 08:30:05–18:30:00 (hourly); EXCH_0020_001 08:30:00–20:30:00
 * (every 30 min). The refactor fires EXCH_0010 at 08:30:00 rather than the
 * legacy 08:30:05 — an accepted, harmless 5-second offset (Spring cron resolves
 * to the same minute; the batch is idempotent per round).</p>
 *
 * <h2>Persistence semantics (one mode per legacy job)</h2>
 * <ul>
 *   <li><b>UPSERT</b> — EXCH_0001 Shinhan first-time: SEQ_NO=1 row is
 *       inserted if missing, updated if exists. Legacy:
 *       {@code EXCH_0001_001#execute} lines 213-241.</li>
 *   <li><b>SKIP_IF_EXISTS (SEQ_NO=1) + UPSERT (SEQ_NO=2)</b> —
 *       EXCH_0010 Shinhan last-time: SEQ_NO=1 first-time data is
 *       preserved (no UPDATE), SEQ_NO=2 is the rolling intra-day
 *       capture. Legacy: {@code EXCH_0010_001#execute} lines 212-240.</li>
 *   <li><b>INSERT_NEW (MAX+1)</b> — EXCH_0020 Woori: every batch run
 *       inserts a fresh round with {@code SEQ_NO = MAX(existing)+1},
 *       never updates. Legacy: {@code EXCH_0020_001#execute} lines
 *       91-104 + 216-220.</li>
 * </ul>
 *
 * <p>All three jobs share the same Coocon NXISAS smart-gateway via
 * {@link NxisasGatewayClient} (same URL + key from
 * {@code wecms.coocon.smart-api-*}, same audit logger, same timeout,
 * same transport-exception type) — see Account VND/FCY and Card
 * Transaction scraping for the canonical client pattern.
 *
 * <p>FX scraping does NOT carry a session — every call is a standalone
 * (Module, Class, Job=FOREIGN_EXCHANGE, Input) envelope with an empty
 * {@code SAS_SessionID}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateBatchService {

    private final BusiInfmRepository busiInfmRepository;
    private final BankExchRateInfmRepository bankExchRateInfmRepository;
    private final NxisasGatewayClient gateway;
    private final com.kosign.wecms.common.legacy.LegacyFunctionRepository legacyFunctions;

    private static final String SYSTEM_USER = "SYSTEM_BATCH";
    private static final String VIETNAM_TZ_ID = "Asia/Ho_Chi_Minh";
    private static final ZoneId VIETNAM_TZ = ZoneId.of(VIETNAM_TZ_ID);
    private static final DateTimeFormatter DATE8  = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DTM_14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** Legacy gateway success — strict bank-adapter-level check.
     *  Mirrors EXCH_*001_001 which only treats {@code "00000000"} as
     *  success (no {@code "0000"} / {@code "OK"} short forms). */
    private static final String OK_CODE = "00000000";

    // Bank metadata — verbatim from legacy EXCH_*001_001.
    private static final String SHINHAN_BANK_CD = "11704088";
    private static final String SHINHAN_MODULE  = "GLOBAL/GBL/BANK_Shinhan";
    private static final String SHINHAN_CLASS   = "COMMON";

    private static final String WOORI_BANK_CD = "11704020";
    private static final String WOORI_MODULE  = "GLOBAL/GBL/BANK_Woori";
    private static final String WOORI_CLASS   = "CORPORATE";

    /** Legacy {@code EXCH_0020} probes for the highest existing seq_no
     *  using a hardcoded {@code ccy_cd = 'VND'} filter. Preserved
     *  verbatim — see {@code EXCH_0020_001#execute} line 82 + 96-100. */
    private static final String WOORI_SEQ_PROBE_CCY = "VND";

    // ── Tenant fan-out filter (G1) — verbatim legacy BUSI_INFM_R001 ──────────
    /** {@code del_yn = 'N'} — exclude soft-deleted companies. */
    private static final String DEL_YN_NO = "N";
    /** {@code busi_stts = 'N'} — legacy {@code BusiStts.NORMAL}; excludes
     *  W (waiting) / S (suspended) / C (closed). */
    private static final String BUSI_STTS_NORMAL = "N";
    /** {@code prdt_type IN ('F','M','H')} — the refactor-recognised product
     *  types (the same set every legacy EXCH job filters on). Sourced from
     *  {@link PrdtType} so it stays in lock-step with the enum. */
    private static final List<String> FX_FAN_OUT_PRDT_TYPES =
            List.of(PrdtType.F.code(), PrdtType.M.code(), PrdtType.H.code());

    /** Per-row persistence mode used by the shared persist loop. */
    private enum PersistMode {
        /** EXCH_0001: insert if missing, update if exists. */
        UPSERT,
        /** EXCH_0010 SEQ_NO=1 branch: insert if missing, skip if exists
         *  (preserves first-time data). */
        SKIP_IF_EXISTS,
        /** EXCH_0020: always insert. Caller has pre-computed the new
         *  {@code seqNo = MAX(existing)+1} per legacy semantics; no
         *  UPDATE path. */
        INSERT_NEW
    }

    // ─────────────────────────────────────────────────────────────────
    // EXCH_0001_001 — Shinhan first-time (once per day at 08:30 ICT)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Legacy parity: {@code bcs.jex.wabooks.exch.EXCH_0001_001}.
     * Fires once per day at 08:30 ICT. Fetches Shinhan SEQ_NO=1 and
     * UPSERTs per (tenant × counter currency).
     */
    @Transactional
    public void runShinhanFirstTime() {
        log.info("EXCH_0001_001 — Shinhan first-time run starting");
        executeShinhanFirstTime(todayIctDate(), nowUtcDtm());
    }

    // ─────────────────────────────────────────────────────────────────
    // EXCH_0010_001 — Shinhan last-time (hourly 08:30–18:30 ICT)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Legacy parity: {@code bcs.jex.wabooks.exch.EXCH_0010_001}.
     * Fires every hour at 08:30, 09:30, …, 18:30 ICT (11 times/day).
     * For each tenant × counter currency:
     * <ul>
     *   <li>SEQ_NO=1 — INSERT if missing, skip if exists (preserves the
     *       first-time data created at 08:30).</li>
     *   <li>SEQ_NO=2 — UPSERT (the rolling intra-day capture).</li>
     * </ul>
     */
    @Transactional
    public void runShinhanLastTime() {
        log.info("EXCH_0010_001 — Shinhan last-time run starting");
        executeShinhanLastTime(todayIctDate(), nowUtcDtm());
    }

    // ─────────────────────────────────────────────────────────────────
    // EXCH_0020_001 — Woori (every 30 minutes 08:30–19:30 ICT)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Legacy parity: {@code bcs.jex.wabooks.exch.EXCH_0020_001}.
     * Fires every 30 minutes in the window 08:30–20:30 ICT (25 fires/day,
     * matching the confirmed JEX Studio schedule: start 08:30:00, end
     * 20:30:00, every 30 min):
     * <ul>
     *   <li>08:30 — single fire at the start of the window (first cron)</li>
     *   <li>09:00, 09:30, 10:00, …, 20:00, 20:30 — :00 and :30 of every
     *       hour from 09 through 20 (second cron, 24 fires)</li>
     * </ul>
     *
     * <p>The split into two {@code @Scheduled} annotations avoids the
     * 08:00 fire that a single {@code 0 0,30 8-20 * * ?} pattern would
     * trigger — that fire is 30 min before the legacy window opens.
     * Spring resolves multiple {@code @Scheduled} annotations on the
     * same method as independent triggers; the body still runs in the
     * scheduler thread's single-threaded executor so the two crons can
     * never overlap.
     *
     * <p>For each batch run:
     * <ol>
     *   <li>Compute {@code nextSeqNo = MAX(seq_no where bank=Woori,
     *       ref_date=today, ccy_cd='VND')+1}, defaulting to 1.</li>
     *   <li>Call Coocon for {@code seqNo = nextSeqNo}.</li>
     *   <li>On success, INSERT one new row per (tenant × counter
     *       currency) with {@code seq_no = nextSeqNo}. No UPDATE path.</li>
     * </ol>
     *
     * <p><b>G2 — Woori strategy decision (open).</b> This ports legacy
     * {@code EXCH_0020_001} (MAX+1, one notification round per run). Legacy
     * also has a newer combined job {@code EXCH_0030_001} that loops a FIXED
     * Woori range {@code seq 1..15} with insert-if-missing (a single-run
     * catch-up of every round). Which job is the live PROD scheduler could
     * not be confirmed from source (no batch-schedule config was found), and
     * the live DB (2 Woori rounds/day) is consistent with both. The
     * fixed-1..15 catch-up is intentionally NOT ported here — porting it
     * requires PO/ops confirmation that PROD runs EXCH_0030_001. Until then
     * the MAX+1 behaviour stands; when the FX schedule is enabled at the
     * legacy cadence (every 30 min) it captures each round as it appears,
     * which is equivalent unless a run is missed.
     */
    @Transactional
    public void runWoori() {
        log.info("EXCH_0020_001 — Woori run starting");
        executeWoori(todayIctDate(), nowUtcDtm());
    }

    // ─────────────────────────────────────────────────────────────────
    // On-demand entry point — called by ExchangeRateService.list when
    // today has no data yet. REQUIRES_NEW suspends the caller's
    // (readOnly) transaction so writes can land in a fresh writable tx.
    // ─────────────────────────────────────────────────────────────────

    /**
     * On-demand catch-up — runs all three legacy jobs sequentially so
     * a user opening the Exchange Rate page on an empty day gets a
     * fully-populated snapshot in one round-trip.
     *
     * <p>{@link Propagation#REQUIRES_NEW} suspends any outer transaction
     * (in particular {@code ExchangeRateService.list}'s
     * {@code @Transactional(readOnly = true)}) and starts a fresh
     * writable transaction for the batch work. Mandatory — without it
     * the inner UPSERTs would join the read-only outer tx and fail
     * under strict JDBC drivers.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void runOnDemand() {
        log.info("Exchange Rate on-demand catch-up starting");
        String inqDate = todayIctDate();
        String nowDtm  = nowUtcDtm();
        executeShinhanFirstTime(inqDate, nowDtm);
        executeShinhanLastTime(inqDate, nowDtm);
        executeWoori(inqDate, nowDtm);
    }

    // ─────────────────────────────────────────────────────────────────
    // Per-job execution
    // ─────────────────────────────────────────────────────────────────

    private void executeShinhanFirstTime(String inqDate, String nowDtm) {
        List<BusiInfm> tenants = activeTenants();
        if (tenants.isEmpty()) {
            log.info("EXCH_0001_001 — no active tenants, nothing to do");
            return;
        }
        fetchAndPersist(SHINHAN_BANK_CD, SHINHAN_MODULE, SHINHAN_CLASS,
                inqDate, "1", "1", tenants, nowDtm, PersistMode.UPSERT);
    }

    private void executeShinhanLastTime(String inqDate, String nowDtm) {
        List<BusiInfm> tenants = activeTenants();
        if (tenants.isEmpty()) {
            log.info("EXCH_0010_001 — no active tenants, nothing to do");
            return;
        }
        // SEQ_NO=1 — preserve any first-time data (INSERT-if-missing only).
        fetchAndPersist(SHINHAN_BANK_CD, SHINHAN_MODULE, SHINHAN_CLASS,
                inqDate, "1", "1", tenants, nowDtm, PersistMode.SKIP_IF_EXISTS);
        // SEQ_NO=2 — the rolling intra-day capture (UPSERT).
        fetchAndPersist(SHINHAN_BANK_CD, SHINHAN_MODULE, SHINHAN_CLASS,
                inqDate, "2", "2", tenants, nowDtm, PersistMode.UPSERT);
    }

    private void executeWoori(String inqDate, String nowDtm) {
        List<BusiInfm> tenants = activeTenants();
        if (tenants.isEmpty()) {
            log.info("EXCH_0020_001 — no active tenants, nothing to do");
            return;
        }
        int nextSeqInt = bankExchRateInfmRepository
                .findHighestSeqNo(WOORI_BANK_CD, inqDate, WOORI_SEQ_PROBE_CCY)
                .map(ExchangeRateBatchService::parseIntOrZero)
                .orElse(0) + 1;
        String requestSeq = String.valueOf(nextSeqInt);
        String persistSeq = requestSeq;
        log.info("EXCH_0020_001 — Woori next seq_no = {}", requestSeq);
        // Legacy passes the freshly-computed seq_no to BOTH the Coocon
        // request and the persisted SEQ_NO column — see EXCH_0020 lines
        // 173 + 300.
        fetchAndPersist(WOORI_BANK_CD, WOORI_MODULE, WOORI_CLASS,
                inqDate, requestSeq, persistSeq, tenants, nowDtm, PersistMode.INSERT_NEW);
    }

    // ─────────────────────────────────────────────────────────────────
    // Shared gateway-call + persist loop
    // ─────────────────────────────────────────────────────────────────

    /**
     * One Coocon FOREIGN_EXCHANGE call for the given (bank, requestSeqNo)
     * pair, followed by per-(tenant × counter currency) persistence
     * under the supplied {@link PersistMode}. The {@code persistSeqNo}
     * is the value stamped into the {@code SEQ_NO} column (legacy
     * EXCH_0001 / 0010 pass {@code 1}/{@code 2}; EXCH_0020 passes
     * {@code MAX+1}).
     */
    private void fetchAndPersist(String bankCd, String module, String category,
                                 String inqDate, String requestSeqNo, String persistSeqNo,
                                 List<BusiInfm> tenants, String nowDtm,
                                 PersistMode mode) {
        NxisasApiOut<NxisasForeignExchangeJobResult> apiOut =
                callGateway(bankCd, module, category, inqDate, requestSeqNo);
        if (apiOut == null) {
            // Transport failure — already logged + audited by
            // NxisasGatewayClient. Nothing to persist.
            return;
        }
        List<NxisasResult<NxisasForeignExchangeJobResult>> results = apiOut.getResultList();
        if (results == null || results.isEmpty()) {
            log.debug("EXCH bank={} seqNo={} — empty resultList", bankCd, requestSeqNo);
            return;
        }

        int inserted = 0;
        int updated  = 0;
        int skipped  = 0;
        int errors   = 0;

        for (NxisasResult<NxisasForeignExchangeJobResult> entry : results) {
            NxisasOutput<NxisasForeignExchangeJobResult> out = entry.getOutput();
            if (out == null) continue;
            String errCode = out.getErrorCode();
            if (!OK_CODE.equals(errCode)) {
                log.info("EXCH bank={} seqNo={} adapter rejected — errorCode={}, msg={}",
                        bankCd, requestSeqNo, errCode, out.getErrorMessage());
                continue;
            }
            NxisasForeignExchangeJobResult result = out.getResult();
            if (result == null) continue;
            List<NxisasForeignExchangeEntry> rates = result.getForeignExchange();
            if (rates == null || rates.isEmpty()) continue;
            String standPoint = result.getStandPoint();

            for (BusiInfm tenant : tenants) {
                for (NxisasForeignExchangeEntry rate : rates) {
                    String ccyCd    = rate.getCounterCurrency();   // VND for Shinhan/Woori VN
                    String refCcyCd = rate.getBaseCurrency();      // USD, KRW, JPY, … — the discriminator
                    try {
                        // Diagnostic: confirm the discriminator on the wire — ccy should be
                        // VND (constant) and ref_ccy should differ per currency. If a deployed
                        // build is stale, the lookup below would be the 5-arg ccy-only form and
                        // rows would collapse. Enable with logging.level for this class = DEBUG.
                        log.debug("EXCH persist probe: bank={} date={} seq={} biz_key={} ccy={} ref_ccy={}",
                                bankCd, inqDate, persistSeqNo, tenant.getBizKey(), ccyCd, refCcyCd);
                        // Legacy existence/upsert key includes ref_ccy_cd — without it,
                        // every currency of a round (all ccy_cd='VND') collapses onto one
                        // logical row. See EXCH_0010_001.java:202.
                        Optional<BankExchRateInfm> existing = bankExchRateInfmRepository
                                .findByBizKeyAndBankCdAndRefDateAndRefCcyCdAndCcyCdAndSeqNo(
                                        tenant.getBizKey(), bankCd, inqDate, refCcyCd, ccyCd, persistSeqNo);

                        if (existing.isPresent()) {
                            if (mode == PersistMode.SKIP_IF_EXISTS) {
                                skipped++;
                                continue;
                            }
                            if (mode == PersistMode.INSERT_NEW) {
                                // Defensive — should not happen because
                                // INSERT_NEW always allocates a fresh
                                // seq_no. Treat as a race / re-run and
                                // skip rather than throw.
                                log.warn("EXCH_0020 INSERT_NEW collision on (biz_key={}, bank={}, date={}, ref_ccy={}, ccy={}, seq={}) — skipping",
                                        tenant.getBizKey(), bankCd, inqDate, refCcyCd, ccyCd, persistSeqNo);
                                skipped++;
                                continue;
                            }
                            BankExchRateInfm entity = existing.get();
                            applyRate(entity, rate, standPoint, inqDate, persistSeqNo,
                                    bankCd, module, category, nowDtm, /* isNew */ false);
                            bankExchRateInfmRepository.save(entity);
                            updated++;
                        } else {
                            // Mode irrelevant — every mode allows INSERT
                            // when nothing exists yet.
                            BankExchRateInfm entity = new BankExchRateInfm();
                            entity.setBizKey(tenant.getBizKey());
                            entity.setBankCd(bankCd);
                            entity.setRefDate(inqDate);
                            entity.setCcyCd(ccyCd);
                            // EXCH_SEQ_NO via the single wb_get_next_val funnel
                            // (V140 #32 — per (biz_key, bank_cd, ref_date, ccy_cd)
                            // MAX+1, matching the live JEX function's scoping). The
                            // counter is per (biz,bank,date,VND) so it runs continuously
                            // across rounds: seq_no=1 → 1..12, seq_no=2 → 13..24.
                            entity.setExchSeqNo(legacyFunctions.nextBankExchRateExchSeqNo(
                                    tenant.getBizKey(), bankCd, inqDate, ccyCd));
                            entity.setRegDtm(nowDtm);
                            entity.setRegUser(SYSTEM_USER);
                            applyRate(entity, rate, standPoint, inqDate, persistSeqNo,
                                    bankCd, module, category, nowDtm, /* isNew */ true);
                            bankExchRateInfmRepository.save(entity);
                            inserted++;
                        }
                    } catch (Exception e) {
                        log.error("EXCH save failure bank={} date={} seq={} ref_ccy={} ccy={} biz_key={}: {}",
                                bankCd, inqDate, persistSeqNo, refCcyCd, ccyCd, tenant.getBizKey(), e.getMessage());
                        errors++;
                    }
                }
            }
        }

        log.info("EXCH bank={} seqNo={} mode={} — inserted={} updated={} skipped={} errors={} tenants={}",
                bankCd, persistSeqNo, mode, inserted, updated, skipped, errors, tenants.size());
    }

    /** Apply the rate fields to an entity. {@code isNew} switches the
     *  audit-stamp set written (legacy distinguishes REG_* on insert
     *  vs MOD_* on update). */
    private void applyRate(BankExchRateInfm entity, NxisasForeignExchangeEntry rate,
                           String standPoint, String inqDate, String seqNo,
                           String bankCd, String module, String category,
                           String nowDtm, boolean isNew) {
        entity.setRefCcyCd(rate.getBaseCurrency());
        entity.setTranSellAmt(parseDecimal(rate.getTransferSell()));
        entity.setTranBuyAmt(parseDecimal(rate.getTransferBuy()));
        entity.setBuyAmt(parseDecimal(rate.getBuy()));
        entity.setSellAmt(parseDecimal(rate.getSell()));
        entity.setBasicRate(parseDecimal(rate.getBasicRate()));
        entity.setBasicTran(parseDecimal(rate.getBasicTransfer()));
        entity.setSrchDt(inqDate);
        entity.setClassExch(category);
        entity.setModule(module);
        entity.setStandPoint(standPoint);
        entity.setLastUpdateDate(rate.getBaseDate());
        entity.setLastUpdateTime(rate.getBaseTime());
        entity.setSeqNo(seqNo);
        entity.setExchRateRegDtm(
                (rate.getBaseDate() == null ? "" : rate.getBaseDate())
              + (rate.getBaseTime() == null ? "" : rate.getBaseTime()));
        if (!isNew) {
            entity.setModDtm(nowDtm);
            entity.setModId(SYSTEM_USER);
        }
    }

    /**
     * Issue one FOREIGN_EXCHANGE call. Returns {@code null} on transport
     * failure (already audited by {@link NxisasGatewayClient}).
     */
    private NxisasApiOut<NxisasForeignExchangeJobResult> callGateway(
            String bankCd, String module, String category, String inqDate, String seqNo) {
        String trSeq = gateway.newTrSeq();
        NxisasForeignExchangeJobInput input = new NxisasForeignExchangeJobInput(inqDate, seqNo);
        try {
            return gateway.foreignExchange(module, category, trSeq, input);
        } catch (NxisasGatewayClient.NxisasTransportException ex) {
            log.warn("NXISAS FOREIGN_EXCHANGE transport failed [bank={}, seqNo={}, inqDate={}]: {}",
                    bankCd, seqNo, inqDate, ex.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    /**
     * Active tenants the rates are fanned out to — G1 parity fix.
     *
     * <p>Matches the legacy {@code BUSI_INFM_R001} filter used by every
     * exchange-rate BCS job verbatim:
     * {@code del_yn = 'N' AND busi_stts = 'N' (NORMAL) AND prdt_type IN ('F','M','H')}.
     * The previous {@code findAll().filter(delYn != 'Y')} over-included
     * suspended/closed companies (busi_stts W/S/C), {@code del_yn = NULL}
     * rows, and non-F/M/H product types — none of which the legacy batch
     * collects rates for. Filtering is pushed to the DB via the repository
     * derived query rather than loading every {@code BUSI_INFM} row.
     */
    private List<BusiInfm> activeTenants() {
        return busiInfmRepository.findByDelYnAndBusiSttsAndPrdtTypeIn(
                DEL_YN_NO, BUSI_STTS_NORMAL, FX_FAN_OUT_PRDT_TYPES);
    }

    private static String todayIctDate() {
        return LocalDate.now(VIETNAM_TZ).format(DATE8);
    }

    private static String nowUtcDtm() {
        return LocalDateTime.now(ZoneId.of("UTC")).format(DTM_14);
    }

    private static int parseIntOrZero(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static BigDecimal parseDecimal(String value) {
        if (value == null || value.trim().isEmpty()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.trim().replaceAll(",", ""));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
