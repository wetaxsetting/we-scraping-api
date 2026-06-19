package com.kosign.wecms.domain.payment.payrollpayment.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.common.TransferLineDetailMapper;
import com.kosign.wecms.domain.payment.dispatch.DispatchCountRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResult;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultDetailResponse;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@code PayrollPaymentResultService.countByStatus} — the detail-header
 * Success / Failed / In-Progress classifier (legacy {@code pymn_0006_002.ts}
 * per-line rule + {@code SCDUL_PYMN_SUMR_R011_CNT} counts) — across every
 * {@code PROC_STAT} × {@code RS_CD} combination, including the space-padded and
 * null/blank cases the live DB carries (`RS_CD = '0000      '`, missing
 * dispatch rows). Drives the assertion through the public {@code detail(...)}
 * path so the private classifier is exercised exactly as production runs it.
 *
 * <p>Classifier contract (mirrors {@code countByStatus}):
 * <ul>
 *   <li>blank {@code proc_stat} AND blank {@code rs_cd} (after trim) &rarr; In&nbsp;Progress</li>
 *   <li>{@code proc_stat='0'} AND {@code rs_cd='0000'} (after trim) &rarr; Success</li>
 *   <li>{@code proc_stat} &isin; {{@code 9},{@code 1},{@code 6}} &rarr; In&nbsp;Progress</li>
 *   <li>everything else &rarr; Failed</li>
 * </ul>
 */
class PayrollPaymentResultStatusCountTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";

    private ScdulPymnSumrRepository     sumrRepository;
    private ScdulPymnSumrDetlRepository detlRepository;
    private DispatchResultResolver      dispatchResultResolver;
    private PayrollPaymentResultService service;

    @BeforeEach
    void setUp() {
        sumrRepository = mock(ScdulPymnSumrRepository.class);
        detlRepository = mock(ScdulPymnSumrDetlRepository.class);
        DispatchCountRepository dispatchCountRepository = mock(DispatchCountRepository.class);
        TransferLineDetailMapper transferLineDetailMapper = mock(TransferLineDetailMapper.class);
        dispatchResultResolver = mock(DispatchResultResolver.class);
        ExptResolMstrRepository exptResolMstrRepository = mock(ExptResolMstrRepository.class);
        LegacyFunctionRepository legacyFunctions = mock(LegacyFunctionRepository.class);
        service = new PayrollPaymentResultService(
                sumrRepository, detlRepository, dispatchCountRepository,
                transferLineDetailMapper, dispatchResultResolver, exptResolMstrRepository,
                legacyFunctions);

        when(exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(anyString(), anyList()))
                .thenReturn(Collections.emptyList());
        when(transferLineDetailMapper.resolveBankNm(any())).thenReturn(null);
        when(legacyFunctions.resolveNapasBankNm(any())).thenReturn(null);
        when(legacyFunctions.resolveChartAcctDisplay(anyString(), anyList()))
                .thenReturn(Collections.emptyMap());

        TenantContext.set("tester", "tester", BIZ_KEY,
                LangCd.EN, TenantContext.AUTH_GR_USER, "F");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static ScdulPymnSumrDetl line(String detlNo) {
        ScdulPymnSumrDetl d = new ScdulPymnSumrDetl();
        d.setScdulPymnDetlNo(detlNo);
        d.setTranAmt(new BigDecimal("1000"));
        return d;
    }

    private static DispatchResult result(String procStat, String rsCd) {
        return new DispatchResult(procStat, null, null, null, rsCd, null);
    }

    /**
     * Run detail() over a fixed line set whose dispatch result is stubbed
     * per detl-no, returning the resolved [success, failed, inProgress] counts.
     */
    private TransferResultDetailResponse detailWith(Map<String, DispatchResult> resultByDetlNo) {
        ScdulPymnSumr master = new ScdulPymnSumr();
        master.setBizKey(BIZ_KEY);
        master.setScdulPymnNo("999");
        master.setScdulPymnTypeCd("00");
        master.setTrnfYn("Y");

        List<ScdulPymnSumrDetl> rows = new ArrayList<>();
        for (String detlNo : resultByDetlNo.keySet()) {
            rows.add(line(detlNo));
        }
        when(sumrRepository.findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(eq(BIZ_KEY), eq("999"), eq("00")))
                .thenReturn(Optional.of(master));
        when(detlRepository.findByBizKeyAndScdulPymnNo(eq(BIZ_KEY), eq("999"), any(Sort.class)))
                .thenReturn(rows);
        when(dispatchResultResolver.resolve(any(), anyList())).thenReturn(resultByDetlNo);

        return service.detail("999");
    }

    @Test
    void classifies_every_proc_stat_and_rs_cd_combination() {
        Map<String, DispatchResult> m = new HashMap<>();
        m.put("1", result("0", "0000"));        // success
        m.put("2", result("3", null));          // failed (proc=3)
        m.put("3", result("0", "9999"));         // failed (proc=0 but rs != 0000)
        m.put("4", result("9", null));          // in progress
        m.put("5", result("1", null));          // in progress
        m.put("6", result("6", null));          // in progress
        m.put("7", result(null, null));         // in progress (no dispatch row)
        m.put("8", result("  ", "  "));         // in progress (blank after trim)
        m.put("9", result("0", "0000   "));      // success (rs trimmed to 0000)

        TransferResultDetailResponse res = detailWith(m);

        assertThat(res.sucCnt()).as("Success").isEqualByComparingTo("2"); // lines 1, 9
        assertThat(res.errCnt()).as("Failed").isEqualByComparingTo("2");  // lines 2, 3
        assertThat(res.prcCnt()).as("In Progress").isEqualByComparingTo("5"); // 4,5,6,7,8
        // All = success + failed + inProgress invariant.
        assertThat(res.sucCnt().add(res.errCnt()).add(res.prcCnt()))
                .isEqualByComparingTo(String.valueOf(m.size()));
    }

    @Test
    void padded_success_code_counts_as_success_not_failed() {
        // The live DB stores RS_CD right-space-padded ('0000      '); a missing
        // TRIM would misclassify a settled success as failed.
        TransferResultDetailResponse res = detailWith(Map.of("1", result("0", "0000      ")));
        assertThat(res.sucCnt()).isEqualByComparingTo("1");
        assertThat(res.errCnt()).isEqualByComparingTo("0");
        assertThat(res.prcCnt()).isEqualByComparingTo("0");
    }

    @Test
    void undispatched_line_counts_as_in_progress() {
        TransferResultDetailResponse res = detailWith(Map.of("1", DispatchResult.EMPTY));
        assertThat(res.prcCnt()).isEqualByComparingTo("1");
        assertThat(res.sucCnt()).isEqualByComparingTo("0");
        assertThat(res.errCnt()).isEqualByComparingTo("0");
    }
}
