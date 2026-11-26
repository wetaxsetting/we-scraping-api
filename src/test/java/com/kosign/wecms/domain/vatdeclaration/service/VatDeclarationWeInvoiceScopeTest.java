package com.kosign.wecms.domain.vatdeclaration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.vatdeclaration.dto.VatDeclarationCreateRequest;
import com.kosign.wecms.domain.vatdeclaration.dto.VatDeclarationDetail;
import com.kosign.wecms.domain.vatdeclaration.entity.VatDeclaration;
import com.kosign.wecms.domain.vatdeclaration.entity.VatDeclarationIndicator;
import com.kosign.wecms.domain.vatdeclaration.repository.VatAnnexInvoiceRepository;
import com.kosign.wecms.domain.vatdeclaration.repository.VatDeclarationIndicatorRepository;
import com.kosign.wecms.domain.vatdeclaration.repository.VatDeclarationRepository;
import com.kosign.wecms.domain.vatdeclaration.repository.VatSyncBucketProjection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * WeInvoice books of {@link VatDeclarationService}: a declaration created / read through a
 * {@link VatScope#weinvoice} scope is keyed by that scope, snapshots the MST and company name of the scope (no
 * {@code BUSI_INFM}), and takes its invoices from the engine source — never from the WeCMS e-invoice tables.
 * Record: docs/features/addon-service/weinvoice/weinvoice-vat-declaration-engine-invoices-2026-11-25.md.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VatDeclarationWeInvoiceScopeTest {

    private static final String OWNER = "lamine@gmail.com";
    private static final String MST = "0319232069";
    private static final VatScope SCOPE = VatScope.weinvoice(OWNER, MST, "CÔNG TY TNHH JK YOUTH MK");

    @Mock private VatDeclarationRepository declRepo;
    @Mock private VatDeclarationIndicatorRepository indicatorRepo;
    @Mock private VatAnnexInvoiceRepository annexInvoiceRepo;
    @Mock private BusiInfmRepository busiInfmRepo;
    @Mock private VatInvoiceSource engineInvoices;
    @Spy  private VatIndicatorCalculator calculator = new VatIndicatorCalculator();
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private VatDeclarationService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(OWNER, "Lamine", "WEINVOICE", LangCd.VI);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static VatSyncBucketProjection bucket(String docType, String rate, String amt, String tax) {
        return new VatSyncBucketProjection() {
            public String getDocType()    { return docType; }
            public String getTaxRate()    { return rate; }
            public BigDecimal getAmt()    { return new BigDecimal(amt); }
            public BigDecimal getTaxAmt() { return new BigDecimal(tax); }
        };
    }

    private static VatDeclaration bookDecl(long sid, String status) {
        VatDeclaration d = VatDeclaration.builder()
                .bizKey(SCOPE.key()).periodType("month").periodValue(10).periodYear(2026)
                .formCode("842").xmlVer("2.6.8").activityName("x").activityCode("0")
                .acctItem("1701").period("10/2026").supplNo(0).declType("C")
                .tpName("CTY").tpTaxCode(MST).tpProv("").periodKind("M")
                .fromDt(LocalDate.of(2026, 10, 1)).toDt(LocalDate.of(2026, 10, 31))
                .officeCode("").officeName("").declDt(LocalDate.now())
                .indicators("{}").delYn("N").status(status)
                .annexBkbrYn("Y").annexBkmvYn("Y").annexAllocYn("N").annexAllocTeleYn("N").annexReduce8Yn("N")
                .build();
        d.setSid(sid);
        return d;
    }

    @Test
    @DisplayName("book key: WI: + 17 hex, 20 chars, stable per (owner, MST), different per owner")
    void scopeKey() {
        assertThat(SCOPE.key()).startsWith("WI:").hasSize(20);
        assertThat(VatScope.weinvoice(OWNER, MST, "x").key()).isEqualTo(SCOPE.key());
        assertThat(VatScope.weinvoice("other@x.vn", MST, "x").key()).isNotEqualTo(SCOPE.key());
        assertThat(VatScope.weinvoice(OWNER, "0300000000", "x").key()).isNotEqualTo(SCOPE.key());
        assertThat(VatScope.isWeInvoiceKey(SCOPE.key())).isTrue();
        assertThat(VatScope.isWeInvoiceKey("VN_VNM0004_000001")).isFalse();
    }

    @Test
    @DisplayName("create: keyed by the book, MST + name from the scope, figures from the engine invoices")
    void createUsesEngineInvoices() {
        when(declRepo.periodTypesInYear(SCOPE.key(), 2026)).thenReturn(List.of());
        when(declRepo.existsByBizKeyAndPeriodTypeAndPeriodValueAndPeriodYearAndSupplNoAndDelYn(
                SCOPE.key(), "month", 10, 2026, 0, "N")).thenReturn(false);
        when(declRepo.findTopByBizKeyAndPeriodTypeAndPeriodValueAndPeriodYearAndDelYnOrderBySupplNoDesc(
                anyString(), anyString(), anyInt(), anyInt(), anyString())).thenReturn(Optional.empty());
        when(declRepo.existsEarlierPeriod(SCOPE.key(), "month", 2026, 10)).thenReturn(false);
        when(declRepo.findPrecedingByDate(eq(SCOPE.key()), any(), any())).thenReturn(List.of());
        when(declRepo.save(any())).thenAnswer(inv -> {
            VatDeclaration d = inv.getArgument(0);
            d.setSid(77L);
            return d;
        });
        when(engineInvoices.syncBuckets(any(), any(), any())).thenReturn(List.of(
                bucket("I", "8%", "2200000", "176000"),
                bucket("I", "KCT", "368680645", "0"),
                bucket("B", "10%", "58288851", "5828885")));
        when(engineInvoices.salesVatByRate(any(), any())).thenReturn(List.of(bucket("I", "8%", "0", "176000")));

        VatDeclarationDetail detail = service.create(SCOPE, new VatDeclarationCreateRequest(
                "M", 10, 2026, "00", null, null, null, null, null, null, null, null));

        ArgumentCaptor<VatDeclaration> saved = ArgumentCaptor.forClass(VatDeclaration.class);
        verify(declRepo).save(saved.capture());
        assertThat(saved.getValue().getBizKey()).isEqualTo(SCOPE.key());
        assertThat(saved.getValue().getTpTaxCode()).isEqualTo(MST);
        assertThat(saved.getValue().getTpName()).isEqualTo("CÔNG TY TNHH JK YOUTH MK");
        assertThat(detail).isNotNull();

        ArgumentCaptor<VatDeclarationIndicator> ind = ArgumentCaptor.forClass(VatDeclarationIndicator.class);
        verify(indicatorRepo).save(ind.capture());
        assertThat(ind.getValue().getSalesAmt10()).isEqualByComparingTo("2200000");   // [32] 8% + 10% base
        assertThat(ind.getValue().getSalesAmtVat10()).isEqualByComparingTo("176000"); // [33] real output VAT
        assertThat(ind.getValue().getSalesAmtNondecl()).isEqualByComparingTo("368680645"); // [26] KCT
        assertThat(ind.getValue().getPurchAmt()).isEqualByComparingTo("58288851");     // [23]
        assertThat(ind.getValue().getPurchAmtVat()).isEqualByComparingTo("5828885");   // [24]

        verifyNoInteractions(busiInfmRepo, annexInvoiceRepo);
    }

    @Test
    @DisplayName("lock / unlock of a book declaration go to the engine source, never to busa_doc_mstr")
    void lockUnlockUseEngineSource() {
        VatDeclaration d = bookDecl(77L, "DRAFT");
        VatDeclarationIndicator i = VatDeclarationIndicator.builder()
                .declaration(d).prevCredit(BigDecimal.ZERO).creditNext(BigDecimal.ZERO).delYn("N").build();
        when(declRepo.findBySidAndBizKeyAndDelYn(77L, SCOPE.key(), "N")).thenReturn(Optional.of(d));
        when(indicatorRepo.findByDeclaration_SidAndDeclaration_BizKeyAndDelYn(77L, SCOPE.key(), "N"))
                .thenReturn(Optional.of(i));
        when(declRepo.findTopByBizKeyAndPeriodTypeAndPeriodValueAndPeriodYearAndDelYnOrderBySupplNoDesc(
                anyString(), anyString(), anyInt(), anyInt(), anyString())).thenReturn(Optional.empty());

        service.lock(SCOPE, 77L);
        verify(engineInvoices).markProcessed(eq(d), any(), any());

        service.unlock(SCOPE, 77L);
        verify(engineInvoices).clearProcessed(d);

        verify(annexInvoiceRepo, never()).markProcessed(anyString(), anyString(), anyString(), any(), any(), anyLong());
        verify(annexInvoiceRepo, never()).clearProcessed(anyString(), anyLong());
    }

    @Test
    @DisplayName("another user's book (other key) is not found")
    void otherOwnerCannotLoad() {
        VatScope other = VatScope.weinvoice("other@x.vn", MST, "x");
        when(declRepo.findBySidAndBizKeyAndDelYn(77L, other.key(), "N")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(other, 77L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE_VAT_DECL_NOT_FOUND");
    }
}
