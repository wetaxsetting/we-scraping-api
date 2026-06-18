package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.common.TransferLineDetailMapper;
import com.kosign.wecms.domain.payment.dispatch.DispatchCountRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver;
import com.kosign.wecms.domain.payment.internaltransfer.dto.CurrencyTotal;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultListResponse;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

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
 * Unit tests for {@link InternalTransferResultService} list behaviour,
 * focused on the <b>F-1 legacy-parity fix</b>: the Transfer Result list
 * filters, sorts, and displays on {@code TRNF_DTM} (dispatch datetime),
 * never {@code REG_DTM} (batch creation).
 *
 * <p>The repository's WHERE-column choice is pinned separately by
 * {@code ScdulPymnSumrSearchDispatchedDateFilterTest} (query-text guard);
 * these Mockito tests cover the service-side sort, the displayed Date/Time
 * source, and the currency-footer totals using the rows the repository
 * returns.
 */
class InternalTransferResultServiceTest {

    private ScdulPymnSumrRepository      sumrRepository;
    private ScdulPymnSumrDetlRepository  detlRepository;
    private DispatchCountRepository      dispatchCountRepository;
    private TransferLineDetailMapper     transferLineDetailMapper;
    private DispatchResultResolver       dispatchResultResolver;
    private ExptResolMstrRepository      exptResolMstrRepository;
    private InternalTransferResultService service;

    @BeforeEach
    void setUp() {
        sumrRepository           = mock(ScdulPymnSumrRepository.class);
        detlRepository           = mock(ScdulPymnSumrDetlRepository.class);
        dispatchCountRepository  = mock(DispatchCountRepository.class);
        transferLineDetailMapper = mock(TransferLineDetailMapper.class);
        dispatchResultResolver   = mock(DispatchResultResolver.class);
        exptResolMstrRepository  = mock(ExptResolMstrRepository.class);
        service = new InternalTransferResultService(
                sumrRepository, detlRepository, dispatchCountRepository,
                transferLineDetailMapper, dispatchResultResolver,
                exptResolMstrRepository);

        // Dispatch counts unreachable in this unit context — service degrades
        // to an empty map (chips disappear); not under test here.
        when(dispatchCountRepository.countByBatch(anyString(), anyList()))
                .thenReturn(Collections.emptyList());

        TenantContext.set("tester", "tester", "VN_VNM0004_000001",
                LangCd.EN, TenantContext.AUTH_GR_USER, "F");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** Build a dispatched ({@code trnf_yn='Y'}) Internal Transfer batch with
     *  deliberately distinct REG_DTM vs TRNF_DTM so the two can never be
     *  confused by an assertion. {@code apprSeqNo} is left null so the
     *  service skips the EXPT_RESOL_MSTR doc_no lookup. */
    private static ScdulPymnSumr batch(String no, String regDtm, String trnfDtm,
                                       String ccyCd, String amt) {
        ScdulPymnSumr s = new ScdulPymnSumr();
        s.setBizKey("VN_VNM0004_000001");
        s.setScdulPymnNo(no);
        s.setScdulPymnTypeCd("03");
        s.setCcyCd(ccyCd);
        s.setTitl("Demo");
        s.setOrgFileNm("demo.xlsx");
        s.setTotalTranAmt(new BigDecimal(amt));
        s.setTotTrnsCnt(BigDecimal.ONE);
        s.setTrnfYn("Y");
        s.setRegDtm(regDtm);
        s.setTrnfDtm(trnfDtm);
        return s;
    }

    @SuppressWarnings("unchecked")
    private void stubSearchReturning(ScdulPymnSumr... rows) {
        when(sumrRepository.searchDispatched(
                anyString(), anyString(), anyString(), anyString(),
                any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(rows)));
    }

    private Pageable captureSearchPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(sumrRepository).searchDispatched(
                anyString(), anyString(), anyString(), anyString(),
                any(), any(), captor.capture());
        return captor.getValue();
    }

    @Test
    void date_time_cell_is_sourced_from_trnf_dtm_not_reg_dtm() {
        // REG_DTM Dec-18 (outside a January window) vs TRNF_DTM Jan-07
        // (inside) — the exact JEX DEV row 275 shape from the F-1 audit.
        stubSearchReturning(batch("275", "20251218075056", "20260107064804", "VND", "100000"));

        TransferResultListResponse resp =
                service.list("20260101", "20260131", null, null, null, null, 0, 10);

        assertThat(resp.rows()).hasSize(1);
        // Wire field name stays `regDtm` (API contract unchanged — legacy
        // aliases TRNF_DTM AS REG_DTM), but the VALUE must be the TRNF_DTM.
        assertThat(resp.rows().get(0).regDtm()).isEqualTo("20260107064804");
    }

