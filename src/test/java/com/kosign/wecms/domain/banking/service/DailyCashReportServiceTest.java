package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.DailyCashReportResponse;
import com.kosign.wecms.domain.banking.repository.DailyCashAccountProjection;
import com.kosign.wecms.domain.banking.repository.DailyCashRecomputeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Account-type bucketing parity for the Daily Cash summary, now driven by
 * the <b>live-recompute</b> source ({@link DailyCashRecomputeRepository},
 * the refactor equivalent of legacy {@code rept_0900_001_rec1}).
 *
 * <p>Locks the DB-confirmed legacy mapping from {@code rept_0900_001_rec2}
 * (recorded in {@code docs/features/daily-cash/refactor-parity-verification.md}
 * §1.5):
 * <ul>
 *   <li>{@code 03},{@code 04} → demand / current</li>
 *   <li>{@code 05},{@code 06} → savings</li>
 *   <li>{@code 02} → term / fixed deposit</li>
 *   <li>{@code 01} → loan (NOT cash)</li>
 *   <li>{@code 07},{@code 08} → no summary bucket</li>
 *   <li>cash → always 0 (legacy {@code TOTL_CASH} comes from
 *       {@code cash_repy_mstr}, out of refactor active-table scope)</li>
 * </ul>
 * Regression guard against the prior fabricated {@code 01/11/12/13/21}
 * constants.
 */
@ExtendWith(MockitoExtension.class)
class DailyCashReportServiceTest {

    @Mock DailyCashRecomputeRepository recomputeRepo;
    @Mock BankCdResolver               bankResolver;
    @Mock AuditLogService              auditLogService;

    DailyCashReportService service;

    private static final String BIZ  = "VN_VNM0004_000001";
    private static final String USER = "tester";
    private static final String DATE = "2026-06-04";

    @BeforeEach
    void setUp() {
        service = new DailyCashReportService(recomputeRepo, bankResolver, auditLogService);
        TenantContext.set(USER, BIZ, LangCd.EN);
        lenient().when(bankResolver.resolveBankNm(any())).thenReturn("TEST BANK");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void summaryBucketsByDbConfirmedLegacyCodes_cashIsZero() {
        // One VND row per legacy code 01..07, balance = numeric code value
        // so each bucket's expected total is unambiguous.
        when(recomputeRepo.findAccountsAsOf(any(), any(), any(), any())).thenReturn(List.of(
                proj("01", "VND", "100"), // loan
                proj("02", "VND", "200"), // term / fixed deposit
                proj("03", "VND", "300"), // current (local)
                proj("04", "VND", "400"), // current (FCY)
                proj("05", "VND", "500"), // savings (local)
                proj("06", "VND", "600"), // savings (FCY)
                proj("07", "VND", "700")  // KRW current — no legacy summary bucket
        ));

        DailyCashReportResponse res = service.search(DATE, DailyCashCurrencyFilter.ALL);

        assertThat(res.currencySummary()).hasSize(1);
        DailyCashReportResponse.CurrencySummary vnd = res.currencySummary().get(0);
        assertThat(vnd.currencyCd()).isEqualTo("VND");
        assertThat(vnd.totalLoan())         .isEqualByComparingTo("100"); // 01
        assertThat(vnd.totalTermDeposit())  .isEqualByComparingTo("200"); // 02
        assertThat(vnd.totalDemandDeposit()).isEqualByComparingTo("700"); // 03 + 04
        assertThat(vnd.totalSavings())      .isEqualByComparingTo("1100");// 05 + 06
        // Cash must NEVER be sourced from 01 (loan) or any ACCT_TYPE_CD.
        assertThat(vnd.totalCash())         .isEqualByComparingTo("0");
        // 07 contributes to no bucket → demand stays 700, not 1400.
    }

    @Test
    void loanIsNotCash() {
        when(recomputeRepo.findAccountsAsOf(any(), any(), any(), any()))
                .thenReturn(List.of(proj("01", "VND", "999")));

        DailyCashReportResponse.CurrencySummary vnd =
                service.search(DATE, DailyCashCurrencyFilter.ALL).currencySummary().get(0);

        assertThat(vnd.totalLoan()).isEqualByComparingTo("999");
        assertThat(vnd.totalCash()).isEqualByComparingTo("0");
    }

    @Test
    void unknownAndOtherCodesAreSafelyIgnored() {
        // "99" unknown; the previously-fabricated "11/12/13/21"; and 07/08
        // (defined but not summarised) must all contribute to nothing —
        // and must never throw.
        when(recomputeRepo.findAccountsAsOf(any(), any(), any(), any())).thenReturn(List.of(
                proj("99", "VND", "10"),
                proj("11", "VND", "10"),
                proj("12", "VND", "10"),
                proj("13", "VND", "10"),
                proj("21", "VND", "10"),
                proj("07", "VND", "10"),
                proj("08", "VND", "10")
        ));

        DailyCashReportResponse.CurrencySummary vnd =
                service.search(DATE, DailyCashCurrencyFilter.ALL).currencySummary().get(0);

        assertThat(vnd.totalCash())         .isEqualByComparingTo("0");
        assertThat(vnd.totalDemandDeposit()).isEqualByComparingTo("0");
        assertThat(vnd.totalSavings())      .isEqualByComparingTo("0");
        assertThat(vnd.totalTermDeposit())  .isEqualByComparingTo("0");
        assertThat(vnd.totalLoan())         .isEqualByComparingTo("0");
    }

    @Test
    void accountRowsCarryRawAcctTypeForTheFrontendSplit() {
        // The FE Current/Savings split keys off acct_type_cd — make sure
        // the per-account rows echo the raw codes unchanged.
        when(recomputeRepo.findAccountsAsOf(any(), any(), any(), any())).thenReturn(List.of(
                proj("03", "VND", "300"),
                proj("05", "VND", "500")
        ));

        List<DailyCashReportResponse.AccountRow> rows =
                service.search(DATE, DailyCashCurrencyFilter.ALL).accounts();

        assertThat(rows).extracting(DailyCashReportResponse.AccountRow::acctTypeCd)
                .containsExactly("03", "05");
        // Live-recompute path carries no snapshot timestamp.
        assertThat(rows.get(0).lastCollectedDate()).isNull();
    }

    @Test
    void emptySource_returnsEmptyAccountsAndSummary_noError() {
        // Date with no master/transaction rows → empty result, no exception.
        when(recomputeRepo.findAccountsAsOf(any(), any(), any(), any())).thenReturn(List.of());

        DailyCashReportResponse res = service.search(DATE, DailyCashCurrencyFilter.ALL);

        assertThat(res.accounts()).isEmpty();
        assertThat(res.currencySummary()).isEmpty();
        assertThat(res.currencyFlow()).isEmpty();
    }

    /* ------------------------------------------------------------------ */

    /** Build a {@link DailyCashAccountProjection} test double — counts /
     *  amounts default to zero; loan/maturity/agreement default to
     *  null/zero. Only the fields the bucketing exercises are set. */
    private static DailyCashAccountProjection proj(String acctTypeCd, String ccyCd, String bal) {
        return new DailyCashAccountProjection() {
            @Override public String getCcyCd()       { return ccyCd; }
            @Override public String getAcctTypeCd()  { return acctTypeCd; }
            @Override public String getBankCd()      { return "0001"; }
            @Override public String getAcctNo()      { return "ACC-" + acctTypeCd; }
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
}
