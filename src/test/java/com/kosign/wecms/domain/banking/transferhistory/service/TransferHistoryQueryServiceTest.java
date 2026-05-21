package com.kosign.wecms.domain.banking.transferhistory.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.entity.UserInfm;
import com.kosign.wecms.domain.auth.repository.UserInfmRepository;
import com.kosign.wecms.domain.banking.entity.NapasRemitTrnsHstr;
import com.kosign.wecms.domain.banking.service.BankCdResolver;
import com.kosign.wecms.domain.banking.transferhistory.dto.TransferHistoryListRequest;
import com.kosign.wecms.domain.banking.transferhistory.dto.TransferHistoryListResponse;
import com.kosign.wecms.domain.banking.transferhistory.dto.TransferHistoryRow;
import com.kosign.wecms.domain.banking.transferhistory.repository.TransferHistoryRepository;
import com.kosign.wecms.domain.banking.transferhistory.repository.TransferHistoryTotals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link TransferHistoryQueryService}.
 *
 * <p>Pure Mockito — the JPA Specification + repository are mocked so
 * the tests focus on the service's orchestration logic: tenant
 * scoping, status filter mapping, sort whitelist, paging defaults,
 * date-range validation, and the bank-name + user-name resolution
 * caches.
 */
@ExtendWith(MockitoExtension.class)
class TransferHistoryQueryServiceTest {

    private static final String TENANT = "VN_VNM0004_000001";
    private static final String USER   = "tester";

    @Mock private TransferHistoryRepository repo;
    @Mock private UserInfmRepository userRepo;
    @Mock private BankCdResolver bankCdResolver;
    @Mock private LegacyFunctionRepository legacyFn;

    @InjectMocks private TransferHistoryQueryService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(USER, TENANT, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* =================================================================
     *  Happy path
     * ================================================================= */

    @Test
    void list_returnsProjectedRowsAndTotals() {
        TransferHistoryListRequest req = req("20260501", "20260520", null, null, null, null, null);

        NapasRemitTrnsHstr row1 = row(
                /* seq */     1L,
                /* outBank */ "11704088",
                /* inBank */  "970418",
                /* ccy */     "VND",
                /* amt */     new BigDecimal("50000.000000"),
                /* status */  "00000000",
                /* msg */     null,
                /* regDtm */  "20260515103045",
                /* regId */   "alice@example.com",
                /* owacNm */  "Bob",
                /* rmrk */    "salary");
        Page<NapasRemitTrnsHstr> page = new PageImpl<>(List.of(row1), Pageable.unpaged(), 1);

        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(repo.aggregateTotals(eq(TENANT), eq("20260501"), eq("20260520"), isNull(), isNull()))
                .thenReturn(new TransferHistoryTotals(
                        1L, new BigDecimal("50000.000000"), BigDecimal.ZERO, 1L, 0L));
        when(bankCdResolver.resolveBankNm("11704088")).thenReturn("Shinhan Bank");
        when(legacyFn.resolveNapasOrCitadBankName("970418", "en")).thenReturn("BIDV");
        UserInfm alice = new UserInfm();
        alice.setUserId("alice@example.com");
        alice.setUserNm("Alice Operator");
        when(userRepo.findAllById(any())).thenReturn(List.of(alice));

        TransferHistoryListResponse resp = service.list(req);

        assertThat(resp.totalCount()).isEqualTo(1L);
        assertThat(resp.totalVndAmount()).isEqualByComparingTo("50000.000000");
        assertThat(resp.totalUsdAmount()).isEqualByComparingTo("0");
        assertThat(resp.totalVndCount()).isEqualTo(1L);
        assertThat(resp.totalUsdCount()).isEqualTo(0L);
        assertThat(resp.rows()).hasSize(1);

        TransferHistoryRow projected = resp.rows().get(0);
        assertThat(projected.seqNo()).isEqualTo(1L);
        assertThat(projected.trnsDt()).isEqualTo("20260515");
        assertThat(projected.trnsTm()).isEqualTo("1030");
        assertThat(projected.outBankCode()).isEqualTo("11704088");
        assertThat(projected.outBankNm()).isEqualTo("Shinhan Bank");
        assertThat(projected.inBankCode()).isEqualTo("970418");
        assertThat(projected.inBankNm()).isEqualTo("BIDV");
        assertThat(projected.ccyCd()).isEqualTo("VND");
        assertThat(projected.trscAmt()).isEqualByComparingTo("50000.000000");
        assertThat(projected.trnsStts()).isEqualTo("00000000");
        assertThat(projected.userNm()).isEqualTo("Alice Operator");
        assertThat(projected.owanNm()).isEqualTo("Bob");
        assertThat(projected.rmrk()).isEqualTo("salary");
    }

    /* =================================================================
     *  Validation
     * ================================================================= */

    @Test
    void list_rejectsInvertedPeriodWithInvalidPeriodCode() {
        TransferHistoryListRequest req = req("20260520", "20260501", null, null, null, null, null);

        assertThatThrownBy(() -> service.list(req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "WBE_TRANSFER_HISTORY_INVALID_PERIOD");

        verify(repo, never()).findAll(any(Specification.class), any(Pageable.class));
        verify(repo, never()).aggregateTotals(anyString(), anyString(), anyString(), any(), any());
    }

    /* =================================================================
     *  Status filter mapping
     * ================================================================= */

    @Test
    void list_successFilterPassesStatusEqOnly() {
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.unpaged(), 0));
        when(repo.aggregateTotals(anyString(), anyString(), anyString(), anyString(), isNull()))
                .thenReturn(new TransferHistoryTotals(0L, BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L));

        service.list(req("20260501", "20260520", "00000000", null, null, null, null));

        verify(repo).aggregateTotals(eq(TENANT), eq("20260501"), eq("20260520"),
                eq("00000000"), isNull());
    }

