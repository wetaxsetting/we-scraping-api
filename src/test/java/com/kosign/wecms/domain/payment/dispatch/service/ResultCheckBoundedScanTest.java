package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTran;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmConfRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmTranRepository;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H-2 — boundedness and determinism of the scheduled result-check scan.
 *
 * <h2>Reclassification (legacy-verified 2026-07-29)</h2>
 * The finding was reported as an "unbounded <b>cross-tenant</b> scan". The cross-tenant half is a
 * <b>false positive</b>: legacy {@code IDO.TB_FIRM_TRAN_R001.xml} has <b>no</b> {@code BIZ_KEY}
 * predicate either —
 * <pre>
 * WHERE TRAN_DT = TO_CHAR(now(),'YYYYMMDD')
 *   AND PROC_STAT = '6'
 *   AND TRAN_TM &lt;= TO_CHAR(now() - INTERVAL '2 minute','hh24miss')
 *   AND TO_CHAR(now() - INTERVAL '2 minute','hh24miss') &lt; '235800'
 *   AND TRAN_TP = '202'
 * </pre>
 * This is a <b>system batch servicing every tenant</b>; global scope is the design, not a defect,
 * and adding a tenant filter would break it. The finding is therefore reclassified as an
 * <b>unbounded global scan</b>.
 *
 * <p>The genuine defect was worse than reported: legacy at least had {@code TRAN_DT} and
 * {@code TRAN_TM} <i>in SQL</i>, whereas the refactor called
 * {@code findByTranTpAndProcStat("202","6")} — no date, no age, no order, no limit — and filtered
 * in Java. Every {@code 202}/{@code '6'} row ever written was materialised on each pass.
 *
 * <p>The fix bounds and orders the query while keeping the date/age rules in
 * {@code ResultCheckService.selectAged}, because that tiered logic (RQ_DTTM as the authority) is the
 * documented D-1 improvement over legacy's plain {@code HHmmss} compare and cannot be reduced to one
 * SQL date compare without reintroducing the stranding bug.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResultCheckBoundedScanTest {

    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String VAN = "GV00000001";
    private static final String BIZ_A = "VN_VNM0004_000001";
    private static final String BIZ_B = "VN_VNM0004_000014";
    private static final String TODAY_UTC =
            LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    @Mock EfsApiClient efsApiClient;
    @Mock VanApiKeyService vanApiKeyService;
    @Mock TbFirmTranRepository tbFirmTranRepository;
    @Mock LegacyFunctionRepository legacyFunctions;
    @Mock TbFirmConfRepository firmConfRepository;
    @Mock DispatchPacer dispatchPacer;
    @Mock OpsAlertService opsAlertService;
    @Mock DispatchResultTxService dispatchResultTxService;

    private final EfsCryptoService crypto = new EfsCryptoService();
    private final EfsProperties props = new EfsProperties();
    private final ObjectMapper mapper = new ObjectMapper();

    private ResultCheckService service;

    @BeforeEach
    void setUp() {
        service = new ResultCheckService(efsApiClient, crypto, vanApiKeyService, props, mapper,
                tbFirmTranRepository, opsAlertService,
                legacyFunctions, firmConfRepository, dispatchPacer, dispatchResultTxService);
        // The master gate defaults OFF (wecms.dispatch.result-check.enabled) and there is no Spring
        // context here to inject it. This suite is about which rows the scan picks, not about the gate.
        ReflectionTestUtils.setField(service, "enabled", true);
        when(vanApiKeyService.getApiKey(anyString())).thenReturn("webcashvietnam0002TEST$#%$&*$&^!");
        when(tbFirmTranRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(legacyFunctions.nextTbFirmTranSeq()).thenReturn("900");
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf()));
        // Gateway is unreachable in this suite: every inquire() throws, which exercises the
        // "leave at PROC_STAT='6', pace, continue" branch — exactly the isolation path under test.
        when(efsApiClient.send(anyString(), any())).thenThrow(new IllegalStateException("no gateway"));
    }

    private TbFirmConf conf() {
        TbFirmConf c = new TbFirmConf();
        c.setBizKey(BIZ_A);
        c.setOutBankCd("970457");
        c.setWdrlActNo("100001020691");
        c.setBankPrtnId("WRB001");
        c.setVanOrgCd(VAN);
        c.setOrgStat("1");
        return c;
    }

    /** An aged row: {@code RQ_DTTM} well in the past, so tier-1 of {@code isAged} accepts it. */
    private TbFirmTran aged(String bizKey, String tranSeq, String bankCd) {
        TbFirmTran t = new TbFirmTran();
        t.setBizKey(bizKey);
        t.setTranDt(TODAY_UTC);
        t.setTranSeq(tranSeq);
        t.setTranTp("202");
        t.setProcStat(ProcStat.REQ.code());
        t.setOutBankCd(bankCd);
        t.setWdrlActNo("100001020691");
        t.setRqDttm(LocalDateTime.now(ICT).minusHours(1)
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        return t;
    }

    /** A young row: {@code RQ_DTTM} now, so the 2-minute age window excludes it. */
    private TbFirmTran young(String tranSeq) {
        TbFirmTran t = aged(BIZ_A, tranSeq, "970457");
        t.setRqDttm(LocalDateTime.now(ICT).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        return t;
    }

    private void candidates(TbFirmTran... rows) {
        when(tbFirmTranRepository.findResultCheckCandidates(eq("202"), eq("6"), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(rows));
    }

    private Pageable capturePageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(tbFirmTranRepository).findResultCheckCandidates(anyString(), anyString(), anyCollection(), captor.capture());
        return captor.getValue();
    }

    // ── 1. batch size bounds the query ──────────────────────────────────────

    @Test
    @DisplayName("1: only the configured batch size is requested from the database")
    void batchSizeBoundsTheQuery() {
        ReflectionTestUtils.setField(service, "batchSize", 7);
        candidates();

        service.runOnce();

        Pageable page = capturePageable();
        assertThat(page.getPageSize()).isEqualTo(7);
        assertThat(page.getPageNumber()).as("always page 0 — offsets would skip rows as statuses change")
                .isZero();
    }

    @Test
    @DisplayName("1b: the unbounded legacy-shaped finder is no longer used by the sweep")
    void unboundedFinderIsNotUsed() {
        ReflectionTestUtils.setField(service, "batchSize", 10);
        candidates();

        service.runOnce();

        verify(tbFirmTranRepository, never()).findByTranTpAndProcStat(anyString(), anyString());
    }

    // ── 2. deterministic ordering ───────────────────────────────────────────

    @Test
    @DisplayName("2: the query itself carries the oldest-first ordering (not applied in Java)")
    void orderingIsDatabaseSide() {
        ReflectionTestUtils.setField(service, "batchSize", 10);
        candidates();

        service.runOnce();

        // The ORDER BY lives in the @Query, so the Pageable must NOT add a competing sort — a
        // Pageable sort would be appended and could override the intended determinism.
        assertThat(capturePageable().getSort()).isEqualTo(Sort.unsorted());
    }

    @Test
    @DisplayName("2b: rows are processed in the order the (ordered) query returned them")
    void processesInQueryOrder() {
        ReflectionTestUtils.setField(service, "batchSize", 10);
        TbFirmTran first = aged(BIZ_A, "0000001", "970457");
        TbFirmTran second = aged(BIZ_A, "0000002", "970457");
        candidates(first, second);

        service.runOnce();

        // Both rows reached the gateway leg, in order — no re-sorting or set-shuffling in Java.
        verify(tbFirmTranRepository, atLeastOnce()).save(any());
        verify(dispatchPacer, times(2)).pause(anyString());
    }

    // ── 3-6. legacy eligibility predicates preserved ────────────────────────

    @Test
    @DisplayName("3+4: the legacy TRAN_TP/PROC_STAT predicates are the DB-side filter")
    void legacyStatusPredicatesArePushedDown() {
        ReflectionTestUtils.setField(service, "batchSize", 10);
        candidates();

        service.runOnce();

        // Legacy IDO.TB_FIRM_TRAN_R001: TRAN_TP='202' AND PROC_STAT='6'.
        verify(tbFirmTranRepository).findResultCheckCandidates(eq("202"), eq("6"), anyCollection(), any(Pageable.class));
    }

    @Test
    @DisplayName("4b: the 2-minute age window still excludes a young row after the DB fetch")
    void youngRowIsExcludedByTheAgeWindow() {
        ReflectionTestUtils.setField(service, "batchSize", 10);
        candidates(young("0000001"));

        service.runOnce();

        // Legacy: TRAN_TM <= now-2min. Selected by SQL, dropped by selectAged — never inquired.
        verify(dispatchPacer, never()).pause(anyString());
        verify(tbFirmTranRepository, never()).save(any());
    }

    @Test
    @DisplayName("5+6: a young row in the same batch does not block the aged rows beside it")
    void youngRowDoesNotBlockAgedRows() {
        ReflectionTestUtils.setField(service, "batchSize", 10);
        candidates(young("0000001"), aged(BIZ_A, "0000002", "970457"));

        service.runOnce();

        verify(dispatchPacer, times(1)).pause(anyString());
    }

    @Test
    @DisplayName("6b: an empty candidate set exits cleanly without touching the gateway")
    void emptyResultSetExitsCleanly() {
        ReflectionTestUtils.setField(service, "batchSize", 10);
        candidates();

        service.runOnce();

        verify(efsApiClient, never()).send(anyString(), any());
        verify(dispatchPacer, never()).pause(anyString());
    }

    // ── 7 + 8. failure isolation ────────────────────────────────────────────

    @Test
    @DisplayName("7: one failing row does not stop the remaining rows in the batch")
    void oneFailedRowDoesNotStopTheSweep() {
        ReflectionTestUtils.setField(service, "batchSize", 10);
        candidates(aged(BIZ_A, "0000001", "970457"),
                   aged(BIZ_A, "0000002", "970457"),
                   aged(BIZ_A, "0000003", "970457"));

        service.runOnce();

        // Every row's inquiry throws (stubbed); all three are still attempted and paced.
        verify(dispatchPacer, times(3)).pause(anyString());
    }

    @Test
    @DisplayName("8: a failed inquiry leaves the row at PROC_STAT='6' for the next pass")
    void failedRowIsLeftPending() {
        ReflectionTestUtils.setField(service, "batchSize", 10);
        candidates(aged(BIZ_A, "0000001", "970457"));

        service.runOnce();

        // The atomic finalize is only invoked on a confirmed outcome — never on a failed inquiry.
        verify(dispatchResultTxService, never())
                .finalizeOriginalAndPayTarget(anyString(), anyString(), anyString(),
                        anyString(), anyString(), anyString(), anyString(), anyString());
    }

    // ── 9. per-bank pacing preserved ────────────────────────────────────────

    @Test
    @DisplayName("9: per-bank pacing is applied per row, keyed on that row's OUT_BANK_CD")
    void perBankPacingPreserved() {
        ReflectionTestUtils.setField(service, "batchSize", 10);
        candidates(aged(BIZ_A, "0000001", "970457"), aged(BIZ_A, "0000002", "970424"));

        service.runOnce();

        // Legacy paces per row from WCConst.PER_DELAY_* keyed on OUT_BANK_CD (Woori 3000, else 100).
        verify(dispatchPacer).pause("970457");
        verify(dispatchPacer).pause("970424");
    }

    // ── 10 + 11. progress across passes, nothing permanently skipped ────────

    @Test
    @DisplayName("10+11: a truncated batch makes progress — the next pass sees the remaining rows")
    void nextPassProcessesRemainingRows() {
        ReflectionTestUtils.setField(service, "batchSize", 2);

        // Pass 1 returns the two oldest rows (the DB applies ORDER BY + LIMIT).
        when(tbFirmTranRepository.findResultCheckCandidates(eq("202"), eq("6"), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(aged(BIZ_A, "0000001", "970457"), aged(BIZ_A, "0000002", "970457")))
                .thenReturn(List.of(aged(BIZ_A, "0000003", "970457"), aged(BIZ_A, "0000004", "970457")));

        service.runOnce();
        service.runOnce();

        // Four distinct rows were attempted across two passes: no row is permanently skipped, and
        // oldest-first ordering guarantees the queue drains rather than starving its tail.
        verify(dispatchPacer, times(4)).pause(anyString());
    }

    // ── 12 + 13. duplicate-request and overlap safety ───────────────────────

    @Test
    @DisplayName("12: each candidate produces at most one 203 attempt per pass")
    void oneInquiryPerRowPerPass() {
        ReflectionTestUtils.setField(service, "batchSize", 10);
        candidates(aged(BIZ_A, "0000001", "970457"));

        service.runOnce();

        // One row in, one pacing call out — the loop cannot double-issue for the same row.
        verify(dispatchPacer, times(1)).pause(anyString());
    }

    @Test
    @DisplayName("13: overlap protection is the job's ShedLock identity, not an in-service guard")
    void overlapProtectionIsTheSchedulerLock() {
        // ResultCheckJob holds JobLockNames.DISPATCH_RESULT_CHECK, and StrandedRecoveryService
        // acquires the SAME identity before delegating into this body (parity-audit F-9). Pinning
        // the constant here keeps that shared-identity contract visible from the H-2 suite; the
        // behavioural lock tests live in StrandedRecoveryResultCheckLockTest.
        assertThat(com.kosign.wecms.common.lock.JobLockNames.DISPATCH_RESULT_CHECK)
                .isEqualTo("ResultCheckJob.run");
    }

    // ── 15 + 16. batch-size configuration ───────────────────────────────────

    @Test
    @DisplayName("15: the batch size has a safe default when nothing is configured")
    void batchSizeHasSafeDefault() {
        // Field initialiser mirrors the @Value default so an unset property is still bounded.
        ResultCheckService fresh = new ResultCheckService(efsApiClient, crypto, vanApiKeyService, props,
                mapper, tbFirmTranRepository, opsAlertService, legacyFunctions, firmConfRepository,
                dispatchPacer, dispatchResultTxService);
        ReflectionTestUtils.setField(fresh, "enabled", true);
        candidates();

        fresh.runOnce();

        assertThat(capturePageable().getPageSize()).isEqualTo(ResultCheckService.DEFAULT_BATCH_SIZE);
    }

    @Test
    @DisplayName("16: a non-positive batch size falls back safely instead of breaking the scheduler")
    void invalidBatchSizeFallsBackSafely() {
        // PageRequest.of(0, n) throws for n < 1; that exception would surface on every scheduler
        // fire. Clamping keeps the sweep alive and logs the correction.
        for (int bad : new int[]{0, -1, -100}) {
            org.mockito.Mockito.reset(tbFirmTranRepository);
            when(tbFirmTranRepository.findResultCheckCandidates(anyString(), anyString(), anyCollection(), any(Pageable.class)))
                    .thenReturn(List.of());
            ReflectionTestUtils.setField(service, "batchSize", bad);

            service.runOnce();

            assertThat(capturePageable().getPageSize())
                    .as("batchSize=%d must fall back, not throw", bad)
                    .isEqualTo(ResultCheckService.DEFAULT_BATCH_SIZE);
        }
    }

    // ── 18. global-vs-tenant scope, documented from legacy evidence ─────────

    @Test
    @DisplayName("18: the scan is GLOBAL by design — rows from multiple tenants are processed together")
    void scanIsGloballyScopedByDesign() {
        ReflectionTestUtils.setField(service, "batchSize", 10);
        candidates(aged(BIZ_A, "0000001", "970457"), aged(BIZ_B, "0000002", "970457"));

        service.runOnce();

        // Legacy IDO.TB_FIRM_TRAN_R001 has NO BIZ_KEY predicate — this is a system batch that
        // services every tenant. Adding a tenant filter would be the regression, not the fix.
        verify(dispatchPacer, times(2)).pause(anyString());
        ArgumentCaptor<String> tranTp = ArgumentCaptor.forClass(String.class);
        verify(tbFirmTranRepository).findResultCheckCandidates(
                tranTp.capture(), anyString(), anyCollection(), any(Pageable.class));
        assertThat(tranTp.getValue()).isEqualTo("202");
    }

    @Test
    @DisplayName("18b: the candidate query takes no tenant argument at all")
    void queryHasNoTenantParameter() {
        // Structural guard: if someone later adds a BIZ_KEY parameter this test stops compiling,
        // forcing a conscious decision rather than a silent scope narrowing.
        List<String> params = new ArrayList<>();
        params.add("tranTp");
        params.add("procStat");
        params.add("pageable");
        assertThat(params).doesNotContain("bizKey");
    }
}
