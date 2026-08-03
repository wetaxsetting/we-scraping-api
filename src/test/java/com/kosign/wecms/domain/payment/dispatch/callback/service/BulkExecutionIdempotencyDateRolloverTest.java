package com.kosign.wecms.domain.payment.dispatch.callback.service;

import com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTran;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMstId;
import com.kosign.wecms.domain.payment.dispatch.enums.ApiTranTp;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStep;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmBatApiTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FIX D — the {@code 00302} idempotency guard must not be scoped by the callback clock.
 *
 * <h2>The bug</h2>
 * {@link BulkExecutionInlineTxService#issueOne} probed
 * {@code existsByTranDtAndBizKeyAndFirmFileNmAndApiTranTp(tranDt, …)} with {@code tranDt} taken from the
 * <b>callback clock</b>. The audit row it probes for is written a few statements later under that same
 * clock — but the clock is read once per invocation, so two invocations that straddle ICT midnight probe
 * two different keyspaces. The second finds nothing, concludes "not yet issued", and <b>re-sends
 * {@code 00302} to the bank</b> — a duplicate bulk-execution instruction, which is the one outcome the
 * whole K9 no-retry design exists to prevent.
 *
 * <h2>Why substituting the file's own TRAN_DT would not have fixed it</h2>
 * {@code TB_FIRM_FILE_MST.TRAN_DT} is the file-BUILD date; the audit row carries the ISSUE date. For the
 * rollover case those are {@code D} and {@code D+1} respectively, so probing with the file's date misses
 * the row just as reliably — in the opposite direction. Neither date is correct, which is why the fix is
 * the removal of the predicate ({@code existsByBizKeyAndFirmFileNmAndApiTranTp}) and not a substitution.
 * The audit row's own {@code TRAN_DT} deliberately stays on the callback clock: it records when the call
 * happened, and legacy imposes no constraint here (BATCH2 had no idempotency guard at all — it re-sent
 * on every 60-second tick).
 *
 * <h2>Reachability</h2>
 * The bulk rail's date is ICT throughout ({@code ExpenseDispatchExecutor} writes
 * {@code TRAN_DT} from {@code nowVn} on the file path, and {@code BulkReceptionResultService} reads its
 * clock in ICT), and the operating-hours gate does not close the window at midnight: seeded
 * {@code TB_BATCH_SCHEDULE_INFO} is {@code 000000-235959} and a BATCH1 channel with no
 * {@code TB_BATCH_SCHEDULE_DTL} row passes unconditionally. Same reachability argument as
 * {@code BulkReceptionCallbackDateRolloverTest} (FIX A), one leg later.
 *
 * <p>The audit ledger is modelled statefully rather than with a fixed stub: the point of the test is that
 * a row written under one date is <i>found</i> under another, which a per-call {@code thenReturn} could
 * not express.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BulkExecutionIdempotencyDateRolloverTest {

    private static final String BIZ_KEY   = "VN_VNM0004_000001";
    private static final String FILE_SEQ  = "1001";
    /** Built on the 30th — note the date component the name carries. */
    private static final String FILE_NM   = "S_GV00000001_SHB_001_20260730";
    private static final String BUILD_DT  = "20260730";
    private static final String VAN_ORG   = "GV00000001";

    /** 23:58:30 on the build date — the last 01301 invocation before ICT midnight. */
    private static final String DT_BEFORE_MIDNIGHT = "20260730";
    private static final String TM_BEFORE_MIDNIGHT = "235830";
    /** 00:02:10 the next morning — the gateway's retry / the sibling fan-out re-entry. */
    private static final String DT_AFTER_MIDNIGHT  = "20260731";
    private static final String TM_AFTER_MIDNIGHT  = "000210";

    @Mock TbFirmFileMstRepository     firmFileMstRepository;
    @Mock TbFirmBatApiTranRepository  firmBatApiTranRepository;
    @Mock TbPayFileMstRepository      payFileMstRepository;
    @Mock DispatchApiTranAuditWriter  apiTranAuditWriter;
    @Mock DispatchKeyGenerator        dispatchKeyGenerator;
    @Mock OpsAlertService             opsAlertService;

    BulkExecutionInlineTxService service;

    /** Stand-in for the committed {@code TB_FIRM_BAT_API_TRAN} ledger. */
    private final List<TbFirmBatApiTran> ledger = new ArrayList<>();
    /** Every {@code 00302} the service actually handed to the gateway sender. */
    private final List<BulkExecutionApiCommand> sent = new ArrayList<>();

    private TbFirmFileMst file;

    @BeforeEach
    void setUp() {
        service = new BulkExecutionInlineTxService(
                firmFileMstRepository, firmBatApiTranRepository, payFileMstRepository,
                apiTranAuditWriter, dispatchKeyGenerator, opsAlertService);

        file = new TbFirmFileMst();
        file.setTranDt(BUILD_DT);
        file.setBizKey(BIZ_KEY);
        file.setFileSeqNo(FILE_SEQ);
        file.setFirmFileNm(FILE_NM);
        file.setOutBankCd("970424");
        file.setBankPrtnId("WEBC1");
        file.setVanOrgCd(VAN_ORG);
        file.setProcStep(ProcStep.REQ_RECV.code());   // '1' — reception-success
        file.setProcStat(ProcStat.REQ.code());        // '6'
        file.setReqCnt(BigDecimal.valueOf(3));
        file.setReqAmt(new BigDecimal("1500000"));

        when(firmFileMstRepository.findById(any())).thenReturn(Optional.of(file));
        when(firmFileMstRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(firmBatApiTranRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(dispatchKeyGenerator.nextTranSeq(anyString(), anyString())).thenReturn("9000001");

        // The committed audit write — appends to the ledger, as saveInNewTransaction does to the table.
        when(apiTranAuditWriter.saveInNewTransaction(any())).thenAnswer(inv -> {
            TbFirmBatApiTran row = inv.getArgument(0);
            ledger.add(row);
            return row;
        });

        // The date-PROOF guard: matches on (BIZ_KEY, FIRM_FILE_NM, API_TRAN_TP) across every date.
        when(firmBatApiTranRepository.existsByBizKeyAndFirmFileNmAndApiTranTp(
                anyString(), anyString(), anyString()))
                .thenAnswer(inv -> ledger.stream().anyMatch(r ->
                        inv.getArgument(0).equals(r.getBizKey())
                                && inv.getArgument(1).equals(r.getFirmFileNm())
                                && inv.getArgument(2).equals(r.getApiTranTp())));

        // The date-SCOPED predicate, wired the way the table really behaves, so that if the service ever
        // goes back to using it this test fails on the assertion rather than on a missing stub.
        when(firmBatApiTranRepository.existsByTranDtAndBizKeyAndFirmFileNmAndApiTranTp(
                anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> ledger.stream().anyMatch(r ->
                        inv.getArgument(0).equals(r.getTranDt())
                                && inv.getArgument(1).equals(r.getBizKey())
                                && inv.getArgument(2).equals(r.getFirmFileNm())
                                && inv.getArgument(3).equals(r.getApiTranTp())));
    }

    private void issueOnceAt(String tranDt, String tranTm) {
        service.issueOne(
                new TbFirmFileMstId(BUILD_DT, BIZ_KEY, FILE_SEQ, FILE_NM),
                VAN_ORG, tranDt, tranTm, sent::add);
    }

    @Test
    @DisplayName("two invocations either side of ICT midnight send exactly ONE 00302")
    void doubleInvocationAcrossMidnight_sendsExactlyOne00302() {
        // 23:58:30 — the file is reception-success, nothing issued yet: this one goes to the bank.
        issueOnceAt(DT_BEFORE_MIDNIGHT, TM_BEFORE_MIDNIGHT);

        // The PROC_STEP advance the first call made is what a real second call would re-read. Undo it,
        // so the state guard cannot be what saves us — this test is about the LEDGER guard, and leaving
        // the file at '2' would let it pass even with the defect present.
        file.setProcStep(ProcStep.REQ_RECV.code());
        file.setProcStat(ProcStat.REQ.code());

        // 00:02:10 the next day — a different clock date, the same file.
        issueOnceAt(DT_AFTER_MIDNIGHT, TM_AFTER_MIDNIGHT);

        assertThat(sent)
                .as("the bank must receive ONE execution instruction for this file, not one per calendar day")
                .hasSize(1);
        assertThat(sent.get(0).fileNm()).isEqualTo(FILE_NM);
    }

    @Test
    @DisplayName("the guard never consults the date-scoped predicate")
    void guardDoesNotUseTheDateScopedPredicate() {
        issueOnceAt(DT_BEFORE_MIDNIGHT, TM_BEFORE_MIDNIGHT);
        file.setProcStep(ProcStep.REQ_RECV.code());
        file.setProcStat(ProcStat.REQ.code());
        issueOnceAt(DT_AFTER_MIDNIGHT, TM_AFTER_MIDNIGHT);

        // Pins the fix itself, not just its effect: with the date predicate back in place the second
        // call's probe would miss and the assertion above would fail — but so would this one, and this
        // one names the cause.
        verify(firmBatApiTranRepository, never())
                .existsByTranDtAndBizKeyAndFirmFileNmAndApiTranTp(
                        anyString(), anyString(), anyString(), anyString());
        verify(firmBatApiTranRepository, org.mockito.Mockito.atLeastOnce())
                .existsByBizKeyAndFirmFileNmAndApiTranTp(BIZ_KEY, FILE_NM, ApiTranTp.BULK_EXECUTION.code());
    }

    @Test
    @DisplayName("the OLD date-scoped predicate would have double-sent — the defect, pinned")
    void dateScopedPredicateWouldHaveMissed() {
        issueOnceAt(DT_BEFORE_MIDNIGHT, TM_BEFORE_MIDNIGHT);
        assertThat(ledger).hasSize(1);

        // The audit row was stamped with the CALLBACK date, not the file's build date. Both of the
        // candidate date keys therefore fail to find it from the far side of midnight:
        assertThat(ledger.get(0).getTranDt())
                .as("audit TRAN_DT is the callback clock — deliberately, it records when the call happened")
                .isEqualTo(DT_BEFORE_MIDNIGHT);

        assertThat(firmBatApiTranRepository.existsByTranDtAndBizKeyAndFirmFileNmAndApiTranTp(
                DT_AFTER_MIDNIGHT, BIZ_KEY, FILE_NM, ApiTranTp.BULK_EXECUTION.code()))
                .as("probing with the NEXT day's callback clock misses — this is the original defect")
                .isFalse();

        // And the "obvious" alternative fix is no better on the other side of the boundary: had the
        // callback landed at 00:02 the audit row would carry D+1 while the file still carries D.
        assertThat(BUILD_DT).isNotEqualTo(DT_AFTER_MIDNIGHT);

        assertThat(firmBatApiTranRepository.existsByBizKeyAndFirmFileNmAndApiTranTp(
                BIZ_KEY, FILE_NM, ApiTranTp.BULK_EXECUTION.code()))
                .as("the date-proof predicate finds it from either side")
                .isTrue();
    }

    @Test
    @DisplayName("same-day duplicate invocation is still suppressed (no regression)")
    void sameDayDoubleInvocation_stillSendsOnce() {
        issueOnceAt(DT_BEFORE_MIDNIGHT, TM_BEFORE_MIDNIGHT);
        file.setProcStep(ProcStep.REQ_RECV.code());
        file.setProcStat(ProcStat.REQ.code());
        issueOnceAt(DT_BEFORE_MIDNIGHT, "235905");

        assertThat(sent).hasSize(1);
    }

    @Test
    @DisplayName("a DIFFERENT file on the same date is not suppressed by its sibling's 00302")
    void differentFile_isNotSuppressed() {
        issueOnceAt(DT_BEFORE_MIDNIGHT, TM_BEFORE_MIDNIGHT);
        assertThat(sent).hasSize(1);

        // Dropping TRAN_DT must not broaden the guard into "any 00302 for this tenant". A branch-split
        // sibling carries a different round in its FIRM_FILE_NM and must still be sent.
        String siblingNm = "S_GV00000001_SHB_002_20260730";
        file.setFirmFileNm(siblingNm);
        file.setProcStep(ProcStep.REQ_RECV.code());
        file.setProcStat(ProcStat.REQ.code());
        service.issueOne(new TbFirmFileMstId(BUILD_DT, BIZ_KEY, FILE_SEQ, siblingNm),
                VAN_ORG, DT_BEFORE_MIDNIGHT, "235845", sent::add);

        assertThat(sent).hasSize(2);
        assertThat(sent).extracting(BulkExecutionApiCommand::fileNm)
                .containsExactly(FILE_NM, siblingNm);
    }
}
