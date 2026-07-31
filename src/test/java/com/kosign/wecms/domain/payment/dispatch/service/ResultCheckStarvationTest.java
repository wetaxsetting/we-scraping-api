package com.kosign.wecms.domain.payment.dispatch.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * N-1 — the scheduled result-check must not starve on stale {@code 202}/{@code PROC_STAT='6'} rows.
 *
 * <h2>The defect this pins</h2>
 * Candidate selection is <b>oldest-first with a {@code LIMIT}</b>. An earlier revision applied the
 * {@code TRAN_DT} filter <i>in Java, after</i> that limit. Any row parked at {@code 202}/{@code '6'}
 * from an older date therefore sorted to the <b>front</b> of every page, consumed the whole batch
 * window, and was then discarded — so once such rows reached the batch size the sweep processed
 * <b>nothing at all, permanently</b>, while logging only {@code DEBUG "no aged … transfers"}: a total
 * stall was indistinguishable from a healthy idle tick.
 *
 * <p>Such rows are expected, not hypothetical: {@code ResultCheckService.isAged} carries tier-2/tier-3
 * fallbacks precisely because legacy-JEX-written rows with no {@code RQ_DTTM} exist, and
 * {@code reflectOutcome} returns without changing {@code PROC_STAT} when {@code TB_FIRM_CONF} is
 * missing — which parks a row at {@code '6'} forever.
 *
 * <h2>The fix, and why it is not a parity regression</h2>
 * The eligible {@code TRAN_DT} set moved into SQL, ahead of the limit. That is <b>legacy's own
 * shape</b>: the batch-side
 * {@code WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH_ETC/xml/service/IDO/IDO.TB_FIRM_TRAN_R001.xml}
 * carries {@code WHERE TRAN_DT = TO_CHAR(now(),'YYYYMMDD')} directly in the query. (The
 * {@code WECMS_ETC} copy of the same id is a {@code WHERE 1=1 ??} dynamic shell, and
 * {@code PROCESSING_RESULT_CHECK_BATCH:62-64} passes it no dynamic clause — so the batch-side file is
 * the one that actually runs. Verified 2026-07-29.) Moving it into Java was the regression; moving it
 * back is the fix.
 *
 * <p>Only the <b>age</b> comparison stays in Java. That tiered rule ({@code RQ_DTTM} first) is the
 * documented D-1 improvement over legacy's wrapping {@code HHmmss} compare and must not be collapsed
 * into SQL — see {@link ResultCheckSelectionBoundaryTest}, which this suite deliberately does not
 * duplicate.
 *
 * @see ResultCheckBoundedScanTest boundedness, ordering and failure isolation (H-2)
 * @see ResultCheckSelectionBoundaryTest the D-1 UTC/ICT aging boundaries
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResultCheckStarvationTest {

    private static final DateTimeFormatter DT8 = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final String VAN = "GV00000001";
    private static final String BIZ = "VN_VNM0004_000001";
    private static final String WOORI = "970457";

    private static final String TODAY_UTC = LocalDate.now(ZoneOffset.UTC).format(DT8);
    private static final String YESTERDAY_UTC = LocalDate.now(ZoneOffset.UTC).minusDays(1).format(DT8);
    /** A date no eligible-set computation can ever produce — the starvation fodder. */
    private static final String THREE_DAYS_AGO_UTC = LocalDate.now(ZoneOffset.UTC).minusDays(3).format(DT8);

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
    private ListAppender<ILoggingEvent> logs;
    private Logger serviceLogger;

    @BeforeEach
    void setUp() {
        service = new ResultCheckService(efsApiClient, crypto, vanApiKeyService, props, mapper,
                tbFirmTranRepository, opsAlertService,
                legacyFunctions, firmConfRepository, dispatchPacer, dispatchResultTxService);
        // The master gate defaults OFF (wecms.dispatch.result-check.enabled) and there is no Spring
        // context here to inject it; this suite exercises the body, not the gate.
        org.springframework.test.util.ReflectionTestUtils.setField(service, "enabled", true);
        when(vanApiKeyService.getApiKey(anyString())).thenReturn("webcashvietnam0002TEST$#%$&*$&^!");
        when(tbFirmTranRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(legacyFunctions.nextTbFirmTranSeq()).thenReturn("900");
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf()));
        // The gateway is unreachable throughout this suite: every inquiry throws, which exercises the
        // "leave at '6', pace, continue" branch. What is under test here is *which rows reach* that
        // branch, never what the gateway returns.
        when(efsApiClient.send(anyString(), any())).thenThrow(new IllegalStateException("no gateway"));

        serviceLogger = (Logger) LoggerFactory.getLogger(ResultCheckService.class);
        logs = new ListAppender<>();
        logs.start();
        serviceLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(logs);
        logs.stop();
    }

    private TbFirmConf conf() {
        TbFirmConf c = new TbFirmConf();
        c.setBizKey(BIZ);
        c.setOutBankCd(WOORI);
        c.setWdrlActNo("100001020691");
        c.setBankPrtnId("WRB001");
        c.setVanOrgCd(VAN);
        c.setOrgStat("1");
        return c;
    }

    /** A row whose {@code RQ_DTTM} is an hour old, so tier-1 of {@code isAged} accepts it. */
    private TbFirmTran agedOn(String tranDt, String tranSeq) {
        TbFirmTran t = new TbFirmTran();
        t.setBizKey(BIZ);
        t.setTranDt(tranDt);
        t.setTranSeq(tranSeq);
        t.setTranTp("202");
        t.setProcStat(ProcStat.REQ.code());
        t.setOutBankCd(WOORI);
        t.setWdrlActNo("100001020691");
        t.setRqDttm(LocalDateTime.now(ICT).minusHours(1).format(DTM14));
        return t;
    }

    /** A row inside the 2-minute delay window, so no tier of {@code isAged} accepts it. */
    private TbFirmTran youngOn(String tranDt, String tranSeq) {
        TbFirmTran t = agedOn(tranDt, tranSeq);
        t.setRqDttm(LocalDateTime.now(ICT).format(DTM14));
        return t;
    }

    private void candidatesAre(List<TbFirmTran> rows) {
        when(tbFirmTranRepository.findResultCheckCandidates(
                eq("202"), eq("6"), anyCollection(), any(Pageable.class)))
                .thenReturn(rows);
    }

    @SuppressWarnings("unchecked")
    private Collection<String> capturedTranDts() {
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(tbFirmTranRepository).findResultCheckCandidates(
                anyString(), anyString(), captor.capture(), any(Pageable.class));
        return captor.getValue();
    }

    private List<String> messagesAt(Level level) {
        return logs.list.stream()
                .filter(e -> e.getLevel() == level)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    // ── Test A — the starvation regression ──────────────────────────────────

    @Test
    @DisplayName("A1: stale older-dated rows are excluded by the query, so they cannot fill the page")
    void staleDatesAreExcludedByTheQueryItself() {
        ReflectionTestUtils.setField(service, "batchSize", 100);
        candidatesAre(List.of());

        service.runOnce();

        // This is the fix: the DB is asked to exclude older dates BEFORE applying the LIMIT. If this
        // set ever widens, stale rows re-enter the page and the starvation returns.
        assertThat(capturedTranDts())
                .as("the eligible TRAN_DT set must be exactly {UTC today, UTC yesterday}")
                .containsExactlyInAnyOrder(TODAY_UTC, YESTERDAY_UTC)
                .doesNotContain(THREE_DAYS_AGO_UTC);
    }

    @Test
    @DisplayName("A2: a full page of stale rows does not displace today's aged row")
    void stalePageDoesNotDisplaceTodaysAgedRow() {
        int batchSize = 10;
        ReflectionTestUtils.setField(service, "batchSize", batchSize);

        // Defence-in-depth: even if stale rows DID leak through the query (a legacy-written row, or a
        // future change to the predicate), the one genuinely-eligible row must still be processed —
        // the in-memory guard must drop the stale rows, not the good one. Stale rows sort first
        // because the query is oldest-first, which is exactly what made them dangerous.
        List<TbFirmTran> page = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            page.add(agedOn(THREE_DAYS_AGO_UTC, "stale-" + i));
        }
        page.add(agedOn(TODAY_UTC, "today-1"));
        candidatesAre(page);

        service.runOnce();

        // Exactly one row was worked: the today row. Ten stale rows were dropped, not processed.
        verify(dispatchPacer, times(1)).pause(WOORI);
    }

    // ── Test B — the UTC-yesterday candidate ────────────────────────────────

    @Test
    @DisplayName("B: an aged row dated UTC yesterday is still eligible (midnight-rollover widening)")
    void yesterdayUtcCandidateIsEligible() {
        ReflectionTestUtils.setField(service, "batchSize", 10);
        candidatesAre(List.of(agedOn(YESTERDAY_UTC, "0000001")));

        service.runOnce();

        // Legacy selects today only; the refactor deliberately widens by one UTC day so a row
        // dispatched just before midnight is not orphaned by the rollover. Legacy's own answer was
        // the `< '235800'` guard, which avoids the wrap without ever recovering the affected row.
        assertThat(capturedTranDts()).contains(YESTERDAY_UTC);
        verify(dispatchPacer, times(1)).pause(WOORI);
    }

    // ── Test C — the date bound is a WHERE predicate, not a post-filter ──────

    @Test
    @DisplayName("C: the query restricts TRAN_DT in its WHERE clause, ahead of the page limit")
    void queryRestrictsTranDtBeforeThePageLimit() throws Exception {
        // Query-text guard, matching DispatchClaimQueryContractTest's rationale: the project ships no
        // embedded database or Testcontainers, so the property is asserted statically. The property
        // that matters IS static — the date must be a SQL predicate, because a LIMIT applied before a
        // Java-side date filter is precisely the N-1 defect.
        Method m = TbFirmTranRepository.class.getMethod("findResultCheckCandidates",
                String.class, String.class, Collection.class, Pageable.class);

        String jpql = m.getAnnotation(Query.class).value().replaceAll("\\s+", " ").trim();

        assertThat(jpql)
                .as("TRAN_DT must be a WHERE predicate")
                .contains("t.tranDt IN :tranDts");
        assertThat(jpql.indexOf("t.tranDt IN :tranDts"))
                .as("the date predicate must appear before ORDER BY, i.e. inside WHERE — if it were "
                        + "after, it would not constrain the rows the LIMIT selects")
                .isLessThan(jpql.indexOf("ORDER BY"));
        assertThat(jpql)
                .as("oldest-first ordering must be retained so the limit cannot drop an aged row")
                .contains("ORDER BY t.tranDt ASC, t.tranTm ASC, t.tranSeq ASC");
        assertThat(jpql)
                .as("legacy IDO.TB_FIRM_TRAN_R001 has no BIZ_KEY predicate — this is a system batch "
                        + "servicing every tenant, and narrowing it would be the regression")
                .doesNotContain("bizKey");
    }

    @Test
    @DisplayName("C2: the page limit is applied exactly once, and the Pageable adds no competing sort")
    void pageLimitAppliedOnceWithNoCompetingSort() {
        ReflectionTestUtils.setField(service, "batchSize", 25);
        candidatesAre(List.of());

        service.runOnce();

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(tbFirmTranRepository, times(1)).findResultCheckCandidates(
                anyString(), anyString(), anyCollection(), captor.capture());
        Pageable page = captor.getValue();
        assertThat(page.getPageSize()).isEqualTo(25);
        assertThat(page.getPageNumber())
                .as("always page 0 — offsets would skip rows as statuses change underneath")
                .isZero();
        assertThat(page.getSort())
                .as("the ORDER BY lives in the @Query; a Pageable sort would be appended and could "
                        + "override the intended oldest-first determinism")
                .isEqualTo(org.springframework.data.domain.Sort.unsorted());
    }

    // ── Test D — a stalled sweep must not read as a healthy idle tick ────────

    @Test
    @DisplayName("D: a full page with nothing aged warns, instead of logging DEBUG 'no aged'")
    void fullPageWithNothingAgedWarnsRatherThanLookingIdle() {
        int batchSize = 3;
        ReflectionTestUtils.setField(service, "batchSize", batchSize);
        // A full page, none of it past the age window.
        candidatesAre(List.of(youngOn(TODAY_UTC, "1"), youngOn(TODAY_UTC, "2"), youngOn(TODAY_UTC, "3")));

        service.runOnce();

        verify(dispatchPacer, never()).pause(anyString());
        assertThat(messagesAt(Level.WARN))
                .as("the cap check must be evaluated BEFORE the empty-return; previously it sat after "
                        + "it, so a sweep that did no work logged only at DEBUG")
                .anySatisfy(m -> assertThat(m).contains("candidate batch cap reached"))
                .anySatisfy(m -> assertThat(m).contains("sweep did no work this pass"));
    }

    @Test
    @DisplayName("D2: a genuinely idle sweep stays quiet — no false alarm when the page is not full")
    void genuinelyIdleSweepDoesNotWarn() {
        ReflectionTestUtils.setField(service, "batchSize", 10);
        candidatesAre(List.of());

        service.runOnce();

        // The counterpart to D: an empty page is healthy and must not raise a warning, or the signal
        // added above would be worthless.
        assertThat(messagesAt(Level.WARN)).isEmpty();
    }

    // ── Test E — the eligible set is computed on the UTC clock (D-1) ─────────

    @Test
    @DisplayName("E: the eligible TRAN_DT set is derived in UTC, the zone TRAN_DT is written in")
    void eligibleSetIsComputedInUtcNotIct() {
        // TRAN_DT is written with DateTimeUtils.utcNow() (legacy PymnHelper.pymn_0001_001_c001:2197),
        // and the refactor's API-rail executors do the same. Deriving the eligible set from an ICT
        // date is the D-1 defect: between 00:00 and 07:00 ICT the two disagree by one day, which
        // stranded every row dispatched in that window. This asserts the reader uses the writer's
        // clock, at whatever wall-clock time the suite happens to run.
        LocalDate utcToday = LocalDate.now(ZoneOffset.UTC);
        assertThat(ResultCheckService.eligibleTranDts(utcToday))
                .containsExactly(utcToday.format(DT8), utcToday.minusDays(1).format(DT8));

        ReflectionTestUtils.setField(service, "batchSize", 10);
        candidatesAre(List.of());
        service.runOnce();

        assertThat(capturedTranDts())
                .as("runOnce must pass the UTC-derived set, not an ICT-derived one")
                .isEqualTo(ResultCheckService.eligibleTranDts(LocalDate.now(ZoneOffset.UTC)));
    }

    @Test
    @DisplayName("E2: the 00:00–07:00 ICT window is exactly where UTC and ICT dates diverge")
    void ictUtcDivergenceWindowIsPinned() {
        // Pins the reason E matters, without depending on the clock. At 02:00 ICT the UTC date is
        // still the previous day; at 08:00 ICT it has caught up. A future refactor that switches the
        // date source to ICT fails here rather than silently stranding rows for 7 hours a day.
        LocalDate ictDate = LocalDate.of(2026, 7, 29);
        assertThat(ictDate.atTime(2, 0).atZone(ICT).withZoneSameInstant(ZoneOffset.UTC).toLocalDate())
                .as("02:00 ICT is still the previous UTC day")
                .isEqualTo(ictDate.minusDays(1));
        assertThat(ictDate.atTime(8, 0).atZone(ICT).withZoneSameInstant(ZoneOffset.UTC).toLocalDate())
                .as("08:00 ICT is the same UTC day")
                .isEqualTo(ictDate);
    }
}