    @Test
    void list_failedSentinelPassesStatusNeqOnly() {
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.unpaged(), 0));
        when(repo.aggregateTotals(anyString(), anyString(), anyString(), isNull(), anyString()))
                .thenReturn(new TransferHistoryTotals(0L, BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L));

        service.list(req("20260501", "20260520", "99999999", null, null, null, null));

        verify(repo).aggregateTotals(eq(TENANT), eq("20260501"), eq("20260520"),
                isNull(), eq("00000000"));
    }

    @Test
    void list_blankStatusFiltersNeitherStatus() {
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.unpaged(), 0));
        when(repo.aggregateTotals(anyString(), anyString(), anyString(), isNull(), isNull()))
                .thenReturn(new TransferHistoryTotals(0L, BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L));

        service.list(req("20260501", "20260520", "", null, null, null, null));

        verify(repo).aggregateTotals(eq(TENANT), eq("20260501"), eq("20260520"),
                isNull(), isNull());
    }

    /* =================================================================
     *  Sort whitelist + paging
     * ================================================================= */

    @Test
    void list_defaultSortIsRegDtmDescTieBrokenBySeqNoDesc() {
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.unpaged(), 0));
        when(repo.aggregateTotals(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new TransferHistoryTotals(0L, BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L));

        service.list(req("20260501", "20260520", null, null, null, null, null));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findAll(any(Specification.class), captor.capture());
        Sort sort = captor.getValue().getSort();
        assertThat(sort.getOrderFor("regDtm").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(sort.getOrderFor("seqNo").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void list_whitelistedSortFieldDescAlwaysTieBreaksBySeqNoDesc() {
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.unpaged(), 0));
        when(repo.aggregateTotals(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new TransferHistoryTotals(0L, BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L));

        service.list(req("20260501", "20260520", null, null, null, "TRSC_AMT", "ASC"));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findAll(any(Specification.class), captor.capture());
        Sort sort = captor.getValue().getSort();
        assertThat(sort.getOrderFor("trscAmt").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(sort.getOrderFor("seqNo").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void list_unknownSortFieldFallsBackToDefault() {
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.unpaged(), 0));
        when(repo.aggregateTotals(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new TransferHistoryTotals(0L, BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L));

        service.list(req("20260501", "20260520", null, null, null, "DROP TABLE", "ASC"));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findAll(any(Specification.class), captor.capture());
        Sort sort = captor.getValue().getSort();
        assertThat(sort.getOrderFor("regDtm").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void list_defaultPageSizeIs15WhenPagePerCntIsNull() {
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.unpaged(), 0));
        when(repo.aggregateTotals(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new TransferHistoryTotals(0L, BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L));

        service.list(req("20260501", "20260520", null, null, null, null, null));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(15);
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0); // 1-based → 0-based
    }

    @Test
    void list_pageNoIsConvertedFromOneBasedToZeroBased() {
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.unpaged(), 0));
        when(repo.aggregateTotals(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new TransferHistoryTotals(0L, BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L));

        service.list(req("20260501", "20260520", null, 3, "20", null, null));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2); // page 3 → index 2
    }

    /* =================================================================
     *  Resolver caches
     * ================================================================= */

    @Test
    void list_resolvesEachBankCodeAtMostOncePerPage() {
        NapasRemitTrnsHstr a = row(1L, "11704088", "970418", "VND",
                new BigDecimal("10"), "00000000", null, "20260515100000",
                "u1", "", "");
        NapasRemitTrnsHstr b = row(2L, "11704088", "970418", "VND",
                new BigDecimal("20"), "00000000", null, "20260515100100",
                "u1", "", "");

        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a, b), Pageable.unpaged(), 2));
        when(repo.aggregateTotals(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new TransferHistoryTotals(2L, new BigDecimal("30"), BigDecimal.ZERO, 2L, 0L));
        when(bankCdResolver.resolveBankNm("11704088")).thenReturn("Shinhan");
        when(legacyFn.resolveNapasOrCitadBankName("970418", "en")).thenReturn("BIDV");

        service.list(req("20260501", "20260520", null, null, null, null, null));

        verify(bankCdResolver, times(1)).resolveBankNm("11704088");
        verify(legacyFn, times(1)).resolveNapasOrCitadBankName("970418", "en");
    }

    @Test
    void list_korenAndKhmerLocalesCollapseToEnglishForLegacyFn() {
        TenantContext.clear();
        TenantContext.set(USER, TENANT, LangCd.KM);

        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row(1L, "11704088", "970418", "VND",
                        BigDecimal.TEN, "00000000", null, "20260515100000", "u1", "", "")),
                        Pageable.unpaged(), 1));
        when(repo.aggregateTotals(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new TransferHistoryTotals(1L, BigDecimal.TEN, BigDecimal.ZERO, 1L, 0L));
        when(legacyFn.resolveNapasOrCitadBankName("970418", "en")).thenReturn("BIDV");

        service.list(req("20260501", "20260520", null, null, null, null, null));

        verify(legacyFn).resolveNapasOrCitadBankName("970418", "en");
        verify(legacyFn, never()).resolveNapasOrCitadBankName(anyString(), eq("km"));
        verify(legacyFn, never()).resolveNapasOrCitadBankName(anyString(), eq("ko"));
    }

    /* =================================================================
     *  Helpers
     * ================================================================= */

    private static TransferHistoryListRequest req(
            String fromDt, String toDt, String trnsStts,
            Integer pageNo, String pagePerCnt, String sortField, String sortDir) {
        return new TransferHistoryListRequest(
                fromDt, toDt, trnsStts, pageNo, pagePerCnt, sortField, sortDir);
    }

    private static NapasRemitTrnsHstr row(
            Long seqNo, String outBank, String inBank, String ccy,
            BigDecimal amt, String status, String msg, String regDtm,
            String regId, String owacNm, String rmrk) {
        NapasRemitTrnsHstr r = new NapasRemitTrnsHstr();
        r.setBizKey(TENANT);
        r.setSeqNo(new BigDecimal(seqNo));
        r.setOutBankCode(outBank);
        r.setOutAcctNo("0001-00000569-14");
        r.setInBankCode(inBank);
        r.setInNapasBankCode(inBank);
        r.setInAcctNo("4001-00234569-14");
        r.setCcyCd(ccy);
        r.setTrscAmt(amt);
        r.setFee(BigDecimal.ZERO);
        r.setTrnsStts(status);
        r.setTrnsMsg(msg);
        r.setRegDtm(regDtm);
        r.setRegId(regId);
        r.setOwacNm(owacNm);
        r.setRmrk(rmrk);
        return r;
    }
}
