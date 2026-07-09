package com.kosign.wecms.domain.payment.haengsung.exchangerate;

import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.banking.entity.BankExchRateInfm;
import com.kosign.wecms.domain.banking.repository.BankExchRateInfmRepository;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HaengsungExchangeRateSyncServiceTest {

    private static final String BIZ = "VN_VNM0004_000014";
    private static final String TAX_ID = "0312345678";

    @Mock BusiInfmRepository busiInfmRepository;
    @Mock BankExchRateInfmRepository bankExchRateInfmRepository;
    @Mock HaengsungFcyExRateOracleRepository fcyExRateOracle;
    @Mock ErpTrnsHistoryWriter auditWriter;

    @InjectMocks HaengsungExchangeRateSyncService service;

    /* ============================ pure mapping ============================ */

    @Test void map_isVerbatimLegacy_ccyCdIsRefCcyCd_andBankCdIsCitad() {
        BankExchRateInfm r = rate("USD", "KRW", "1");
        r.setBuyAmt(new BigDecimal("1000"));
        r.setSellAmt(new BigDecimal("1010"));
        r.setBasicRate(new BigDecimal("1005"));
        r.setLastUpdateDate("20260628");
        r.setLastUpdateTime("093000");

        OracleFcyExRate m = HaengsungExchangeRateSyncService.map(TAX_ID, "01663001", r, "20260628094500");

        assertThat(m.bizNo()).isEqualTo(TAX_ID);
        assertThat(m.bankCd()).isEqualTo("01663001");          // CITAD code, not the original
        assertThat(m.ccyCd()).isEqualTo("KRW");                 // legacy maps CCY_CD = REF_CCY_CD (base)
        assertThat(m.refDt()).isEqualTo("20260628");            // LAST_UPDATE_DATE
        assertThat(m.refTm()).isEqualTo("093000");              // LAST_UPDATE_TIME
        assertThat(m.refSeq()).isEqualByComparingTo(new BigDecimal("1"));
        assertThat(m.lastInqDttm()).isEqualTo("20260628093000"); // date + time
        assertThat(m.cmsPutDttm()).isEqualTo("20260628094500");  // now(VN)
        assertThat(m.buyAmt()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(m.sellAmt()).isEqualByComparingTo(new BigDecimal("1010"));
        assertThat(m.basicRate()).isEqualByComparingTo(new BigDecimal("1005"));
    }

    /* ============================ insert vs update (legacy ERP_SND_YN) ============================ */

    @Test void newRow_erpSndN_inserts_thenFlagsSent() {
        haengsungBusinesses(busi(BIZ, TAX_ID));
        BankExchRateInfm r = rate("USD", "KRW", "1");
        r.setErpSndYn("N");
        when(bankExchRateInfmRepository.findByBizKeyAndRefDate(eq(BIZ), anyString()))
                .thenReturn(List.of(r));

        HaengsungExchangeRateSyncService.Summary s = service.sync();

        verify(fcyExRateOracle).insert(any(OracleFcyExRate.class));
        verify(fcyExRateOracle, never()).update(any());
        // WeCMS send-flag flipped by the row's natural PK (original bank code 11704020)
        verify(bankExchRateInfmRepository).markErpSent(
                eq(BIZ), eq("11704020"), anyString(), eq("USD"), eq("E1"), anyString(), anyString());
        assertThat(s.processed()).isEqualTo(1);
    }

    @Test void alreadySent_erpSndY_updates_notInsert() {
        haengsungBusinesses(busi(BIZ, TAX_ID));
        BankExchRateInfm r = rate("USD", "KRW", "2");
        r.setErpSndYn("Y");
        when(bankExchRateInfmRepository.findByBizKeyAndRefDate(eq(BIZ), anyString()))
                .thenReturn(List.of(r));

        service.sync();

        verify(fcyExRateOracle).update(any(OracleFcyExRate.class));
        verify(fcyExRateOracle, never()).insert(any());
        verify(bankExchRateInfmRepository).markErpSent(
                eq(BIZ), eq("11704020"), anyString(), eq("USD"), eq("E1"), anyString(), anyString());
    }

    /* ============================ resilience: one row fails, continue ============================ */

    @Test void oracleFailureOnOneRow_continuesNext_andDoesNotFlagFailedRow() {
        haengsungBusinesses(busi(BIZ, TAX_ID));
        BankExchRateInfm bad = rate("USD", "KRW", "1");
        bad.setErpSndYn("N");
        BankExchRateInfm good = rate("EUR", "KRW", "1");
        good.setExchSeqNo("E2");
        good.setErpSndYn("N");
        when(bankExchRateInfmRepository.findByBizKeyAndRefDate(eq(BIZ), anyString()))
                .thenReturn(List.of(bad, good));
        // first insert throws, second succeeds (Mockito returns 0 for the 2nd by default after the throw is consumed)
        doThrow(new RuntimeException("ORA-12541")).doReturn(1)
                .when(fcyExRateOracle).insert(any(OracleFcyExRate.class));

        HaengsungExchangeRateSyncService.Summary s = service.sync();

        // bad row: WeCMS NOT flagged; good row: flagged
        verify(bankExchRateInfmRepository, never()).markErpSent(
                eq(BIZ), eq("11704020"), anyString(), eq("USD"), anyString(), anyString(), anyString());
        verify(bankExchRateInfmRepository).markErpSent(
                eq(BIZ), eq("11704020"), anyString(), eq("EUR"), eq("E2"), anyString(), anyString());
        assertThat(s.processed()).isEqualTo(1);
        assertThat(s.failed()).isEqualTo(1);
    }

    /* ============================ unmapped bank skipped ============================ */

    @Test void unmappedBankCode_skipped_noOracleNoFlag() {
        haengsungBusinesses(busi(BIZ, TAX_ID));
        BankExchRateInfm r = rate("USD", "KRW", "1");
        r.setBankCd("99999999"); // not in the CITAD map
        when(bankExchRateInfmRepository.findByBizKeyAndRefDate(eq(BIZ), anyString()))
                .thenReturn(List.of(r));

        HaengsungExchangeRateSyncService.Summary s = service.sync();

        verifyNoInteractions(fcyExRateOracle);
        verify(bankExchRateInfmRepository, never()).markErpSent(any(), any(), any(), any(), any(), any(), any());
        assertThat(s.skipped()).isEqualTo(1);
    }

    /* ============================ audit row (even on 0 records) ============================ */

    @Test void auditRowWritten_perBusiness_evenWithZeroRates() {
        haengsungBusinesses(busi(BIZ, TAX_ID));
        when(bankExchRateInfmRepository.findByBizKeyAndRefDate(eq(BIZ), anyString()))
                .thenReturn(List.of());

        service.sync();

        // ERP_TYPE handled inside writer; TRNS_TYPE_CD '15' = Exchange Rate
        verify(auditWriter).recordBatchPush(eq(BIZ), eq("15"), anyString());
        verifyNoInteractions(fcyExRateOracle);
    }

    @Test void secondBankMapped_11704088_to_79616001() {
        haengsungBusinesses(busi(BIZ, TAX_ID));
        BankExchRateInfm r = rate("USD", "KRW", "1");
        r.setBankCd("11704088");
        r.setErpSndYn("N");
        when(bankExchRateInfmRepository.findByBizKeyAndRefDate(eq(BIZ), anyString()))
                .thenReturn(List.of(r));

        service.sync();

        ArgumentCaptor<OracleFcyExRate> captor = ArgumentCaptor.forClass(OracleFcyExRate.class);
        verify(fcyExRateOracle).insert(captor.capture());
        assertThat(captor.getValue().bankCd()).isEqualTo("79616001");
    }

    /* ============================ no payment tables touched / tax-id guard ============================ */

    @Test void businessWithoutTaxId_skipped() {
        haengsungBusinesses(busi(BIZ, null));

        HaengsungExchangeRateSyncService.Summary s = service.sync();

        verifyNoInteractions(fcyExRateOracle);
        verifyNoInteractions(bankExchRateInfmRepository);
        assertThat(s.failedBusinesses()).isEqualTo(1);
    }

    @Test void usesHaengsungBusinessFanout_prdtTypeH_delYnN() {
        when(busiInfmRepository.findByPrdtTypeAndDelYn("H", "N")).thenReturn(List.of());

        service.sync();

        verify(busiInfmRepository).findByPrdtTypeAndDelYn("H", "N");
        verifyNoInteractions(fcyExRateOracle);
    }

    /* ================================== fixtures ================================== */

    private void haengsungBusinesses(BusiInfm... rows) {
        when(busiInfmRepository.findByPrdtTypeAndDelYn("H", "N")).thenReturn(List.of(rows));
        // audit writer is invoked per business; keep stubs lenient where a test
        // path returns before reaching some interactions.
        lenient().doNothing().when(auditWriter).recordBatchPush(anyString(), anyString(), anyString());
    }

    private static BusiInfm busi(String bizKey, String taxId) {
        BusiInfm b = new BusiInfm();
        b.setBizKey(bizKey);
        b.setPrdtType("H");
        b.setDelYn("N");
        b.setTaxId(taxId);
        return b;
    }

    private static BankExchRateInfm rate(String ccyCd, String refCcyCd, String seqNo) {
        BankExchRateInfm r = new BankExchRateInfm();
        r.setBizKey(BIZ);
        r.setBankCd("11704020");          // WeCMS-stored ORIGINAL code → CITAD 01663001
        r.setRefDate("20260628");
        r.setCcyCd(ccyCd);                // counter currency (PK + send-flag WHERE)
        r.setRefCcyCd(refCcyCd);          // base currency → Oracle CCY_CD (legacy)
        r.setExchSeqNo("E1");
        r.setSeqNo(seqNo);                // round number → Oracle REF_SEQ
        r.setLastUpdateDate("20260628");
        r.setLastUpdateTime("093000");
        r.setBuyAmt(new BigDecimal("1000"));
        r.setSellAmt(new BigDecimal("1010"));
        r.setTranSellAmt(new BigDecimal("1011"));
        r.setTranBuyAmt(new BigDecimal("999"));
        r.setBasicRate(new BigDecimal("1005"));
        r.setErpSndYn("N");
        return r;
    }
}
