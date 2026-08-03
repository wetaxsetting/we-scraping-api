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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
 * Item 3 — the per-pass wall-clock deadline on {@link ResultCheckService#runOnce()}.
 *
 * <p>The contract being pinned has three parts, and the third is the one that makes the other two
 * safe:
 * <ol>
 *   <li>a pass stops once its budget is spent, instead of grinding through the whole batch;</li>
 *   <li>the rows it did not reach are left untouched at {@code PROC_STAT='6'} — the candidate query
 *       is oldest-first, so the next pass resumes from them rather than starving them;</li>
 *   <li><b>the row that was already in flight when the budget expired runs to completion.</b> The
 *       check sits at the top of the iteration precisely so a row can never be abandoned between
 *       "203 audit row written / inquiry sent" and "outcome recorded" — that would leave an audit
 *       row at {@code '6'} with a request already at the gateway.</li>
 * </ol>
 *
 * <p><b>Why the gateway stub sleeps.</b> Determinism here needs one row to outlast the budget, not a
 * guess about machine speed. {@link #ROW_COST} (200 ms) is an order of magnitude above
 * {@link #DEADLINE} (20 ms), so the first iteration always starts inside the budget and the second
 * always finds it spent — on any machine, without a clock abstraction.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResultCheckPassDeadlineTest {

    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String VAN = "GV00000001";
    private static final String BIZ = "VN_VNM0004_000001";
    private static final String WOORI = "970457";
    private static final String TODAY_UTC =
            LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    /** Simulated cost of one row's gateway leg. */
    private static final Duration ROW_COST = Duration.ofMillis(200);
    /** Budget deliberately far below {@link #ROW_COST} — one row spends it many times over. */
    private static final Duration DEADLINE = Duration.ofMillis(20);

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
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "batchSize", 100);
        when(vanApiKeyService.getApiKey(anyString())).thenReturn("webcashvietnam0002TEST$#%$&*$&^!");
        when(tbFirmTranRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(legacyFunctions.nextTbFirmTranSeq()).thenReturn("900");
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf()));
        // A slow, unreachable gateway — the exact shape the deadline exists for. Each call burns
        // ROW_COST and then fails, so the row takes the comms-fail branch (left at '6', paced).
        when(efsApiClient.send(anyString(), any())).thenAnswer(i -> {
            Thread.sleep(ROW_COST.toMillis());
            throw new IllegalStateException("gateway not responding");
        });
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

    private TbFirmTran aged(String tranSeq) {
        TbFirmTran t = new TbFirmTran();
        t.setBizKey(BIZ);
        t.setTranDt(TODAY_UTC);
        t.setTranSeq(tranSeq);
        t.setTranTp("202");
        t.setProcStat(ProcStat.REQ.code());
        t.setOutBankCd(WOORI);
        t.setWdrlActNo("100001020691");
        t.setRqDttm(LocalDateTime.now(ICT).minusHours(1)
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        return t;
    }

    private void candidates(TbFirmTran... rows) {
        when(tbFirmTranRepository.findResultCheckCandidates(
                eq("202"), eq("6"), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(rows));
    }

    @Test
    @DisplayName("the pass stops once the deadline is spent — later rows are never inquired")
    void deadlineStopsThePass() {
        ReflectionTestUtils.setField(service, "passDeadline", DEADLINE);
        candidates(aged("0000001"), aged("0000002"), aged("0000003"), aged("0000004"));

        service.runOnce();

        // Row 1 starts inside the budget and spends it; rows 2-4 never reach the gateway.
        verify(efsApiClient, times(1)).send(anyString(), any());
    }

    @Test
    @DisplayName("the in-flight row completes — its 203 audit row is finalized and it is paced")
    void inFlightRowIsNeverAbandoned() {
        ReflectionTestUtils.setField(service, "passDeadline", DEADLINE);
        candidates(aged("0000001"), aged("0000002"), aged("0000003"));

        service.runOnce();

        // Exactly one row's worth of work, and all of it. The 203 audit row was inserted, and the
        // per-bank pace — which runs at the END of the iteration, after the row body — was applied.
        // The pace is the load-bearing assertion: if the loop could break part-way through a row,
        // the audit row would exist with an inquiry already at the gateway and no pace behind it.
        verify(tbFirmTranRepository, times(1)).save(any());
        verify(dispatchPacer, times(1)).pause(WOORI);
    }

    @Test
    @DisplayName("a generous deadline does not truncate the pass")
    void generousDeadlineProcessesEveryRow() {
        ReflectionTestUtils.setField(service, "passDeadline", Duration.ofMinutes(10));
        candidates(aged("0000001"), aged("0000002"), aged("0000003"));

        service.runOnce();

        verify(efsApiClient, times(3)).send(anyString(), any());
        verify(dispatchPacer, times(3)).pause(WOORI);
    }

    @Test
    @DisplayName("a non-positive configured deadline falls back to the default, never to zero work")
    void nonPositiveDeadlineFallsBackInsteadOfDisablingTheSweep() {
        // A zero budget would break before the first row and silently stop the only sweep that
        // finalizes transfers already sent to the bank. It must clamp, not disable.
        ReflectionTestUtils.setField(service, "passDeadline", Duration.ZERO);
        candidates(aged("0000001"));

        service.runOnce();

        verify(efsApiClient, times(1)).send(anyString(), any());
    }

    @Test
    @DisplayName("a null configured deadline falls back to the default")
    void nullDeadlineFallsBack() {
        ReflectionTestUtils.setField(service, "passDeadline", null);
        candidates(aged("0000001"));

        service.runOnce();

        verify(efsApiClient, times(1)).send(anyString(), any());
    }

    @Test
    @DisplayName("the default budget is PT10M — the approved value, asserted so a silent drift fails")
    void defaultBudgetIsTenMinutes() {
        assertThat(ResultCheckService.DEFAULT_PASS_DEADLINE).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("an empty candidate set never evaluates the budget")
    void emptyPassIsUnaffected() {
        ReflectionTestUtils.setField(service, "passDeadline", DEADLINE);
        candidates();

        service.runOnce();

        verify(efsApiClient, never()).send(anyString(), any());
    }
}
