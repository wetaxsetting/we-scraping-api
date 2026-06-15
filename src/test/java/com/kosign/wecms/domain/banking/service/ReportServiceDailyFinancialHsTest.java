package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.DailyFinancialHsReport;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.repository.DailyFinancialCmcFunctionRepository;
import com.kosign.wecms.domain.banking.repository.DailyFinancialHsReportRepository;
import com.kosign.wecms.domain.banking.repository.DailyFinancialHsRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DF-2 guard for {@code ReportService.dailyFinancialHs} — locks the real
 * DB-backed path: the service must call the legacy function repository
 * {@link DailyFinancialHsReportRepository#fetchReport} and map its rows
 * verbatim (row type + order preserved), never the removed hardcoded
 * 6-row stub.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceDailyFinancialHsTest {

    @Mock AcctMstrRepository               acctRepo;
    @Mock AcctTrnsHstrRepository           trnsRepo;
    @Mock BankCdResolver                   bankResolver;
    @Mock DailyFinancialHsReportRepository hsReportRepo;
    @Mock DailyFinancialCmcFunctionRepository cmcFnRepo;

    ReportService service;

    private static final String BIZ  = "VN_VNM0004_000014"; // HAENGSUNG tenant
    private static final String USER = "tester";

    @BeforeEach
    void setUp() {
        service = new ReportService(acctRepo, trnsRepo, bankResolver, hsReportRepo, cmcFnRepo);
        TenantContext.set(USER, BIZ, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void callsFunctionWithGivenDateAndFilter_andPreservesRowTypeOrderAndAmounts() {
        // HEADER (no amounts) → TRANSACTION (receipt set, before-balance null)
        // → ACCOUNT_TOTAL (all amounts) — the exact row-type sequence the
        // function emits per account.
        when(hsReportRepo.fetchReport(BIZ, "20260613", "ALL")).thenReturn(List.of(
                row("HEADER",        "BIDV\n(Current account)\n", "VND", null,              null,             null,        1, 0),
                row("TRANSACTION",   "",                          "VND", null,              new BigDecimal("5000000"), null, 1, 1001),
                row("ACCOUNT_TOTAL", "SUB TOTAL",                 "VND", new BigDecimal("0"), new BigDecimal("5000000"), new BigDecimal("25000000"), 1, 99999)
        ));

        DailyFinancialHsReport res = service.dailyFinancialHs("20260613", "ALL");

        verify(hsReportRepo).fetchReport(BIZ, "20260613", "ALL");

        // Not the removed 6-row stub — exactly what the function returned, in order.
        assertThat(res.asOfDt()).isEqualTo("20260613");
        assertThat(res.accountFilter()).isEqualTo("ALL");
        assertThat(res.rows()).extracting(DailyFinancialHsReport.LedgerRow::rowType)
                .containsExactly("HEADER", "TRANSACTION", "ACCOUNT_TOTAL");

        // HEADER carries no amounts → null preserved (render blank, not "0").
        DailyFinancialHsReport.LedgerRow header = res.rows().get(0);
        assertThat(header.beforeBalance()).isNull();
        assertThat(header.receipt()).isNull();
        assertThat(header.balance()).isNull();

        // TRANSACTION receipt wired; before-balance stays null.
        DailyFinancialHsReport.LedgerRow txn = res.rows().get(1);
        assertThat(txn.receipt()).isEqualTo("5000000");
        assertThat(txn.beforeBalance()).isNull();

        // ACCOUNT_TOTAL amounts wired.
        DailyFinancialHsReport.LedgerRow subtotal = res.rows().get(2);
        assertThat(subtotal.bankAccount()).isEqualTo("SUB TOTAL");
        assertThat(subtotal.balance()).isEqualTo("25000000");
    }

    @Test
    void blankDateDefaultsToToday_andBlankFilterDefaultsToAll() {
        when(hsReportRepo.fetchReport(org.mockito.ArgumentMatchers.eq(BIZ),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("ALL")))
                .thenReturn(List.of());

        DailyFinancialHsReport res = service.dailyFinancialHs("  ", "  ");

        ArgumentCaptor<String> dateCap = ArgumentCaptor.forClass(String.class);
        verify(hsReportRepo).fetchReport(org.mockito.ArgumentMatchers.eq(BIZ),
                dateCap.capture(), org.mockito.ArgumentMatchers.eq("ALL"));

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(dateCap.getValue()).isEqualTo(today);
        assertThat(res.accountFilter()).isEqualTo("ALL");
    }

    @Test
    void returnsEmptyWhenFunctionReturnsNoRows_provesNoHardcodedSeed() {
        when(hsReportRepo.fetchReport(BIZ, "20260101", "ALL")).thenReturn(List.of());

        DailyFinancialHsReport res = service.dailyFinancialHs("20260101", "ALL");

        // The removed stub always returned 6 rows; the real path returns
        // exactly what the function gives — here, none.
        assertThat(res.rows()).isEmpty();
    }

    /* ------------------------------------------------------------------ */

    /** Minimal {@link DailyFinancialHsRow} fake — only the fields the test
     *  asserts on are populated; the rest default to null. */
    private static DailyFinancialHsRow row(String rowType, String bankAccount, String currency,
                                           BigDecimal beforeBalance, BigDecimal receipt,
                                           BigDecimal balance, Integer accountTypeOrder,
                                           Integer transactionSeq) {
        return new DailyFinancialHsRow() {
            @Override public String getRowType() { return rowType; }
            @Override public String getBankAccount() { return bankAccount; }
            @Override public String getCurrency() { return currency; }
            @Override public String getReptCd() { return null; }
            @Override public String getPymnCd() { return null; }
            @Override public String getDescriptionEnglish() { return null; }
            @Override public String getDescriptionKorean() { return null; }
            @Override public String getCustomerSupplier() { return null; }
            @Override public BigDecimal getBeforeBalance() { return beforeBalance; }
            @Override public BigDecimal getReceipt() { return receipt; }
            @Override public BigDecimal getPayment() { return null; }
            @Override public BigDecimal getBalance() { return balance; }
            @Override public BigDecimal getBalanceCompanyActual() { return balance; }
            @Override public String getAccountNumber() { return ""; }
            @Override public Integer getAccountTypeOrder() { return accountTypeOrder; }
            @Override public String getAcctSeqNo() { return ""; }
            @Override public String getTransactionDate() { return ""; }
            @Override public String getTransactionTime() { return ""; }
            @Override public Integer getTransactionSeq() { return transactionSeq; }
            @Override public String getMemo() { return ""; }
            @Override public String getBankCdOut() { return ""; }
            @Override public String getBankNameSort() { return bankAccount; }
        };
    }
}
