package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTran;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMstId;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTarget;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Direct unit tests for {@link DispatchResultResolver} — the shared per-line dispatch-result
 * projection behind every Payment <b>Result detail</b> page (Internal Transfer / Vendor / Expense /
 * Payroll via {@code resolve}, Overseas via {@code resolveOverseas}) and the HAENGSUNG dispatch
 * result write-back.
 *
 * <p>Closes {@code docs/testing/missing-tests.md} P3-1 and
 * {@code docs/testing/integration-test-audit.md} §5 Flow A / §6.4 (the class was the largest untested
 * one in the dispatch package — mocked in 12 suites, 0 % executed).
 *
 * <h2>Legacy source of truth (read end-to-end before these tests were written)</h2>
 *
 * <table>
 *   <caption>Legacy queries this resolver replaces</caption>
 *   <tr><th>Path</th><th>Legacy IDO</th><th>Consumer</th></tr>
 *   <tr><td>SCDUL_PYMN family (API + File)</td>
 *       <td>{@code WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.SCDUL_PYMN_SUMR_R006.xml}</td>
 *       <td>{@code PymnHelper.pymn_0002_002_r001} (:3050-3067) ← {@code pymn_0010_002_r001_act.jsp}</td></tr>
 *   <tr><td>Overseas (File only)</td>
 *       <td>{@code IDO.OVRS_PYMN_SUMR_R007.xml}</td>
 *       <td>{@code pymn_0008_002_r001_act.jsp:186}</td></tr>
 * </table>
 *
 * <h3>Legacy {@code SCDUL_PYMN_SUMR_R006} semantics, field by field</h3>
 * <ul>
 *   <li><b>Route</b> — {@code IS_FILE_ROUTE} is an {@code EXISTS} over {@code TB_PAY_FILE_DATA}
 *       matching {@code (BIZ_KEY, TRAN_DT, FILE_SEQ_NO) = (SPS.BIZ_KEY, SPS.PAY_TRAN_DT,
 *       SPS.PAY_FILE_SEQ_NO)} AND {@code F.TRAN_DT = SPMD.PAY_TRAN_DT} AND
 *       {@code CAST(F.SEQ AS VARCHAR) = SPMD.PAY_TRAN_SEQ}. The tuple comparison is NULL when the
 *       master carries no {@code PAY_FILE_SEQ_NO}, so an API-path batch always takes the ELSE branch.</li>
 *   <li><b>{@code PROC_STAT}</b> — {@code COALESCE(TPFM.PROC_STAT,'3')} on the File route,
 *       {@code COALESCE(TPT.PROC_STAT,'3')} on the API route. A missing status therefore <b>displays
 *       as failed</b>.</li>
 *   <li><b>{@code RS_CD}</b> — {@code TRIM(COALESCE(TPFD.RS_CD,''))} (File) /
 *       {@code TRIM(COALESCE(TFT.RS_CD,''))} (API).</li>
 *   <li><b>{@code RS_MSG}</b> — {@code NULL} on the File route, {@code TFT.RS_MSG} on the API route.</li>
 *   <li><b>{@code RSLT_DTTM}</b> — {@code TPFM.PROC_DTTM} (File) / {@code TPT.PROC_DTTM} (API).</li>
 *   <li><b>{@code TRAN_SEQ}</b> — {@code SPMD.PAY_TRAN_SEQ} (File) / {@code TPT.TRAN_SEQ} (API); the
 *       {@code TB_PAY_TARGET} / {@code TB_FIRM_TRAN} joins are keyed on
 *       {@code (SPMD.PAY_TRAN_DT, SPMD.PAY_TRAN_SEQ, SPMD.BIZ_KEY)} — <b>per line</b>, so lines whose
 *       {@code PAY_TRAN_DT} differ (a batch that crossed the cut-off boundary) each join to their own
 *       date.</li>
 * </ul>
 *
 * <h3>Known divergences — traced here, NOT changed here</h3>
 * Changing any of these alters what a Result page displays for money that has already moved, so per
 * {@code CLAUDE.md} §5 ("Required analysis before modifying a copied legacy table … present this
 * analysis and obtain explicit approval before making the change") they are recorded as findings for
 * the PO rather than edited under a test pass. Each is pinned below <b>as current behaviour</b> with
 * the legacy value named, so the divergence is visible instead of silently blessed:
 * <ul>
 *   <li><b>DRR-1</b> — {@code proc_dttm}. <b>FIXED 2026-07-30.</b> Legacy {@code PROC_DTTM} is the
 *       <i>master request</i> datetime ({@code SPS.TRNF_DTM} in R006:89, {@code SPS.TRNF_DT} in
 *       R007:75) and is a distinct column from {@code RSLT_DTTM} ({@code TPFM.PROC_DTTM} /
 *       {@code TPT.PROC_DTTM}, R006:96-100 / R007:77). The resolver used to emit the dispatch row's
 *       {@code PROC_DTTM} for both. Now each field projects its own legacy column.</li>
 *   <li><b>DRR-2</b> — Overseas {@code rs_msg}. <b>FIXED 2026-07-30.</b> Legacy R007:80 emits
 *       {@code TPFM.RS_MSG}; the resolver hardcoded {@code null}. Now sourced from the pay-file
 *       master (blank normalised to null, as the API path already does).</li>
 *   <li><b>DRR-3</b> — Overseas {@code proc_stat}. Legacy R007 emits {@code TPFM.PROC_STAT} with
 *       <b>no</b> {@code COALESCE(...,'3')} (unlike R006); the resolver applies the R006 coalesce on
 *       the overseas path too, so a <i>blank</i> stored status becomes {@code '3'}. (A stored
 *       {@code NULL} incidentally matches legacy here — see DRR-6.)</li>
 *   <li><b>DRR-4</b> — Route granularity. Legacy decides File-vs-API <b>per line</b> (the
 *       {@code EXISTS} above); the resolver decides once, from
 *       {@code master.payFileSeqNo != null}. They agree for every batch whose lines all have a
 *       matching {@code TB_PAY_FILE_DATA} row, which is what the dispatch executors write.</li>
 *   <li><b>DRR-6</b> — File-path {@code PROC_STAT} coalesce is unreachable for a stored {@code NULL}.
 *       {@code mstOpt.map(TbPayFileMst::getProcStat).map(::coalesceProcStat)} short-circuits to
 *       {@code Optional.empty()} when the getter returns {@code null}, so a present
 *       {@code TB_PAY_FILE_MST} row with {@code PROC_STAT IS NULL} resolves to {@code null}
 *       (bucketed <i>in progress</i>) where legacy R006's {@code COALESCE(TPFM.PROC_STAT,'3')} gives
 *       {@code '3'} (<i>failed</i>). Only the <i>blank</i> case is coalesced. The API path is
 *       unaffected — it calls {@code coalesceProcStat(...)} directly.</li>
 *   <li><b>DRR-5</b> (not observable) — legacy {@code RS_CD} coalesces to the empty string, the
 *       resolver to {@code null}. Every consumer treats blank uniformly
 *       ({@code countByStatus}, the frontend {@code deriveResultStatus}), so the wire difference
 *       {@code "" vs null} changes no rendered value.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DispatchResultResolverTest {

    private static final String BIZ = "VN_VNM0004_000001";
    private static final String TRAN_DT = "20260613";
    private static final String OTHER_DT = "20260614";
    private static final String FILE_SEQ_NO = "7";
    /** Master {@code TRNF_DTM} / {@code TRNF_DT} — the request datetime legacy maps to PROC_DTTM. */
    private static final String REQUEST_DTTM = "20260613090000";

    @Mock TbPayTargetRepository payTargetRepository;
    @Mock TbPayFileMstRepository payFileMstRepository;
    @Mock TbPayFileDataRepository payFileDataRepository;
    @Mock TbFirmTranRepository firmTranRepository;

    private DispatchResultResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DispatchResultResolver(
                payTargetRepository, payFileMstRepository, payFileDataRepository, firmTranRepository);
    }

    /* ─────────────────────────── fixtures ─────────────────────────── */

    /** API-path master: no {@code PAY_FILE_SEQ_NO} (legacy EXISTS tuple is NULL → ELSE branch). */
    private ScdulPymnSumr apiMaster() {
        ScdulPymnSumr m = new ScdulPymnSumr();
        m.setBizKey(BIZ);
        m.setPayTranDt(TRAN_DT);
        m.setPayFileSeqNo(null);
        return m;
    }

    /** File-path master: {@code PAY_TRAN_DT} + {@code PAY_FILE_SEQ_NO} both set. */
    private ScdulPymnSumr fileMaster() {
        ScdulPymnSumr m = new ScdulPymnSumr();
        m.setBizKey(BIZ);
        m.setPayTranDt(TRAN_DT);
        m.setPayFileSeqNo(FILE_SEQ_NO);
        return m;
    }

    private ScdulPymnSumrDetl line(String detlNo, String payTranDt, String payTranSeq) {
        ScdulPymnSumrDetl d = new ScdulPymnSumrDetl();
        d.setBizKey(BIZ);
        d.setScdulPymnDetlNo(detlNo);
        d.setPayTranDt(payTranDt);
        d.setPayTranSeq(payTranSeq);
        return d;
    }

    private TbPayTarget target(String tranSeq, String procStat, String procDttm) {
        TbPayTarget t = new TbPayTarget();
        t.setBizKey(BIZ);
        t.setTranSeq(tranSeq);
        t.setProcStat(procStat);
        t.setProcDttm(procDttm);
        return t;
    }

    private TbFirmTran firmTran(String tranSeq, String rsCd, String rsMsg) {
        TbFirmTran f = new TbFirmTran();
        f.setBizKey(BIZ);
        f.setTranSeq(tranSeq);
        f.setRsCd(rsCd);
        f.setRsMsg(rsMsg);
        return f;
    }

    private TbPayFileMst payFileMst(String procStat, String procDttm, String rsMsg) {
        TbPayFileMst m = new TbPayFileMst();
        m.setTranDt(TRAN_DT);
        m.setBizKey(BIZ);
        m.setFileSeqNo(FILE_SEQ_NO);
        m.setProcStat(procStat);
        m.setProcDttm(procDttm);
        m.setRsMsg(rsMsg);
        return m;
    }

    private TbPayFileData fileData(long seq, String rsCd) {
        TbPayFileData d = new TbPayFileData();
        d.setTranDt(TRAN_DT);
        d.setBizKey(BIZ);
        d.setFileSeqNo(FILE_SEQ_NO);
        d.setSeq(BigDecimal.valueOf(seq));
        d.setRsCd(rsCd);
        return d;
    }

    private OvrsPymnSumr ovrsMaster(String payTranDt, String payFileSeqNo) {
        OvrsPymnSumr m = new OvrsPymnSumr();
        m.setBizKey(BIZ);
        m.setPayTranDt(payTranDt);
        m.setPayFileSeqNo(payFileSeqNo);
        return m;
    }

    private OvrsPymnSumrDetl ovrsLine(String detlNo, String payTranSeq) {
        OvrsPymnSumrDetl d = new OvrsPymnSumrDetl();
        d.setBizKey(BIZ);
        d.setOvrsPymnDetlNo(detlNo);
        d.setPayTranSeq(payTranSeq);
        return d;
    }

    /* ─────────────────── route selection (legacy IS_FILE_ROUTE) ─────────────────── */

    /**
     * Legacy: the {@code EXISTS} tuple {@code (…, SPS.PAY_FILE_SEQ_NO)} is NULL when the master has no
     * file sequence, so every CASE falls to the API branch — {@code TPT}/{@code TFT}. The refactor
     * expresses the same choice as {@code payFileSeqNo == null → resolveApiPath} (DRR-4).
     */
    @Test
    @DisplayName("no PAY_FILE_SEQ_NO -> API path (TB_PAY_TARGET + TB_FIRM_TRAN only)")
    void nullPayFileSeqNo_selectsApiPath() {
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeqIn(eq(BIZ), eq(TRAN_DT), anyList()))
                .thenReturn(List.of(target("11", "0", "20260613101500")));
        when(firmTranRepository.findByBizKeyAndTranDtAndTranSeqIn(eq(BIZ), eq(TRAN_DT), anyList()))
                .thenReturn(List.of(firmTran("11", "0000", "정상처리")));

        Map<String, DispatchResult> out =
                resolver.resolve(apiMaster(), List.of(line("D1", TRAN_DT, "11")));

        assertEquals(1, out.size());
        DispatchResult r = out.get("D1");
        assertEquals("0", r.procStat());
        assertEquals("0000", r.rsCd());
        assertEquals("정상처리", r.rsMsg());
        // The File-path tables must not be touched at all on the API route.
        verifyNoInteractions(payFileMstRepository, payFileDataRepository);
    }

    /** A blank (not just null) {@code PAY_FILE_SEQ_NO} is API too — the resolver guards {@code isBlank()}. */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("null / empty / whitespace PAY_FILE_SEQ_NO all select the API path")
    void blankPayFileSeqNo_selectsApiPath(String payFileSeqNo) {
        ScdulPymnSumr m = apiMaster();
        m.setPayFileSeqNo(payFileSeqNo);
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeqIn(eq(BIZ), eq(TRAN_DT), anyList()))
                .thenReturn(List.of(target("11", "6", "20260613101500")));

        resolver.resolve(m, List.of(line("D1", TRAN_DT, "11")));

        verify(payTargetRepository).findByBizKeyAndTranDtAndTranSeqIn(eq(BIZ), eq(TRAN_DT), anyList());
        verifyNoInteractions(payFileMstRepository, payFileDataRepository);
    }

    /**
     * Legacy File route: {@code PROC_STAT}/{@code RSLT_DTTM} come from the batch-level
     * {@code TB_PAY_FILE_MST} (so every line in the batch shares them), the per-line {@code RS_CD}
     * from {@code TB_PAY_FILE_DATA} joined on {@code CAST(SEQ AS VARCHAR) = PAY_TRAN_SEQ}.
     */
    @Test
    @DisplayName("File path: master-level PROC_STAT/PROC_DTTM + per-line RS_CD from TB_PAY_FILE_DATA")
    void filePath_masterStatus_perLineRsCd() {
        when(payFileMstRepository.findById(new TbPayFileMstId(TRAN_DT, BIZ, FILE_SEQ_NO)))
                .thenReturn(Optional.of(payFileMst("0", "20260613113000", "batch ok")));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(fileData(1, "0000"), fileData(2, "E015")));

        Map<String, DispatchResult> out = resolver.resolve(fileMaster(), List.of(
                line("D1", TRAN_DT, "1"),
                line("D2", TRAN_DT, "2")));

        assertEquals("0", out.get("D1").procStat());
        assertEquals("0", out.get("D2").procStat());
        assertEquals("20260613113000", out.get("D1").rsltDttm());
        assertEquals("0000", out.get("D1").rsCd());
        assertEquals("E015", out.get("D2").rsCd());
        // The API-path tables must not be touched at all on the File route.
        verifyNoInteractions(payTargetRepository, firmTranRepository);
    }

    /* ─────────────────── PROC_STAT coalesce (legacy COALESCE(...,'3')) ─────────────────── */

    /**
     * Verbatim legacy R006: {@code COALESCE(TPT.PROC_STAT,'3')} / {@code COALESCE(TPFM.PROC_STAT,'3')}.
     * A dispatch row that exists but carries no status <b>displays as failed</b>, on both routes.
     */
    @ParameterizedTest(name = "stored PROC_STAT [{0}] -> [{1}]")
    @CsvSource(value = {
            "0    | 0",
            "3    | 3",
            "6    | 6",
            "9    | 9",
            "NULL | 3",     // legacy COALESCE
            "'  ' | 3",     // blank is not a status either
            "' 0 '| 0",     // trimmed
    }, delimiter = '|', nullValues = "NULL")
    void apiPath_nullOrBlankProcStat_coalescesToFailed(String stored, String expected) {
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeqIn(eq(BIZ), eq(TRAN_DT), anyList()))
                .thenReturn(List.of(target("11", stored, "20260613101500")));

        Map<String, DispatchResult> out =
                resolver.resolve(apiMaster(), List.of(line("D1", TRAN_DT, "11")));

        assertEquals(expected, out.get("D1").procStat());
        assertEquals(ProcStatKey.forCode(expected), out.get("D1").procStatKey());
    }

    /**
     * <b>DRR-6, pinned as current behaviour.</b> On the File path the coalesce is applied through
     * {@code mstOpt.map(TbPayFileMst::getProcStat).map(::coalesceProcStat)} — and
     * {@link Optional#map} returns {@code Optional.empty()} when the mapper yields {@code null}, so
     * the coalesce is <b>never reached for a stored NULL</b>. A present {@code TB_PAY_FILE_MST} row
     * with {@code PROC_STAT IS NULL} therefore resolves to {@code null} (which
     * {@code countByStatus} buckets as <i>in progress</i>) where legacy
     * {@code COALESCE(TPFM.PROC_STAT,'3')} resolves to {@code '3'} (<i>failed</i>). A stored blank
     * <i>is</i> coalesced, because {@code Optional.of("  ")} survives the first {@code map}.
     *
     * <p>The API path does not have this gap — it calls
     * {@code coalesceProcStat(tpt.getProcStat())} directly, so {@code null → '3'} there (see the
     * test above). Recorded as a finding rather than fixed here: it changes what a Result page shows
     * for dispatched money.
     */
    @ParameterizedTest(name = "stored PROC_STAT [{0}] -> [{1}]")
    @CsvSource(value = {
            "0    | 0",
            "3    | 3",
            "NULL | NULL",   // DRR-6: legacy COALESCE would give '3'; Optional.map swallows the null
            "'  ' | 3",      // blank survives the first map, so the coalesce does fire
    }, delimiter = '|', nullValues = "NULL")
    void filePath_blankProcStatCoalesces_butStoredNullDoesNot(String stored, String expected) {
        when(payFileMstRepository.findById(any()))
                .thenReturn(Optional.of(payFileMst(stored, "20260613113000", null)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(fileData(1, "0000")));

        Map<String, DispatchResult> out =
                resolver.resolve(fileMaster(), List.of(line("D1", TRAN_DT, "1")));

        assertEquals(expected, out.get("D1").procStat());
    }

    /**
     * The coalesce is deliberately NOT applied when the dispatch row is <b>absent</b> — the refactor
     * returns {@code null} so the frontend renders the neutral "not dispatched yet" state. Legacy's
     * LEFT JOIN would give {@code COALESCE(NULL,'3') = '3'} here, i.e. legacy renders such a line as
     * failed. Pinned as the current, intentional shape (the class Javadoc states it: "Returns
     * {@code DispatchResult#EMPTY} … whose corresponding {@code TB_PAY_*} row is missing").
     */
    @Test
    @DisplayName("absent dispatch row -> null status (NOT coalesced to '3'); legacy LEFT JOIN would give '3'")
    void missingDispatchRow_yieldsNullStatus_notCoalescedFail() {
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeqIn(eq(BIZ), eq(TRAN_DT), anyList()))
                .thenReturn(List.of());
        when(firmTranRepository.findByBizKeyAndTranDtAndTranSeqIn(eq(BIZ), eq(TRAN_DT), anyList()))
                .thenReturn(List.of());

        Map<String, DispatchResult> out =
                resolver.resolve(apiMaster(), List.of(line("D1", TRAN_DT, "11")));

        DispatchResult r = out.get("D1");
        assertNull(r.procStat());
        assertNull(r.procStatKey());
        assertNull(r.rsCd());
        assertNull(r.rsMsg());
        assertNull(r.procDttm());
        assertNull(r.rsltDttm());
    }

    /** Same for the File route when {@code TB_PAY_FILE_MST} is gone. */
    @Test
    @DisplayName("File path with no TB_PAY_FILE_MST row -> null status, per-line RS_CD still resolved")
    void filePath_missingMst_yieldsNullStatus() {
        when(payFileMstRepository.findById(any())).thenReturn(Optional.empty());
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(fileData(1, "E999")));

        Map<String, DispatchResult> out =
                resolver.resolve(fileMaster(), List.of(line("D1", TRAN_DT, "1")));

        assertNull(out.get("D1").procStat());
        assertEquals("E999", out.get("D1").rsCd());
    }

    /* ─────────────────── RS_MSG / RS_CD trimming ─────────────────── */

    /**
     * Legacy R006: {@code RS_MSG} is {@code NULL} on the File route and {@code TFT.RS_MSG} on the API
     * route — the bulk file rail has no per-line message column to read.
     */
    @Test
    @DisplayName("RS_MSG is API-only: File path always emits null (legacy R006 CASE ... THEN NULL)")
    void rsMsg_isNullOnFilePath_evenWhenTheMstCarriesOne() {
        when(payFileMstRepository.findById(any()))
                .thenReturn(Optional.of(payFileMst("3", "20260613113000", "잔액부족")));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(fileData(1, "E015")));

        Map<String, DispatchResult> out =
                resolver.resolve(fileMaster(), List.of(line("D1", TRAN_DT, "1")));

        assertNull(out.get("D1").rsMsg(), "legacy R006 emits NULL for RS_MSG on the File route");
        assertEquals("E015", out.get("D1").rsCd());
    }

    /** Legacy {@code TRIM(COALESCE(RS_CD,''))} — the refactor trims and maps blank to null (DRR-5). */
    @ParameterizedTest(name = "stored RS_CD [{0}] -> [{1}]")
    @CsvSource(value = {
            "0000    | 0000",
            "' 0000'| 0000",
            "'0000 '| 0000",
            "'   '  | NULL",
            "NULL    | NULL",
    }, delimiter = '|', nullValues = "NULL")
    void rsCd_isTrimmed_blankBecomesNull(String stored, String expected) {
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeqIn(eq(BIZ), eq(TRAN_DT), anyList()))
                .thenReturn(List.of(target("11", "0", "20260613101500")));
        when(firmTranRepository.findByBizKeyAndTranDtAndTranSeqIn(eq(BIZ), eq(TRAN_DT), anyList()))
                .thenReturn(List.of(firmTran("11", stored, null)));

        Map<String, DispatchResult> out =
                resolver.resolve(apiMaster(), List.of(line("D1", TRAN_DT, "11")));

        assertEquals(expected, out.get("D1").rsCd());
    }

    /* ─────────────────── datetime columns (DRR-1) ─────────────────── */

    /**
     * <b>DRR-1 — domestic (SCDUL) API route.</b> {@code procDttm} is the master's
     * {@code TRNF_DTM} (legacy R006:89 {@code SPS.TRNF_DTM AS PROC_DTTM}); {@code rsltDttm} is the
     * dispatch row's {@code PROC_DTTM} (legacy R006:99 {@code ELSE TPT.PROC_DTTM}). They are
     * different columns and must carry different values.
     */
    @Test
    @DisplayName("DRR-1 domestic API route: procDttm = master TRNF_DTM, rsltDttm = TPT.PROC_DTTM")
    void drr1_apiRoute_procDttmIsMasterTrnfDtm_rsltDttmIsDispatchRow() {
        ScdulPymnSumr master = apiMaster();
        master.setTrnfDtm(REQUEST_DTTM);
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeqIn(eq(BIZ), eq(TRAN_DT), anyList()))
                .thenReturn(List.of(target("11", "0", "20260613101500")));

        DispatchResult r = resolver.resolve(master, List.of(line("D1", TRAN_DT, "11"))).get("D1");

        assertEquals(REQUEST_DTTM, r.procDttm(), "procDttm must be the master's TRNF_DTM");
        assertEquals("20260613101500", r.rsltDttm(), "rsltDttm must be TB_PAY_TARGET.PROC_DTTM");
        assertNotEquals(r.procDttm(), r.rsltDttm(),
                "legacy keeps these two as distinct columns — equal values would be the DRR-1 bug");
    }

    /**
     * <b>DRR-1 — domestic (SCDUL) File route.</b> Same rule, with {@code rsltDttm} read from
     * {@code TB_PAY_FILE_MST.PROC_DTTM} (legacy R006:98 {@code THEN TPFM.PROC_DTTM}).
     */
    @Test
    @DisplayName("DRR-1 domestic File route: procDttm = master TRNF_DTM, rsltDttm = TPFM.PROC_DTTM")
    void drr1_fileRoute_procDttmIsMasterTrnfDtm_rsltDttmIsFileMst() {
        ScdulPymnSumr master = fileMaster();
        master.setTrnfDtm(REQUEST_DTTM);
        when(payFileMstRepository.findById(any()))
                .thenReturn(Optional.of(payFileMst("0", "20260613113000", null)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(fileData(1, "0000")));

        DispatchResult r = resolver.resolve(master, List.of(line("D1", TRAN_DT, "1"))).get("D1");

        assertEquals(REQUEST_DTTM, r.procDttm());
        assertEquals("20260613113000", r.rsltDttm());
        assertNotEquals(r.procDttm(), r.rsltDttm());
    }

    /**
     * <b>DRR-1 — overseas.</b> {@code procDttm} is the master's {@code TRNF_DT} (legacy R007:75),
     * {@code rsltDttm} is {@code TPFM.PROC_DTTM} (legacy R007:77).
     */
    @Test
    @DisplayName("DRR-1 overseas: procDttm = master TRNF_DT, rsltDttm = TPFM.PROC_DTTM")
    void drr1_overseas_procDttmIsMasterTrnfDt_rsltDttmIsFileMst() {
        OvrsPymnSumr master = ovrsMaster(TRAN_DT, FILE_SEQ_NO);
        master.setTrnfDt(REQUEST_DTTM);
        when(payFileMstRepository.findById(any()))
                .thenReturn(Optional.of(payFileMst("0", "20260613120000", null)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(fileData(1, "0000")));

        DispatchResult r = resolver.resolveOverseas(master, List.of(ovrsLine("OD9", "1"))).get("OD9");

        assertEquals(REQUEST_DTTM, r.procDttm());
        assertEquals("20260613120000", r.rsltDttm());
        assertNotEquals(r.procDttm(), r.rsltDttm());
    }

    /**
     * <b>DRR-1 — nulls.</b> A batch with no {@code TRNF_DTM} (never dispatched) yields a null
     * {@code procDttm}; a dispatched batch whose bank-side result has not landed yields a non-null
     * {@code procDttm} with a null {@code rsltDttm}. The two nulls are independent — that
     * independence is exactly what the old always-equal mapping destroyed.
     */
    @Test
    @DisplayName("DRR-1 nulls: the two datetimes are independently nullable")
    void drr1_nullDatetimes_areIndependent() {
        // (a) master never dispatched -> procDttm null, but the dispatch row (if any) still shows.
        ScdulPymnSumr noTrnf = apiMaster();
        noTrnf.setTrnfDtm(null);
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeqIn(eq(BIZ), eq(TRAN_DT), anyList()))
                .thenReturn(List.of(target("11", "6", "20260613101500")));
        DispatchResult a = resolver.resolve(noTrnf, List.of(line("D1", TRAN_DT, "11"))).get("D1");
        assertNull(a.procDttm());
        assertEquals("20260613101500", a.rsltDttm());

        // (b) dispatched, but the bank has not reported: PROC_DTTM null on the dispatch row.
        ScdulPymnSumr dispatched = apiMaster();
        dispatched.setTrnfDtm(REQUEST_DTTM);
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeqIn(eq(BIZ), eq(TRAN_DT), anyList()))
                .thenReturn(List.of(target("11", "6", null)));
        DispatchResult b = resolver.resolve(dispatched, List.of(line("D1", TRAN_DT, "11"))).get("D1");
        assertEquals(REQUEST_DTTM, b.procDttm());
        assertNull(b.rsltDttm());
    }

    /**
     * <b>DRR-1 — multiple details.</b> {@code procDttm} is a master column, so every line of the
     * batch carries the identical value while {@code rsltDttm} varies per line. A per-line
     * {@code procDttm} would be the regression.
     */
    @Test
    @DisplayName("DRR-1 multi-detail: procDttm identical on every line, rsltDttm per line")
    void drr1_multipleDetails_shareOneProcDttm_butHaveOwnRsltDttm() {
        ScdulPymnSumr master = apiMaster();
        master.setTrnfDtm(REQUEST_DTTM);
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeqIn(eq(BIZ), eq(TRAN_DT), anyList()))
                .thenReturn(List.of(
                        target("11", "0", "20260613101500"),
                        target("12", "3", "20260613102000"),
                        target("13", "6", null)));

        Map<String, DispatchResult> out = resolver.resolve(master, List.of(
                line("D1", TRAN_DT, "11"),
                line("D2", TRAN_DT, "12"),
                line("D3", TRAN_DT, "13"),
                line("D4", null, null)));          // never dispatched — still gets the master datetime

        assertEquals(REQUEST_DTTM, out.get("D1").procDttm());
        assertEquals(REQUEST_DTTM, out.get("D2").procDttm());
        assertEquals(REQUEST_DTTM, out.get("D3").procDttm());
        assertEquals(REQUEST_DTTM, out.get("D4").procDttm(),
                "PROC_DTTM is a plain master column in legacy — it does not depend on any join");

        assertEquals("20260613101500", out.get("D1").rsltDttm());
        assertEquals("20260613102000", out.get("D2").rsltDttm());
        assertNull(out.get("D3").rsltDttm());
        assertNull(out.get("D4").rsltDttm());
    }

    /** Overseas multi-detail twin of the case above. */
    @Test
    @DisplayName("DRR-1 overseas multi-detail: one shared procDttm, one shared rsltDttm")
    void drr1_overseasMultipleDetails_shareBothDatetimes() {
        OvrsPymnSumr master = ovrsMaster(TRAN_DT, FILE_SEQ_NO);
        master.setTrnfDt(REQUEST_DTTM);
        when(payFileMstRepository.findById(any()))
                .thenReturn(Optional.of(payFileMst("0", "20260613120000", null)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(fileData(1, "0000"), fileData(2, "S102")));

        Map<String, DispatchResult> out = resolver.resolveOverseas(
                master, List.of(ovrsLine("OD1", "1"), ovrsLine("OD2", "2")));

        // Overseas status/datetime are both batch-level (TB_PAY_FILE_MST), only RS_CD is per line.
        assertEquals(REQUEST_DTTM, out.get("OD1").procDttm());
        assertEquals(REQUEST_DTTM, out.get("OD2").procDttm());
        assertEquals("20260613120000", out.get("OD1").rsltDttm());
        assertEquals("20260613120000", out.get("OD2").rsltDttm());
        assertEquals("0000", out.get("OD1").rsCd());
        assertEquals("S102", out.get("OD2").rsCd());
    }

    /* ─────────────────── two PAY_TRAN_DT values in one batch ─────────────────── */

    /**
     * Legacy joins {@code TB_PAY_TARGET}/{@code TB_FIRM_TRAN} on {@code SPMD.PAY_TRAN_DT} — the
     * <b>line's</b> date, not the master's. A batch that crossed the cut-off boundary therefore has
     * lines on two adjacent dates and each still resolves. The refactor groups the shadow keys by
     * date and issues one bulk lookup per distinct date; this test would fail if it collapsed to the
     * master's date or issued a single unqualified query.
     */
    @Test
    @DisplayName("lines spanning two PAY_TRAN_DT values are all resolved (one lookup per date)")
    void linesSpanningTwoTranDts_areAllResolved() {
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeqIn(eq(BIZ), eq(TRAN_DT), anyList()))
                .thenReturn(List.of(target("11", "0", "20260613101500")));
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeqIn(eq(BIZ), eq(OTHER_DT), anyList()))
                .thenReturn(List.of(target("11", "3", "20260614010500")));
        when(firmTranRepository.findByBizKeyAndTranDtAndTranSeqIn(eq(BIZ), eq(TRAN_DT), anyList()))
                .thenReturn(List.of(firmTran("11", "0000", "ok")));
        when(firmTranRepository.findByBizKeyAndTranDtAndTranSeqIn(eq(BIZ), eq(OTHER_DT), anyList()))
                .thenReturn(List.of(firmTran("11", "E015", "insufficient")));

        Map<String, DispatchResult> out = resolver.resolve(apiMaster(), List.of(
                line("D1", TRAN_DT, "11"),
                line("D2", OTHER_DT, "11")));

        assertEquals(2, out.size());
        // Same TRAN_SEQ on two different dates must NOT collide — the cache key is date|seq.
        assertEquals("0", out.get("D1").procStat());
        assertEquals("0000", out.get("D1").rsCd());
        assertEquals("3", out.get("D2").procStat());
        assertEquals("E015", out.get("D2").rsCd());
        verify(payTargetRepository).findByBizKeyAndTranDtAndTranSeqIn(eq(BIZ), eq(TRAN_DT), anyList());
        verify(payTargetRepository).findByBizKeyAndTranDtAndTranSeqIn(eq(BIZ), eq(OTHER_DT), anyList());
    }

    /**
     * A line with no shadow keys yet (never dispatched) resolves to an all-null dispatch result —
     * never to a sibling's row. Since DRR-1 it still carries the master's {@code TRNF_DTM}, because
     * legacy's {@code PROC_DTTM} is a plain master column that no join can suppress.
     */
    @Test
    @DisplayName("un-dispatched line (null PAY_TRAN_DT/SEQ) resolves empty except the master datetime")
    void lineWithoutShadowKeys_resolvesToMasterDatetimeOnly() {
        ScdulPymnSumr master = apiMaster();
        master.setTrnfDtm(REQUEST_DTTM);
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeqIn(eq(BIZ), eq(TRAN_DT), anyList()))
                .thenReturn(List.of(target("11", "0", "20260613101500")));

        Map<String, DispatchResult> out = resolver.resolve(master, List.of(
                line("D1", TRAN_DT, "11"),
                line("D2", null, null),
                line("D3", TRAN_DT, null)));

        assertEquals("0", out.get("D1").procStat());
        for (String key : List.of("D2", "D3")) {
            DispatchResult r = out.get(key);
            assertNull(r.procStat(), key);
            assertNull(r.procStatKey(), key);
            assertNull(r.rsltDttm(), key);
            assertNull(r.rsCd(), key);
            assertNull(r.rsMsg(), key);
            assertEquals(REQUEST_DTTM, r.procDttm(), key);
        }
    }

    /** Every line's shadow keys null → nothing to look up; no query is issued at all. */
    @Test
    @DisplayName("no line carries shadow keys -> empty map and no repository call")
    void noShadowKeysAtAll_returnsEmptyMap_withoutQuerying() {
        Map<String, DispatchResult> out =
                resolver.resolve(apiMaster(), List.of(line("D1", null, null)));

        assertTrue(out.isEmpty());
        verify(payTargetRepository, never())
                .findByBizKeyAndTranDtAndTranSeqIn(anyString(), anyString(), anyList());
    }

    /* ─────────────────── duplicate SEQ merge rule ─────────────────── */

    /**
     * {@code Collectors.toMap(..., (a, b) -> a)} — a duplicate {@code TB_PAY_FILE_DATA.SEQ} keeps the
     * <b>first</b> row and does not throw. {@code SEQ} is part of that table's key so the case should
     * not arise, but an unguarded {@code toMap} would blow up an entire Result page with
     * {@code IllegalStateException: Duplicate key} instead of rendering it.
     */
    @Test
    @DisplayName("duplicate TB_PAY_FILE_DATA.SEQ keeps the first row and never throws")
    void duplicateFileDataSeq_keepsFirst() {
        when(payFileMstRepository.findById(any()))
                .thenReturn(Optional.of(payFileMst("0", "20260613113000", null)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(fileData(1, "FIRST"), fileData(1, "SECOND")));

        Map<String, DispatchResult> out =
                resolver.resolve(fileMaster(), List.of(line("D1", TRAN_DT, "1")));

        assertEquals("FIRST", out.get("D1").rsCd());
    }

    /**
     * {@code SEQ} is numeric in the DB and {@code PAY_TRAN_SEQ} is a string; legacy bridges them with
     * {@code CAST(TPFD.SEQ AS VARCHAR)}. The refactor keys the map on
     * {@code BigDecimal.toPlainString()}, so an unpadded {@code "1"} matches and a scaled or padded
     * form does not — exactly legacy's {@code CAST} semantics.
     */
    @Test
    @DisplayName("SEQ join is the plain-string CAST: '1' matches, '01' does not")
    void seqJoin_usesPlainStringCast() {
        when(payFileMstRepository.findById(any()))
                .thenReturn(Optional.of(payFileMst("0", "20260613113000", null)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(fileData(1, "0000")));

        Map<String, DispatchResult> out = resolver.resolve(fileMaster(), List.of(
                line("D1", TRAN_DT, "1"),
                line("D2", TRAN_DT, "01")));

        assertEquals("0000", out.get("D1").rsCd());
        assertNull(out.get("D2").rsCd(), "'01' is not the CAST form of numeric 1");
        // Both lines still get the batch-level status — legacy's File-route PROC_STAT is master-level.
        assertEquals("0", out.get("D2").procStat());
    }

    /* ─────────────────── empty / defensive inputs ─────────────────── */

    @Test
    @DisplayName("null or empty line list -> empty map, no query")
    void nullOrEmptyLines_returnEmptyMap() {
        assertTrue(resolver.resolve(fileMaster(), null).isEmpty());
        assertTrue(resolver.resolve(fileMaster(), List.of()).isEmpty());
        assertTrue(resolver.resolveOverseas(ovrsMaster(TRAN_DT, FILE_SEQ_NO), null).isEmpty());
        assertTrue(resolver.resolveOverseas(ovrsMaster(TRAN_DT, FILE_SEQ_NO), List.of()).isEmpty());
        verifyNoInteractions(payTargetRepository, payFileMstRepository,
                payFileDataRepository, firmTranRepository);
    }

    /** File-path master missing either shadow key cannot be joined — legacy's tuple compare is NULL. */
    @Test
    @DisplayName("File path with null PAY_TRAN_DT -> empty map, no query")
    void filePath_nullPayTranDt_returnsEmptyMap() {
        ScdulPymnSumr m = fileMaster();
        m.setPayTranDt(null);

        assertTrue(resolver.resolve(m, List.of(line("D1", TRAN_DT, "1"))).isEmpty());
        verifyNoInteractions(payFileMstRepository, payFileDataRepository);
    }

    /* ─────────────────── Overseas (legacy OVRS_PYMN_SUMR_R007) ─────────────────── */

    /**
     * Legacy R007 keys its rows on {@code SPMD.OVRS_PYMN_DETL_NO} while joining
     * {@code TB_PAY_FILE_DATA} on {@code CAST(TPFD.SEQ AS VARCHAR) = SPMD.PAY_TRAN_SEQ} — two
     * different columns. The map key must be the detail number (what the caller looks up) and the
     * {@code RS_CD} must come from the {@code PAY_TRAN_SEQ} join.
     */
    @Test
    @DisplayName("Overseas keys by OVRS_PYMN_DETL_NO while joining RS_CD on PAY_TRAN_SEQ")
    void overseas_keysByDetlNo_joinsOnPayTranSeq() {
        when(payFileMstRepository.findById(new TbPayFileMstId(TRAN_DT, BIZ, FILE_SEQ_NO)))
                .thenReturn(Optional.of(payFileMst("0", "20260613120000", "swift accepted")));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(fileData(1, "0000"), fileData(2, "S102")));

        Map<String, DispatchResult> out = resolver.resolveOverseas(
                ovrsMaster(TRAN_DT, FILE_SEQ_NO),
                List.of(ovrsLine("OD9", "1"), ovrsLine("OD8", "2")));

        // Keys are detail numbers, NOT the PAY_TRAN_SEQ values.
        assertEquals(java.util.Set.of("OD9", "OD8"), out.keySet());
        assertEquals("0000", out.get("OD9").rsCd());
        assertEquals("S102", out.get("OD8").rsCd());
        assertEquals("20260613120000", out.get("OD9").rsltDttm());
        // Overseas is always the File route — the API tables are never consulted.
        verifyNoInteractions(payTargetRepository, firmTranRepository);
    }

    /* ─────────────────── DRR-2 · Overseas RS_MSG (legacy R007:80) ─────────────────── */

    /**
     * <b>DRR-2 — failure.</b> Legacy R007:80 selects {@code TPFM.RS_MSG}, so a SWIFT rejection
     * message reaches the Overseas Result detail. Source precedence is unambiguous: the pay-file
     * master is the only table on this path with an {@code RS_MSG} column
     * ({@code TB_PAY_FILE_DATA} carries {@code RS_CD} only; there is no {@code TB_FIRM_TRAN} row
     * without an API rail).
     */
    @Test
    @DisplayName("DRR-2 failure: Overseas rs_msg comes from TB_PAY_FILE_MST.RS_MSG")
    void drr2_overseasFailure_emitsMasterRsMsg() {
        when(payFileMstRepository.findById(any()))
                .thenReturn(Optional.of(payFileMst("3", "20260613120000", "BENEFICIARY BANK REJECTED")));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(fileData(1, "S102")));

        DispatchResult r = resolver.resolveOverseas(
                        ovrsMaster(TRAN_DT, FILE_SEQ_NO), List.of(ovrsLine("OD9", "1")))
                .get("OD9");

        assertEquals("BENEFICIARY BANK REJECTED", r.rsMsg());
        assertEquals("3", r.procStat());
        assertEquals("S102", r.rsCd());
    }

    /** <b>DRR-2 — success.</b> A settled file's message is surfaced just the same. */
    @Test
    @DisplayName("DRR-2 success: a settled Overseas file still surfaces its master message")
    void drr2_overseasSuccess_emitsMasterRsMsg() {
        when(payFileMstRepository.findById(any()))
                .thenReturn(Optional.of(payFileMst("0", "20260613120000", "정상처리")));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(fileData(1, "0000")));

        DispatchResult r = resolver.resolveOverseas(
                        ovrsMaster(TRAN_DT, FILE_SEQ_NO), List.of(ovrsLine("OD9", "1")))
                .get("OD9");

        assertEquals("정상처리", r.rsMsg());
        assertEquals("0", r.procStat());
        assertEquals("0000", r.rsCd());
    }

    /**
     * <b>DRR-2 — null / blank message.</b> A genuinely null master message stays null. A blank one is
     * normalised to null, matching how the API path already treats {@code TFT.RS_MSG} — the frontend
     * and {@code countByStatus} treat blank and null identically, so this normalisation changes no
     * rendered value.
     */
    @ParameterizedTest(name = "stored RS_MSG [{0}] -> null")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("DRR-2 null/blank: a genuinely null (or blank) master message stays null")
    void drr2_nullOrBlankMasterMessage_staysNull(String stored) {
        when(payFileMstRepository.findById(any()))
                .thenReturn(Optional.of(payFileMst("0", "20260613120000", stored)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(fileData(1, "0000")));

        DispatchResult r = resolver.resolveOverseas(
                        ovrsMaster(TRAN_DT, FILE_SEQ_NO), List.of(ovrsLine("OD9", "1")))
                .get("OD9");

        assertNull(r.rsMsg());
    }

    /**
     * <b>DRR-2 — fallback when the master row is absent.</b> Legacy's LEFT JOIN yields a null
     * {@code TPFM.RS_MSG}; the resolver returns null without consulting any other table. In
     * particular it must NOT fall back to {@code TB_FIRM_TRAN} — Overseas has no API rail, so
     * reading one would be an invented source.
     */
    @Test
    @DisplayName("DRR-2 fallback: no TB_PAY_FILE_MST row -> null rs_msg, no API-table lookup")
    void drr2_missingMasterRow_fallsBackToNull_withoutApiLookup() {
        when(payFileMstRepository.findById(any())).thenReturn(Optional.empty());
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(fileData(1, "S102")));

        DispatchResult r = resolver.resolveOverseas(
                        ovrsMaster(TRAN_DT, FILE_SEQ_NO), List.of(ovrsLine("OD9", "1")))
                .get("OD9");

        assertNull(r.rsMsg());
        assertNull(r.procStat());
        assertNull(r.rsltDttm());
        assertEquals("S102", r.rsCd(), "the per-line RS_CD is still resolved");
        verifyNoInteractions(payTargetRepository, firmTranRepository);
    }

    /**
     * The SCDUL File path must keep emitting {@code null} — DRR-2 is scoped to Overseas only, because
     * legacy R006 selects {@code NULL} outright for the File route while R007 selects
     * {@code TPFM.RS_MSG}. Guards against the fix leaking across paths.
     */
    @Test
    @DisplayName("DRR-2 scope: the SCDUL File path still emits null rs_msg (legacy R006 THEN NULL)")
    void drr2_isScopedToOverseas_scdulFilePathUnchanged() {
        when(payFileMstRepository.findById(any()))
                .thenReturn(Optional.of(payFileMst("3", "20260613113000", "잔액부족")));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(fileData(1, "E015")));

        DispatchResult r = resolver.resolve(fileMaster(), List.of(line("D1", TRAN_DT, "1"))).get("D1");

        assertNull(r.rsMsg(), "R006 emits NULL for RS_MSG on the File route — unchanged by DRR-2");
    }

    /**
     * <b>DRR-3, pinned as current behaviour.</b> A <i>blank</i> (non-null) {@code TPFM.PROC_STAT} is
     * coalesced to {@code '3'} on the Overseas path. Legacy R007 selects {@code TPFM.PROC_STAT}
     * verbatim with no {@code COALESCE}, so it would emit the blank. Narrow — the dispatch writers
     * always set a status — but it is a real per-path divergence from R007, and it is the same
     * coalesce R006 legitimately applies on the SCDUL paths.
     */
    @Test
    @DisplayName("DRR-3: Overseas blank PROC_STAT is coalesced to '3' (legacy R007 has no COALESCE)")
    void overseas_blankProcStatIsCoalesced() {
        when(payFileMstRepository.findById(any()))
                .thenReturn(Optional.of(payFileMst("   ", "20260613120000", null)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(fileData(1, "0000")));

        DispatchResult r = resolver.resolveOverseas(
                        ovrsMaster(TRAN_DT, FILE_SEQ_NO), List.of(ovrsLine("OD9", "1")))
                .get("OD9");

        assertEquals("3", r.procStat(), "DRR-3: legacy R007 would carry the blank through");
    }

    /** A line whose {@code OVRS_PYMN_DETL_NO} is null cannot be keyed and is skipped, not NPE'd. */
    @Test
    @DisplayName("Overseas line with null OVRS_PYMN_DETL_NO is skipped, siblings still resolved")
    void overseas_nullDetlNo_isSkipped() {
        when(payFileMstRepository.findById(any()))
                .thenReturn(Optional.of(payFileMst("0", "20260613120000", null)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(fileData(1, "0000")));

        Map<String, DispatchResult> out = resolver.resolveOverseas(
                ovrsMaster(TRAN_DT, FILE_SEQ_NO),
                List.of(ovrsLine(null, "1"), ovrsLine("OD9", "1")));

        assertEquals(1, out.size());
        assertNotNull(out.get("OD9"));
    }

    /**
     * An un-dispatched Overseas batch (no {@code PAY_TRAN_DT} / {@code PAY_FILE_SEQ_NO}, or blank)
     * has nothing to join to. Legacy's LEFT JOINs would yield all-null columns; the refactor returns
     * an empty map so the caller's {@code getOrDefault(..., EMPTY)} supplies the same all-null row —
     * and, unlike legacy, without a wasted query.
     */
    @ParameterizedTest(name = "payTranDt=[{0}] payFileSeqNo=[{1}]")
    @CsvSource(value = {
            "NULL     | 7",
            "20260613 | NULL",
            "''       | 7",
            "20260613 | ''",
            "NULL     | NULL",
    }, delimiter = '|', nullValues = "NULL")
    @DisplayName("Overseas with missing/blank shadow keys -> empty map, no query")
    void overseas_missingShadowKeys_returnsEmptyMap(String payTranDt, String payFileSeqNo) {
        Map<String, DispatchResult> out = resolver.resolveOverseas(
                ovrsMaster(payTranDt, payFileSeqNo), List.of(ovrsLine("OD9", "1")));

        assertTrue(out.isEmpty());
        verifyNoInteractions(payFileMstRepository, payFileDataRepository);
    }

    /** Overseas duplicate-SEQ merge follows the same first-wins rule as the SCDUL File path. */
    @Test
    @DisplayName("Overseas duplicate TB_PAY_FILE_DATA.SEQ keeps the first row")
    void overseas_duplicateSeq_keepsFirst() {
        when(payFileMstRepository.findById(any()))
                .thenReturn(Optional.of(payFileMst("0", "20260613120000", null)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(fileData(1, "FIRST"), fileData(1, "SECOND")));

        Map<String, DispatchResult> out = resolver.resolveOverseas(
                ovrsMaster(TRAN_DT, FILE_SEQ_NO), List.of(ovrsLine("OD9", "1")));

        assertEquals("FIRST", out.get("OD9").rsCd());
    }
}
