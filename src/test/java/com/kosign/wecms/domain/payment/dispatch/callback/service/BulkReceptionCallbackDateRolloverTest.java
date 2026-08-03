package com.kosign.wecms.domain.payment.dispatch.callback.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.BulkReceptionResultRequest;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.EfsCallbackEnvelope;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStep;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmBatApiTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchKeyGenerator;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FIX A — the 01301 callback must find its file when the callback crosses ICT midnight.
 *
 * <h2>The bug</h2>
 * {@code BulkReceptionResultService} derived the {@code TRAN_DT} lookup key from
 * {@code LocalDateTime.now(ICT)} <b>at callback time</b>, while the row's {@code TRAN_DT} was stamped at
 * <b>file build</b> time. A file built at 23:58 ICT whose 01301 arrived at 00:02 missed the primary
 * lookup, and {@code handleUnmatchedCallback} filtered on the same clock-derived date — so every gateway
 * retry failed identically and the file stayed at {@code PROC_STEP='0'}/{@code PROC_STAT='6'} forever.
 *
 * <h2>Why it is reachable (checked against seeded DEV data, 2026-07-30)</h2>
 * {@code OperatingHoursService} is <b>not</b> business-hours-only for the bulk path:
 * <ul>
 *   <li>{@code TB_BATCH_SCHEDULE_INFO} carries {@code 000000-235959} for both banks, so the INFO gate
 *       never closes on time;</li>
 *   <li>{@code TB_BATCH_SCHEDULE_DTL} for {@code FILE_DEPOSIT_TRANSFER_BATCH1} + {@code 970457} +
 *       {@code NAPAS} is {@code 001500-235500} — a dispatch at 23:54:59 is in-window;</li>
 *   <li>and a channel with <b>no DTL row at all</b> passes unconditionally (BATCH1 missing-row = 'Y',
 *       {@code OperatingHoursService.evaluateBulk}) — e.g. {@code 970457} + {@code EBANK}, which has no
 *       seeded row, so that file can be built at 23:59:59.
 * </ul>
 * So the window is minutes wide at worst and 24/7 at best. Not theoretical.
 *
 * <h2>What these tests pin</h2>
 * The lookup key is the <b>file name</b> date, and the audit / {@code PROC_DTTM} stamps stay on the
 * <b>callback clock</b>. Both halves matter: feeding the filename date into {@code PROC_DTTM} would
 * manufacture a timestamp for a day that never happened, which is the same defect class as a
 * UTC-date + ICT-time splice.
 *
 * <p>These tests do not move the clock. They express the rollover the way it actually presents — a row
 * whose {@code TRAN_DT} is <b>not</b> today — which is what any callback arriving after midnight sees.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BulkReceptionCallbackDateRolloverTest {

    private static final String TEST_KEY = "webcashvietnam0002TEST$#%$&*$&^!";
    private static final String VAN_ORG = "ORG000001";
    private static final String BANK_CD = "970457";
    private static final String PRTN_ID = "WRB001";
    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String FILE_SEQ_NO = "0000001";
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DT8 = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * "Yesterday" in ICT — the build date of a file dispatched at 23:58 whose callback lands at 00:02.
     * Derived from the real clock so the test is correct on every calendar day, including 1 Jan.
     */
    private static final String BUILD_DT = LocalDate.now(ICT).minusDays(1).format(DT8);
    private static final String TODAY_DT = LocalDate.now(ICT).format(DT8);

    /** {@code S_{VAN}_{TYPE}_{round}_{TRAN_DT}} — BulkReceptionService:513. Date token = yesterday. */
    private static final String FILE_NM = "S_" + VAN_ORG + "_WRB_001_" + BUILD_DT;

    private final EfsCryptoService crypto = new EfsCryptoService();
    private final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private final EfsProperties props = new EfsProperties();

    @Mock EfsApiClient efsApiClient;
    @Mock VanApiKeyService vanApiKeyService;
    @Mock TbFirmFileMstRepository firmFileMstRepository;
    @Mock TbFirmBatApiTranRepository firmBatApiTranRepository;
    @Mock DispatchKeyGenerator dispatchKeyGenerator;
    @Mock com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository payFileMstRepository;
    @Mock com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService opsAlertService;
    @Mock DispatchApiTranAuditWriter apiTranAuditWriter;

    private BulkReceptionResultService service;

    @BeforeEach
    void setUp() {
        // Same wiring as BulkReceptionResultServiceTest: a REAL BulkExecutionInlineTxService over the
        // same mock repositories, so the 01301 → 00302 chain runs end to end.
        service = new BulkReceptionResultService(efsApiClient, crypto, vanApiKeyService, props, mapper,
                firmFileMstRepository, firmBatApiTranRepository, dispatchKeyGenerator, opsAlertService,
                payFileMstRepository,
                new BulkExecutionInlineTxService(firmFileMstRepository, firmBatApiTranRepository,
                        payFileMstRepository, apiTranAuditWriter, dispatchKeyGenerator, opsAlertService),
                apiTranAuditWriter);
    }

    @Test
    @DisplayName("built 23:58, callback 00:02 — the file is FOUND via the file-name date")
    void rollover_fileIsFoundViaFileNameDate() throws Exception {
        // Nothing under TODAY's date (this is what the old clock-derived lookup asked for)...
        when(firmFileMstRepository.findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                eq(TODAY_DT), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        // ...but the row exists under the BUILD date carried by the file name.
        TbFirmFileMst file = receptionFile();
        when(firmFileMstRepository.findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                BUILD_DT, FILE_NM, ProcStep.REQ.code(), ProcStat.REQ.code()))
                .thenReturn(Optional.of(file));
        stubDownstream(file);

        EfsEnvelope ack = service.handle(callback(request("0000")));

        // The decisive assertion: the row was resolved and advanced 0 → 1, not treated as unmatched.
        assertThat(file.getProcStep())
                .as("reception must advance PROC_STEP 0 -> 1; still '0' means the lookup missed")
                .isEqualTo(ProcStep.REQ_RECV.code());
        assertThat(ack.rc()).isEqualTo("0000");

        // And it must NOT have taken the unmatched path.
        verify(firmFileMstRepository, never()).findByTranDtAndFirmFileNm(anyString(), anyString());
    }

    @Test
    @DisplayName("the lookup uses the file-name date, never the callback clock date")
    void lookupUsesFileNameDate() throws Exception {
        TbFirmFileMst file = receptionFile();
        when(firmFileMstRepository.findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(file));
        stubDownstream(file);

        service.handle(callback(request("0000")));

        verify(firmFileMstRepository).findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                eq(BUILD_DT), eq(FILE_NM), eq(ProcStep.REQ.code()), eq(ProcStat.REQ.code()));
        verify(firmFileMstRepository, never()).findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                eq(TODAY_DT), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("PROC_DTTM stays on the CALLBACK clock — the file-name date must not leak into it")
    void procDttmStaysOnTheCallbackClock() throws Exception {
        TbFirmFileMst file = receptionFile();
        when(firmFileMstRepository.findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(file));
        stubDownstream(file);

        service.handle(callback(request("0000")));

        // The whole point of splitting the two dates. Stamping BUILD_DT + callback-time-of-day would
        // produce a 14-char timestamp for an instant that never occurred, and every stranded finder
        // compares procDttm against an ICT cutoff — a backdated stamp satisfies the cutoff immediately
        // and the row would be reported as stranded the moment it was written.
        assertThat(file.getProcDttm())
                .as("PROC_DTTM must carry today's ICT date, not the file-name build date")
                .startsWith(TODAY_DT)
                .doesNotStartWith(BUILD_DT)
                .hasSize(14);
    }

    @Test
    @DisplayName("unmatched fallback also filters on the file-name date, so a retry can still match")
    void unmatchedPathUsesFileNameDate() throws Exception {
        // No row in the reception-requested state under any date → the unmatched path runs.
        when(firmFileMstRepository.findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(firmFileMstRepository.findByTranDtAndFirmFileNm(anyString(), anyString()))
                .thenReturn(List.of());

        service.handle(callback(request("0000")));

        // Before the fix this asked for TODAY_DT and therefore could never find a file built yesterday
        // — the retries were guaranteed to fail identically forever.
        verify(firmFileMstRepository).findByTranDtAndFirmFileNm(BUILD_DT, FILE_NM);
        verify(firmFileMstRepository, never()).findByTranDtAndFirmFileNm(eq(TODAY_DT), anyString());
    }

    @Test
    @DisplayName("a malformed file name falls back to the callback clock rather than failing the callback")
    void malformedFileNameFallsBackToTheClock() throws Exception {
        // Legacy-shaped / renamed / gateway-contract-changed name with fewer than 5 underscore parts.
        String badName = "FB_20240101_0001.txt";
        when(firmFileMstRepository.findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(firmFileMstRepository.findByTranDtAndFirmFileNm(anyString(), anyString()))
                .thenReturn(List.of());

        service.handle(callback(request("0000", badName)));

        // Falling back (rather than erroring) keeps an unrecognised name shape from stranding EVERY
        // file — only the ones crossing midnight, which is the pre-fix behaviour, not a regression.
        verify(firmFileMstRepository).findByTranDtAndFirmFileNm(TODAY_DT, badName);
    }

    @Test
    @DisplayName("a non-numeric date token is rejected and falls back, not used as a key")
    void nonNumericDateTokenFallsBack() throws Exception {
        // 5 parts, but the trailing token is not a date — the 8-digit guard is what stops a
        // plausible-but-wrong token silently becoming the lookup key.
        String badName = "S_" + VAN_ORG + "_WRB_001_FINAL";
        when(firmFileMstRepository.findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(firmFileMstRepository.findByTranDtAndFirmFileNm(anyString(), anyString()))
                .thenReturn(List.of());

        service.handle(callback(request("0000", badName)));

        verify(firmFileMstRepository).findByTranDtAndFirmFileNm(TODAY_DT, badName);
    }

    /* ───────────────────────────── fixtures ───────────────────────────── */

    /** Stub the sibling / pay-file reads the success path performs after the row is resolved. */
    private void stubDownstream(TbFirmFileMst file) {
        TbPayFileMst pay = new TbPayFileMst();
        pay.setTranDt(BUILD_DT);
        pay.setBizKey(BIZ_KEY);
        pay.setFileSeqNo(FILE_SEQ_NO);
        pay.setProcStat(ProcStat.REQ.code());
        // Keyed on the ROW's TRAN_DT (legacy API1_act.jsp:177 switches to the row's own date once
        // the file is resolved) — a stub keyed on TODAY would not be consulted.
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(BUILD_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(pay));
        when(firmFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(BUILD_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(List.of(file));
        when(firmFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(firmFileMstRepository.findById(any())).thenReturn(Optional.of(file));
        when(dispatchKeyGenerator.nextTranSeq(anyString(), anyString())).thenReturn("1");
        when(firmBatApiTranRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(apiTranAuditWriter.saveInNewTransaction(any())).thenAnswer(i -> i.getArgument(0));
    }

    private TbFirmFileMst receptionFile() {
        TbFirmFileMst f = new TbFirmFileMst();
        f.setTranDt(BUILD_DT);                // stamped at build time — YESTERDAY
        f.setBizKey(BIZ_KEY);
        f.setFileSeqNo(FILE_SEQ_NO);
        f.setFirmFileNm(FILE_NM);
        f.setOutBankCd(BANK_CD);
        f.setBankPrtnId(PRTN_ID);
        f.setVanOrgCd(VAN_ORG);
        f.setProcStep(ProcStep.REQ.code());   // '0'
        f.setProcStat(ProcStat.REQ.code());   // '6'
        f.setReqCnt(new BigDecimal("3"));
        f.setReqAmt(new BigDecimal("1500000.00"));
        return f;
    }

    private BulkReceptionResultRequest request(String resultCd) {
        return request(resultCd, FILE_NM);
    }

    private BulkReceptionResultRequest request(String resultCd, String fileNm) {
        return new BulkReceptionResultRequest(
                BANK_CD, PRTN_ID, fileNm, "3", "1500000", "0", resultCd, "SUCCESS");
    }

    private EfsCallbackEnvelope callback(BulkReceptionResultRequest req) throws Exception {
        when(vanApiKeyService.getApiKey(VAN_ORG)).thenReturn(TEST_KEY);
        String json = mapper.writeValueAsString(req);
        String ev = crypto.encryptEv(json, TEST_KEY, "20240101000000");
        String vv = crypto.buildVv(json, TEST_KEY);
        return new EfsCallbackEnvelope(VAN_ORG, ev, vv, "AES", "HmacSHA256",
                "20240101000000", "TNO-CB", null, null, null);
    }
}