    @Test
    void default_sort_is_trnf_dtm_desc_then_scdul_pymn_no_desc() {
        stubSearchReturning(batch("1", "20260101010000", "20260102010000", "VND", "10"));

        service.list("20260101", "20260131", null, null, null, null, 0, 10);

        Sort sort = captureSearchPageable().getSort();
        assertThat(sort).containsExactly(
                Sort.Order.desc("trnfDtm"),
                Sort.Order.desc("scdulPymnNo"));
    }

    @Test
    void user_sort_appends_trnf_dtm_desc_tiebreak() {
        stubSearchReturning(batch("1", "20260101010000", "20260102010000", "VND", "10"));

        // FILE_NM -> orgFileNm (a non-date column) so the tie-break column is
        // distinguishable from the primary sort column.
        service.list("20260101", "20260131", null, null, "FILE_NM", "ASC", 0, 10);

        Sort sort = captureSearchPageable().getSort();
        assertThat(sort).containsExactly(
                Sort.Order.asc("orgFileNm"),
                Sort.Order.desc("trnfDtm"));
    }

    @Test
    void legacy_row_approval_title_comes_from_erm_titl_not_sps_titl() {
        // Legacy shape (JEX DEV: 53/53 approved rows diverge): SPS.TITL is the
        // upload subject ≈ file base, ERM.TITL is the real approval title.
        ScdulPymnSumr s = batch("267", "20251210074351", "20251224045841", "VND", "100000");
        s.setApprSeqNo("1385");
        s.setTitl("WeCMS_Internal_Transfer_upload_EN (1)");       // SPS.TITL (file base, NOT the title)
        s.setOrgFileNm("WeCMS_Internal_Transfer_upload_EN (1).xlsx"); // ORG_FILE_NM (File Name)
        stubSearchReturning(s);

        ExptResolMstr erm = new ExptResolMstr();
        erm.setApprSeqNo("1385");
        erm.setTitl("Approval Testing");  // ERM.TITL — the real Approval Title
        erm.setDocNo("VO_251224_001");    // ERM.DOC_NO — the Approval Ref
        when(exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(anyString(), anyList()))
                .thenReturn(List.of(erm));

        TransferResultListResponse resp =
                service.list("20251201", "20251231", null, null, null, null, 0, 10);

        assertThat(resp.rows()).hasSize(1);
        // Approval Title (wire `titl`) must be ERM.TITL, never SPS.TITL.
        assertThat(resp.rows().get(0).titl()).isEqualTo("Approval Testing");
        // File Name (wire `file_nm`) unchanged — still ORG_FILE_NM.
        assertThat(resp.rows().get(0).fileNm()).isEqualTo("WeCMS_Internal_Transfer_upload_EN (1).xlsx");
        // Approval Ref (wire `doc_no`) unchanged — still ERM.DOC_NO.
        assertThat(resp.rows().get(0).docNo()).isEqualTo("VO_251224_001");
    }

    @Test
    void refactor_row_falls_back_to_sps_titl_when_erm_titl_blank() {
        // Refactor-created row: stampApproval writes ERM.TITL = "" when no
        // post title was given. COALESCE(ERM.TITL, SPS.TITL) must fall back
        // to SPS.TITL (the user-entered title).
        ScdulPymnSumr s = batch("900", "20260601010000", "20260601020000", "VND", "10");
        s.setApprSeqNo("2001");
        s.setTitl("User Title");          // SPS.TITL — the fallback value
        s.setOrgFileNm("upload.xlsx");
        stubSearchReturning(s);

        ExptResolMstr erm = new ExptResolMstr();
        erm.setApprSeqNo("2001");
        erm.setTitl("");                  // ERM.TITL blank (refactor no-post-title case)
        erm.setDocNo("VO_260601_001");
        when(exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(anyString(), anyList()))
                .thenReturn(List.of(erm));

        TransferResultListResponse resp =
                service.list("20260601", "20260630", null, null, null, null, 0, 10);

        assertThat(resp.rows().get(0).titl()).isEqualTo("User Title");
        assertThat(resp.rows().get(0).fileNm()).isEqualTo("upload.xlsx");
    }

