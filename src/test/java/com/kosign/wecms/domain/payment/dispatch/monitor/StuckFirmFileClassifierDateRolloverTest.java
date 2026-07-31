package com.kosign.wecms.domain.payment.dispatch.monitor;

import com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTran;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.enums.ApiTranTp;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStep;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmBatApiTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmConfRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.service.OperatingHoursService;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FIX G — {@code classifyFirmFile} must reach {@code BULK_EXECUTION_UNCONFIRMED} for a file whose
 * {@code 00302} audit row was written on a <b>different calendar date</b> than the file row.
 *
 * <h2>Why this is a safety test, not a labelling test</h2>
 * The classifier used {@code existsByTranDtAndBizKeyAndFirmFileNmAndApiTranTp(file.getTranDt(), …)}.
 * The two dates involved come from different clocks:
 * <ul>
 *   <li>{@code TB_FIRM_FILE_MST.TRAN_DT} — stamped at file BUILD time;</li>
 *   <li>{@code TB_FIRM_BAT_API_TRAN.TRAN_DT} for the {@code 00302} row — stamped at ISSUE time, i.e.
 *       the {@code 01301} callback clock ({@code BulkExecutionInlineTxService}), deliberately, because
 *       that column records when the call happened.</li>
 * </ul>
 * A file built at 23:58 whose callback landed at 00:02 therefore has its 00302 row under {@code D+1}
 * while the file row carries {@code D}. The probe missed, and the file was reported
 * {@code BULK_EXECUTION_NOT_ISSUED}.
 *
 * <p>{@code docs/deployment/bulk-file-reconciliation-procedure.md} §5.1 tells the operator that
 * {@code NOT_ISSUED} + "not executed" means <b>re-drive is routine</b>. So the mislabel does not just
 * read wrong — it routes a human towards re-issuing a {@code 00302} the bank may already have executed.
 * {@code UNCONFIRMED} ("reconcile with the VAN Center first") is the fail-safe verdict, and the whole
 * point of dropping the date predicate is that the classifier lands there whenever a row exists at all.
 *
 * <p>The ledger is modelled statefully: a boolean stub cannot express "a row exists, under another
 * date", which is the entire condition under test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StuckFirmFileClassifierDateRolloverTest {

    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");
    /** 2026-07-31 12:00:00 ICT — a day and a half after the file was built. */
    private static final Instant NOW = Instant.parse("2026-07-31T05:00:00Z");

    private static final String BIZ = "VN_VNM0004_000001";
    private static final String FILE_SEQ = "0000001";
    private static final String FILE_NM = "S_GV00000001_SHB_001_20260730";
    private static final String VAN_ORG = "GV00000001";

    /** The file was built on the 30th… */
    private static final String BUILD_DT = "20260730";
    /** …and its 01301 callback — and therefore its 00302 audit row — landed on the 31st. */
    private static final String ISSUE_DT = "20260731";

    @Mock TbPayFileMstRepository payFileMstRepository;
    @Mock TbPayFileDataRepository payFileDataRepository;
    @Mock TbFirmConfRepository firmConfRepository;
    @Mock OperatingHoursService operatingHoursService;
    @Mock OpsAlertService opsAlertService;
    @Mock TbFirmFileMstRepository firmFileMstRepository;
    @Mock TbFirmBatApiTranRepository firmBatApiTranRepository;

    private StuckPayFileMonitorService service;

    /** Stand-in for the committed {@code TB_FIRM_BAT_API_TRAN} ledger. */
    private final List<TbFirmBatApiTran> ledger = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new StuckPayFileMonitorService(payFileMstRepository, payFileDataRepository,
                firmConfRepository, operatingHoursService, opsAlertService,
                Clock.fixed(NOW, ICT), firmFileMstRepository, firmBatApiTranRepository);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "thresholdMinutes", 15L);
        ReflectionTestUtils.setField(service, "reAlertMinutes", 60L);
        ReflectionTestUtils.setField(service, "maxAlertKeys", 20);
        ReflectionTestUtils.setField(service, "autoReceiveEnabled", true);
        ReflectionTestUtils.setField(service, "firmFileBatchSize", 200);
        ReflectionTestUtils.setField(service, "retentionDays", 7);

        when(payFileMstRepository.findStrandedPayFiles(anyString(), anyString(), any(Pageable.class)))
                .thenReturn(List.of());

        // Date-proof predicate — matches on (BIZ_KEY, FIRM_FILE_NM, API_TRAN_TP) across every date.
        when(firmBatApiTranRepository.existsByBizKeyAndFirmFileNmAndApiTranTp(
                anyString(), anyString(), anyString()))
                .thenAnswer(inv -> ledger.stream().anyMatch(r ->
                        inv.getArgument(0).equals(r.getBizKey())
                                && inv.getArgument(1).equals(r.getFirmFileNm())
                                && inv.getArgument(2).equals(r.getApiTranTp())));

        // Date-scoped predicate, wired the way the table really behaves, so a regression to it fails on
        // the assertion rather than on a missing stub.
        when(firmBatApiTranRepository.existsByTranDtAndBizKeyAndFirmFileNmAndApiTranTp(
                anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> ledger.stream().anyMatch(r ->
                        inv.getArgument(0).equals(r.getTranDt())
                                && inv.getArgument(1).equals(r.getBizKey())
                                && inv.getArgument(2).equals(r.getFirmFileNm())
                                && inv.getArgument(3).equals(r.getApiTranTp())));
    }

    /** A file stranded at {@code '1'/'6'}, built on {@link #BUILD_DT}. */
    private TbFirmFileMst strandedFile() {
        TbFirmFileMst f = new TbFirmFileMst();
        f.setTranDt(BUILD_DT);
        f.setBizKey(BIZ);
        f.setFileSeqNo(FILE_SEQ);
        f.setFirmFileNm(FILE_NM);
        f.setVanOrgCd(VAN_ORG);
        f.setOutBankCd("970424");
        f.setBankPrtnId("WEBC1");
        f.setProcStep(ProcStep.REQ_RECV.code());
        f.setProcStat(ProcStat.REQ.code());
        f.setReqCnt(new BigDecimal("3"));
        // 00:02:10 on the 31st — the reception advance, i.e. just after the rollover.
        f.setProcDttm("20260731000210");
        return f;
    }

    /** Record a {@code 00302} audit row dated {@code auditTranDt}. */
    private void ledgerHas00302On(String auditTranDt) {
        TbFirmBatApiTran row = new TbFirmBatApiTran();
        row.setTranDt(auditTranDt);
        row.setBizKey(BIZ);
        row.setFirmFileNm(FILE_NM);
        row.setApiTranTp(ApiTranTp.BULK_EXECUTION.code());
        row.setTranSeq("9000001");
        ledger.add(row);
    }

    /**
     * Stubbed on {@code PROC_STEP='1'} specifically: the sweep makes two calls since 2026-07-31 (one
     * per stranded step), so a step-agnostic stub would hand the same file to both and double-count it.
     * These rollover cases are all about the {@code '1'} population.
     */
    private void sweepReturns(TbFirmFileMst... files) {
        when(firmFileMstRepository.findStrandedBulkExecutionFiles(
                eq(ProcStep.REQ_RECV.code()), anyString(), anyString(), anyString(), any(Pageable.class)))
                .thenReturn(List.of(files));
    }

    @Test
    @DisplayName("audit row on D+1, file row on D ⇒ UNCONFIRMED, not NOT_ISSUED")
    void rolloverAuditRow_classifiesUnconfirmed() {
        sweepReturns(strandedFile());
        ledgerHas00302On(ISSUE_DT);          // 00302 WAS issued — just under the next day's date

        StuckPayFileReport report = service.monitorOnce();

        assertThat(report.count(StuckPayFileCause.BULK_EXECUTION_UNCONFIRMED))
                .as("a 00302 exists, so the bank may have executed — the operator must reconcile first")
                .isEqualTo(1);
        assertThat(report.count(StuckPayFileCause.BULK_EXECUTION_NOT_ISSUED))
                .as("NOT_ISSUED would tell the operator a re-drive is routine — that is the double-send")
                .isZero();
    }

    @Test
    @DisplayName("the old date-scoped probe would have said NOT_ISSUED — the defect, pinned")
    void dateScopedProbeWouldHaveMisclassified() {
        ledgerHas00302On(ISSUE_DT);

        // The file's own date finds nothing…
        assertThat(firmBatApiTranRepository.existsByTranDtAndBizKeyAndFirmFileNmAndApiTranTp(
                BUILD_DT, BIZ, FILE_NM, ApiTranTp.BULK_EXECUTION.code()))
                .as("probing with the file's build date misses the audit row — the original defect")
                .isFalse();
        // …while the date-proof predicate finds it.
        assertThat(firmBatApiTranRepository.existsByBizKeyAndFirmFileNmAndApiTranTp(
                BIZ, FILE_NM, ApiTranTp.BULK_EXECUTION.code()))
                .isTrue();
    }

    @Test
    @DisplayName("the classifier does not consult the date-scoped predicate at all")
    void classifierDoesNotUseTheDateScopedPredicate() {
        sweepReturns(strandedFile());
        ledgerHas00302On(ISSUE_DT);

        service.monitorOnce();

        verify(firmBatApiTranRepository, never())
                .existsByTranDtAndBizKeyAndFirmFileNmAndApiTranTp(
                        anyString(), anyString(), anyString(), anyString());
        verify(firmBatApiTranRepository)
                .existsByBizKeyAndFirmFileNmAndApiTranTp(BIZ, FILE_NM, ApiTranTp.BULK_EXECUTION.code());
    }

    @Test
    @DisplayName("same-date audit row still classifies UNCONFIRMED (no regression)")
    void sameDateAuditRow_stillUnconfirmed() {
        TbFirmFileMst f = strandedFile();
        f.setProcDttm("20260730235900");     // no rollover — everything on the 30th
        sweepReturns(f);
        ledgerHas00302On(BUILD_DT);

        assertThat(service.monitorOnce().count(StuckPayFileCause.BULK_EXECUTION_UNCONFIRMED)).isEqualTo(1);
    }

    @Test
    @DisplayName("no audit row on ANY date still classifies NOT_ISSUED — the cause is not dead")
    void noAuditRowAnywhere_stillNotIssued() {
        // The negative control. Dropping the date predicate must not make every file look UNCONFIRMED —
        // that would erase the afterCommit-residual signal this cause exists to carry.
        sweepReturns(strandedFile());
        // ledger deliberately empty

        StuckPayFileReport report = service.monitorOnce();

        assertThat(report.count(StuckPayFileCause.BULK_EXECUTION_NOT_ISSUED)).isEqualTo(1);
        assertThat(report.count(StuckPayFileCause.BULK_EXECUTION_UNCONFIRMED)).isZero();
    }

    @Test
    @DisplayName("a 00301 row does not satisfy the 00302 probe")
    void receptionRowDoesNotCountAsExecution() {
        // Both legs write to the same ledger keyed by the same (BIZ_KEY, FIRM_FILE_NM); API_TRAN_TP is
        // the only thing separating them. If it were ever dropped from the predicate every reception
        // would look like an execution and NOT_ISSUED would become unreachable.
        TbFirmBatApiTran reception = new TbFirmBatApiTran();
        reception.setTranDt(BUILD_DT);
        reception.setBizKey(BIZ);
        reception.setFirmFileNm(FILE_NM);
        reception.setApiTranTp(ApiTranTp.BULK_RECEPTION.code());
        ledger.add(reception);

        sweepReturns(strandedFile());

        assertThat(service.monitorOnce().count(StuckPayFileCause.BULK_EXECUTION_NOT_ISSUED)).isEqualTo(1);
    }
}
