package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.internaltransfer.dto.CurrencyTotal;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ScheduledTransferListResponse;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F-1: the Scheduled-Transfer list footer currency totals must aggregate the
 * ENTIRE filtered result set (legacy {@code SCDUL_PYMN_SUMR_R003}), not the
 * current page slice.
 *
 * <p>The page-slice bug fed {@code rows.getContent()} (≤ page size) into an
 * in-memory sum, so totals were understated whenever the filtered set spanned
 * more than one page. The fix routes totals through the dedicated
 * {@link ScdulPymnSumrRepository#searchCurrencyTotals} aggregate (no
 * pagination, same predicate as {@code search}).
 *
 * <p>These tests use an <em>empty page content</em> with a non-empty aggregate:
 * if totals still came from the page slice they would be empty, so a non-empty
 * footer proves the totals are sourced from the full-set aggregate. (A
 * row-level DB test is not possible here — {@code search}/{@code
 * searchCurrencyTotals} call the Postgres {@code wb_get_appr_stts} function and
 * the project ships no Testcontainers; see wecms-api CLAUDE.md "Tech Stack".)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InternalTransferListFooterTotalsTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String TYPE_03 = "03";
    private static final String FROM    = "20260101";
    private static final String TO      = "20260131";
    private static final String FROM_DTM = "20260101000000";
    private static final String TO_DTM   = "20260131235959";

    @Mock private ScdulPymnSumrRepository sumrRepository;
    @Mock private LegacyFunctionRepository legacyFunctions;

    @InjectMocks private InternalTransferService service;

    @BeforeEach
    void setUp() {
        TenantContext.set("tester", "Tester", BIZ_KEY, LangCd.EN);
        // search() returns an EMPTY page slice but a large total — exactly the
        // multi-page condition that exposed the page-slice bug.
        when(sumrRepository.search(anyString(), anyString(), anyString(), anyString(),
                any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.<ScdulPymnSumr>of(), Pageable.ofSize(10), 42));
        when(legacyFunctions.apprSttsByApprSeqNos(anyString(), anyList()))
                .thenReturn(Map.of());
        // Full-set aggregate: 42 docs / 4,200,000 VND + 3 docs / 600 USD.
        when(sumrRepository.searchCurrencyTotals(eq(BIZ_KEY), eq(TYPE_03), eq(FROM_DTM), eq(TO_DTM),
                isNull(), isNull(), isNull()))
                .thenReturn(List.of(
                        new Object[]{ "USD", new BigDecimal("600"),     3L,  new BigDecimal("3")  },
                        new Object[]{ "VND", new BigDecimal("4200000"), 42L, new BigDecimal("42") }));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void footerTotals_comeFromFullSetAggregate_notPageSlice() {
        ScheduledTransferListResponse resp = service.list(FROM, TO, null, null, null, null, 0, 10);

        // Page content is empty; if totals were page-sliced they'd be empty.
        // They are not — they reflect the full 42-row / 4,200,000 VND aggregate.
        assertThat(resp.currencyTotals()).hasSize(2);
        CurrencyTotal vnd = resp.currencyTotals().stream()
                .filter(t -> t.ccyCd().equals("VND")).findFirst().orElseThrow();
        assertThat(vnd.totalAmount()).isEqualByComparingTo("4200000");
        assertThat(vnd.totalCount()).isEqualByComparingTo("42");
        assertThat(vnd.totalTransactionCount()).isEqualByComparingTo("42");
        CurrencyTotal usd = resp.currencyTotals().stream()
                .filter(t -> t.ccyCd().equals("USD")).findFirst().orElseThrow();
        assertThat(usd.totalAmount()).isEqualByComparingTo("600");
        assertThat(usd.totalCount()).isEqualByComparingTo("3");

        // The aggregate ran with the SAME filters as the list search and with
        // NO pagination argument (the method has none).
        verify(sumrRepository).searchCurrencyTotals(
                eq(BIZ_KEY), eq(TYPE_03), eq(FROM_DTM), eq(TO_DTM), isNull(), isNull(), isNull());
    }

    @Test
    void footerTotals_identicalAcrossPages_forSameFilter() {
        ScheduledTransferListResponse page1 = service.list(FROM, TO, null, null, null, null, 0, 10);
        ScheduledTransferListResponse page2 = service.list(FROM, TO, null, null, null, null, 1, 10);

        // Totals are page-independent: page 2 must equal page 1.
        assertThat(page2.currencyTotals()).isEqualTo(page1.currencyTotals());

        // The aggregate is the page-independent source; called once per list() call.
        verify(sumrRepository, times(2)).searchCurrencyTotals(
                eq(BIZ_KEY), eq(TYPE_03), eq(FROM_DTM), eq(TO_DTM), isNull(), isNull(), isNull());
    }
}
