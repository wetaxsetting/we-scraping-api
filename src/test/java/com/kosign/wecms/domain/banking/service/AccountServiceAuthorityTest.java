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
        when(acctRepo.aggregateByCcy(any(), any(), any(), any(), any(), any(), any()))
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

    /* ------------------------------------------------------------------ */

    private String capturedFindFilteredAuthUserId() {
        ArgumentCaptor<String> c = ArgumentCaptor.forClass(String.class);
        verify(acctRepo).findFiltered(any(), any(), any(), any(), any(), any(), c.capture(), any(Pageable.class));
        return c.getValue();
    }

    private String capturedAggregateAuthUserId() {
        ArgumentCaptor<String> c = ArgumentCaptor.forClass(String.class);
        verify(acctRepo).aggregateByCcy(any(), any(), any(), any(), any(), any(), c.capture());
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
