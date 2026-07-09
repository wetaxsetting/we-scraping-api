package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.DailyCashReportResponse;
import com.kosign.wecms.domain.banking.repository.DailyCashRec1Row;
import com.kosign.wecms.domain.banking.repository.DailyCashRec2Row;
import com.kosign.wecms.domain.banking.repository.DailyCashRec3Row;
import com.kosign.wecms.domain.banking.repository.DailyCashRec4Row;
import com.kosign.wecms.domain.banking.repository.DailyCashReportFunctionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Daily Cash now reads the legacy DB functions {@code rept_0900_001_rec1..rec4}
 * directly ({@link DailyCashReportFunctionRepository}). These tests lock the
 * mapping from each function's output onto the wire shape and the
 * apply-outside currency filter — the account-type bucketing itself lives in
 * {@code rec2} (DB), so it is no longer re-asserted in Java.
 *
 * <ul>
 *   <li>service calls rec1..rec4 with reqDate2 == reqDate</li>
 *   <li>currency filter ALL / exact / FCY → ccyExact / ccyExclude args</li>
 *   <li>rec2 buckets + rec4 cash → currency summary</li>
 *   <li>rec3 opening / closing balance → currency flow</li>
 *   <li>empty rec3 / rec4 → safe zero / empty response</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DailyCashReportServiceTest {

    @Mock DailyCashReportFunctionRepository fnRepo;
    @Mock AuditLogService                   auditLogService;

    DailyCashReportService service;

    private static final String BIZ      = "VN_VNM0004_000001";
    private static final String USER     = "tester";
    private static final String DATE     = "2026-06-04";
    private static final String REF_DATE = "20260604";

    @BeforeEach
    void setUp() {
        service = new DailyCashReportService(fnRepo, auditLogService);
        TenantContext.set(USER, BIZ, LangCd.EN);
        // Default: every function returns nothing unless a test overrides it.
        lenient().when(fnRepo.rec1(any(), any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(fnRepo.rec2(any(), any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(fnRepo.rec3(any(), any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(fnRepo.rec4(any(), any(), any(), any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void callsAllFourFunctions_withReqDate2EqualToReqDate_andAllFilterPassesNullCcyArgs() {
        service.search(DATE, DailyCashCurrencyFilter.ALL);

        // rec1..rec4 each invoked once with (bizKey, refDate, refDate, null, null)
        verify(fnRepo).rec1(eq(BIZ), eq(REF_DATE), eq(REF_DATE), isNull(), isNull());
        verify(fnRepo).rec2(eq(BIZ), eq(REF_DATE), eq(REF_DATE), isNull(), isNull());
        verify(fnRepo).rec3(eq(BIZ), eq(REF_DATE), eq(REF_DATE), isNull(), isNull());
        verify(fnRepo).rec4(eq(BIZ), eq(REF_DATE), eq(REF_DATE), isNull(), isNull());
    }

    @Test
    void exactCurrencyFilter_pinsCcyExact_noExclude() {
        service.search(DATE, DailyCashCurrencyFilter.USD);

        ArgumentCaptor<String> exact = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> exclude = ArgumentCaptor.forClass(String.class);
        verify(fnRepo).rec1(eq(BIZ), eq(REF_DATE), eq(REF_DATE), exact.capture(), exclude.capture());
        assertThat(exact.getValue()).isEqualTo("USD");
        assertThat(exclude.getValue()).isNull();
    }

    @Test
    void fcyFilter_excludesLocalCurrency_noExact() {
        service.search(DATE, DailyCashCurrencyFilter.FCY);

        ArgumentCaptor<String> exact = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> exclude = ArgumentCaptor.forClass(String.class);
        verify(fnRepo).rec2(eq(BIZ), eq(REF_DATE), eq(REF_DATE), exact.capture(), exclude.capture());
        assertThat(exact.getValue()).isNull();
        assertThat(exclude.getValue()).isEqualTo("VND"); // local currency excluded
    }

    @Test
    void rec2BucketsAndRec4Cash_mapToCurrencySummary() {
        when(fnRepo.rec2(any(), any(), any(), any(), any())).thenReturn(List.of(
                rec2("VND", "700", "1100", "200", "100"))); // norm, sav, acct(term), loan
        when(fnRepo.rec4(any(), any(), any(), any(), any())).thenReturn(List.of(
                rec4("VND", "5000")));

        DailyCashReportResponse res = service.search(DATE, DailyCashCurrencyFilter.ALL);

        assertThat(res.currencySummary()).hasSize(1);
        DailyCashReportResponse.CurrencySummary vnd = res.currencySummary().get(0);
        assertThat(vnd.currencyCd()).isEqualTo("VND");
        assertThat(vnd.totalDemandDeposit()).isEqualByComparingTo("700");  // totl_norm_amt (03/04)
        assertThat(vnd.totalSavings())      .isEqualByComparingTo("1100"); // totl_sav_amt (05/06)
        assertThat(vnd.totalTermDeposit())  .isEqualByComparingTo("200");  // totl_acct_amt (02)
        assertThat(vnd.totalLoan())         .isEqualByComparingTo("100");  // totl_loan_amt (01)
        assertThat(vnd.totalCash())         .isEqualByComparingTo("5000"); // rec4 totl_cash
    }

    @Test
    void rec4CashOnlyCurrency_appearsInSummary_withZeroBuckets() {
        // KHR has cash but no bank accounts (no rec2 row) — its cash must not be lost.
        when(fnRepo.rec2(any(), any(), any(), any(), any())).thenReturn(List.of(
                rec2("VND", "700", "0", "0", "0")));
        when(fnRepo.rec4(any(), any(), any(), any(), any())).thenReturn(List.of(
                rec4("VND", "10"), rec4("KHR", "999")));

        DailyCashReportResponse res = service.search(DATE, DailyCashCurrencyFilter.ALL);

        assertThat(res.currencySummary()).hasSize(2);
        DailyCashReportResponse.CurrencySummary khr = res.currencySummary().stream()
                .filter(s -> s.currencyCd().equals("KHR")).findFirst().orElseThrow();
        assertThat(khr.totalCash()).isEqualByComparingTo("999");
        assertThat(khr.totalDemandDeposit()).isEqualByComparingTo("0");
        assertThat(khr.totalSavings()).isEqualByComparingTo("0");
    }

    @Test
    void rec3OpeningAndClosingBalance_mapToCurrencyFlow() {
        when(fnRepo.rec3(any(), any(), any(), any(), any())).thenReturn(List.of(
                rec3("VND", "1000", "1500", "2", "300", "3", "800")));

        DailyCashReportResponse res = service.search(DATE, DailyCashCurrencyFilter.ALL);

        assertThat(res.currencyFlow()).hasSize(1);
        DailyCashReportResponse.CurrencyFlow f = res.currencyFlow().get(0);
        assertThat(f.currencyCd()).isEqualTo("VND");
        assertThat(f.beforeDateBalance()).isEqualByComparingTo("1000"); // opening (bef_date_bal)
        assertThat(f.balance())          .isEqualByComparingTo("1500"); // closing (bal)
        assertThat(f.sendCount())        .isEqualByComparingTo("2");
        assertThat(f.sendAmount())       .isEqualByComparingTo("300");
        assertThat(f.receiveCount())     .isEqualByComparingTo("3");
        assertThat(f.receiveAmount())    .isEqualByComparingTo("800");
    }

    @Test
    void rec1_mapsToAccountRows_bankNameFromFunction_noSnapshotTimestamp() {
        when(fnRepo.rec1(any(), any(), any(), any(), any())).thenReturn(List.of(
                rec1("03", "VND", "Shinhan VN", "700005285280", "12345"),
                rec1("05", "VND", "Shinhan VN", "700005285281", "67890")));

        List<DailyCashReportResponse.AccountRow> rows =
                service.search(DATE, DailyCashCurrencyFilter.ALL).accounts();

        assertThat(rows).extracting(DailyCashReportResponse.AccountRow::acctTypeCd)
                .containsExactly("03", "05");
        assertThat(rows.get(0).bankNm()).isEqualTo("Shinhan VN"); // from rec1 (wb_get_bank_nm)
        assertThat(rows.get(0).balance()).isEqualByComparingTo("12345");
        assertThat(rows.get(0).lastCollectedDate()).isNull(); // DB-function path carries no snapshot ts
    }

    @Test
    void emptyRec3AndRec4_returnsSafeZeroAndEmpty_noError() {
        // Accounts present, but cash functions empty (CASH_REPY_MSTR empty in R&D).
        when(fnRepo.rec1(any(), any(), any(), any(), any())).thenReturn(List.of(
                rec1("03", "VND", "Shinhan VN", "700005285280", "100")));
        when(fnRepo.rec2(any(), any(), any(), any(), any())).thenReturn(List.of(
                rec2("VND", "100", "0", "0", "0")));
        // rec3 + rec4 empty (default mocks)

        DailyCashReportResponse res = service.search(DATE, DailyCashCurrencyFilter.ALL);

        assertThat(res.accounts()).hasSize(1);
        assertThat(res.currencyFlow()).isEmpty();                 // no cash flow rows
        assertThat(res.currencySummary()).hasSize(1);
        assertThat(res.currencySummary().get(0).totalCash())      // cash safely zero
                .isEqualByComparingTo("0");
    }

    @Test
    void emptyEverything_returnsEmptyResponse_noError() {
        DailyCashReportResponse res = service.search(DATE, DailyCashCurrencyFilter.ALL);

        assertThat(res.accounts()).isEmpty();
        assertThat(res.currencySummary()).isEmpty();
        assertThat(res.currencyFlow()).isEmpty();
    }

    /* ------------------------------------------------------------------ */

    private static DailyCashRec1Row rec1(String acctTypeCd, String ccyCd, String bankNm,
                                         String acctNo, String bal) {
        return new DailyCashRec1Row() {
            @Override public String getBizKey()      { return BIZ; }
            @Override public String getRefDate()     { return REF_DATE; }
            @Override public String getCcyCd()       { return ccyCd; }
            @Override public String getAcctTypeCd()  { return acctTypeCd; }
            @Override public String getBankCd()      { return "11704088"; }
            @Override public String getBankNm()      { return bankNm; }
            @Override public String getAcctNo()      { return acctNo; }
            @Override public BigDecimal getBal()     { return new BigDecimal(bal); }
            @Override public String getLoanNm()      { return null; }
            @Override public String getExpiDt()      { return null; }
            @Override public BigDecimal getAgmtAmt() { return BigDecimal.ZERO; }
            @Override public BigDecimal getSndCnt()  { return BigDecimal.ZERO; }
            @Override public BigDecimal getSndAmt()  { return BigDecimal.ZERO; }
            @Override public BigDecimal getRcvCnt()  { return BigDecimal.ZERO; }
            @Override public BigDecimal getRcvAmt()  { return BigDecimal.ZERO; }
        };
    }

    private static DailyCashRec2Row rec2(String ccyCd, String norm, String sav,
                                         String acct, String loan) {
        return new DailyCashRec2Row() {
            @Override public String getCcyCd()           { return ccyCd; }
            @Override public BigDecimal getTotlNormAmt() { return new BigDecimal(norm); }
            @Override public BigDecimal getTotlSavAmt()  { return new BigDecimal(sav); }
            @Override public BigDecimal getTotlAcctAmt() { return new BigDecimal(acct); }
            @Override public BigDecimal getTotlLoanAmt() { return new BigDecimal(loan); }
        };
    }

    private static DailyCashRec3Row rec3(String ccyCd, String befBal, String bal,
                                         String sndCnt, String sndAmt, String rcvCnt, String rcvAmt) {
        return new DailyCashRec3Row() {
            @Override public String getCcyCd()          { return ccyCd; }
            @Override public BigDecimal getBefDateBal() { return new BigDecimal(befBal); }
            @Override public BigDecimal getBal()        { return new BigDecimal(bal); }
            @Override public BigDecimal getSndCnt()     { return new BigDecimal(sndCnt); }
            @Override public BigDecimal getSndAmt()     { return new BigDecimal(sndAmt); }
            @Override public BigDecimal getRcvCnt()     { return new BigDecimal(rcvCnt); }
            @Override public BigDecimal getRcvAmt()     { return new BigDecimal(rcvAmt); }
        };
    }

    private static DailyCashRec4Row rec4(String ccyCd, String totlCash) {
        return new DailyCashRec4Row() {
            @Override public String getCcyCd()         { return ccyCd; }
            @Override public BigDecimal getTotlCash()  { return new BigDecimal(totlCash); }
        };
    }
}
