package com.kosign.wecms.domain.vatdeclaration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.vatdeclaration.dto.VatDeclarationCreateRequest;
import com.kosign.wecms.domain.vatdeclaration.entity.VatDeclaration;
import com.kosign.wecms.domain.vatdeclaration.entity.VatDeclarationIndicator;
import com.kosign.wecms.domain.vatdeclaration.repository.VatAnnexInvoiceRepository;
import com.kosign.wecms.domain.vatdeclaration.repository.VatDeclarationIndicatorRepository;
import com.kosign.wecms.domain.vatdeclaration.repository.VatDeclarationRepository;
import com.kosign.wecms.domain.vatdeclaration.repository.VatSyncBucketProjection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lifecycle coverage for {@link VatDeclarationService} — the guards that keep
 * the period chain consistent. Pins:
 *
 * <ul>
 *   <li>lock walks BACKWARD: previous period locked, no hole in the chain,
 *       [22] still agreeing with the previous [43];</li>
 *   <li>unlock walks FORWARD: nothing later may already be locked;</li>
 *   <li>delete refuses while ANY later period exists (OQ-VAT-6);</li>
 *   <li>lock/unlock are idempotent toggles;</li>
 *   <li>the invoice marker is stamped on lock and released on unlock, and the
 *       replaced / cancelled statuses never reach any invoice query.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VatDeclarationLifecycleTest {

    private static final String BIZ = "VN_VNM0004_000001";
    private static final String USER = "tester";
    private static final long SID = 12L;

    @Mock private VatDeclarationRepository declRepo;
    @Mock private VatDeclarationIndicatorRepository indicatorRepo;
    @Mock private VatAnnexInvoiceRepository annexInvoiceRepo;
    @Mock private BusiInfmRepository busiInfmRepo;
    @Spy  private VatIndicatorCalculator calculator = new VatIndicatorCalculator();
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private VatDeclarationService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(USER, "Tester", BIZ, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** Quarter-2/2026 declaration in the given status. */
    private static VatDeclaration decl(long sid, String status, int periodValue, int year) {
        VatDeclaration d = VatDeclaration.builder()
                .bizKey(BIZ).periodType("quarter").periodValue(periodValue).periodYear(year)
                .formCode("842").xmlVer("2.6.8").activityName("x").activityCode("0")
                .acctItem("1701").period("Q" + periodValue + "/" + year).supplNo(0).declType("C")
                .tpName("CTY").tpTaxCode("0301234567").tpProv("").periodKind("Q")
                .fromDt(LocalDate.of(year, (periodValue - 1) * 3 + 1, 1))
                .toDt(LocalDate.of(year, periodValue * 3, 30))
                .officeCode("").officeName("").declDt(LocalDate.now())
                .indicators("{}").delYn("N").status(status)
                .annexBkbrYn("Y").annexBkmvYn("Y").annexAllocYn("N")
                .annexAllocTeleYn("N").annexReduce8Yn("N")
                .build();
        d.setSid(sid);
        return d;
    }

    private static VatDeclarationIndicator ind(VatDeclaration owner, String prevCredit, String creditNext) {
        VatDeclarationIndicator i = VatDeclarationIndicator.builder()
                .declaration(owner)
                .prevCredit(new BigDecimal(prevCredit))
                .creditNext(new BigDecimal(creditNext))
                .delYn("N")
                .build();
        return i;
    }

    private void given(VatDeclaration d, VatDeclarationIndicator i) {
        when(declRepo.findBySidAndBizKeyAndDelYn(d.getSid(), BIZ, "N")).thenReturn(Optional.of(d));
        when(indicatorRepo.findByDeclaration_SidAndDeclaration_BizKeyAndDelYn(d.getSid(), BIZ, "N"))
                .thenReturn(Optional.of(i));
    }

    /**
     * One aggregation bucket as {@code syncBuckets} would return it.
     *
     * <p>A plain implementation, not a mock: stubbing it with {@code when(...)}
     * inside the argument list of another {@code when(...)} is what Mockito
     * rejects as unfinished stubbing, and the failure names the outer call —
     * which sends you looking in the wrong place.
     */
    private static VatSyncBucketProjection bucket(String docType, String rate,
                                                  String amt, String tax) {
        return new VatSyncBucketProjection() {
            public String getDocType()     { return docType; }
            public String getTaxRate()     { return rate; }
            public BigDecimal getAmt()     { return new BigDecimal(amt); }
            public BigDecimal getTaxAmt()  { return new BigDecimal(tax); }
        };
    }

    /** No K-1 row in the repository for the period before this one. */
    private void noPreviousRow() {
        when(declRepo.findTopByBizKeyAndPeriodTypeAndPeriodValueAndPeriodYearAndDelYnOrderBySupplNoDesc(
                anyString(), anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(Optional.empty());
    }

    @Nested
    @DisplayName("lock — walks backward")
    class Lock {

        @Test
        @DisplayName("first period ever: no K-1 and nothing earlier → locks")
        void firstPeriodLocks() {
            VatDeclaration d = decl(SID, VatDeclarationService.STATUS_DRAFT, 1, 2026);
            given(d, ind(d, "0", "0"));
            noPreviousRow();
            when(declRepo.existsEarlierPeriod(BIZ, "quarter", 2026, 1)).thenReturn(false);

            service.lock(SID);

            assertThat(d.getStatus()).isEqualTo(VatDeclarationService.STATUS_LOCKED);
        }

        /**
         * PO 2026-08-06 moved the contiguity guard to {@code create()}: lock only
         * walks back one period to carry [43] → [22], and a deliberate filing gap
         * has nothing to carry, so it locks. This test used to expect
         * {@code WBE_VAT_DECL_PREV_MISSING} here and had been failing ever since
         * that decision landed — the guard it was written for now lives one
         * lifecycle step earlier, where {@code gapInChainRejectedAtCreate} covers
         * it.
         */
        @Test
        @DisplayName("hole in the chain: K-1 missing but older periods exist → locks anyway")
        void gapInChainLocks() {
            // Q2/2026, not Q3: Q3 was still running when assertPeriodEnded landed
            // and this case would fail on that guard instead of exercising the gap.
            VatDeclaration d = decl(SID, VatDeclarationService.STATUS_DRAFT, 2, 2026);
            given(d, ind(d, "0", "0"));
            noPreviousRow();

            service.lock(SID);

            assertThat(d.getStatus()).isEqualTo(VatDeclarationService.STATUS_LOCKED);
        }

        @Test
        @DisplayName("K-1 still Draft → refused")
        void previousStillDraftRefused() {
            VatDeclaration d = decl(SID, VatDeclarationService.STATUS_DRAFT, 2, 2026);
            VatDeclaration prev = decl(11L, VatDeclarationService.STATUS_DRAFT, 1, 2026);
            given(d, ind(d, "0", "0"));
            when(declRepo.findTopByBizKeyAndPeriodTypeAndPeriodValueAndPeriodYearAndDelYnOrderBySupplNoDesc(
                    BIZ, "quarter", 1, 2026, "N")).thenReturn(Optional.of(prev));

            assertThatThrownBy(() -> service.lock(SID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBE_VAT_DECL_PREV_NOT_LOCKED");
        }

        @Test
        @DisplayName("[22] drifted from the previous [43] → refused")
        void carryForwardMismatchRefused() {
            VatDeclaration d = decl(SID, VatDeclarationService.STATUS_DRAFT, 2, 2026);
            VatDeclaration prev = decl(11L, VatDeclarationService.STATUS_LOCKED, 1, 2026);
            given(d, ind(d, "5000000", "0"));                       // K  [22] = 5,000,000
            when(declRepo.findTopByBizKeyAndPeriodTypeAndPeriodValueAndPeriodYearAndDelYnOrderBySupplNoDesc(
                    BIZ, "quarter", 1, 2026, "N")).thenReturn(Optional.of(prev));
            when(indicatorRepo.findByDeclaration_SidAndDeclaration_BizKeyAndDelYn(11L, BIZ, "N"))
                    .thenReturn(Optional.of(ind(prev, "0", "7000000")));   // K-1 [43] = 7,000,000

            assertThatThrownBy(() -> service.lock(SID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBE_VAT_DECL_PREV_CREDIT_MISMATCH");
            assertThat(d.getStatus()).isEqualTo(VatDeclarationService.STATUS_DRAFT);
        }

        @Test
        @DisplayName("[22] equal to the previous [43] → locks and stamps the invoices")
        void carryForwardMatchLocksAndStamps() {
            VatDeclaration d = decl(SID, VatDeclarationService.STATUS_DRAFT, 2, 2026);
            VatDeclaration prev = decl(11L, VatDeclarationService.STATUS_LOCKED, 1, 2026);
            given(d, ind(d, "7000000", "0"));
            when(declRepo.findTopByBizKeyAndPeriodTypeAndPeriodValueAndPeriodYearAndDelYnOrderBySupplNoDesc(
                    BIZ, "quarter", 1, 2026, "N")).thenReturn(Optional.of(prev));
            when(indicatorRepo.findByDeclaration_SidAndDeclaration_BizKeyAndDelYn(11L, BIZ, "N"))
                    .thenReturn(Optional.of(ind(prev, "0", "7000000")));

            service.lock(SID);

            assertThat(d.getStatus()).isEqualTo(VatDeclarationService.STATUS_LOCKED);
            verify(annexInvoiceRepo).markProcessed(eq(BIZ), eq("20260401"), eq("20260630"),
                    any(), any(), eq(SID));
        }

        /**
         * Year 2999 rather than "next quarter": the period must still be in the
         * future the day someone runs this suite in 2030, and a relative date
         * would need the clock injected to stay honest.
         */
        @Test
        @DisplayName("period still running → lock refused")
        void unfinishedPeriodRefused() {
            VatDeclaration d = decl(SID, VatDeclarationService.STATUS_DRAFT, 1, 2999);
            given(d, ind(d, "0", "0"));

            assertThatThrownBy(() -> service.lock(SID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBE_VAT_DECL_PERIOD_NOT_ENDED");
            assertThat(d.getStatus()).isEqualTo(VatDeclarationService.STATUS_DRAFT);
        }

        /**
         * A future period locked before this guard existed stays locked — the
         * idempotent early-return fires first. Re-locking it after an unlock is
         * refused, which is the intended nudge to clean the row up rather than a
         * regression.
         */
        @Test
        @DisplayName("future period already locked → still idempotent, not an error")
        void alreadyLockedFuturePeriodTolerated() {
            VatDeclaration d = decl(SID, VatDeclarationService.STATUS_LOCKED, 1, 2999);
            given(d, ind(d, "0", "0"));

            service.lock(SID);

            assertThat(d.getStatus()).isEqualTo(VatDeclarationService.STATUS_LOCKED);
        }

        /** First-period-ever setup, so only the [21] guard is in play. */
        private VatDeclarationIndicator openFirstPeriod() {
            VatDeclaration d = decl(SID, VatDeclarationService.STATUS_DRAFT, 1, 2026);
            VatDeclarationIndicator i = ind(d, "0", "0");
            given(d, i);
            noPreviousRow();
            when(declRepo.existsEarlierPeriod(BIZ, "quarter", 2026, 1)).thenReturn(false);
            i.setNoActivity(BigDecimal.ONE);
            return i;
        }

        @Test
        @DisplayName("[21] set but [34] reports sales → refused")
        void noActivityWithSalesRefused() {
            VatDeclarationIndicator i = openFirstPeriod();
            i.setTotalSalesAmt(new BigDecimal("39858181"));

            assertThatThrownBy(() -> service.lock(SID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBE_VAT_DECL_NO_ACTIVITY_CONFLICT");
            assertThat(i.getDeclaration().getStatus())
                    .isEqualTo(VatDeclarationService.STATUS_DRAFT);
        }

        @Test
        @DisplayName("[21] set with purchases only → still refused")
        void noActivityWithPurchasesRefused() {
            VatDeclarationIndicator i = openFirstPeriod();
            i.setPurchAmt(new BigDecimal("12000000"));   // bought, sold nothing

            assertThatThrownBy(() -> service.lock(SID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBE_VAT_DECL_NO_ACTIVITY_CONFLICT");
        }

        @Test
        @DisplayName("[21] set on a genuinely empty period → locks")
        void noActivityOnEmptyPeriodLocks() {
            VatDeclarationIndicator i = openFirstPeriod();

            service.lock(SID);

            assertThat(i.getDeclaration().getStatus())
                    .isEqualTo(VatDeclarationService.STATUS_LOCKED);
        }

        @Test
        @DisplayName("already locked → idempotent, no re-stamp")
        void alreadyLockedIsIdempotent() {
            VatDeclaration d = decl(SID, VatDeclarationService.STATUS_LOCKED, 2, 2026);
            given(d, ind(d, "0", "0"));

            service.lock(SID);

            assertThat(d.getStatus()).isEqualTo(VatDeclarationService.STATUS_LOCKED);
            verify(annexInvoiceRepo, never()).markProcessed(anyString(), anyString(), anyString(),
                    any(), any(), anyLong());
        }

        @Test
        @DisplayName("the stamp never touches replaced / cancelled invoices")
        void stampExcludesReplacedAndCancelled() {
            VatDeclaration d = decl(SID, VatDeclarationService.STATUS_DRAFT, 1, 2026);
            given(d, ind(d, "0", "0"));
            noPreviousRow();
            when(declRepo.existsEarlierPeriod(BIZ, "quarter", 2026, 1)).thenReturn(false);

            service.lock(SID);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<String>> excluded = ArgumentCaptor.forClass(Collection.class);
            verify(annexInvoiceRepo).markProcessed(anyString(), anyString(), anyString(),
                    any(), excluded.capture(), anyLong());
            assertThat(excluded.getValue())
                    .containsExactlyInAnyOrder("Hóa đơn đã bị thay thế", "Hóa đơn đã bị hủy")
                    .doesNotContain("Hóa đơn đã bị điều chỉnh");   // adjustment pairs stay
        }
    }

    @Nested
    @DisplayName("unlock — walks forward")
    class Unlock {

        @Test
        @DisplayName("a later period is already locked → refused")
        void laterLockedRefused() {
            VatDeclaration d = decl(SID, VatDeclarationService.STATUS_LOCKED, 2, 2026);
            given(d, ind(d, "0", "0"));
            when(declRepo.existsLaterLockedPeriod(BIZ, "quarter", 2026, 2,
                    VatDeclarationService.STATUS_LOCKED)).thenReturn(true);

            assertThatThrownBy(() -> service.unlock(SID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBE_VAT_DECL_NEXT_LOCKED");
            assertThat(d.getStatus()).isEqualTo(VatDeclarationService.STATUS_LOCKED);
        }

        @Test
        @DisplayName("nothing later locked → re-opens and releases the invoices")
        void unlocksAndReleases() {
            VatDeclaration d = decl(SID, VatDeclarationService.STATUS_LOCKED, 2, 2026);
            given(d, ind(d, "0", "0"));
            when(declRepo.existsLaterLockedPeriod(BIZ, "quarter", 2026, 2,
                    VatDeclarationService.STATUS_LOCKED)).thenReturn(false);

            service.unlock(SID);

            assertThat(d.getStatus()).isEqualTo(VatDeclarationService.STATUS_DRAFT);
            verify(annexInvoiceRepo).clearProcessed(BIZ, SID);
        }

        @Test
        @DisplayName("already draft → idempotent, no release")
        void alreadyDraftIsIdempotent() {
            VatDeclaration d = decl(SID, VatDeclarationService.STATUS_DRAFT, 2, 2026);
            given(d, ind(d, "0", "0"));

            service.unlock(SID);

            assertThat(d.getStatus()).isEqualTo(VatDeclarationService.STATUS_DRAFT);
            verify(annexInvoiceRepo, never()).clearProcessed(anyString(), anyLong());
        }
    }

    @Nested
    @DisplayName("delete — OQ-VAT-6")
    class Delete {

        @Test
        @DisplayName("a later period exists → refused, row untouched")
        void laterPeriodBlocksDelete() {
            VatDeclaration d = decl(SID, VatDeclarationService.STATUS_DRAFT, 2, 2026);
            given(d, ind(d, "0", "0"));
            when(declRepo.existsLaterPeriod(BIZ, "quarter", 2026, 2)).thenReturn(true);

            assertThatThrownBy(() -> service.delete(SID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBE_VAT_DECL_LATER_PERIOD_EXISTS");
            assertThat(d.getDelYn()).isEqualTo("N");
        }

        @Test
        @DisplayName("newest period → soft-deletes header and indicator")
        void newestPeriodDeletes() {
            VatDeclaration d = decl(SID, VatDeclarationService.STATUS_DRAFT, 2, 2026);
            VatDeclarationIndicator i = ind(d, "0", "0");
            given(d, i);
            when(declRepo.existsLaterPeriod(BIZ, "quarter", 2026, 2)).thenReturn(false);

            service.delete(SID);

            assertThat(d.getDelYn()).isEqualTo("Y");
            assertThat(i.getDelYn()).isEqualTo("Y");
        }

        @Test
        @DisplayName("a locked declaration is never deletable")
        void lockedNotDeletable() {
            VatDeclaration d = decl(SID, VatDeclarationService.STATUS_LOCKED, 2, 2026);
            given(d, ind(d, "0", "0"));

            assertThatThrownBy(() -> service.delete(SID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBE_VAT_DECL_LOCKED");
        }
    }

    @Nested
    @DisplayName("annex listing — status filter")
    class AnnexFilter {

        @Test
        @DisplayName("replaced / cancelled are excluded from every invoice query")
        void excludesReplacedAndCancelled() {
            VatDeclaration d = decl(SID, VatDeclarationService.STATUS_DRAFT, 2, 2026);
            given(d, ind(d, "0", "0"));
            when(annexInvoiceRepo.groupTotals(anyString(), anyString(), anyString(),
                    any(), any(), any(), any())).thenReturn(List.of());

            service.listAnnexInvoices(SID, "REDUCE8", null, null, 0, 20);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<String>> excluded = ArgumentCaptor.forClass(Collection.class);
            verify(annexInvoiceRepo).groupTotals(anyString(), anyString(), anyString(),
                    any(), any(), any(), excluded.capture());
            assertThat(excluded.getValue())
                    .containsExactlyInAnyOrder("Hóa đơn đã bị thay thế", "Hóa đơn đã bị hủy");
        }

        @Test
        @DisplayName("the two 01-6/GTGT allocation annexes have no invoice source → empty")
        void allocationAnnexesReturnEmpty() {
            VatDeclaration d = decl(SID, VatDeclarationService.STATUS_DRAFT, 2, 2026);
            given(d, ind(d, "0", "0"));

            var res = service.listAnnexInvoices(SID, "ALLOC_CSSX", null, null, 0, 20);

            assertThat(res.rows()).isEmpty();
            assertThat(res.totalCount()).isZero();
            assertThat(res.totalPages()).isZero();
            assertThat(res.pageNumber()).isZero();
            assertThat(res.pageSize()).isEqualTo(20);
            assertThat(res.taxGroups()).isEmpty();
            assertThat(res.grandTotal().groupCd()).isEqualTo("TOTAL");
            verify(annexInvoiceRepo, never()).groupTotals(anyString(), anyString(), anyString(),
                    any(), any(), any(), any());
        }

        @Test
        @DisplayName("BKBR reads output invoices, BKMV reads input ones")
        void directionMapsToDocType() {
            VatDeclaration d = decl(SID, VatDeclarationService.STATUS_DRAFT, 2, 2026);
            given(d, ind(d, "0", "0"));
            when(annexInvoiceRepo.groupTotals(anyString(), anyString(), anyString(),
                    any(), any(), any(), any())).thenReturn(List.of());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<String>> docTypes = ArgumentCaptor.forClass(Collection.class);

            service.listAnnexInvoices(SID, "BKBR", null, null, 0, 20);
            verify(annexInvoiceRepo).groupTotals(anyString(), anyString(), anyString(),
                    docTypes.capture(), any(), any(), any());
            assertThat(docTypes.getValue()).containsExactly("I");   // output / sales
        }
    }

    @Nested
    @DisplayName("create — required fields")
    class Create {

        /** Q2/2026 create request with the given business-industry code. */
        private static VatDeclarationCreateRequest requestWithIndustry(String bsnsLineCd) {
            return new VatDeclarationCreateRequest(
                    "Q", 2, 2026,
                    bsnsLineCd, null,
                    null, null,
                    null, null, null, null, null);
        }

        @Test
        @DisplayName("bsns_line_cd null → WBE_VAT_DECL_BSNS_LINE_REQUIRED, nothing persisted")
        void nullIndustryRejected() {
            assertThatThrownBy(() -> service.create(requestWithIndustry(null)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBE_VAT_DECL_BSNS_LINE_REQUIRED");
            verify(declRepo, never()).save(any());
        }

        @Test
        @DisplayName("bsns_line_cd blank → same guard (whitespace is not a code)")
        void blankIndustryRejected() {
            assertThatThrownBy(() -> service.create(requestWithIndustry("   ")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBE_VAT_DECL_BSNS_LINE_REQUIRED");
            verify(declRepo, never()).save(any());
        }

        /** Same shape as {@link #requestWithIndustry}, for an arbitrary period. */
        private static VatDeclarationCreateRequest request(String type, int value, int year) {
            return new VatDeclarationCreateRequest(
                    type, value, year, "00", null, null, null, null, null, null, null, null);
        }

        @Test
        @DisplayName("year already filed monthly → a quarterly period is refused")
        void mixedPeriodTypeInYearRefused() {
            when(declRepo.periodTypesInYear(BIZ, 2026)).thenReturn(List.of("month"));

            assertThatThrownBy(() -> service.create(request("Q", 2, 2026)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBE_VAT_DECL_PERIOD_TYPE_MISMATCH");
            verify(declRepo, never()).save(any());
        }

        @Test
        @DisplayName("same type again in the same year → allowed past the type guard")
        void samePeriodTypeInYearAllowed() {
            when(declRepo.periodTypesInYear(BIZ, 2026)).thenReturn(List.of("quarter"));
            when(declRepo.existsByBizKeyAndPeriodTypeAndPeriodValueAndPeriodYearAndSupplNoAndDelYn(
                    BIZ, "quarter", 2, 2026, 0, "N")).thenReturn(false);
            noPreviousRow();
            when(declRepo.existsEarlierPeriod(BIZ, "quarter", 2026, 2)).thenReturn(true);

            // Falls through to the contiguity guard, which is the next one along —
            // proving the type check let it past rather than swallowing it.
            assertThatThrownBy(() -> service.create(request("Q", 2, 2026)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBE_VAT_DECL_PREV_MISSING");
        }

        /**
         * The legal switch: monthly through 12/2025, quarterly from Q1/2026. The
         * type-scoped lookup sees no Q4/2025 and used to seed [22] = 0, throwing
         * away a credit balance the tenant is entitled to deduct.
         */
        @Test
        @DisplayName("first quarter after a monthly year → [22] carries December's [43]")
        void yearBoundarySwitchCarriesCredit() {
            VatDeclaration dec2025 = VatDeclaration.builder()
                    .bizKey(BIZ).periodType("month").periodValue(12).periodYear(2025)
                    .supplNo(0).delYn("N").status(VatDeclarationService.STATUS_LOCKED)
                    .fromDt(LocalDate.of(2025, 12, 1)).toDt(LocalDate.of(2025, 12, 31))
                    .build();
            dec2025.setSid(99L);

            when(declRepo.findPrecedingByDate(eq(BIZ), eq(LocalDate.of(2026, 1, 1)), any()))
                    .thenReturn(List.of(dec2025));
            when(indicatorRepo.findByDeclaration_SidAndDeclaration_BizKeyAndDelYn(99L, BIZ, "N"))
                    .thenReturn(Optional.of(ind(dec2025, "0", "7000000")));
            when(busiInfmRepo.findById(BIZ)).thenReturn(Optional.of(new BusiInfm()));
            when(declRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.create(request("Q", 1, 2026));

            ArgumentCaptor<VatDeclarationIndicator> saved =
                    ArgumentCaptor.forClass(VatDeclarationIndicator.class);
            verify(indicatorRepo).save(saved.capture());
            assertThat(saved.getValue().getPrevCredit())
                    .isEqualByComparingTo(new BigDecimal("7000000"));
        }

        /**
         * QA S-35. {@code SPLY_AMT} is {@code NUMERIC(20,6)} and live invoices
         * carry fractions — [23] came out at 4,040,744.71 on 02/2026. The XML
         * exporter then dropped the fraction while the stored declaration kept
         * it, so one filing held two different numbers for the same line.
         *
         * <p>Rounding belongs at the sync boundary, not at export: after this,
         * every downstream artifact reads the same integral figure.
         */
        @Test
        @DisplayName("sync rounds a fractional bucket to whole đồng")
        void syncRoundsToWholeDong() {
            when(busiInfmRepo.findById(BIZ)).thenReturn(Optional.of(new BusiInfm()));
            when(declRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(annexInvoiceRepo.syncBuckets(eq(BIZ), any(), any(), any(), any()))
                    .thenReturn(List.of(bucket("B", "10%", "4040744.71", "404074"),
                                        bucket("I", "10%", "1901577943.5", "190157794")));

            service.create(request("M", 5, 2026));

            ArgumentCaptor<VatDeclarationIndicator> saved =
                    ArgumentCaptor.forClass(VatDeclarationIndicator.class);
            verify(indicatorRepo).save(saved.capture());
            // .71 rounds up, .5 rounds up (HALF_UP) — never silently truncated
            assertThat(saved.getValue().getPurchAmt()).isEqualByComparingTo("4040745");
            assertThat(saved.getValue().getSalesAmt10()).isEqualByComparingTo("1901577944");
        }

        /**
         * QA S-07. A downward-adjustment invoice can exceed the period's own
         * revenue when it cancels part of what an earlier period billed. The
         * period then really did book negative net revenue.
         *
         * <p>Sync used to refuse the whole period with
         * {@code WBE_VAT_DECL_INVALID_INDICATOR}, so a lawful filing could not be
         * made at all. PO ruled 2026-08-13 that negative indicators are allowed.
         */
        @Test
        @DisplayName("sync accepts a period whose adjustments exceed its revenue")
        void negativeRevenueSyncs() {
            when(busiInfmRepo.findById(BIZ)).thenReturn(Optional.of(new BusiInfm()));
            when(declRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(annexInvoiceRepo.syncBuckets(eq(BIZ), any(), any(), any(), any()))
                    .thenReturn(List.of(bucket("I", "10%", "-4900000000", "-490000000")));

            service.create(request("M", 5, 2026));

            ArgumentCaptor<VatDeclarationIndicator> saved =
                    ArgumentCaptor.forClass(VatDeclarationIndicator.class);
            verify(indicatorRepo).save(saved.capture());
            assertThat(saved.getValue().getSalesAmt10()).isEqualByComparingTo("-4900000000");
            // Negative output VAT is a credit, so it lands in [41]/[43], not [40].
            assertThat(saved.getValue().getVatPayableNet()).isEqualByComparingTo("0");
            assertThat(saved.getValue().getCreditNext()).isPositive();
        }

        /**
         * The other half of the PO ruling: a minus sign on a magnitude cell is a
         * different statement, not a smaller number. {@code [38]} is an increase
         * whose sign the formula supplies ({@code +ct37 - ct38}); accepting
         * {@code -1000} there would make it an indistinguishable second way to
         * write {@code [37] = 1000}.
         */
        @Test
        @DisplayName("[38] still refuses a negative — the formula owns its sign")
        void negativeStillRefusedOnMagnitudeCell() {
            VatDeclarationIndicator ind = new VatDeclarationIndicator();
            ind.setAdjIncrease(new BigDecimal("-1000"));

            assertThatThrownBy(() -> calculator.recompute(ind))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBE_VAT_DECL_INVALID_INDICATOR");
        }

        /**
         * QA S-08. February of a leap year must end on the 29th, or an invoice
         * dated 29/02 falls outside its own period and vanishes from the return.
         *
         * <p>Tested here rather than through the API because reaching 02/2028
         * over HTTP needs a contiguous chain of nineteen more monthly periods —
         * the question is only what {@code periodRange} computes, and that is
         * visible on the row {@code create()} builds.
         */
        @Test
        @DisplayName("February 2028 (leap) spans 01–29, not 01–28")
        void leapFebruarySpansTwentyNineDays() {
            when(busiInfmRepo.findById(BIZ)).thenReturn(Optional.of(new BusiInfm()));
            when(declRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.create(request("M", 2, 2028));

            ArgumentCaptor<VatDeclaration> saved = ArgumentCaptor.forClass(VatDeclaration.class);
            verify(declRepo).save(saved.capture());
            assertThat(saved.getValue().getFromDt()).isEqualTo(LocalDate.of(2028, 2, 1));
            assertThat(saved.getValue().getToDt()).isEqualTo(LocalDate.of(2028, 2, 29));
        }

        @Test
        @DisplayName("February 2026 (non-leap) spans 01–28")
        void ordinaryFebruarySpansTwentyEightDays() {
            when(busiInfmRepo.findById(BIZ)).thenReturn(Optional.of(new BusiInfm()));
            when(declRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.create(request("M", 2, 2026));

            ArgumentCaptor<VatDeclaration> saved = ArgumentCaptor.forClass(VatDeclaration.class);
            verify(declRepo).save(saved.capture());
            assertThat(saved.getValue().getToDt()).isEqualTo(LocalDate.of(2026, 2, 28));
        }

        /**
         * The contiguity guard the Lock nest used to assert. PO 2026-08-06: a
         * period may be created only when K-1 exists (and is locked), or when it
         * is the tenant's very first — no skipping ahead over an absent period.
         */
        @Test
        @DisplayName("hole in the chain: K-1 missing but older periods exist → refused at create")
        void gapInChainRejectedAtCreate() {
            when(declRepo.existsByBizKeyAndPeriodTypeAndPeriodValueAndPeriodYearAndSupplNoAndDelYn(
                    BIZ, "quarter", 2, 2026, 0, "N")).thenReturn(false);
            noPreviousRow();
            when(declRepo.existsEarlierPeriod(BIZ, "quarter", 2026, 2)).thenReturn(true);

            assertThatThrownBy(() -> service.create(requestWithIndustry("00")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBE_VAT_DECL_PREV_MISSING");
            verify(declRepo, never()).save(any());
        }

        @Test
        @DisplayName("guard runs before the duplicate-period lookup")
        void guardPrecedesRepositoryWork() {
            assertThatThrownBy(() -> service.create(requestWithIndustry(null)))
                    .isInstanceOf(BusinessException.class);
            verify(declRepo, never())
                    .existsByBizKeyAndPeriodTypeAndPeriodValueAndPeriodYearAndSupplNoAndDelYn(
                            anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyString());
        }
    }
}
