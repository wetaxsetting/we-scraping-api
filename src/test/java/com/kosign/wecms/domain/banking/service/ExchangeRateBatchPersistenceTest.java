package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.banking.entity.BankExchRateInfm;
import com.kosign.wecms.domain.banking.nxisas.NxisasGatewayClient;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasApiOut;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasForeignExchangeEntry;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasForeignExchangeJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasOutput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasResult;
import com.kosign.wecms.domain.banking.repository.BankExchRateInfmRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Write-path correctness for {@link ExchangeRateBatchService} — proves the
 * BLOCKER fix: the upsert/skip lookup discriminates on {@code ref_ccy_cd} (the
 * foreign currency) and not just {@code ccy_cd} (always {@code 'VND'} for
 * Shinhan/Woori VN). Before the fix all 12/5 currencies of a round collapsed
 * onto one logical row; these tests assert every currency persists as its own
 * row, with {@code exch_seq_no} allocated once per inserted row.
 */
@ExtendWith(MockitoExtension.class)
class ExchangeRateBatchPersistenceTest {

    private static final String BIZ      = "VN_VNM0004_000001";
    private static final String SHINHAN  = "11704088";
    private static final String WOORI    = "11704020";

    /** Legacy Shinhan currency set (12), in the order Coocon returns them. */
    private static final List<String> SHINHAN_12 = List.of(
            "USD", "KRW", "JPY", "EUR", "AUD", "GBP",
            "SGD", "HKD", "CAD", "CHF", "THB", "NZD");

    /** Live Woori currency set (5). */
    private static final List<String> WOORI_5 = List.of("USD", "EUR", "KRW", "JPY", "GBP");

    @Mock private BusiInfmRepository busiInfmRepository;
    @Mock private BankExchRateInfmRepository bankExchRateInfmRepository;
    @Mock private NxisasGatewayClient gateway;
    @Mock private LegacyFunctionRepository legacyFunctions;

    @InjectMocks private ExchangeRateBatchService service;

    // ── fixtures ────────────────────────────────────────────────────────────

    private static BusiInfm tenant() {
        BusiInfm t = new BusiInfm();
        t.setBizKey(BIZ);
        return t;
    }

    /** One successful Coocon FX response carrying {@code baseCurrencies} as
     *  ref_ccy_cd and a constant {@code counterCurrency = 'VND'}. */
    private static NxisasApiOut<NxisasForeignExchangeJobResult> apiOut(List<String> baseCurrencies) {
        List<NxisasForeignExchangeEntry> entries = new ArrayList<>();
        for (String base : baseCurrencies) {
            NxisasForeignExchangeEntry e = new NxisasForeignExchangeEntry();
            e.setBaseCurrency(base);        // ref_ccy_cd  (USD, KRW, …)
            e.setCounterCurrency("VND");    // ccy_cd      (constant)
            e.setBuy("1"); e.setSell("2");
            e.setTransferBuy("3"); e.setTransferSell("4");
            e.setBasicRate("5"); e.setBasicTransfer("6");
            e.setBaseDate("20260619"); e.setBaseTime("083335");
            entries.add(e);
        }
        NxisasForeignExchangeJobResult result = new NxisasForeignExchangeJobResult();
        result.setStandPoint("SP");
        result.setForeignExchange(entries);
        NxisasOutput<NxisasForeignExchangeJobResult> out = new NxisasOutput<>();
        out.setErrorCode("00000000");   // OK_CODE
        out.setResult(result);
        NxisasResult<NxisasForeignExchangeJobResult> res = new NxisasResult<>();
        res.setOutput(out);
        NxisasApiOut<NxisasForeignExchangeJobResult> apiOut = new NxisasApiOut<>();
        apiOut.setResultList(List.of(res));
        return apiOut;
    }

    private void stubTenantAndGateway(List<String> currencies) {
        when(busiInfmRepository.findByDelYnAndBusiSttsAndPrdtTypeIn(eq("N"), eq("N"), anyCollection()))
                .thenReturn(List.of(tenant()));
        when(gateway.newTrSeq()).thenReturn("trseq");
        when(gateway.foreignExchange(anyString(), anyString(), anyString(), any()))
                .thenReturn(apiOut(currencies));
    }

    // ── tests ───────────────────────────────────────────────────────────────

