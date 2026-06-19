package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.ExchangeRateListResponse;
import com.kosign.wecms.domain.banking.entity.BankExchRateInfm;
import com.kosign.wecms.domain.banking.repository.BankExchRateInfmRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExchangeRateService} — the Exchange Rate Inquiry read
 * path. Focus is the H-1 / H-2 / M-1 parity fixes:
 * <ul>
 *   <li>reads are tenant-scoped on {@code biz_key = TenantContext.currentBizKey()},</li>
 *   <li>a tenant with no rows gets an empty result (never another company's rates),</li>
 *   <li>an explicit {@code seqNo} routes to the exact-round query, omitted seqNo to latest,</li>
 *   <li>the rounds dropdown query is tenant-scoped too.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    private static final String BIZ      = "VN_VNM0004_000001";
    private static final String OTHER_BIZ = "VN_VNM0004_000008";
    private static final String SHINHAN  = "11704088";
    private static final String DATE     = "20260619";

    @Mock private BankExchRateInfmRepository exchRateRepo;
    @Mock private BankCdResolver bankCdResolver;
    @Mock private ExchangeRateBatchService exchangeRateBatchService;

    @InjectMocks private ExchangeRateService service;

    @BeforeEach
    void setUp() {
        TenantContext.set("tester", BIZ, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static BankExchRateInfm row(String refCcyCd, String seqNo, String buy) {
        BankExchRateInfm e = new BankExchRateInfm();
        e.setBizKey(BIZ);
        e.setBankCd(SHINHAN);
        e.setRefDate(DATE);
        e.setCcyCd("VND");
        e.setRefCcyCd(refCcyCd);
        e.setSeqNo(seqNo);
        e.setBuyAmt(new BigDecimal(buy));
        e.setLastUpdateDate(DATE);
        e.setLastUpdateTime("083335");
        return e;
    }

    @Test
    void list_scopesReadToCurrentTenant_andLatestRoundWhenSeqOmitted() {
        when(bankCdResolver.resolveBankNm(SHINHAN)).thenReturn("Shinhan Bank");
        when(exchRateRepo.findLatestPerCurrencyByBizKeyAndBankCdAndRefDate(eq(BIZ), eq(SHINHAN), eq(DATE)))
                .thenReturn(List.of(row("USD", "2", "26115")));
        when(exchRateRepo.findDistinctRoundsByBizKeyAndBankCdAndRefDate(eq(BIZ), eq(SHINHAN), eq(DATE)))
                .thenReturn(List.of());

        ExchangeRateListResponse resp = service.list(DATE, SHINHAN, null, null, null);

        // tenant bizKey is forwarded to the rate query (H-1)
        ArgumentCaptor<String> bizCaptor = ArgumentCaptor.forClass(String.class);
        verify(exchRateRepo).findLatestPerCurrencyByBizKeyAndBankCdAndRefDate(
                bizCaptor.capture(), eq(SHINHAN), eq(DATE));
        assertThat(bizCaptor.getValue()).isEqualTo(BIZ).isNotEqualTo(OTHER_BIZ);

        // omitted seqNo → latest-round query, never the exact-round variant
        verify(exchRateRepo, never())
                .findLatestPerCurrencyByBizKeyAndBankCdAndRefDateAndSeqNo(any(), any(), any(), any());

        // rounds dropdown query is tenant-scoped too
        verify(exchRateRepo).findDistinctRoundsByBizKeyAndBankCdAndRefDate(eq(BIZ), eq(SHINHAN), eq(DATE));

        assertThat(resp.rates()).hasSize(1);
        assertThat(resp.rates().get(0).refCcyCd()).isEqualTo("USD");
    }

    @Test
    void list_withExplicitSeqNo_usesExactRoundQuery() {
        when(bankCdResolver.resolveBankNm(SHINHAN)).thenReturn("Shinhan Bank");
        when(exchRateRepo.findLatestPerCurrencyByBizKeyAndBankCdAndRefDateAndSeqNo(
                eq(BIZ), eq(SHINHAN), eq(DATE), eq("1")))
                .thenReturn(List.of(row("USD", "1", "26100")));
        when(exchRateRepo.findDistinctRoundsByBizKeyAndBankCdAndRefDate(eq(BIZ), eq(SHINHAN), eq(DATE)))
                .thenReturn(List.of());

        ExchangeRateListResponse resp = service.list(DATE, SHINHAN, "1", null, null);

        verify(exchRateRepo).findLatestPerCurrencyByBizKeyAndBankCdAndRefDateAndSeqNo(
                eq(BIZ), eq(SHINHAN), eq(DATE), eq("1"));
        verify(exchRateRepo, never())
                .findLatestPerCurrencyByBizKeyAndBankCdAndRefDate(any(), any(), any());
        assertThat(resp.rates()).hasSize(1);
    }

    @Test
    void list_tenantWithNoRows_returnsEmpty_noBatch_noCrossTenantFallback() {
        when(bankCdResolver.resolveBankNm(SHINHAN)).thenReturn("Shinhan Bank");
        when(exchRateRepo.findLatestPerCurrencyByBizKeyAndBankCdAndRefDate(eq(BIZ), eq(SHINHAN), eq(DATE)))
                .thenReturn(List.of());
        when(exchRateRepo.findDistinctRoundsByBizKeyAndBankCdAndRefDate(eq(BIZ), eq(SHINHAN), eq(DATE)))
                .thenReturn(List.of());

        // refDate explicitly supplied → the on-demand batch path (refDate==null) must NOT run
        ExchangeRateListResponse resp = service.list(DATE, SHINHAN, null, null, null);

        assertThat(resp.rates()).isEmpty();
        verify(exchangeRateBatchService, never()).runOnDemand();
        verify(exchRateRepo, never()).findLatestRefDateByBizKeyAndBankCd(any(), any());
    }
}
