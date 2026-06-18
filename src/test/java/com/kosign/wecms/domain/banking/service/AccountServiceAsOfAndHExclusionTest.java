package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.repository.BankCertInfoRepository;
import com.kosign.wecms.domain.banking.dto.AccountListResponse;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.nxisas.BankTransactionSyncService;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.repository.NapasBankInfoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI-01 (as-of-date historical balance) + AI-06 (PrdtType H summary-card
 * exclusion) wiring on the Account Inquiry list path.
 *
 * <p>The legacy formulas live in SQL (the {@code ACCT_MSTR_R004} port in
 * {@code findAsOfBalances}, and the {@code tot_dynamic0} predicate inside
 * {@code aggregateByCcy}); those are validated against the live DB. These
 * tests pin the <b>service wiring</b>: that the as-of override is applied to
 * row balances only when a date is supplied, and that the H-exclusion flag is
 * passed to the aggregate (summary cards) based on the JWT product type.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountServiceAsOfAndHExclusionTest {

    @Mock AcctMstrRepository         acctRepo;
    @Mock BankCdResolver             bankResolver;
    @Mock NapasBankInfoRepository    napasRepo;
    @Mock BankCertInfoRepository     bankCertRepo;
    @Mock BankTransactionSyncService bankSync;

    AccountService service;

    private static final String BIZ  = "VN_VNM0004_000001";
    private static final String USER = "tester";

    @BeforeEach
    void setUp() {
        service = new AccountService(acctRepo, bankResolver, napasRepo, bankCertRepo, bankSync);
        when(acctRepo.findFiltered(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(oneAccountPage());
        when(acctRepo.aggregateByCcy(any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(List.of());
        when(acctRepo.findDistinctBankCds(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(bankCertRepo.findConnectedBankCds(BIZ)).thenReturn(List.of());
        when(bankResolver.resolveBankNm(any())).thenReturn("Shinhan VN");
        when(bankResolver.resolveAcctTypeNm(any())).thenReturn("Current");
        when(bankResolver.resolveAcctTypeKey(any())).thenReturn("bank.acct_type.03");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── AI-06: PrdtType H summary-card exclusion flag ──────────────────

    @Test
    void hTenant_passesExcludeMaturedHSavingsTrue_toAggregate() {
        TenantContext.set(USER, USER, BIZ, LangCd.EN, TenantContext.AUTH_GR_ADMIN, "H");

        service.list("VND");

        assertThat(capturedAggregateHFlag()).isTrue();
    }

    @Test
    void fTenant_passesExcludeMaturedHSavingsFalse_toAggregate() {
        TenantContext.set(USER, USER, BIZ, LangCd.EN, TenantContext.AUTH_GR_ADMIN, "F");

        service.list("VND");

        assertThat(capturedAggregateHFlag()).isFalse();
    }

    @Test
    void nullPrdtType_passesExcludeMaturedHSavingsFalse_toAggregate() {
        TenantContext.set(USER, USER, BIZ, LangCd.EN, TenantContext.AUTH_GR_ADMIN, null);

        service.list("VND");

        assertThat(capturedAggregateHFlag()).isFalse();
    }

    // ── AI-01: as-of-date row balance override ─────────────────────────

    @Test
    void asOfDateSupplied_overridesRowBalanceWithHistoricalValue() {
        TenantContext.set(USER, USER, BIZ, LangCd.EN, TenantContext.AUTH_GR_ADMIN, "F");
        // ACCT_MSTR_R004 port returns the as-of bal/real_amt for the account.
        when(acctRepo.findAsOfBalances(eq(BIZ), eq("20250101")))
                .thenReturn(List.<Object[]>of(new Object[]{
                        "11704088", "700005285280",
                        new BigDecimal("800"), new BigDecimal("750")}));

        AccountListResponse res = service.list(
                AccountFilter.currencyOnly("VND"),
                null, null, "20250101", 0, 0);

        assertThat(res.accounts()).hasSize(1);
        // Row shows the as-of values (800 / 750), NOT the current 1000 / 900.
        assertThat(res.accounts().get(0).bal()).isEqualTo(AmountFormatter.wire(new BigDecimal("800")));
        assertThat(res.accounts().get(0).realAmt()).isEqualTo(AmountFormatter.wire(new BigDecimal("750")));
        verify(acctRepo).findAsOfBalances(BIZ, "20250101");
    }

    @Test
    void noAsOfDate_keepsCurrentBalance_andSkipsAsOfQuery() {
        TenantContext.set(USER, USER, BIZ, LangCd.EN, TenantContext.AUTH_GR_ADMIN, "F");

        AccountListResponse res = service.list("VND"); // no asOfDate overload

        assertThat(res.accounts()).hasSize(1);
        // Current snapshot values (1000 / 900) — the as-of query never ran.
        assertThat(res.accounts().get(0).bal()).isEqualTo(AmountFormatter.wire(new BigDecimal("1000")));
        assertThat(res.accounts().get(0).realAmt()).isEqualTo(AmountFormatter.wire(new BigDecimal("900")));
        verify(acctRepo, never()).findAsOfBalances(any(), any());
    }

    /* ------------------------------------------------------------------ */

    private boolean capturedAggregateHFlag() {
        ArgumentCaptor<Boolean> c = ArgumentCaptor.forClass(Boolean.class);
        verify(acctRepo).aggregateByCcy(any(), any(), any(), any(), any(), any(), any(), c.capture());
        return c.getValue();
    }

    private static Page<AcctMstr> oneAccountPage() {
        AcctMstr a = new AcctMstr();
        a.setBizKey(BIZ);
        a.setBankCd("11704088");
        a.setAcctNo("700005285280");
        a.setAcctTypeCd("03");
        a.setCcyCd("VND");
        a.setBal(new BigDecimal("1000"));
        a.setRealAmt(new BigDecimal("900"));
        a.setHandRegYn("N");
        a.setAcctHisLstInqDt("20260101000000");
        return new PageImpl<>(List.of(a));
    }
}
