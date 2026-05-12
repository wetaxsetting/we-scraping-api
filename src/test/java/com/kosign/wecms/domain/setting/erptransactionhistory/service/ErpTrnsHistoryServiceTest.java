package com.kosign.wecms.domain.setting.erptransactionhistory.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.setting.erptransactionhistory.dto.ErpTrnsHistoryCounts;
import com.kosign.wecms.domain.setting.erptransactionhistory.dto.ErpTrnsHistoryListResponse;
import com.kosign.wecms.domain.setting.erptransactionhistory.dto.ErpTrnsHistoryProjection;
import com.kosign.wecms.domain.setting.erptransactionhistory.dto.ErpTrnsHistoryRow;
import com.kosign.wecms.domain.setting.erptransactionhistory.entity.ErpTrnsHstr;
import com.kosign.wecms.domain.setting.erptransactionhistory.entity.ErpTrnsHstrId;
import com.kosign.wecms.domain.setting.erptransactionhistory.repository.ErpTrnsHstrRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service-layer coverage for Settings &gt; ERP Transaction History:
 * date validation parity with legacy {@code comm_0219_001_r001_act.jsp},
 * direction (W/E/D) enum guard, trns_type_cd range guard, keyword
 * escape conventions, sort whitelist parsing + default fall-through +
 * tiebreaker, pagination clamping, tenant scoping, counts pass-through,
 * i18n key resolution on rows, and download-flow tenant + missing-file
 * guards.
 */
@ExtendWith(MockitoExtension.class)
class ErpTrnsHistoryServiceTest {

    @Mock
    ErpTrnsHstrRepository repo;

    @TempDir
    Path storageRoot;

    private ErpTrnsHistoryService service;
    private ErpTrnsHistoryFileResolver resolver;

    private static final String BIZ  = "VN_VNM0004_000001";
    private static final String USER = "tester";
    private static final String FROM = "20260101";
    private static final String TO   = "20260131";

