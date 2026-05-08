package com.kosign.wecms.domain.setting.firmbankinghistory.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.setting.firmbankinghistory.dto.FirmBankingHistoryListResponse;
import com.kosign.wecms.domain.setting.firmbankinghistory.entity.FirmBankingHistoryView;
import com.kosign.wecms.domain.setting.firmbankinghistory.repository.FirmBankingHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service-layer coverage for Settings &gt; Firm Banking History:
 * date validation parity with legacy {@code comm_0221_001_r001_act.jsp},
 * SVC_TYPE enum guard (refactor enhancement), keyword pattern building,
 * sort whitelist parsing + default fall-through, pagination clamping,
 * and tenant scoping via {@link TenantContext}.
 */
@ExtendWith(MockitoExtension.class)
class FirmBankingHistoryServiceTest {

    @Mock
    FirmBankingHistoryRepository repo;

    private FirmBankingHistoryService service;

    private static final String BIZ  = "VN_VNM0004_000001";
    private static final String USER = "tester";
    private static final String FROM = "20260101";
    private static final String TO   = "20260131";

    @BeforeEach
    void setUp() {
        service = new FirmBankingHistoryService(repo);
        TenantContext.set(USER, BIZ, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------
    // Validation — date range parity with legacy ValidationHelper
    // ------------------------------------------------------------------

    @Test
    void list_missingFromDate_throwsValidation() {
        assertBusinessCode("WBE_VALIDATION", () -> call(null, TO));
        assertBusinessCode("WBE_VALIDATION", () -> call("",   TO));
        assertBusinessCode("WBE_VALIDATION", () -> call("  ", TO));
        verify(repo, never()).search(any(), any(), any(), any(), any(), any());
    }

    @Test
    void list_missingToDate_throwsValidation() {
        assertBusinessCode("WBE_VALIDATION", () -> call(FROM, null));
        assertBusinessCode("WBE_VALIDATION", () -> call(FROM, ""));
    }

    @Test
    void list_malformedDate_throwsValidation() {
        // Wrong length
        assertBusinessCode("WBE_VALIDATION", () -> call("2026010", TO));
        // Non-numeric
        assertBusinessCode("WBE_VALIDATION", () -> call("garbage1", TO));
        // Unparseable as a real date (Feb 30)
        assertBusinessCode("WBE_VALIDATION", () -> call("20260230", TO));
    }

    @Test
    void list_invertedRange_throwsValidation() {
        assertBusinessCode("WBE_VALIDATION", () -> call("20260301", "20260101"));
    }

    @Test
    void list_sameDayRange_passes() {
        whenRepoReturnsEmpty();
        FirmBankingHistoryListResponse res = call("20260115", "20260115");
        assertThat(res.totalCount()).isEqualTo(0L);
        verify(repo).search(eq(BIZ), eq("20260115"), eq("20260115"), isNull(), isNull(), any(Pageable.class));
    }

    // ------------------------------------------------------------------
    // svc_type — refactor enum guard
    // ------------------------------------------------------------------

    @Test
    void list_unknownSvcType_throwsValidation() {
        assertBusinessCode("WBE_VALIDATION",
                () -> service.list(FROM, TO, "X", null, null, null, 0, 0));
        assertBusinessCode("WBE_VALIDATION",
                () -> service.list(FROM, TO, "FOO", null, null, null, 0, 0));
        verify(repo, never()).search(any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void list_validSvcType_isPassedToRepo() {
        whenRepoReturnsEmpty();

        for (String type : List.of("F", "S", "G", "B")) {
            service.list(FROM, TO, type, null, null, null, 0, 0);
            verify(repo).search(eq(BIZ), eq(FROM), eq(TO), eq(type), isNull(), any(Pageable.class));
        }
    }

    @Test
    void list_lowerCaseSvcType_isUppercased() {
        whenRepoReturnsEmpty();
        service.list(FROM, TO, "g", null, null, null, 0, 0);
        verify(repo).search(eq(BIZ), eq(FROM), eq(TO), eq("G"), isNull(), any(Pageable.class));
    }

    @Test
    void list_blankSvcType_passesNull() {
        whenRepoReturnsEmpty();
        service.list(FROM, TO, "", null, null, null, 0, 0);
        verify(repo).search(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull(), any(Pageable.class));
    }

    // ------------------------------------------------------------------
    // Keyword — same escape conventions as AccountService.buildKeywordPattern
    // ------------------------------------------------------------------

    @Test
    void list_blankKeyword_passesNullPattern() {
        whenRepoReturnsEmpty();
        service.list(FROM, TO, null, "   ", null, null, 0, 0);
        verify(repo).search(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void list_keyword_isLowercasedAndWrapped() {
        whenRepoReturnsEmpty();
        service.list(FROM, TO, null, "ABC", null, null, 0, 0);
        verify(repo).search(eq(BIZ), eq(FROM), eq(TO), isNull(), eq("%abc%"), any(Pageable.class));
    }

    @Test
    void list_keyword_escapesLikeMetaChars() {
        whenRepoReturnsEmpty();
        service.list(FROM, TO, null, "100%_test!", null, null, 0, 0);
        verify(repo).search(eq(BIZ), eq(FROM), eq(TO), isNull(), eq("%100!%!_test!!%"), any(Pageable.class));
    }

    // ------------------------------------------------------------------
    // Pagination — clamp + Page passthrough
    // ------------------------------------------------------------------

    @Test
    void list_negativePage_isClampedToZero() {
        whenRepoReturnsEmpty();
        service.list(FROM, TO, null, null, null, null, -5, 20);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).search(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull(), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
    }

    @Test
    void list_zeroSize_defaultsTo15() {
        whenRepoReturnsEmpty();
        service.list(FROM, TO, null, null, null, null, 0, 0);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).search(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull(), captor.capture());
        assertThat(captor.getValue().getPageSize())
                .isEqualTo(FirmBankingHistoryService.DEFAULT_PAGE_SIZE);
    }

    @Test
    void list_oversizedSize_isClampedToMaximum() {
        whenRepoReturnsEmpty();
        service.list(FROM, TO, null, null, null, null, 0, 999);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).search(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull(), captor.capture());
        assertThat(captor.getValue().getPageSize())
                .isEqualTo(FirmBankingHistoryService.MAX_PAGE_SIZE);
    }

    @Test
    void list_responseEnvelopesPaginationMetadata() {
        FirmBankingHistoryView v = sampleRow("F", "0001", "20260115101010");
        when(repo.search(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(v), PageRequest.of(2, 10), 53L));

        FirmBankingHistoryListResponse res =
                service.list(FROM, TO, null, null, null, null, 2, 10);

        assertThat(res.totalCount()).isEqualTo(53L);
        assertThat(res.totalPages()).isEqualTo(6);   // ceil(53/10)
        assertThat(res.pageNumber()).isEqualTo(2);
        assertThat(res.pageSize()).isEqualTo(10);
        assertThat(res.list()).hasSize(1);
        assertThat(res.list().get(0).svcType()).isEqualTo("F");
        assertThat(res.list().get(0).seqNo()).isEqualTo("0001");
        // F-leg svcNm "201" resolves through TranTp to its i18n key —
        // the row should ship svcNameKey alongside the raw svcNm so
        // the frontend renders the localised label without holding
        // a hardcoded SERVICE_NAME_MAP.
        assertThat(res.list().get(0).svcNm()).isEqualTo("201");
        assertThat(res.list().get(0).svcNameKey()).isEqualTo("fb_history.service.201");
    }

    // ------------------------------------------------------------------
    // Sort — whitelist + default fall-through + tiebreaker
    // ------------------------------------------------------------------

    @Test
    void sortField_unknown_resolvesToNull() {
        assertThat(FirmBankingHistorySortField.parse(null)).isNull();
        assertThat(FirmBankingHistorySortField.parse("")).isNull();
        assertThat(FirmBankingHistorySortField.parse("BOGUS")).isNull();
    }

    @Test
    void sortField_whitelist_resolvesToEntityProperty() {
        assertThat(FirmBankingHistorySortField.parse("TARN_DTM").entityProperty()).isEqualTo("tarnDtm");
        assertThat(FirmBankingHistorySortField.parse("SVC_TYPE").entityProperty()).isEqualTo("svcType");
        assertThat(FirmBankingHistorySortField.parse("SVC_NM").entityProperty()).isEqualTo("svcNm");
        assertThat(FirmBankingHistorySortField.parse("SEQ_NO").entityProperty()).isEqualTo("seqNo");
        assertThat(FirmBankingHistorySortField.parse("DEP_BANK_CD").entityProperty()).isEqualTo("depBankCd");
        assertThat(FirmBankingHistorySortField.parse("RSLT_CD").entityProperty()).isEqualTo("rsltCd");
        // Lower-case is accepted
        assertThat(FirmBankingHistorySortField.parse("tarn_dtm"))
                .isEqualTo(FirmBankingHistorySortField.TARN_DTM);
    }

    @Test
    void list_unknownSortField_fallsBackToDefaultOrder() {
        whenRepoReturnsEmpty();
        service.list(FROM, TO, null, null, "BOGUS", "ASC", 0, 0);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).search(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull(), captor.capture());
        List<Sort.Order> orders = captor.getValue().getSort().toList();
        assertThat(orders).extracting(Sort.Order::getProperty).containsExactly("tarnDtm", "seqNo");
        assertThat(orders.get(0).getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(orders.get(1).getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void list_validSortField_appendsSeqNoTiebreaker() {
        whenRepoReturnsEmpty();
        service.list(FROM, TO, null, null, "RSLT_CD", "ASC", 0, 0);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).search(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull(), captor.capture());
        List<Sort.Order> orders = captor.getValue().getSort().toList();
        assertThat(orders).extracting(Sort.Order::getProperty).containsExactly("rsltCd", "seqNo");
        assertThat(orders.get(0).getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(orders.get(1).getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void list_blankDirection_fallsBackToDefault() {
        whenRepoReturnsEmpty();
        // A valid sortField but a blank direction is treated as "no
        // direction" — same as legacy SortDirection.parse("").
        service.list(FROM, TO, null, null, "RSLT_CD", "", 0, 0);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).search(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull(), captor.capture());
        List<Sort.Order> orders = captor.getValue().getSort().toList();
        assertThat(orders).extracting(Sort.Order::getProperty).containsExactly("tarnDtm", "seqNo");
    }

    @Test
    void sortDirection_isLenient() {
        assertThat(SortDirection.parse(null)).isNull();
        assertThat(SortDirection.parse("")).isNull();
        assertThat(SortDirection.parse("asc")).isEqualTo(SortDirection.ASC);
        assertThat(SortDirection.parse("DESC")).isEqualTo(SortDirection.DESC);
    }

    // ------------------------------------------------------------------
    // Tenant scoping — repository call always carries TenantContext bizKey
    // ------------------------------------------------------------------

    @Test
    void list_alwaysUsesTenantContextBizKey() {
        // Lenient stub — any bizKey returns empty so we can flip the
        // context and verify the service reads from TenantContext, not
        // a hardcoded value.
        when(repo.search(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 15), 0L));
        TenantContext.set(USER, "VN_OTHER_TENANT", LangCd.EN);
        service.list(FROM, TO, null, null, null, null, 0, 0);
        verify(repo).search(eq("VN_OTHER_TENANT"), eq(FROM), eq(TO), isNull(), isNull(), any(Pageable.class));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private FirmBankingHistoryListResponse call(String fromDate, String toDate) {
        return service.list(fromDate, toDate, null, null, null, null, 0, 0);
    }

    private void whenRepoReturnsEmpty() {
        when(repo.search(eq(BIZ), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 15), 0L));
    }

    private static FirmBankingHistoryView sampleRow(String svcType, String seqNo, String tarnDtm) {
        FirmBankingHistoryView v = new FirmBankingHistoryView();
        v.setBizKey(BIZ);
        v.setSvcType(svcType);
        v.setSeqNo(seqNo);
        v.setTarnDtm(tarnDtm);
        v.setSvcNm("201");
        v.setDepBankCd("Shinhan VN - 970424");
        v.setRsltCd("00000000");
        v.setRsltMsg("00000000");
        v.setJob("201");
        v.setReqData(null);
        v.setVanOrgCd("FB");
        return v;
    }

    private void assertBusinessCode(String expectedCode, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(expectedCode);
    }
}
