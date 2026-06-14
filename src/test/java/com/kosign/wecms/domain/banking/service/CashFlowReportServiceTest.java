package com.kosign.wecms.domain.banking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.BankFilter;
import com.kosign.wecms.domain.banking.dto.CashFlowReportResponse;
import com.kosign.wecms.domain.banking.dto.CashFlowReportResponse.CashFlowRow;
import com.kosign.wecms.domain.banking.repository.CashFlowReportRepository;
import com.kosign.wecms.domain.banking.repository.CashFlowReportRow;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DB-function-source-of-truth guard for {@code CashFlowReportService}
 * (2026-06-13). The service must:
 * <ul>
 *   <li>delegate to {@link CashFlowReportRepository#fetchReport} (the
 *       legacy {@code get_cashflow_report} funnel) — no Java recompute;</li>
 *   <li>pass {@code p_quarter = null} regardless of the requested quarter
 *       (production parity), while echoing the requested quarter in the
 *       response;</li>
 *   <li>serialise the bank filter to the function's
 *       {@code [{"BANK_CD":..,"ACCT_NO":..}]} JSON (null when empty);</li>
 *   <li>re-assemble the function's row-type-major output into the
 *       frontend's root-major order without changing figures;</li>
 *   <li>return a safe empty response when the function yields no rows.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CashFlowReportServiceTest {

    @Mock CashFlowReportRepository reportRepo;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CashFlowReportService service;

    private static final String BIZ  = "VN_VNM0004_000001";
    private static final String USER = "tester";
    private static final BigDecimal USD = new BigDecimal("25000");
    private static final BigDecimal KRW = new BigDecimal("18");

    @BeforeEach
    void setUp() {
        service = new CashFlowReportService(reportRepo, objectMapper);
        TenantContext.set(USER, BIZ, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void passesArgs_quarterNull_andSerialisesBankFilter() {
        when(reportRepo.fetchReport(any(), anyIntArg(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        CashFlowReportResponse res = service.search(
                2026, 2, "VND", USD, KRW,
                List.of(new BankFilter("B1", "A1")));

        ArgumentCaptor<String> quarterCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bankCap = ArgumentCaptor.forClass(String.class);
        verify(reportRepo).fetchReport(
                eq(BIZ), eq(2026), quarterCap.capture(),
                eq("VND"), eq(USD), eq(KRW), bankCap.capture());

        // p_quarter must be null (production parity), even though quarter=2 was requested.
        assertThat(quarterCap.getValue()).isNull();
        // Bank filter serialised to the legacy JSON contract.
        assertThat(bankCap.getValue()).contains("\"BANK_CD\":\"B1\"", "\"ACCT_NO\":\"A1\"");
        // Requested quarter echoed back so the frontend can hide columns.
        assertThat(res.quarter()).isEqualTo(2);
        assertThat(res.year()).isEqualTo(2026);
        assertThat(res.baseCurrency()).isEqualTo("VND");
    }

    @Test
    void emptyBankFilter_passesNullJson() {
        when(reportRepo.fetchReport(any(), anyIntArg(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        service.search(2026, null, "VND", USD, KRW, List.of());

        ArgumentCaptor<String> bankCap = ArgumentCaptor.forClass(String.class);
        verify(reportRepo).fetchReport(
                eq(BIZ), eq(2026), isNull(), eq("VND"), eq(USD), eq(KRW), bankCap.capture());
        assertThat(bankCap.getValue()).isNull();
    }

    @Test
    void reassemblesRowTypeMajorIntoRootMajor_andMapsFields() {
        // Function output: row-type-major (all L1, then L2, then L3, then
        // subtotals), L1 in ACC-03/02/01 order, L2/L3 unsorted — exactly
        // the shape get_cashflow_report emits.
        when(reportRepo.fetchReport(any(), anyIntArg(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(
                        row("Opening Balance", "AAAAA-OPENING-BALANCE", "Opening Balance", null, "100"),
                        row("Level 1", "ACC-03", "ACC-03", null, "0"),
                        row("Level 1", "ACC-02", "ACC-02", null, "0"),
                        row("Level 1", "ACC-01", "ACC-01", null, "0"),
                        row("Level 2", "ACC-01", "Code002", null, "0"),
                        row("Level 2", "ACC-01", "Code001", null, "0"),
                        row("Level 2", "ACC-02", "Code004", null, "0"),
                        row("Level 3", "ACC-01", "Code0012", "Code001", "12"),
                        row("Level 3", "ACC-01", "Code0011", "Code001", "11"),
                        row("Level 3", "ACC-01", "Code0021", "Code002", "21"),
                        row("Level 1 Subtotal", "ACC-03", "NON SALES PROFIT", null, "0"),
                        row("Level 1 Subtotal", "ACC-02", "S. TOTAL", null, "0"),
                        row("Level 1 Subtotal", "ACC-01", "S. TOTAL", null, "44"),
                        row("Sales Profit", "ZZZZZ-SALES-PROFIT", "SALES PROFIT", null, "44"),
                        row("End Balance", "ZZZZZ-END-BALANCE", "End Balance", null, "144")
                ));

        List<CashFlowRow> rows = service.search(2025, null, "VND", USD, KRW, List.of()).rows();

        // Root-major order, L2s sorted by code, L3s nested under their L2 (sorted).
        assertThat(rows).extracting(CashFlowRow::displayCd).containsExactly(
                "Opening Balance",
                "ACC-01", "Code001", "Code0011", "Code0012", "Code002", "Code0021", "S. TOTAL",
                "ACC-02", "Code004", "S. TOTAL",
                "ACC-03", "NON SALES PROFIT",
                "SALES PROFIT",
                "End Balance");

        // Field mapping spot-checks.
        CashFlowRow l3 = rows.stream().filter(r -> "Code0011".equals(r.displayCd())).findFirst().orElseThrow();
        assertThat(l3.rowType()).isEqualTo("Level 3");
        assertThat(l3.groupCd()).isEqualTo("ACC-01");
        assertThat(l3.parentL2Cd()).isEqualTo("Code001");
        assertThat(l3.monthly().jan()).isEqualByComparingTo("11");
        assertThat(l3.annualTotal()).isEqualByComparingTo("11");

        CashFlowRow end = rows.stream().filter(r -> "End Balance".equals(r.rowType())).findFirst().orElseThrow();
        assertThat(end.annualTotal()).isEqualByComparingTo("144");
    }

    @Test
    void quarterDoesNotChangeResult_andAlwaysCallsFunctionWithNullQuarter() {
        when(reportRepo.fetchReport(any(), anyIntArg(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(
                        row("Opening Balance", "AAAAA-OPENING-BALANCE", "Opening Balance", null, "60"),
                        row("End Balance", "ZZZZZ-END-BALANCE", "End Balance", null, "780")));

        List<CashFlowRow> thisYear = service.search(2026, null, "VND", USD, KRW, List.of()).rows();
        for (Integer q : List.of(1, 2, 3, 4)) {
            List<CashFlowRow> quarterRows = service.search(2026, q, "VND", USD, KRW, List.of()).rows();
            assertThat(quarterRows).usingRecursiveComparison().isEqualTo(thisYear);
        }
        // Every call passed null to the function (never the requested quarter).
        verify(reportRepo, org.mockito.Mockito.times(5))
                .fetchReport(eq(BIZ), eq(2026), isNull(), eq("VND"), eq(USD), eq(KRW), isNull());
    }

    @Test
    void emptyFunctionResult_returnsSafeEmptyResponse() {
        when(reportRepo.fetchReport(any(), anyIntArg(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        CashFlowReportResponse res = service.search(2030, 3, "USD", null, KRW, List.of());

        assertThat(res.rows()).isEmpty();
        assertThat(res.year()).isEqualTo(2030);
        assertThat(res.quarter()).isEqualTo(3);
        assertThat(res.baseCurrency()).isEqualTo("USD");
    }

    /* ------------------------------------------------------------------ */

    private static int anyIntArg() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    /** Minimal {@link CashFlowReportRow} fake — sets the fields the tests assert on. */
    private static CashFlowReportRow row(String rowType, String groupKey,
                                         String displayCode, String parentL2Code, String amt) {
        BigDecimal v = new BigDecimal(amt);
        return new CashFlowReportRow() {
            @Override public Integer getSortOrder() { return 0; }
            @Override public String getDisplayCode() { return displayCode; }
            @Override public String getDisplayName() { return displayCode; }
            @Override public String getItemCode() { return displayCode; }
            @Override public String getItemName() { return displayCode; }
            @Override public String getRowType() { return rowType; }
            @Override public String getGroupKey() { return groupKey; }
            @Override public String getParentL2Code() { return parentL2Code; }
            @Override public BigDecimal getJanAmount() { return v; }
            @Override public BigDecimal getFebAmount() { return v; }
            @Override public BigDecimal getMarAmount() { return v; }
            @Override public BigDecimal getAprAmount() { return v; }
            @Override public BigDecimal getMayAmount() { return v; }
            @Override public BigDecimal getJunAmount() { return v; }
            @Override public BigDecimal getJulAmount() { return v; }
            @Override public BigDecimal getAugAmount() { return v; }
            @Override public BigDecimal getSepAmount() { return v; }
            @Override public BigDecimal getOctAmount() { return v; }
            @Override public BigDecimal getNovAmount() { return v; }
            @Override public BigDecimal getDecAmount() { return v; }
            @Override public BigDecimal getTotalAmount() { return v; }
            @Override public String getDisplayCurrency() { return "VND"; }
        };
    }
}