    @BeforeEach
    void setUp() {
        resolver = new ErpTrnsHistoryFileResolver(
                new ErpTrnsHistoryStorageProperties(storageRoot.toString()));
        service = new ErpTrnsHistoryService(repo, resolver);
        TenantContext.set(USER, BIZ, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* =================================================================
     *  Validation — date range parity with legacy ValidationHelper
     * ================================================================= */

    @Test
    void list_missingFromDate_throwsValidation() {
        assertBusinessCode("WBE_VALIDATION", () -> call(null, TO));
        assertBusinessCode("WBE_VALIDATION", () -> call("",   TO));
        assertBusinessCode("WBE_VALIDATION", () -> call("  ", TO));
        verify(repo, never()).search(any(), any(), any(), any(), any(), any(), any());
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
        ErpTrnsHistoryListResponse res = call("20260115", "20260115");
        assertThat(res.totalCount()).isEqualTo(0L);
        verify(repo).search(eq(BIZ), eq("20260115"), eq("20260115"), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    /* =================================================================
     *  erp_type — refactor enum guard
     * ================================================================= */

    @Test
    void list_unknownErpType_throwsValidation() {
        assertBusinessCode("WBE_VALIDATION",
                () -> service.list(FROM, TO, "X", null, null, null, null, 0, 0));
        assertBusinessCode("WBE_VALIDATION",
                () -> service.list(FROM, TO, "ALL", null, null, null, null, 0, 0));
        verify(repo, never()).search(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void list_validErpType_isPassedToRepo() {
        whenRepoReturnsEmpty();
        for (String type : List.of("W", "E", "D")) {
            service.list(FROM, TO, type, null, null, null, null, 0, 0);
            verify(repo).search(eq(BIZ), eq(FROM), eq(TO), eq(type), isNull(), isNull(), any(Pageable.class));
        }
    }

    @Test
    void list_lowerCaseErpType_isUppercased() {
        whenRepoReturnsEmpty();
        service.list(FROM, TO, "e", null, null, null, null, 0, 0);
        verify(repo).search(eq(BIZ), eq(FROM), eq(TO), eq("E"), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void list_blankErpType_passesNull() {
        whenRepoReturnsEmpty();
        service.list(FROM, TO, "", null, null, null, null, 0, 0);
        verify(repo).search(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    /* =================================================================
     *  trns_type_cd — refactor range guard
     * ================================================================= */

    @Test
    void list_unknownTrnsTypeCd_throwsValidation() {
        assertBusinessCode("WBE_VALIDATION",
                () -> service.list(FROM, TO, null, "AB", null, null, null, 0, 0));
        assertBusinessCode("WBE_VALIDATION",
                () -> service.list(FROM, TO, null, "999", null, null, null, 0, 0));
        assertBusinessCode("WBE_VALIDATION",
                () -> service.list(FROM, TO, null, "00", null, null, null, 0, 0));
        assertBusinessCode("WBE_VALIDATION",
                () -> service.list(FROM, TO, null, "18", null, null, null, 0, 0));
    }

    @Test
    void list_validTrnsTypeCd_isPassedToRepo() {
        whenRepoReturnsEmpty();
        for (String code : List.of("01", "09", "15", "17")) {
            service.list(FROM, TO, null, code, null, null, null, 0, 0);
            verify(repo).search(eq(BIZ), eq(FROM), eq(TO), isNull(), eq(code), isNull(), any(Pageable.class));
        }
    }

    @Test
    void list_blankTrnsTypeCd_passesNull() {
        whenRepoReturnsEmpty();
        service.list(FROM, TO, null, "", null, null, null, 0, 0);
        verify(repo).search(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    /* =================================================================
     *  Keyword — same escape conventions as FirmBankingHistoryService
     * ================================================================= */

    @Test
    void list_blankKeyword_passesNullPattern() {
        whenRepoReturnsEmpty();
        service.list(FROM, TO, null, null, "   ", null, null, 0, 0);
        verify(repo).search(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void list_keyword_isLowercasedAndWrapped() {
        whenRepoReturnsEmpty();
        service.list(FROM, TO, null, null, "Lamine", null, null, 0, 0);
        verify(repo).search(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull(), eq("%lamine%"), any(Pageable.class));
    }

    @Test
    void list_keyword_escapesLikeMetaChars() {
        whenRepoReturnsEmpty();
        service.list(FROM, TO, null, null, "100%_test!", null, null, 0, 0);
        verify(repo).search(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull(), eq("%100!%!_test!!%"), any(Pageable.class));
    }

    /* =================================================================
     *  Pagination — clamp + page slice passthrough
     * ================================================================= */

    @Test
    void list_negativePage_isClampedToZero() {
        whenRepoReturnsEmpty();
        service.list(FROM, TO, null, null, null, null, null, -5, 20);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).search(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull(), isNull(), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
    }

    @Test
    void list_zeroSize_defaultsTo15() {
        whenRepoReturnsEmpty();
        service.list(FROM, TO, null, null, null, null, null, 0, 0);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).search(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull(), isNull(), captor.capture());
        assertThat(captor.getValue().getPageSize())
                .isEqualTo(ErpTrnsHistoryService.DEFAULT_PAGE_SIZE);
    }

    @Test
    void list_oversizedSize_isClampedToMaximum() {
        whenRepoReturnsEmpty();
        service.list(FROM, TO, null, null, null, null, null, 0, 9999);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).search(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull(), isNull(), captor.capture());
        assertThat(captor.getValue().getPageSize())
                .isEqualTo(ErpTrnsHistoryService.MAX_PAGE_SIZE);
    }

    /* =================================================================
     *  Sort whitelist — fall-through + tiebreaker
     * ================================================================= */

    @Test
    void sortField_unknown_resolvesToNull() {
        assertThat(ErpTrnsHistorySortField.parse(null)).isNull();
        assertThat(ErpTrnsHistorySortField.parse("")).isNull();
        assertThat(ErpTrnsHistorySortField.parse("BOGUS")).isNull();
        assertThat(ErpTrnsHistorySortField.parse("FILE")).isNull();   // OQ-5 — not whitelisted
    }

    @Test
    void sortField_whitelist_resolvesToEntityProperty() {
        assertThat(ErpTrnsHistorySortField.parse("REG_DTM").entityProperty()).isEqualTo("regDtm");
        assertThat(ErpTrnsHistorySortField.parse("ERP_TYPE").entityProperty()).isEqualTo("erpType");
        assertThat(ErpTrnsHistorySortField.parse("TRNS_TYPE_CD").entityProperty()).isEqualTo("trnsTypeCd");
        assertThat(ErpTrnsHistorySortField.parse("REG_USER").entityProperty()).isEqualTo("regId");
        // Lower-case is accepted
        assertThat(ErpTrnsHistorySortField.parse("reg_dtm"))
                .isEqualTo(ErpTrnsHistorySortField.REG_DTM);
    }

    @Test
    void list_unknownSortField_fallsBackToDefaultOrder() {
        whenRepoReturnsEmpty();
        service.list(FROM, TO, null, null, null, "BOGUS", "ASC", 0, 0);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).search(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull(), isNull(), captor.capture());
        List<Sort.Order> orders = captor.getValue().getSort().toList();
        assertThat(orders).extracting(Sort.Order::getProperty).containsExactly("regDtm", "seqNo");
        assertThat(orders.get(0).getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(orders.get(1).getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void list_validSortField_appendsSeqNoTiebreaker() {
        whenRepoReturnsEmpty();
        service.list(FROM, TO, null, null, null, "REG_USER", "ASC", 0, 0);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).search(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull(), isNull(), captor.capture());
        List<Sort.Order> orders = captor.getValue().getSort().toList();
        assertThat(orders).extracting(Sort.Order::getProperty).containsExactly("regId", "seqNo");
        assertThat(orders.get(0).getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(orders.get(1).getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void list_blankDirection_fallsBackToDefault() {
        whenRepoReturnsEmpty();
        service.list(FROM, TO, null, null, null, "REG_USER", "", 0, 0);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).search(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull(), isNull(), captor.capture());
        List<Sort.Order> orders = captor.getValue().getSort().toList();
        assertThat(orders).extracting(Sort.Order::getProperty).containsExactly("regDtm", "seqNo");
    }

    @Test
    void sortDirection_isLenient() {
        assertThat(SortDirection.parse(null)).isNull();
        assertThat(SortDirection.parse("")).isNull();
        assertThat(SortDirection.parse("asc")).isEqualTo(SortDirection.ASC);
        assertThat(SortDirection.parse("DESC")).isEqualTo(SortDirection.DESC);
    }

    /* =================================================================
     *  Counts + row mapping — pagination metadata + i18n keys
     * ================================================================= */

    @Test
    void list_responseEnvelopesCountsAndPaginationMetadata() {
        ErpTrnsHistoryProjection p = sampleProjection(759L, "W", "09", "20260430073409", "lamine@gmail.com", "Test User");
        when(repo.search(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p), PageRequest.of(0, 15), 759L));
        when(repo.countAllDirections(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull()))
                .thenReturn(new ErpTrnsHistoryCounts(759L, 533L, 205L, 21L));

        ErpTrnsHistoryListResponse res = call(FROM, TO);

        // Tab counts come from the dedicated countAllDirections query —
        // the search Page's totalElements is intentionally NOT used as
        // total_count (the latter mirrors the legacy IDO_R001_CNT
        // query which drops ERP_TYPE from the WHERE).
        assertThat(res.totalCount()).isEqualTo(759L);
        assertThat(res.totalCountWecms()).isEqualTo(533L);
        assertThat(res.totalCountErp()).isEqualTo(205L);
        assertThat(res.totalCountDeleted()).isEqualTo(21L);
        assertThat(res.pageNumber()).isEqualTo(0);
        assertThat(res.pageSize()).isEqualTo(15);

        // Row mapping — i18n keys resolved server-side.
        assertThat(res.list()).hasSize(1);
        ErpTrnsHistoryRow row = res.list().get(0);
        assertThat(row.seqNo()).isEqualTo(759L);
        assertThat(row.erpType()).isEqualTo("W");
        assertThat(row.erpTypeKey()).isEqualTo("settings.erp_transaction_history.direction.wecms_to_erp");
        assertThat(row.trnsTypeCd()).isEqualTo("09");
        assertThat(row.trnsTypeKey()).isEqualTo("settings.erp_transaction_history.type.09");
        assertThat(row.userNm()).isEqualTo("Test User");
    }

    @Test
    void rowMapping_unmappableErpTypeResolvesKeyToNull() {
        ErpTrnsHistoryProjection p = sampleProjection(1L, "X", "09", "20260101000000", "lamine@gmail.com", null);
        when(repo.search(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p), PageRequest.of(0, 15), 0L));
        when(repo.countAllDirections(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull()))
                .thenReturn(ErpTrnsHistoryCounts.empty());
        ErpTrnsHistoryRow row = call(FROM, TO).list().get(0);
        assertThat(row.erpTypeKey()).isNull();
    }

    @Test
    void rowMapping_outOfRangeTrnsTypeResolvesKeyToNull() {
        ErpTrnsHistoryProjection p = sampleProjection(1L, "W", "99", "20260101000000", "lamine@gmail.com", null);
        when(repo.search(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p), PageRequest.of(0, 15), 0L));
        when(repo.countAllDirections(eq(BIZ), eq(FROM), eq(TO), isNull(), isNull()))
                .thenReturn(ErpTrnsHistoryCounts.empty());
        ErpTrnsHistoryRow row = call(FROM, TO).list().get(0);
        assertThat(row.trnsTypeKey()).isNull();
    }

    /* =================================================================
     *  Tenant scoping — repository call always carries TenantContext bizKey
     * ================================================================= */

    @Test
    void list_alwaysUsesTenantContextBizKey() {
        when(repo.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 15), 0L));
        when(repo.countAllDirections(any(), any(), any(), any(), any()))
                .thenReturn(ErpTrnsHistoryCounts.empty());
        TenantContext.set(USER, "VN_OTHER_TENANT", LangCd.EN);
        service.list(FROM, TO, null, null, null, null, null, 0, 0);
        verify(repo).search(eq("VN_OTHER_TENANT"), eq(FROM), eq(TO), isNull(), isNull(), isNull(), any(Pageable.class));
        verify(repo).countAllDirections(eq("VN_OTHER_TENANT"), eq(FROM), eq(TO), isNull(), isNull());
    }

    /* =================================================================
     *  Download — tenant ownership + file presence
     * ================================================================= */

    @Test
    void resolveDownload_rowNotFound_throwsWbe2014() {
        when(repo.findById(new ErpTrnsHstrId(BIZ, 9999L))).thenReturn(Optional.empty());
        assertBusinessCode("WBE2014", () -> service.resolveDownload(9999L));
    }

    @Test
    void resolveDownload_rowFromDifferentTenant_throwsWbe2014() {
        // findById is keyed by (currentBizKey, seqNo) — a row that
        // exists for a different tenant simply does not match this
        // composite id, surfacing as "not found" with WBE2014.
        when(repo.findById(new ErpTrnsHstrId(BIZ, 759L))).thenReturn(Optional.empty());
        assertBusinessCode("WBE2014", () -> service.resolveDownload(759L));
    }

    @Test
    void resolveDownload_zeroFileSize_throwsWbe2015() {
        ErpTrnsHstr row = entity(759L, 0L, "batch.txt");
        when(repo.findById(new ErpTrnsHstrId(BIZ, 759L))).thenReturn(Optional.of(row));
        assertBusinessCode("WBE2015", () -> service.resolveDownload(759L));
    }

    @Test
    void resolveDownload_missingFile_throwsWbe2015() {
        ErpTrnsHstr row = entity(759L, 1234L, "missing.xlsx");
        when(repo.findById(new ErpTrnsHstrId(BIZ, 759L))).thenReturn(Optional.of(row));
        // Tenant subtree does not exist yet — resolver returns a path
        // but Files.isReadable returns false, so WBE2015.
        assertBusinessCode("WBE2015", () -> service.resolveDownload(759L));
    }

    @Test
    void resolveDownload_realFile_returnsResolvedPath() throws IOException {
        ErpTrnsHstr row = entity(759L, 11L, "hello.txt");
        when(repo.findById(new ErpTrnsHstrId(BIZ, 759L))).thenReturn(Optional.of(row));

        Path tenantSubtree = storageRoot.resolve(BIZ).resolve("erp-transaction-history");
        Files.createDirectories(tenantSubtree);
        Path file = tenantSubtree.resolve("hello.txt");
        Files.writeString(file, "hello world");

        ErpTrnsHistoryService.DownloadResource d = service.resolveDownload(759L);
        assertThat(d.path()).isEqualTo(file);
        assertThat(d.orgFileNm()).isEqualTo("original.txt");
        assertThat(d.saveFileNm()).isEqualTo("hello.txt");
        assertThat(d.fileSize()).isEqualTo(11L);
    }

    /* =================================================================
     *  helpers
     * ================================================================= */

    private ErpTrnsHistoryListResponse call(String fromDate, String toDate) {
        return service.list(fromDate, toDate, null, null, null, null, null, 0, 0);
    }

    private void whenRepoReturnsEmpty() {
        when(repo.search(eq(BIZ), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 15), 0L));
        when(repo.countAllDirections(eq(BIZ), any(), any(), any(), any()))
                .thenReturn(ErpTrnsHistoryCounts.empty());
    }

    private static ErpTrnsHistoryProjection sampleProjection(
            Long seqNo, String erpType, String trnsTypeCd, String regDtm, String regId, String userNm) {
        return new ErpTrnsHistoryProjection(
                seqNo, "Original.xlsx", "Saved.xlsx", 12345L,
                erpType, trnsTypeCd, regDtm, regId, userNm);
    }

    private static ErpTrnsHstr entity(long seqNo, long fileSize, String saveFileNm) {
        ErpTrnsHstr e = new ErpTrnsHstr();
        e.setBizKey(BIZ);
        e.setSeqNo(seqNo);
        e.setOrgFileNm("original.txt");
        e.setSaveFileNm(saveFileNm);
        e.setFileSize(fileSize);
        e.setErpType("W");
        e.setTrnsTypeCd("09");
        e.setRegId("lamine@gmail.com");
        e.setRegDtm("20260430073409");
        return e;
    }

    private void assertBusinessCode(String expectedCode, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(expectedCode);
    }
}
