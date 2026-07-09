package com.kosign.wecms.domain.payment.expensepayment.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.common.TransferLineDetailMapper;
import com.kosign.wecms.domain.payment.dispatch.DispatchCountRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultListResponse;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the Expense Payment Result footer currency totals
 * (F-1 fix) — totals come from the full filtered, dispatched result set
 * (legacy {@code SCDUL_PYMN_SUMR_R008} / {@code searchDispatchedCurrencyTotals}),
 * never the current page slice. Type discriminator {@code '02'}.
 */
class ExpensePaymentResultFooterTotalsTest {

    private ScdulPymnSumrRepository      sumrRepository;
    private ExpensePaymentResultService  service;

    @BeforeEach
    void setUp() {
        sumrRepository = mock(ScdulPymnSumrRepository.class);
        ScdulPymnSumrDetlRepository detlRepository = mock(ScdulPymnSumrDetlRepository.class);
        DispatchCountRepository dispatchCountRepository = mock(DispatchCountRepository.class);
        TransferLineDetailMapper transferLineDetailMapper = mock(TransferLineDetailMapper.class);
        DispatchResultResolver dispatchResultResolver = mock(DispatchResultResolver.class);
        ExptResolMstrRepository exptResolMstrRepository = mock(ExptResolMstrRepository.class);
        service = new ExpensePaymentResultService(
                sumrRepository, detlRepository, dispatchCountRepository,
                transferLineDetailMapper, dispatchResultResolver, exptResolMstrRepository);

        when(dispatchCountRepository.countByBatch(anyString(), anyList()))
                .thenReturn(Collections.emptyList());

        TenantContext.set("tester", "tester", "VN_VNM0004_000001",
                LangCd.EN, TenantContext.AUTH_GR_USER, "F");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static ScdulPymnSumr batch(String no, String ccyCd, String amt) {
        ScdulPymnSumr s = new ScdulPymnSumr();
        s.setBizKey("VN_VNM0004_000001");
        s.setScdulPymnNo(no);
        s.setScdulPymnTypeCd("02");
        s.setCcyCd(ccyCd);
        s.setTitl("Demo");
        s.setOrgFileNm("demo.xlsx");
        s.setTotalTranAmt(new BigDecimal(amt));
        s.setTotTrnsCnt(BigDecimal.ONE);
        s.setTrnfYn("Y");
        s.setRegDtm("20260101010000");
        s.setTrnfDtm("20260105010000");
        return s;
    }

    private void stubCurrencyTotals(Object[]... rows) {
        when(sumrRepository.searchDispatchedCurrencyTotals(
                anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(List.of(rows));
    }

    private static BigDecimal vndTotal(TransferResultListResponse resp) {
        return resp.currencyTotals().stream()
                .filter(t -> "VND".equals(t.ccyCd())).findFirst().orElseThrow().totalAmount();
    }

    @Test
    void footer_totals_are_identical_across_pages() {
        stubCurrencyTotals(
                new Object[]{"VND", new BigDecimal("642643325.18"), 26L, new BigDecimal("260")});
        when(sumrRepository.searchDispatched(
                anyString(), anyString(), anyString(), anyString(),
                any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        batch("1", "VND", "100000"), batch("2", "VND", "200000"))))
                .thenReturn(new PageImpl<>(List.of(batch("3", "VND", "5"))));

        BigDecimal page0 = vndTotal(service.list("20260101", "20260131", null, null, null, null, 0, 10));
        BigDecimal page1 = vndTotal(service.list("20260101", "20260131", null, null, null, null, 1, 10));

        assertThat(page0).isEqualByComparingTo("642643325.18");
        assertThat(page1).isEqualByComparingTo("642643325.18");
        assertThat(page0).isEqualByComparingTo(page1);
    }

    @Test
    void footer_aggregate_uses_same_filter_args_as_the_list_query() {
        when(sumrRepository.searchDispatched(
                anyString(), anyString(), anyString(), anyString(),
                any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(batch("1", "VND", "100000"))));
        stubCurrencyTotals(new Object[]{"VND", new BigDecimal("100000"), 1L, new BigDecimal("1")});

        service.list("20260101", "20260131", "acme", null, null, null, 0, 10);

        verify(sumrRepository).searchDispatchedCurrencyTotals(
                eq("VN_VNM0004_000001"), eq("02"),
                eq("20260101000000"), eq("20260131235959"),
                eq("%acme%"), isNull());
    }
}
