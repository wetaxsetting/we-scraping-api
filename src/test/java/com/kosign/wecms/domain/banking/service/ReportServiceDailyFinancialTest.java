package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.DailyFinancialCmcReport;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.repository.DailyFinancialCmcFunctionRepository;
import com.kosign.wecms.domain.banking.repository.DailyFinancialCmcRec1Row;
import com.kosign.wecms.domain.banking.repository.DailyFinancialHsReportRepository;
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
 * Daily Financial CMC now reads the legacy DB function
 * {@code rept_0015_001_r001_rec1} via {@link DailyFinancialCmcFunctionRepository}
 * (the prior Java live-recompute is removed). These tests lock:
 *
 * <ul>
 *   <li>the service calls {@code rec1} with {@code reqDate2 == reqDate};</li>
 *   <li>currency scope ALL / VND / FCY → {@code ccyExact} / {@code ccyExclude} args;</li>
 *   <li>{@code activeAccountsOnly} → {@code actvFilter} arg (DF-3);</li>
 *   <li>07/08 (KRW current/savings) are kept in the current/savings totals;</li>
 *   <li>{@code includeInternalTransfers=false} zeroes the internal columns (DF-4);</li>
 *   <li>Financial Status = per-currency current + saving of the function's balances.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceDailyFinancialTest {

    @Mock AcctMstrRepository               acctRepo;
    @Mock AcctTrnsHstrRepository           trnsRepo;
    @Mock BankCdResolver                   bankResolver;
    @Mock DailyFinancialHsReportRepository hsReportRepo;
    @Mock DailyFinancialCmcFunctionRepository cmcFnRepo;

    ReportService service;

    private static final String BIZ  = "VN_VNM0004_000001";
    private static final String USER = "tester";
    private static final String ASOF = "20260604";

    @BeforeEach
    void setUp() {
        service = new ReportService(acctRepo, trnsRepo, bankResolver, hsReportRepo, cmcFnRepo);
        TenantContext.set(USER, BIZ, LangCd.EN);
        lenient().when(cmcFnRepo.rec1(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void callsRec1_withReqDate2EqualReqDate_allScope_activeOnly() {
        service.dailyFinancial(ASOF, "ALL", true, true);
        // ALL → no ccy filter; activeAccountsOnly=true → actvFilter "Y"
        verify(cmcFnRepo).rec1(eq(BIZ), eq(ASOF), eq(ASOF), isNull(), isNull(), eq("Y"));
    }

    @Test
    void vndScope_pinsCcyExact() {
        service.dailyFinancial(ASOF, "VND", true, true);
        ArgumentCaptor<String> exact = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> exclude = ArgumentCaptor.forClass(String.class);
        verify(cmcFnRepo).rec1(eq(BIZ), eq(ASOF), eq(ASOF), exact.capture(), exclude.capture(), eq("Y"));
        assertThat(exact.getValue()).isEqualTo("VND");
        assertThat(exclude.getValue()).isNull();
    }

    @Test
    void fcyScope_excludesVnd() {
        service.dailyFinancial(ASOF, "FCY", true, true);
        ArgumentCaptor<String> exact = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> exclude = ArgumentCaptor.forClass(String.class);
        verify(cmcFnRepo).rec1(eq(BIZ), eq(ASOF), eq(ASOF), exact.capture(), exclude.capture(), eq("Y"));
        assertThat(exact.getValue()).isNull();
        assertThat(exclude.getValue()).isEqualTo("VND");
    }

    @Test
    void activeAccountsOnlyFalse_passesNullActvFilter() { // DF-3
        service.dailyFinancial(ASOF, "ALL", false, true);
        verify(cmcFnRepo).rec1(eq(BIZ), eq(ASOF), eq(ASOF), isNull(), isNull(), isNull());
    }

    @Test
    void keeps07And08_inCurrentAndSavingTotals() {
        // 03 (current) + 07 (KRW current) → current bucket; 05 (saving) + 08 (KRW saving) → saving bucket.
        when(cmcFnRepo.rec1(any(), any(), any(), any(), any(), any())).thenReturn(List.of(
                rec1("B1", "A03", "03", "VND", "300"),
                rec1("B1", "A07", "07", "KRW", "700"),
                rec1("B1", "A05", "05", "VND", "500"),
                rec1("B1", "A08", "08", "KRW", "800")
        ));

        DailyFinancialCmcReport res = service.dailyFinancial(ASOF, "ALL", true, true);

        DailyFinancialCmcReport.FinancialStatusRow vnd = status(res, "VND");
        assertThat(vnd.currentAccounts()).isEqualTo("300"); // 03
        assertThat(vnd.savingAccounts()).isEqualTo("500");  // 05
        assertThat(vnd.total()).isEqualTo("800");

        DailyFinancialCmcReport.FinancialStatusRow krw = status(res, "KRW");
        assertThat(krw.currentAccounts()).isEqualTo("700"); // 07 KEPT
        assertThat(krw.savingAccounts()).isEqualTo("800");  // 08 KEPT
        assertThat(krw.total()).isEqualTo("1500");

        // Ledger surfaces the 07/08 rows too.
        assertThat(res.currentAccounts()).extracting(DailyFinancialCmcReport.CurrentAccountRow::acctTypeCd)
                .containsExactlyInAnyOrder("03", "07", "05", "08");
    }

    @Test
    void loanAndTermAccountsExcludedFromCmc() {
        when(cmcFnRepo.rec1(any(), any(), any(), any(), any(), any())).thenReturn(List.of(
                rec1("B1", "A01", "01", "VND", "999"), // loan
                rec1("B1", "A02", "02", "VND", "888"), // term/fixed deposit
                rec1("B1", "A03", "03", "VND", "300")  // current
        ));

        DailyFinancialCmcReport res = service.dailyFinancial(ASOF, "ALL", true, true);

        assertThat(res.currentAccounts()).extracting(DailyFinancialCmcReport.CurrentAccountRow::acctTypeCd)
                .containsExactly("03");
        assertThat(status(res, "VND").total()).isEqualTo("300");
    }

    @Test
    void includeInternalTransfersTrue_surfacesInternalColumns() {
        when(cmcFnRepo.rec1(any(), any(), any(), any(), any(), any())).thenReturn(List.of(
                rec1Full("B1", "A03", "03", "VND", "300",
                        "2", "100", "3", "250",   // snd cnt/amt, rcv cnt/amt
                        "1", "20", "1", "40")     // intl snd cnt/amt, intl rcv cnt/amt
        ));

        DailyFinancialCmcReport.CurrentAccountRow row =
                service.dailyFinancial(ASOF, "ALL", true, true).currentAccounts().get(0);

        assertThat(row.numDebits()).isEqualTo(2);
        assertThat(row.debit()).isEqualTo("100");
        assertThat(row.numCredits()).isEqualTo(3);
        assertThat(row.credit()).isEqualTo("250");
        assertThat(row.numInternalDebits()).isEqualTo(1);
        assertThat(row.internalDebit()).isEqualTo("20");
        assertThat(row.numInternalCredits()).isEqualTo(1);
        assertThat(row.internalCredit()).isEqualTo("40");
    }

    @Test
    void includeInternalTransfersFalse_zeroesInternalColumns() { // DF-4
        when(cmcFnRepo.rec1(any(), any(), any(), any(), any(), any())).thenReturn(List.of(
                rec1Full("B1", "A03", "03", "VND", "300",
                        "2", "100", "3", "250",
                        "1", "20", "1", "40")
        ));

        DailyFinancialCmcReport.CurrentAccountRow row =
                service.dailyFinancial(ASOF, "ALL", true, false).currentAccounts().get(0);

        // Non-internal columns unchanged; internal columns zeroed.
        assertThat(row.numDebits()).isEqualTo(2);
        assertThat(row.numCredits()).isEqualTo(3);
        assertThat(row.numInternalDebits()).isZero();
        assertThat(row.internalDebit()).isEqualTo("0");
        assertThat(row.numInternalCredits()).isZero();
        assertThat(row.internalCredit()).isEqualTo("0");
    }

    @Test
    void mapsBalanceAndBankNameFromFunction() {
        when(cmcFnRepo.rec1(any(), any(), any(), any(), any(), any())).thenReturn(List.of(
                rec1("B1", "A03", "03", "VND", "12345")));

        DailyFinancialCmcReport.CurrentAccountRow row =
                service.dailyFinancial(ASOF, "ALL", true, true).currentAccounts().get(0);

        assertThat(row.balance()).isEqualTo("12345");
        assertThat(row.bank()).isEqualTo("Bank B1");   // from rec1.bank_nm (wb_get_bank_nm)
        assertThat(row.id()).isEqualTo("B1_A03");
        assertThat(row.actvYn()).isEqualTo("Y");
    }

    @Test
    void emptyFunctionResult_returnsEmptyReport_noError() {
        DailyFinancialCmcReport res = service.dailyFinancial(ASOF, "ALL", true, true);
        assertThat(res.currentAccounts()).isEmpty();
        assertThat(res.financialStatus()).isEmpty();
        assertThat(res.asOfDt()).isEqualTo(ASOF);
    }

    /* ------------------------------------------------------------------ */

    private static DailyFinancialCmcReport.FinancialStatusRow status(DailyFinancialCmcReport res, String ccy) {
        return res.financialStatus().stream().filter(s -> s.ccyCd().equals(ccy)).findFirst()
                .orElseThrow(() -> new AssertionError("no status row " + ccy));
    }

    private static DailyFinancialCmcRec1Row rec1(String bankCd, String acctNo, String typeCd,
                                                 String ccy, String bal) {
        return rec1Full(bankCd, acctNo, typeCd, ccy, bal, "0", "0", "0", "0", "0", "0", "0", "0");
    }

    private static DailyFinancialCmcRec1Row rec1Full(String bankCd, String acctNo, String typeCd,
                                                     String ccy, String bal,
                                                     String sndCnt, String sndAmt, String rcvCnt, String rcvAmt,
                                                     String intlSndCnt, String intlSndAmt,
                                                     String intlRcvCnt, String intlRcvAmt) {
        return new DailyFinancialCmcRec1Row() {
            @Override public String getBizKey()         { return BIZ; }
            @Override public String getRefDate()        { return ASOF; }
            @Override public String getCcyCd()          { return ccy; }
            @Override public String getAcctTypeCd()     { return typeCd; }
            @Override public String getBankCd()         { return bankCd; }
            @Override public String getBankNm()         { return "Bank " + bankCd; }
            @Override public String getAcctNo()         { return acctNo; }
            @Override public String getAcctNickNm()     { return "Nick " + acctNo; }
            @Override public BigDecimal getBal()        { return new BigDecimal(bal); }
            @Override public String getLoanNm()         { return null; }
            @Override public String getExpiDt()         { return null; }
            @Override public BigDecimal getAgmtAmt()    { return BigDecimal.ZERO; }
            @Override public BigDecimal getSndCnt()     { return new BigDecimal(sndCnt); }
            @Override public BigDecimal getSndAmt()     { return new BigDecimal(sndAmt); }
            @Override public BigDecimal getRcvCnt()     { return new BigDecimal(rcvCnt); }
            @Override public BigDecimal getRcvAmt()     { return new BigDecimal(rcvAmt); }
            @Override public BigDecimal getIntlRcvAmt() { return new BigDecimal(intlRcvAmt); }
            @Override public BigDecimal getIntlSndAmt() { return new BigDecimal(intlSndAmt); }
            @Override public BigDecimal getIntlRcvCnt() { return new BigDecimal(intlRcvCnt); }
            @Override public BigDecimal getIntlSndCnt() { return new BigDecimal(intlSndCnt); }
            @Override public String getActvYn()         { return "Y"; }
        };
    }
}
