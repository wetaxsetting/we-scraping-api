package com.kosign.wecms.domain.payment.payrollpayment.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.common.TransferLineDetailMapper;
import com.kosign.wecms.domain.payment.dispatch.DispatchCountRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultDetailResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultLine;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Legacy-parity regression for the Payroll Payment Result <b>detail</b> page —
 * the Reporting Account display (legacy {@code IDO.SCDUL_PYMN_SUMR_R011.xml:70-71}
 * → {@code wb_get_chart_acct_nm} / {@code wb_get_chart_acct_lv_nm}, rendered by
 * {@code pymn_0006_002.ts:475-479}) and the detail-header withdrawal bank
 * (legacy {@code R005.xml:50} → single-arg {@code wb_get_napas_bank_nm}).
 *
 * <p>Fixture values are the real JEX DEV payroll row {@code SCDUL_PYMN_NO=241}
 * ({@code CHART_ACCT_CD='Code0011'}, {@code CHART_ACCT_SEQ_NO='86'}) →
 * {@code wb_get_chart_acct_nm = "Code0011 - SALES TEST"},
 * {@code wb_get_chart_acct_lv_nm = "REVENUE > SALES"}, and
 * {@code OUT_BANK_CD='970424'} → {@code wb_get_napas_bank_nm = "Shinhan VN"}.
 */
class PayrollPaymentResultReportingAccountTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";

    private ScdulPymnSumrRepository     sumrRepository;
    private ScdulPymnSumrDetlRepository detlRepository;
    private DispatchResultResolver      dispatchResultResolver;
    private TransferLineDetailMapper    transferLineDetailMapper;
    private LegacyFunctionRepository    legacyFunctions;
    private PayrollPaymentResultService service;

    @BeforeEach
    void setUp() {
        sumrRepository = mock(ScdulPymnSumrRepository.class);
        detlRepository = mock(ScdulPymnSumrDetlRepository.class);
        DispatchCountRepository dispatchCountRepository = mock(DispatchCountRepository.class);
        transferLineDetailMapper = mock(TransferLineDetailMapper.class);
        dispatchResultResolver = mock(DispatchResultResolver.class);
        ExptResolMstrRepository exptResolMstrRepository = mock(ExptResolMstrRepository.class);
        legacyFunctions = mock(LegacyFunctionRepository.class);
        service = new PayrollPaymentResultService(
                sumrRepository, detlRepository, dispatchCountRepository,
                transferLineDetailMapper, dispatchResultResolver, exptResolMstrRepository,
                legacyFunctions);

        // detail() has no approval master for these batches → empty lookup.
        when(exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(anyString(), anyList()))
                .thenReturn(Collections.emptyList());
        // Per-line statuses are not under test — return no dispatch results so
        // every line resolves to DispatchResult.EMPTY.
        when(dispatchResultResolver.resolve(any(), anyList()))
                .thenReturn(Collections.emptyMap());
        // Per-line deposit-bank name is a separate concern (R011.xml:54) — stub.
        when(transferLineDetailMapper.resolveBankNm(anyString())).thenReturn("ShinhanBank");

        TenantContext.set("tester", "tester", BIZ_KEY,
                LangCd.EN, TenantContext.AUTH_GR_USER, "F");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static ScdulPymnSumr master(String no, String outBankCd) {
        ScdulPymnSumr s = new ScdulPymnSumr();
        s.setBizKey(BIZ_KEY);
        s.setScdulPymnNo(no);
        s.setScdulPymnTypeCd("00");
        s.setCcyCd("VND");
        s.setOutBankCd(outBankCd);
        s.setWdrlActNo("700005285299");
        s.setTitl("WeCMS_Payroll_upload_EN");
        s.setTotalTranAmt(new BigDecimal("3000000"));
        s.setTotTrnsCnt(BigDecimal.ONE);
        s.setTrnfYn("Y");
        return s;
    }

    private static ScdulPymnSumrDetl line(String detlNo, String chartCd, String chartSeqNo) {
        ScdulPymnSumrDetl d = new ScdulPymnSumrDetl();
        d.setScdulPymnDetlNo(detlNo);
        d.setTranAmt(new BigDecimal("3000000"));
        d.setTarBankCd("79616001");
        d.setTrnfCcyCd("VND");
        d.setChartAcctCd(chartCd);
        d.setChartAcctSeqNo(chartSeqNo);
        return d;
    }

    private void stubDetail(ScdulPymnSumr master, ScdulPymnSumrDetl... rows) {
        when(sumrRepository.findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(
                eq(BIZ_KEY), eq(master.getScdulPymnNo()), eq("00")))
                .thenReturn(Optional.of(master));
        when(detlRepository.findByBizKeyAndScdulPymnNo(
                eq(BIZ_KEY), eq(master.getScdulPymnNo()), any(Sort.class)))
                .thenReturn(List.of(rows));
    }

    @Test
    void reporting_account_resolves_name_and_level_via_legacy_functions() {
        stubDetail(master("241", "970424"),
                line("324", "Code0011", "86"),   // has chart account
                line("325", null, null));        // no chart account
        when(legacyFunctions.resolveChartAcctDisplay(eq(BIZ_KEY), anyList()))
                .thenReturn(Map.of("86",
                        new String[] { "Code0011 - SALES TEST", "REVENUE > SALES" }));
        when(legacyFunctions.resolveNapasBankNm("970424")).thenReturn("Shinhan VN");

        TransferResultDetailResponse res = service.detail("241");

        TransferResultLine withChart = res.rows().get(0);
        assertThat(withChart.chartAcctNm()).isEqualTo("Code0011 - SALES TEST");
        assertThat(withChart.chartAcctLevelNm()).isEqualTo("REVENUE > SALES");

        TransferResultLine noChart = res.rows().get(1);
        assertThat(noChart.chartAcctNm()).isNull();
        assertThat(noChart.chartAcctLevelNm()).isNull();
    }

    @Test
    void chart_display_lookup_only_includes_seq_nos_for_lines_with_a_chart_code() {
        stubDetail(master("241", "970424"),
                line("324", "Code0011", "86"),
                line("325", null, null),
                line("326", "  ", "999"));   // blank code → excluded
        when(legacyFunctions.resolveChartAcctDisplay(eq(BIZ_KEY), anyList()))
                .thenReturn(Map.of("86",
                        new String[] { "Code0011 - SALES TEST", "REVENUE > SALES" }));
        when(legacyFunctions.resolveNapasBankNm(anyString())).thenReturn("Shinhan VN");

        service.detail("241");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> seqCaptor = ArgumentCaptor.forClass(List.class);
        verify(legacyFunctions).resolveChartAcctDisplay(eq(BIZ_KEY), seqCaptor.capture());
        assertThat(seqCaptor.getValue()).containsExactly("86");
    }

    @Test
    void withdrawal_bank_header_uses_single_arg_wb_get_napas_bank_nm() {
        stubDetail(master("241", "970424"), line("324", "Code0011", "86"));
        when(legacyFunctions.resolveChartAcctDisplay(eq(BIZ_KEY), anyList()))
                .thenReturn(Map.of("86",
                        new String[] { "Code0011 - SALES TEST", "REVENUE > SALES" }));
        // Legacy R005.xml:50 → "Shinhan VN" (NOT the or-CITAD "ShinhanBank").
        when(legacyFunctions.resolveNapasBankNm("970424")).thenReturn("Shinhan VN");

        TransferResultDetailResponse res = service.detail("241");

        assertThat(res.outBankNm()).isEqualTo("Shinhan VN");
        verify(legacyFunctions).resolveNapasBankNm("970424");
    }
}