    @Test
    void falls_back_to_sps_titl_when_no_approval_row() {
        // No APPR_SEQ_NO → no EXPT_RESOL_MSTR row → COALESCE falls back to
        // SPS.TITL. Repository is not even queried.
        ScdulPymnSumr s = batch("1", "20260101010000", "20260102010000", "VND", "10");
        s.setApprSeqNo(null);
        s.setTitl("User Title");
        stubSearchReturning(s);

        TransferResultListResponse resp =
                service.list("20260101", "20260131", null, null, null, null, 0, 10);

        assertThat(resp.rows().get(0).titl()).isEqualTo("User Title");
        assertThat(resp.rows().get(0).docNo()).isNull();
    }

    /** Stub the full-dataset currency aggregate (legacy SCDUL_PYMN_SUMR_R008).
     *  Rows are {@code [ccyCd, SUM(totalTranAmt), COUNT, SUM(totTrnsCnt)]}. */
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
    void footer_totals_come_from_full_dataset_aggregate_not_the_page() {
        // The current page returns ONE USD row, but the full filtered set spans
        // both currencies (legacy R008 aggregate). The footer must reflect the
        // aggregate — never a re-sum of the page slice (F-1).
        stubSearchReturning(batch("2", "20260102010000", "20260106010000", "USD", "50000"));
        stubCurrencyTotals(
                new Object[]{"VND", new BigDecimal("100000"), 1L, new BigDecimal("1")},
                new Object[]{"USD", new BigDecimal("50000"),  1L, new BigDecimal("1")});

        TransferResultListResponse resp =
                service.list("20260101", "20260131", null, null, null, null, 0, 10);

        assertThat(resp.currencyTotals())
                .extracting(CurrencyTotal::ccyCd)
                .containsExactlyInAnyOrder("USD", "VND");
        assertThat(vndTotal(resp)).isEqualByComparingTo("100000");   // present even though no VND row on the page
        assertThat(resp.currencyTotals().stream()
                .filter(t -> "USD".equals(t.ccyCd())).findFirst().orElseThrow().totalAmount())
                .isEqualByComparingTo("50000");
    }

    @Test
    void footer_totals_are_identical_across_pages() {
        // The aggregate is the full filtered set (26 batches, 642,643,325.18 —
        // the F-1 DEV figure). Page 1 and page 2 return DIFFERENT row slices,
        // yet the footer total must be identical: pagination cannot move it.
        stubCurrencyTotals(
                new Object[]{"VND", new BigDecimal("642643325.18"), 26L, new BigDecimal("260")});
        when(sumrRepository.searchDispatched(
                anyString(), anyString(), anyString(), anyString(),
                any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(   // page 0 slice
                        batch("1", "20260101010000", "20260105010000", "VND", "100000"),
                        batch("2", "20260102010000", "20260106010000", "VND", "200000"))))
                .thenReturn(new PageImpl<>(List.of(   // page 1 slice — different rows
                        batch("3", "20260103010000", "20260107010000", "VND", "5"))));

        BigDecimal page0 = vndTotal(service.list("20260101", "20260131", null, null, null, null, 0, 10));
        BigDecimal page1 = vndTotal(service.list("20260101", "20260131", null, null, null, null, 1, 10));

        assertThat(page0).isEqualByComparingTo("642643325.18");
        assertThat(page1).isEqualByComparingTo("642643325.18");
        assertThat(page0).isEqualByComparingTo(page1);   // pagination does not affect totals
    }

    @Test
    void footer_aggregate_uses_same_filter_args_as_the_list_query() {
        // Legacy parity: SCDUL_PYMN_SUMR_R008 reuses the list predicate. The
        // aggregate must be invoked with the SAME tenant/type/date/search/trnfYn
        // args the paged searchDispatched receives. Internal Transfer passes the
        // raw keyword (emptyToNull(srchWd)) on both — not a %like% pattern.
        stubSearchReturning(batch("1", "20260101010000", "20260105010000", "VND", "100000"));
        stubCurrencyTotals(new Object[]{"VND", new BigDecimal("100000"), 1L, new BigDecimal("1")});

        service.list("20260101", "20260131", "acme", null, null, null, 0, 10);

        verify(sumrRepository).searchDispatchedCurrencyTotals(
                eq("VN_VNM0004_000001"), eq("03"),
                eq("20260101000000"), eq("20260131235959"),
                eq("acme"), isNull());
    }
}