    @Test
    void shinhanFirstTime_insertsTwelveDistinctRows_notOne() {
        stubTenantAndGateway(SHINHAN_12);
        // Nothing exists yet → every currency is an INSERT.
        when(bankExchRateInfmRepository.findByBizKeyAndBankCdAndRefDateAndRefCcyCdAndCcyCdAndSeqNo(
                any(), any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        // Funnel emits a continuous counter (mirrors wb_get_next_val MAX+1 per
        // biz_key+bank_cd+ref_date+ccy_cd): 1, 2, 3, … so seq_no=1 → exch_seq_no 1..12.
        AtomicInteger seq = new AtomicInteger(0);
        when(legacyFunctions.nextBankExchRateExchSeqNo(any(), any(), any(), any()))
                .thenAnswer(inv -> String.valueOf(seq.incrementAndGet()));

        service.runShinhanFirstTime();   // UPSERT, seq_no = "1"

        ArgumentCaptor<BankExchRateInfm> cap = ArgumentCaptor.forClass(BankExchRateInfm.class);
        verify(bankExchRateInfmRepository, times(12)).save(cap.capture());

        List<BankExchRateInfm> saved = cap.getAllValues();
        assertThat(saved).hasSize(12);
        assertThat(saved).extracting(BankExchRateInfm::getRefCcyCd)
                .containsExactlyInAnyOrderElementsOf(SHINHAN_12);   // 12 distinct foreign currencies
        assertThat(saved).extracting(BankExchRateInfm::getCcyCd)
                .containsOnly("VND");                                // ccy_cd constant
        assertThat(saved).extracting(BankExchRateInfm::getSeqNo)
                .containsOnly("1");                                  // round 1
        // exch_seq_no is allocated once per inserted row, continuous 1..12 in
        // insertion (currency) order — never reused / collapsed.
        assertThat(saved).extracting(BankExchRateInfm::getExchSeqNo)
                .containsExactly("1","2","3","4","5","6","7","8","9","10","11","12");
        verify(legacyFunctions, times(12))
                .nextBankExchRateExchSeqNo(eq(BIZ), eq(SHINHAN), any(), eq("VND"));
        // Lookup discriminates by ref_ccy_cd — 12 distinct values probed.
        ArgumentCaptor<String> refCcyCap = ArgumentCaptor.forClass(String.class);
        verify(bankExchRateInfmRepository, times(12))
                .findByBizKeyAndBankCdAndRefDateAndRefCcyCdAndCcyCdAndSeqNo(
                        eq(BIZ), eq(SHINHAN), any(), refCcyCap.capture(), eq("VND"), eq("1"));
        assertThat(refCcyCap.getAllValues()).containsExactlyInAnyOrderElementsOf(SHINHAN_12);
    }

    @Test
    void shinhanFirstTime_rerun_updatesMatchingRefCcyCd_notSibling_noNewExchSeqNo() {
        stubTenantAndGateway(SHINHAN_12);
        // Re-run: each currency already exists → UPSERT updates the row whose
        // ref_ccy_cd matches the looked-up value (never a sibling currency).
        when(bankExchRateInfmRepository.findByBizKeyAndBankCdAndRefDateAndRefCcyCdAndCcyCdAndSeqNo(
                any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    BankExchRateInfm existing = new BankExchRateInfm();
                    existing.setBizKey(inv.getArgument(0));
                    existing.setBankCd(inv.getArgument(1));
                    existing.setRefDate(inv.getArgument(2));
                    existing.setRefCcyCd(inv.getArgument(3));   // the matched currency
                    existing.setCcyCd(inv.getArgument(4));
                    existing.setSeqNo(inv.getArgument(5));
                    existing.setExchSeqNo("99");                // pre-existing PK — must be preserved
                    return Optional.of(existing);
                });

        service.runShinhanFirstTime();

        ArgumentCaptor<BankExchRateInfm> cap = ArgumentCaptor.forClass(BankExchRateInfm.class);
        verify(bankExchRateInfmRepository, times(12)).save(cap.capture());
        List<BankExchRateInfm> saved = cap.getAllValues();
        // Updated rows keep their identity (no sibling overwrite): 12 distinct ref_ccy_cd,
        // each retaining its original exch_seq_no, ccy_cd still VND.
        assertThat(saved).extracting(BankExchRateInfm::getRefCcyCd)
                .containsExactlyInAnyOrderElementsOf(SHINHAN_12);
        assertThat(saved).extracting(BankExchRateInfm::getExchSeqNo).containsOnly("99");
        assertThat(saved).extracting(BankExchRateInfm::getCcyCd).containsOnly("VND");
        // No INSERT path → no new exch_seq_no allocation.
        verify(legacyFunctions, never()).nextBankExchRateExchSeqNo(any(), any(), any(), any());
    }

    @Test
    void woori_insertNew_insertsFiveDistinctRows() {
        stubTenantAndGateway(WOORI_5);
        // No prior Woori round today → MAX+1 = 1.
        when(bankExchRateInfmRepository.findHighestSeqNo(eq(WOORI), any(), eq("VND")))
                .thenReturn(Optional.empty());
        when(bankExchRateInfmRepository.findByBizKeyAndBankCdAndRefDateAndRefCcyCdAndCcyCdAndSeqNo(
                any(), any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        when(legacyFunctions.nextBankExchRateExchSeqNo(any(), any(), any(), any())).thenReturn("X");

        service.runWoori();   // INSERT_NEW, seq_no = "1"

        ArgumentCaptor<BankExchRateInfm> cap = ArgumentCaptor.forClass(BankExchRateInfm.class);
        verify(bankExchRateInfmRepository, times(5)).save(cap.capture());
        List<BankExchRateInfm> saved = cap.getAllValues();
        assertThat(saved).hasSize(5);
        assertThat(saved).extracting(BankExchRateInfm::getRefCcyCd)
                .containsExactlyInAnyOrderElementsOf(WOORI_5);
        assertThat(saved).extracting(BankExchRateInfm::getCcyCd).containsOnly("VND");
        assertThat(saved).extracting(BankExchRateInfm::getSeqNo).containsOnly("1");
        verify(legacyFunctions, times(5)).nextBankExchRateExchSeqNo(eq(BIZ), eq(WOORI), any(), eq("VND"));
    }
}
