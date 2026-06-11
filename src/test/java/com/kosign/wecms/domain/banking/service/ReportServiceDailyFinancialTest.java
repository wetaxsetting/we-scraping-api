package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.DailyFinancialCmcReport;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsRcvPayInfm;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.repository.AcctTrnsRcvPayInfmRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * P0 parity guard for {@code ReportService.dailyFinancial} — locks the
 * live-compute (Strategy B) behavior introduced to close gaps G-1
 * (per-account aggregates), G-2 (as-of date), and I-1 (Financial Status).
 * See {@code docs/features/banking/reports/daily-financial/P0-fix-plan.md}.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceDailyFinancialTest {

    @Mock AcctMstrRepository           acctRepo;
    @Mock AcctTrnsHstrRepository       trnsRepo;
    @Mock AcctTrnsRcvPayInfmRepository rcvPayRepo;
    @Mock BankCdResolver               bankResolver;

    ReportService service;

    private static final String BIZ  = "VN_VNM0004_000001";
    private static final String USER = "tester";
    private static final String ASOF = "20260604";

    @BeforeEach
    void setUp() {
        service = new ReportService(acctRepo, trnsRepo, rcvPayRepo, bankResolver);
        TenantContext.set(USER, BIZ, LangCd.EN);
        lenient().when(bankResolver.resolveBankNm(any())).thenReturn("TEST BANK");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void perAccountAggregates_splitInternalVsExternal_andStatusFromAsOfBalances() {
        AcctMstr current = acct("B1", "A100", "03", "VND", new BigDecimal("999")); // current
        AcctMstr saving  = acct("B2", "A200", "05", "VND", new BigDecimal("111")); // saving
        when(acctRepo.findByBizKeyAndActvYnOrderByBankCdAscAcctNoAsc(BIZ, "Y"))
                .thenReturn(List.of(current, saving));

        // Day's transactions for asOf:
        //  A100: T10 debit 100 (external), T11 credit 250 (external), T12 debit 20 (internal)
        //  A200: T20 credit 500 (internal)
        when(trnsRepo.findByBizKeyAndTrscDt(BIZ, ASOF)).thenReturn(List.of(
                tx("10", "B1", "A100", "2", "100", "8000", "0900"),
                tx("11", "B1", "A100", "1", "250", "8250", "1000"),
                tx("12", "B1", "A100", "2", "20",  "8230", "1100"),
                tx("20", "B2", "A200", "1", "500", "1500", "0930")
        ));
        // T12 + T20 are internal transfers (usage codes from the legacy set).
        when(rcvPayRepo.findByBizKeyAndAcctSeqNoInAndRcvPayUsagCdIn(eq(BIZ), anyList(), anyList()))
                .thenReturn(List.of(
                        rcvPay("12", "4000000008"),
                        rcvPay("20", "6000000061")
                ));

        // As-of end-of-day balances (latest BAL <= asOf).
        whenAsOfBalance("B1", "A100", "8230");
        whenAsOfBalance("B2", "A200", "1500");

        DailyFinancialCmcReport res = service.dailyFinancial(ASOF, "ALL", true, true);

        assertThat(res.asOfDt()).isEqualTo(ASOF);

        DailyFinancialCmcReport.CurrentAccountRow a100 = row(res, "B1_A100");
        assertThat(a100.numDebits()).isEqualTo(1);          // external debit only
        assertThat(a100.debit()).isEqualTo("100");
        assertThat(a100.numInternalDebits()).isEqualTo(1);  // internal debit
        assertThat(a100.internalDebit()).isEqualTo("20");
        assertThat(a100.numCredits()).isEqualTo(1);
        assertThat(a100.credit()).isEqualTo("250");
        assertThat(a100.numInternalCredits()).isEqualTo(0);
        assertThat(a100.internalCredit()).isEqualTo("0");
        assertThat(a100.balance()).isEqualTo("8230");       // as-of balance, not 999

        DailyFinancialCmcReport.CurrentAccountRow a200 = row(res, "B2_A200");
        assertThat(a200.numCredits()).isEqualTo(0);          // T20 is internal
        assertThat(a200.credit()).isEqualTo("0");
        assertThat(a200.numInternalCredits()).isEqualTo(1);
        assertThat(a200.internalCredit()).isEqualTo("500");
        assertThat(a200.balance()).isEqualTo("1500");

        // Financial Status — current = A100 (8230), saving = A200 (1500), total = 9730.
        assertThat(res.financialStatus()).hasSize(1);
        DailyFinancialCmcReport.FinancialStatusRow vnd = res.financialStatus().get(0);
        assertThat(vnd.ccyCd()).isEqualTo("VND");
        assertThat(vnd.currentAccounts()).isEqualTo("8230");
        assertThat(vnd.savingAccounts()).isEqualTo("1500");
        assertThat(vnd.total()).isEqualTo("9730");
    }

    @Test
    void asOfBalance_fallsBackToAcctMstrWhenNoHistory_andAggregatesAreZero() {
        AcctMstr usd = acct("B3", "A300", "03", "USD", new BigDecimal("777"));
        when(acctRepo.findByBizKeyAndActvYnOrderByBankCdAscAcctNoAsc(BIZ, "Y"))
                .thenReturn(List.of(usd));
        when(trnsRepo.findByBizKeyAndTrscDt(BIZ, ASOF)).thenReturn(List.of()); // no activity
        when(trnsRepo
                .findFirstByBizKeyAndBankCdAndAcctNoAndTrscDtLessThanEqualOrderByTrscDtDescTrscTmDescAcctSeqNoDesc(
                        BIZ, "B3", "A300", ASOF))
                .thenReturn(Optional.empty()); // no history on/before asOf

        DailyFinancialCmcReport res = service.dailyFinancial(ASOF, "ALL", true, true);

        DailyFinancialCmcReport.CurrentAccountRow a300 = row(res, "B3_A300");
        assertThat(a300.numDebits()).isZero();
        assertThat(a300.debit()).isEqualTo("0");
        assertThat(a300.numCredits()).isZero();
        assertThat(a300.balance()).isEqualTo("777"); // ACCT_MSTR fallback
        assertThat(res.financialStatus().get(0).currentAccounts()).isEqualTo("777");
        assertThat(res.financialStatus().get(0).total()).isEqualTo("777");
    }

    @Test
    void financialStatus_includesKrwSection_whenKrwCurrentOrSavingAccountExists() {
        // Tenant carries an active KRW current account (acct_type_cd '03',
        // ccy KRW) alongside VND/USD — verified shape on the dev DB. Under
        // Currency = All the report must emit a KRW Financial Status row,
        // not fold KRW into VND (legacy lumped non-USD into "local"; the
        // refactor is per-currency).
        AcctMstr vnd = acct("B1", "A100", "03", "VND", new BigDecimal("100"));
        AcctMstr usd = acct("B2", "A200", "03", "USD", new BigDecimal("200"));
        AcctMstr krw = acct("B9", "A900", "03", "KRW", new BigDecimal("1500000"));
        when(acctRepo.findByBizKeyAndActvYnOrderByBankCdAscAcctNoAsc(BIZ, "Y"))
                .thenReturn(List.of(vnd, usd, krw));
        when(trnsRepo.findByBizKeyAndTrscDt(BIZ, ASOF)).thenReturn(List.of()); // no activity
        lenient().when(trnsRepo
                .findFirstByBizKeyAndBankCdAndAcctNoAndTrscDtLessThanEqualOrderByTrscDtDescTrscTmDescAcctSeqNoDesc(
                        any(), any(), any(), any()))
                .thenReturn(Optional.empty()); // fall back to ACCT_MSTR.bal

        DailyFinancialCmcReport res = service.dailyFinancial(ASOF, "ALL", true, true);

        assertThat(res.financialStatus())
                .extracting(DailyFinancialCmcReport.FinancialStatusRow::ccyCd)
                .containsExactlyInAnyOrder("VND", "USD", "KRW");

        DailyFinancialCmcReport.FinancialStatusRow krwRow = res.financialStatus().stream()
                .filter(s -> s.ccyCd().equals("KRW"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no KRW Financial Status row"));
        assertThat(krwRow.currentAccounts()).isEqualTo("1500000");
        assertThat(krwRow.savingAccounts()).isEqualTo("0");
        assertThat(krwRow.total()).isEqualTo("1500000");
    }

    @Test
    void currencyScopeFcy_excludesVnd() {
        AcctMstr vnd = acct("B1", "A100", "03", "VND", new BigDecimal("100"));
        AcctMstr usd = acct("B3", "A300", "03", "USD", new BigDecimal("200"));
        when(acctRepo.findByBizKeyAndActvYnOrderByBankCdAscAcctNoAsc(BIZ, "Y"))
                .thenReturn(List.of(vnd, usd));
        when(trnsRepo.findByBizKeyAndTrscDt(BIZ, ASOF)).thenReturn(List.of());
        lenient().when(trnsRepo
                .findFirstByBizKeyAndBankCdAndAcctNoAndTrscDtLessThanEqualOrderByTrscDtDescTrscTmDescAcctSeqNoDesc(
                        any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        DailyFinancialCmcReport res = service.dailyFinancial(ASOF, "FCY", true, true);

        assertThat(res.currentAccounts()).extracting(DailyFinancialCmcReport.CurrentAccountRow::ccyCd)
                .containsExactly("USD");
        assertThat(res.financialStatus()).extracting(DailyFinancialCmcReport.FinancialStatusRow::ccyCd)
                .containsExactly("USD");
    }

    /* ------------------------------------------------------------------ */

    private void whenAsOfBalance(String bankCd, String acctNo, String bal) {
        when(trnsRepo
                .findFirstByBizKeyAndBankCdAndAcctNoAndTrscDtLessThanEqualOrderByTrscDtDescTrscTmDescAcctSeqNoDesc(
                        BIZ, bankCd, acctNo, ASOF))
                .thenReturn(Optional.of(tx("z", bankCd, acctNo, "1", "0", bal, "2359")));
    }

    private static DailyFinancialCmcReport.CurrentAccountRow row(DailyFinancialCmcReport res, String id) {
        return res.currentAccounts().stream()
                .filter(r -> r.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no row " + id));
    }

    private static AcctMstr acct(String bankCd, String acctNo, String typeCd, String ccy, BigDecimal bal) {
        AcctMstr a = new AcctMstr();
        a.setBizKey(BIZ);
        a.setBankCd(bankCd);
        a.setAcctNo(acctNo);
        a.setAcctTypeCd(typeCd);
        a.setCcyCd(ccy);
        a.setAcctNickNm("Nick " + acctNo);
        a.setBal(bal);
        a.setActvYn("Y");
        return a;
    }

    private static AcctTrnsHstr tx(String seqNo, String bankCd, String acctNo,
                                   String inoutDv, String amt, String bal, String trscTm) {
        AcctTrnsHstr t = new AcctTrnsHstr();
        t.setBizKey(BIZ);
        t.setAcctSeqNo(seqNo);
        t.setBankCd(bankCd);
        t.setAcctNo(acctNo);
        t.setTrscDt(ASOF);
        t.setTrscTm(trscTm);
        t.setCcyCd("VND");
        t.setInoutDv(inoutDv);
        t.setTrscAmt(new BigDecimal(amt));
        t.setBal(new BigDecimal(bal));
        return t;
    }

    private static AcctTrnsRcvPayInfm rcvPay(String acctSeqNo, String usagCd) {
        AcctTrnsRcvPayInfm r = new AcctTrnsRcvPayInfm();
        r.setBizKey(BIZ);
        r.setAcctSeqNo(acctSeqNo);
        r.setRcvPaySeqNo("1");
        r.setRcvPayUsagCd(usagCd);
        return r;
    }
}
