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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the two HIGH parity fixes on the Account Inquiry read path:
 *
 * <ul>
 *   <li><b>FCY-A02</b> (PO decision 2026-06-06, supersedes HR-1) — the
 *       "Last Update" wire field ({@code AccountRow.acctBalUpdDtm}, wire
 *       name retained) is sourced from {@code ACCT_HIS_LST_INQ_DT} for
 *       online accounts (the column the legacy fnsh_0005_001 /
 *       fnsh_0001_001 grid binds), falling back to
 *       {@code ACCT_BAL_LST_INQ_DTM} only for fixed-deposit-only banks —
 *       NOT from {@code ACCT_BAL_UPD_DTM}.</li>
 *   <li><b>HR-3</b> — per-user account authority: an admin's list query
 *       passes a {@code null} {@code authUserId} (no ACCT_AUTH_INFO
 *       filter); a non-admin's passes the caller's user id so the
 *       repository EXISTS predicate restricts to granted accounts.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountServiceAuthorityTest {

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
        // Common stubs — a single VND account flows through toRow.
        when(acctRepo.findFiltered(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(oneAccountPage());
        when(acctRepo.aggregateByCcy(any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(List.of());
        when(acctRepo.findDistinctBankCds(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(bankCertRepo.findConnectedBankCds(BIZ)).thenReturn(List.of());
        when(bankResolver.resolveBankNm(any())).thenReturn("Shinhan Bank Vietnam");
        when(bankResolver.resolveAcctTypeNm(any())).thenReturn("Current Account");
        when(bankResolver.resolveAcctTypeKey(any())).thenReturn("bank.acct_type.03");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── HR-3 ────────────────────────────────────────────────────────────

    @Test
    void admin_passesNullAuthUserId_soNoAccountAuthorityFilterApplies() {
        TenantContext.set(USER, USER, BIZ, LangCd.EN, TenantContext.AUTH_GR_ADMIN);

        service.list("VND");

        assertThat(capturedFindFilteredAuthUserId()).isNull();
        assertThat(capturedAggregateAuthUserId()).isNull();
        assertThat(capturedDistinctBankAuthUserId()).isNull();
    }

    @Test
    void nonAdmin_passesCallerUserId_soAccountAuthorityFilterApplies() {
        TenantContext.set(USER, USER, BIZ, LangCd.EN, TenantContext.AUTH_GR_USER);

        service.list("VND");

        assertThat(capturedFindFilteredAuthUserId()).isEqualTo(USER);
        assertThat(capturedAggregateAuthUserId()).isEqualTo(USER);
        assertThat(capturedDistinctBankAuthUserId()).isEqualTo(USER);
    }

    // ── FCY-A02 ─────────────────────────────────────────────────────────

    @Test
    void lastUpdateColumn_readsAcctHisLstInqDt_forOnlineAccount() {
        TenantContext.set(USER, USER, BIZ, LangCd.EN, TenantContext.AUTH_GR_ADMIN);

        AccountListResponse res = service.list("VND");

        // The wire field name stays acct_bal_upd_dtm (frontend contract
        // unchanged), but for an online account (ACCT_TYPE_CD '03') its
        // value must be ACCT_HIS_LST_INQ_DT — the transaction-history
        // last-inquiry date the legacy grid displays — NOT
        // ACCT_BAL_LST_INQ_DTM and NOT ACCT_BAL_UPD_DTM.
        assertThat(res.accounts()).hasSize(1);
        assertThat(res.accounts().get(0).acctBalUpdDtm()).isEqualTo("20260606120000");
        assertThat(res.accounts().get(0).acctBalUpdDtm()).isNotEqualTo("20250101000000");
        assertThat(res.accounts().get(0).acctBalUpdDtm()).isNotEqualTo("19990101000000");
    }

    @Test
    void lastUpdateColumn_fallsBackToAcctBalLstInqDtm_forFixedDepositOnlyBank() {
        TenantContext.set(USER, USER, BIZ, LangCd.EN, TenantContext.AUTH_GR_ADMIN);
        // Fixed-deposit-only bank: ACCT_TYPE_CD '02', not Sacombank
        // (11704004). Legacy displays ACCT_BAL_LST_INQ_DTM here because
        // the FD account runs no TRANSACTION job (no history date).
        when(acctRepo.findFiltered(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(fixedDepositOnlyPage());

        AccountListResponse res = service.list("VND");

        assertThat(res.accounts()).hasSize(1);
        assertThat(res.accounts().get(0).acctBalUpdDtm()).isEqualTo("20250101000000");
    }

    // ── FCY type-code scope (Transaction History / Account Inquiry FCY) ──

    /**
     * Regression for the FCY-empty bug: currency="FCY" must resolve to
     * {@code CCY_CD <> 'VND'} plus a type-code whitelist that admits the
     * generic current/savings codes (03/05) as well as the
     * currency-specific ones (04/06/07/08). The refactor DB codes every
     * non-VND current account as the generic CURRENT_KHR (03); a
     * 04/06/07/08-only whitelist matched zero rows and the FCY page
     * rendered empty. Loans (01) stay excluded.
     */
    @Test
    void fcyScope_excludesVnd_andAdmitsGenericCurrentSavingsTypeCodes() {
        TenantContext.set(USER, USER, BIZ, LangCd.EN, TenantContext.AUTH_GR_ADMIN);

        service.list("FCY");

        ArgumentCaptor<String> ccyNot = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> types =
                ArgumentCaptor.forClass((Class<List<String>>) (Class<?>) List.class);
        verify(acctRepo).findFiltered(
                any(), any(), ccyNot.capture(), types.capture(), any(), any(), any(), any(Pageable.class));

        assertThat(ccyNot.getValue()).isEqualTo("VND");
        assertThat(types.getValue())
                .contains("02", "03", "04", "05", "06", "07", "08")
                .doesNotContain("01");
    }

    /**
     * Regression for the "selecting KRW returns Not Found" bug: a concrete
     * non-VND currency must resolve to {@code CCY_CD = 'KRW'} with the SAME
     * generic non-loan type set FCY uses (admits 03/05). The old per-currency
     * whitelist {@code KRW = {02,07,08}} excluded the generic CURRENT_KHR (03)
     * that the live JEX DB assigns to every foreign current account, so a real
     * type-03 KRW account matched zero rows.
     */
    @Test
    void fcySelectedKrw_filtersByKrw_withGenericNonLoanTypeCodes() {
        TenantContext.set(USER, USER, BIZ, LangCd.EN, TenantContext.AUTH_GR_ADMIN);

        service.list("KRW");

        ArgumentCaptor<String> ccyEq = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ccyNot = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> types =
                ArgumentCaptor.forClass((Class<List<String>>) (Class<?>) List.class);
        verify(acctRepo).findFiltered(
                any(), ccyEq.capture(), ccyNot.capture(), types.capture(), any(), any(), any(), any(Pageable.class));

        assertThat(ccyEq.getValue()).isEqualTo("KRW");
        assertThat(ccyNot.getValue()).isNull();
        assertThat(types.getValue())
                .contains("02", "03", "04", "05", "06", "07", "08")
                .doesNotContain("01");
    }

    /** Same fix on the USD narrow — USD foreign accounts are also type-03 in
     *  the live JEX DB; the old {@code USD = {02,04,06}} whitelist dropped them. */
    @Test
    void fcySelectedUsd_filtersByUsd_withGenericNonLoanTypeCodes() {
        TenantContext.set(USER, USER, BIZ, LangCd.EN, TenantContext.AUTH_GR_ADMIN);

        service.list("USD");

        ArgumentCaptor<String> ccyEq = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> types =
                ArgumentCaptor.forClass((Class<List<String>>) (Class<?>) List.class);
        verify(acctRepo).findFiltered(
                any(), ccyEq.capture(), any(), types.capture(), any(), any(), any(), any(Pageable.class));

        assertThat(ccyEq.getValue()).isEqualTo("USD");
        assertThat(types.getValue())
                .contains("02", "03", "04", "05", "06", "07", "08")
                .doesNotContain("01");
    }

    /** VND keeps its own legacy type set (02/03/05) — the concrete-CCY fix
     *  must not bleed into the VND scope. */
    @Test
    void vndScope_keepsLegacyVndTypeCodes() {
        TenantContext.set(USER, USER, BIZ, LangCd.EN, TenantContext.AUTH_GR_ADMIN);

        service.list("VND");

        ArgumentCaptor<String> ccyEq = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> types =
                ArgumentCaptor.forClass((Class<List<String>>) (Class<?>) List.class);
        verify(acctRepo).findFiltered(
                any(), ccyEq.capture(), any(), types.capture(), any(), any(), any(), any(Pageable.class));

        assertThat(ccyEq.getValue()).isEqualTo("VND");
        assertThat(types.getValue())
                .containsExactlyInAnyOrder("02", "03", "05");
    }

    // ── FCY-A04 (Product Type H summary-card maturity / balance exclusion) ──

    /**
     * An H tenant must pass {@code excludeMaturedHSavings = true} so the
     * per-currency summary aggregate drops matured / zero-balance savings
     * accounts (legacy {@code FnshHelper.fnsh_0001_001_r001_tot_dynamic0}
     * :506-509, Redmine #42633). Product type is read from the signed JWT
     * via {@code TenantContext.currentPrdtType()}.
     */
    @Test
    void hTenant_passesExcludeMaturedHSavingsTrue() {
        TenantContext.set(USER, USER, BIZ, LangCd.EN, TenantContext.AUTH_GR_ADMIN, "H");

        service.list("VND");

        assertThat(capturedAggregateExcludeFlag()).isTrue();
    }

    /**
     * A non-H tenant ({@code F}) must pass {@code excludeMaturedHSavings =
     * false} — the aggregate runs the unchanged WHERE chain and counts
     * every account, so non-HAENGSUNG tenants see no behaviour change.
     */
    @Test
    void nonHTenant_passesExcludeMaturedHSavingsFalse() {
        TenantContext.set(USER, USER, BIZ, LangCd.EN, TenantContext.AUTH_GR_ADMIN, "F");

        service.list("VND");

        assertThat(capturedAggregateExcludeFlag()).isFalse();
    }

    /**
     * FCY-A04 still applies when a concrete currency is selected on the FCY
     * page — narrowing to KRW must not drop the H maturity/zero-balance
     * exclusion on the per-currency summary aggregate.
     */
    @Test
    void hTenant_withSelectedCurrency_stillExcludesMaturedHSavings() {
        TenantContext.set(USER, USER, BIZ, LangCd.EN, TenantContext.AUTH_GR_ADMIN, "H");

        service.list("KRW");

        assertThat(capturedAggregateExcludeFlag()).isTrue();
    }

    /* ------------------------------------------------------------------ */

    private String capturedFindFilteredAuthUserId() {
        ArgumentCaptor<String> c = ArgumentCaptor.forClass(String.class);
        verify(acctRepo).findFiltered(any(), any(), any(), any(), any(), any(), c.capture(), any(Pageable.class));
        return c.getValue();
    }

    private String capturedAggregateAuthUserId() {
        ArgumentCaptor<String> c = ArgumentCaptor.forClass(String.class);
        verify(acctRepo).aggregateByCcy(any(), any(), any(), any(), any(), any(), c.capture(), anyBoolean());
        return c.getValue();
    }

    private boolean capturedAggregateExcludeFlag() {
        ArgumentCaptor<Boolean> c = ArgumentCaptor.forClass(Boolean.class);
        verify(acctRepo).aggregateByCcy(any(), any(), any(), any(), any(), any(), any(), c.capture());
        return c.getValue();
    }

    private String capturedDistinctBankAuthUserId() {
        ArgumentCaptor<String> c = ArgumentCaptor.forClass(String.class);
        verify(acctRepo).findDistinctBankCds(any(), any(), any(), any(), c.capture());
        return c.getValue();
    }

    private static Page<AcctMstr> oneAccountPage() {
        AcctMstr a = new AcctMstr();
        a.setBizKey(BIZ);
        a.setBankCd("11704088");
        a.setAcctNo("5280123456");
        a.setAcctTypeCd("03");
        a.setCcyCd("VND");
        a.setBal(new BigDecimal("100"));
        a.setRealAmt(new BigDecimal("90"));
        a.setHandRegYn("N");
        // FCY-A02 fixture: online account (ACCT_TYPE_CD '03') → the
        // displayed "Last Update" must follow ACCT_HIS_LST_INQ_DT, NOT
        // ACCT_BAL_LST_INQ_DTM and NOT ACCT_BAL_UPD_DTM (both distinct
        // below so a regression would surface as a different value).
        a.setAcctHisLstInqDt("20260606120000");
        a.setAcctBalLstInqDtm("20250101000000");
        a.setAcctBalUpdDtm("19990101000000");
        return new PageImpl<>(List.of(a));
    }

    private static Page<AcctMstr> fixedDepositOnlyPage() {
        AcctMstr a = new AcctMstr();
        a.setBizKey(BIZ);
        a.setBankCd("11704088"); // not Sacombank (11704004)
        a.setAcctNo("5280999999");
        a.setAcctTypeCd("02");   // fixed-deposit-only → falls back to balance date
        a.setCcyCd("VND");
        a.setBal(new BigDecimal("100"));
        a.setRealAmt(new BigDecimal("90"));
        a.setHandRegYn("N");
        a.setAcctHisLstInqDt(null);            // no TRANSACTION job for FD-only
        a.setAcctBalLstInqDtm("20250101000000"); // displayed value for FD-only
        a.setAcctBalUpdDtm("19990101000000");
        return new PageImpl<>(List.of(a));
    }
}
