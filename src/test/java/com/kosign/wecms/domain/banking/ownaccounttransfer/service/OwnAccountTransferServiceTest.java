package com.kosign.wecms.domain.banking.ownaccounttransfer.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.entity.NapasBankInfo;
import com.kosign.wecms.domain.banking.entity.NapasRemitTrnsHstr;
import com.kosign.wecms.domain.banking.ownaccounttransfer.OwnAccountTransferAuditActions;
import com.kosign.wecms.domain.banking.ownaccounttransfer.OwnAccountTransferConstants;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.BalanceRefreshRequest;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.EngineConnectionCheckRequest;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.BalanceRefreshResponse;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.NapasBankInfoRow;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.SourceAccountListResponse;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.repository.NapasBankInfoRepository;
import com.kosign.wecms.domain.banking.service.BankCdResolver;
import com.kosign.wecms.domain.setting.bankaccount.entity.AcctAuthInfo;
import com.kosign.wecms.domain.setting.bankaccount.repository.AcctAuthInfoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service-layer coverage for Banking &gt; Own Account Transfer (Path
 * A — 4 endpoints). Pure Mockito; no Spring context. Mirrors the
 * sibling {@code ChartOfAccountsServiceTest} style: each test sets up
 * {@link TenantContext}, exercises one method, and asserts both the
 * repository / audit interactions and the response shape.
 */
@ExtendWith(MockitoExtension.class)
class OwnAccountTransferServiceTest {

    private static final String TENANT     = "VN_VNM0004_000001";
    private static final String USER       = "tester";
    private static final String USER_NM    = "Test User";
    private static final String ADMIN      = "adminuser";
    private static final String SHINHAN    = "11704088";
    private static final String VIETIN     = "11704001";

    @Mock private AcctMstrRepository           acctMstrRepo;
    @Mock private AcctAuthInfoRepository       acctAuthRepo;
    @Mock private BankCdResolver               bankCdResolver;
    @Mock private NapasBankInfoRepository      napasBankInfoRepo;
    @Mock private com.kosign.wecms.domain.banking.repository.NapasRemitTrnsHstrRepository napasRemitTrnsHstrRepo;
    @Mock private AuditLogService              auditLog;
    @Mock private com.kosign.wecms.common.legacy.LegacyFunctionRepository legacyFunctions;
    /** Only exercised by the bank-account-password tests; every other
     *  intake sends null passwords and short-circuits before encryption. */
    @Mock private com.kosign.wecms.domain.banking.bankcredentials.service.BankCertCryptoService bankCertCrypto;

    @InjectMocks
    private OwnAccountTransferService service;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* =================================================================
     *  listSourceAccounts
     * ================================================================= */

    @Test
    void list_admin_returnsAllSupportedBanks() {
        setUpAdmin();
        AcctMstr a1 = acct(SHINHAN, "0001", new BigDecimal("100.00"), new BigDecimal("90.00"));
        AcctMstr a2 = acct(VIETIN,  "0002", new BigDecimal("50.00"),  new BigDecimal("40.00"));
        when(acctMstrRepo.findOwnTransferSourceRows(
                eq(TENANT), anyList(), eq(false), anyString(), any(Sort.class)))
                .thenReturn(List.of(a1, a2));
        stubBankName(SHINHAN, "Shinhan VN");
        stubBankName(VIETIN,  "VietinBank");

        SourceAccountListResponse resp = service.listSourceAccounts(null, null, null);

        assertThat(resp.list()).hasSize(2);
        assertThat(resp.list()).allMatch(r -> r.canTransfer());
        assertThat(resp.totalCount()).isEqualTo(2);
        assertThat(resp.totBal()).isEqualByComparingTo("150.00");
        assertThat(resp.totAviBal()).isEqualByComparingTo("130.00");
        assertThat(resp.allowedBankCds())
                .isEqualTo(OwnAccountTransferConstants.ALLOWED_BANK_CDS_DEFAULT);

        verifyAuditAppended(OwnAccountTransferAuditActions.LIST_SOURCE_ACCOUNTS);
    }

    @Test
    void list_balanceTimestamps_shippedAsRawUtc() {
        // acct_mstr stores ACCT_BAL_LST_INQ_DTM / ACCT_BAL_UPD_DTM as a
        // UTC wall-clock (legacy DateTimeUtils.utcNow() + refactor
        // utcNow14()). The source-row projection ships them RAW (no
        // server-side zone shift) — the frontend owns the single
        // UTC→Vietnam-local conversion (composer "Last updated time" via
        // formatUtcDtmByLanguage, main "Last Refresh" via humanizeDatetime,
        // which parses UTC). This matches AccountService, which also ships
        // these columns raw; the prior server-side pre-shift double-shifted
        // the relative-time cell.
        setUpAdmin();
        AcctMstr a = acct(SHINHAN, "0001", new BigDecimal("100.00"), new BigDecimal("90.00"));
        a.setAcctBalLstInqDtm("20260601075200"); // 07:52 UTC
        a.setAcctBalUpdDtm("20260601075200");
        when(acctMstrRepo.findOwnTransferSourceRows(
                eq(TENANT), anyList(), eq(false), anyString(), any(Sort.class)))
                .thenReturn(List.of(a));
        stubBankName(SHINHAN, "Shinhan VN");

        SourceAccountListResponse resp = service.listSourceAccounts(null, null, null);

        assertThat(resp.list()).hasSize(1);
        // Shipped verbatim — no UTC→UTC+7 shift on the server side.
        assertThat(resp.list().get(0).acctBalLstInqDtm()).isEqualTo("20260601075200");
        assertThat(resp.list().get(0).acctBalUpdDtm()).isEqualTo("20260601075200");
    }

    @Test
    void list_haengsung_forcesVietinOnlyAndPassesPrdtTypeFlag() {
        setUpHaengsungAdmin();
        when(acctMstrRepo.findOwnTransferSourceRows(
                eq(TENANT), eq(OwnAccountTransferConstants.ALLOWED_BANK_CDS_HAENGSUNG),
                eq(true), anyString(), any(Sort.class)))
                .thenReturn(List.of());

        SourceAccountListResponse resp = service.listSourceAccounts(null, null, null);

        assertThat(resp.allowedBankCds())
                .containsExactly(VIETIN);
        assertThat(resp.list()).isEmpty();
    }

    @Test
    void list_user_filtersByGrants_keepsRowsWithW_dropsRowsWithoutAny() {
        setUpUser();
        AcctMstr granted = acct(SHINHAN, "0001", new BigDecimal("100"), new BigDecimal("90"));
        AcctMstr noGrant = acct(VIETIN,  "0002", new BigDecimal("50"),  new BigDecimal("40"));
        when(acctMstrRepo.findOwnTransferSourceRows(any(), anyList(), anyBoolean(), anyString(), any()))
                .thenReturn(List.of(granted, noGrant));
        when(acctAuthRepo.findByBizKeyAndUserIdAndUseYn(TENANT, USER, "Y"))
                .thenReturn(List.of(grant(SHINHAN, "0001", "W")));
        stubBankName(SHINHAN, "Shinhan VN");

        SourceAccountListResponse resp = service.listSourceAccounts(null, null, null);

        assertThat(resp.list()).hasSize(1);
        assertThat(resp.list().get(0).bankCd()).isEqualTo(SHINHAN);
        assertThat(resp.list().get(0).canTransfer()).isTrue();
    }

    @Test
    void list_user_viewOnlyGrant_doesNotMarkCanTransfer() {
        setUpUser();
        AcctMstr viewOnly = acct(SHINHAN, "0001", new BigDecimal("100"), new BigDecimal("90"));
        when(acctMstrRepo.findOwnTransferSourceRows(any(), anyList(), anyBoolean(), anyString(), any()))
                .thenReturn(List.of(viewOnly));
        when(acctAuthRepo.findByBizKeyAndUserIdAndUseYn(TENANT, USER, "Y"))
                .thenReturn(List.of(grant(SHINHAN, "0001", "V")));
        stubBankName(SHINHAN, "Shinhan VN");

        SourceAccountListResponse resp = service.listSourceAccounts(null, null, null);

        assertThat(resp.list()).hasSize(1);
        assertThat(resp.list().get(0).canTransfer()).isFalse();
    }

    @Test
    void list_unknownBankFilter_coercedToAll() {
        setUpAdmin();
        when(acctMstrRepo.findOwnTransferSourceRows(any(), anyList(), anyBoolean(), anyString(), any()))
                .thenReturn(List.of());

        service.listSourceAccounts("99999999", null, null);

        // Should request the full default list, not the unsupported "99999999".
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> banks = ArgumentCaptor.forClass((Class) List.class);
        verify(acctMstrRepo).findOwnTransferSourceRows(
                eq(TENANT), banks.capture(), eq(false), anyString(), any(Sort.class));
        assertThat(banks.getValue())
                .isEqualTo(OwnAccountTransferConstants.ALLOWED_BANK_CDS_DEFAULT);
    }

    /* =================================================================
     *  applyBalanceRefresh
     * ================================================================= */

    @Test
    void balanceRefresh_success_withBalanceDelta_writesCorcBfBalAndUpdDtm() {
        setUpAdmin();
        AcctMstr existing = acct(SHINHAN, "0001", new BigDecimal("100"), new BigDecimal("90"));
        when(acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(TENANT, SHINHAN, "0001"))
                .thenReturn(Optional.of(existing));
        when(acctMstrRepo.applyOwnTransferBalanceRefresh(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        BalanceRefreshRequest req = new BalanceRefreshRequest(
                SHINHAN, "0001",
                new BigDecimal("150"),         // bal — different from existing 100
                new BigDecimal("140"),
                OwnAccountTransferConstants.ENGINE_SUCCESS_STTS,
                "Refreshed",
                "VND");

        BalanceRefreshResponse resp = service.applyBalanceRefresh(req);

        // newBal + newCorcBfBal + newUpdDtm + newRealAmt should all be non-null
        verify(acctMstrRepo).applyOwnTransferBalanceRefresh(
                eq(TENANT), eq(SHINHAN), eq("0001"),
                eq(OwnAccountTransferConstants.ENGINE_SUCCESS_STTS), eq("Refreshed"),
                anyString(), eq(ADMIN),
                eq(new BigDecimal("150")),
                eq(new BigDecimal("140")),
                eq(existing.getBal()),         // newCorcBfBal = pre-existing bal
                anyString());

        assertThat(resp.bankCd()).isEqualTo(SHINHAN);
        assertThat(resp.bal()).isEqualByComparingTo("150");
        assertThat(resp.ccyCd()).isEqualTo("VND");
        verifyAuditAppended(OwnAccountTransferAuditActions.BALANCE_REFRESH);
    }

    @Test
    void balanceRefresh_success_sameBalance_skipsCorcBfBalUpdate() {
        setUpAdmin();
        AcctMstr existing = acct(SHINHAN, "0001", new BigDecimal("100"), new BigDecimal("90"));
        when(acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(TENANT, SHINHAN, "0001"))
                .thenReturn(Optional.of(existing));
        when(acctMstrRepo.applyOwnTransferBalanceRefresh(
                any(), any(), any(), any(), any(), any(), any(),
                isNull(), any(), isNull(), isNull()))
                .thenReturn(1);

        BalanceRefreshRequest req = new BalanceRefreshRequest(
                SHINHAN, "0001",
                new BigDecimal("100"),   // bal equal to existing
                new BigDecimal("95"),
                OwnAccountTransferConstants.ENGINE_SUCCESS_STTS,
                null,
                null);

        service.applyBalanceRefresh(req);

        verify(acctMstrRepo).applyOwnTransferBalanceRefresh(
                eq(TENANT), eq(SHINHAN), eq("0001"),
                anyString(), isNull(), anyString(), eq(ADMIN),
                isNull(),                                       // newBal = null (no delta)
                eq(new BigDecimal("95")),                        // newRealAmt set
                isNull(),                                       // newCorcBfBal = null
                isNull());                                      // newUpdDtm = null
    }

    @Test
    void balanceRefresh_failure_recordsStatusOnly_noBalUpdate() {
        setUpAdmin();
        AcctMstr existing = acct(SHINHAN, "0001", new BigDecimal("100"), new BigDecimal("90"));
        when(acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(TENANT, SHINHAN, "0001"))
                .thenReturn(Optional.of(existing));
        when(acctMstrRepo.applyOwnTransferBalanceRefresh(
                any(), any(), any(), any(), any(), any(), any(),
                isNull(), isNull(), isNull(), isNull()))
                .thenReturn(1);

        BalanceRefreshRequest req = new BalanceRefreshRequest(
                SHINHAN, "0001", new BigDecimal("999"), new BigDecimal("999"),
                "80003270",   // engine failure code
                "OTP expired", "VND");

        BalanceRefreshResponse resp = service.applyBalanceRefresh(req);

        verify(acctMstrRepo).applyOwnTransferBalanceRefresh(
                anyString(), anyString(), anyString(),
                eq("80003270"), eq("OTP expired"), anyString(), anyString(),
                isNull(), isNull(), isNull(), isNull());
        // Response BAL echoes the pre-existing value on failure.
        assertThat(resp.bal()).isEqualByComparingTo("100");
    }

    @Test
    void balanceRefresh_user_withoutWithdrawAuth_throwsForbidden() {
        setUpUser();
        AcctMstr existing = acct(SHINHAN, "0001", new BigDecimal("100"), new BigDecimal("90"));
        when(acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(TENANT, SHINHAN, "0001"))
                .thenReturn(Optional.of(existing));
        when(acctAuthRepo.findAllByAccount(TENANT, SHINHAN, "0001", "W"))
                .thenReturn(List.of());

        BalanceRefreshRequest req = new BalanceRefreshRequest(
                SHINHAN, "0001", new BigDecimal("100"), null,
                OwnAccountTransferConstants.ENGINE_SUCCESS_STTS, null, "VND");

        assertThatThrownBy(() -> service.applyBalanceRefresh(req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE_OWN_ACCOUNT_TRANSFER_FORBIDDEN");

        verify(acctMstrRepo, never()).applyOwnTransferBalanceRefresh(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void balanceRefresh_wrongTenant_throwsAccountNotFound() {
        setUpAdmin();
        when(acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(TENANT, SHINHAN, "0001"))
                .thenReturn(Optional.empty());

        BalanceRefreshRequest req = new BalanceRefreshRequest(
                SHINHAN, "0001", new BigDecimal("100"), null,
                OwnAccountTransferConstants.ENGINE_SUCCESS_STTS, null, "VND");

        assertThatThrownBy(() -> service.applyBalanceRefresh(req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE_OWN_ACCOUNT_TRANSFER_ACCOUNT_NOT_FOUND");
    }

    @Test
    void balanceRefresh_inactiveAccount_throwsInactive() {
        setUpAdmin();
        AcctMstr inactive = acct(SHINHAN, "0001", new BigDecimal("100"), new BigDecimal("90"));
        inactive.setActvYn("N");
        when(acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(TENANT, SHINHAN, "0001"))
                .thenReturn(Optional.of(inactive));

        BalanceRefreshRequest req = new BalanceRefreshRequest(
                SHINHAN, "0001", new BigDecimal("100"), null,
                OwnAccountTransferConstants.ENGINE_SUCCESS_STTS, null, "VND");

        assertThatThrownBy(() -> service.applyBalanceRefresh(req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE_OWN_ACCOUNT_TRANSFER_ACCOUNT_INACTIVE");
    }

    @Test
    void balanceRefresh_nonVndCcy_throwsInvalidCcy() {
        setUpAdmin();

        BalanceRefreshRequest req = new BalanceRefreshRequest(
                SHINHAN, "0001", new BigDecimal("100"), null,
                OwnAccountTransferConstants.ENGINE_SUCCESS_STTS, null, "USD");

        assertThatThrownBy(() -> service.applyBalanceRefresh(req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE_OWN_ACCOUNT_TRANSFER_INVALID_CCY");
    }

    /* =================================================================
     *  recordEngineConnectionCheck
     * ================================================================= */

    @Test
    void engineConnectionCheck_writesAuditOnly() {
        setUpAdmin();

        EngineConnectionCheckRequest req = new EngineConnectionCheckRequest(
                "OK", "COCON", "NXSAS 2025.03.11.0",
                "Mozilla/5.0", "Win32", null, null, null);

        service.recordEngineConnectionCheck(req);

        ArgumentCaptor<AuditLogContext> ctx = ArgumentCaptor.forClass(AuditLogContext.class);
        verify(auditLog).append(ctx.capture());
        AuditLogContext captured = ctx.getValue();
        assertThat(captured.wsvcId()).isEqualTo("own-account-transfer");
        assertThat(captured.detailKey())
                .isEqualTo("audit.own_account_transfer.engine_connection_check.detail");
        assertThat(captured.refNo()).isEqualTo("NXISAS");
        assertThat(captured.refNo1()).isEqualTo("OK");
        assertThat(captured.refNo2()).contains("COCON");
        assertThat(captured.refNo2()).contains("NXSAS 2025.03.11.0");
    }

    /* =================================================================
     *  lookupNapasBankInfo
     * ================================================================= */

    @Test
    void napasLookup_hit_returnsRowAndAudits() {
        setUpAdmin();
        NapasBankInfo entity = new NapasBankInfo();
        entity.setNapasBankCd("970424");
        entity.setBankCd(SHINHAN);
        when(napasBankInfoRepo.findById("970424")).thenReturn(Optional.of(entity));

        NapasBankInfoRow row = service.lookupNapasBankInfo("970424");

        assertThat(row.napasBankCd()).isEqualTo("970424");
        assertThat(row.bankCd()).isEqualTo(SHINHAN);
        assertThat(row.shortName()).isNull();   // composer-scope projection
        verifyAuditAppended(OwnAccountTransferAuditActions.LOOKUP_NAPAS_BANK_INFO);
    }

    @Test
    void napasLookup_miss_throwsNapasNotFound() {
        setUpAdmin();
        when(napasBankInfoRepo.findById("000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.lookupNapasBankInfo("000000"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE_OWN_ACCOUNT_TRANSFER_NAPAS_NOT_FOUND");

        verify(auditLog, never()).append(any());
    }

    /* =================================================================
     *  listTargetAccounts
     * ================================================================= */

    @Test
    void targets_admin_excludesSourceRow() {
        setUpAdmin();
        AcctMstr src   = acct(SHINHAN, "0001", new BigDecimal("100"), new BigDecimal("90"));
        AcctMstr other = acct(VIETIN,  "0002", new BigDecimal("50"),  new BigDecimal("40"));
        when(acctMstrRepo.findOwnTransferSourceRows(any(), anyList(), anyBoolean(), anyString(), any()))
                .thenReturn(List.of(src, other));
        stubBankName(VIETIN, "Vietin Bank");
        when(napasBankInfoRepo.findFirstByBankCdOrderByNapasBankCdAsc(VIETIN))
                .thenReturn(java.util.Optional.empty());

        var resp = service.listTargetAccounts(SHINHAN, "0001");

        assertThat(resp.list()).hasSize(1);
        assertThat(resp.list().get(0).bankCd()).isEqualTo(VIETIN);
        assertThat(resp.list().get(0).kind())
                .isEqualTo("OWN_ACCOUNT");
        verifyAuditAppended(OwnAccountTransferAuditActions.LIST_TARGET_ACCOUNTS);
    }

    @Test
    void targets_user_filtersByGrant_excludesSourceRow() {
        setUpUser();
        AcctMstr src   = acct(SHINHAN, "0001", new BigDecimal("100"), new BigDecimal("90"));
        AcctMstr withGrant = acct(VIETIN,  "0002", new BigDecimal("50"),  new BigDecimal("40"));
        AcctMstr noGrant   = acct(VIETIN,  "9999", new BigDecimal("20"),  new BigDecimal("20"));
        when(acctMstrRepo.findOwnTransferSourceRows(any(), anyList(), anyBoolean(), anyString(), any()))
                .thenReturn(List.of(src, withGrant, noGrant));
        when(acctAuthRepo.findByBizKeyAndUserIdAndUseYn(TENANT, USER, "Y"))
                .thenReturn(List.of(grant(VIETIN, "0002", "W")));
        stubBankName(VIETIN, "Vietin Bank");
        when(napasBankInfoRepo.findFirstByBankCdOrderByNapasBankCdAsc(VIETIN))
                .thenReturn(java.util.Optional.empty());

        var resp = service.listTargetAccounts(SHINHAN, "0001");

        assertThat(resp.list()).hasSize(1);
        assertThat(resp.list().get(0).acctNo()).isEqualTo("0002");
    }

    /* =================================================================
     *  recordScrapingResult
     * ================================================================= */

    /** Legacy parity — an engine-reported amount that differs from the
     *  composed amount is LOGGED, never rejected, and the row is written
     *  with the BANK's figure.
     *
     *  <p>Legacy has no requested-vs-actual comparison at all: the composer
     *  sends one {@code TRSC_AMT} (the bank's {@code remitAmt} on success,
     *  the entered amount on failure — {@code trns_0001_007.ts:2158, 2169})
     *  and {@code trns_0002_001_c001_act.jsp} validates only that it is
     *  positive. Rejecting here would discard the only record of a transfer
     *  the bank may already have executed. */
    @Test
    void scraping_amountMismatch_persistsLegacyParity() {
        setUpAdmin();
        var src = acct(SHINHAN, "0001", new BigDecimal("100"), new BigDecimal("90"));
        var tgt = acct(SHINHAN, "0002", new BigDecimal("50"),  new BigDecimal("50"));
        when(acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(TENANT, SHINHAN, "0001"))
                .thenReturn(java.util.Optional.of(src));
        when(acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(TENANT, SHINHAN, "0002"))
                .thenReturn(java.util.Optional.of(tgt));
        when(legacyFunctions.nextNapasRemitSeqNo(TENANT)).thenReturn("501");
        when(napasRemitTrnsHstrRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var drift = req("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "SAME_BANK", SHINHAN, "0001", SHINHAN, null, "0002",
                "1000", "1001", "", "00000000");

        var resp = service.recordScrapingResult(drift);

        // Recorded, not rejected — and the bank's figure is what lands.
        assertThat(resp.status()).isEqualTo("success");
        assertThat(resp.seqNo()).isEqualTo("501");
        var saved = ArgumentCaptor.forClass(NapasRemitTrnsHstr.class);
        verify(napasRemitTrnsHstrRepo).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getTrscAmt()).isEqualByComparingTo(new BigDecimal("1001"));
        verifyAuditAppended(OwnAccountTransferAuditActions.SCRAPING_RESULT_OK);
    }

    /** Legacy parity — a blank {@code trns_stts} is accepted and stored.
     *  Legacy's {@code WSVC.trns_0002_001_c001.xml} does not require the
     *  field, {@code valiateInput} never checks it, and the composer
     *  initialises it to {@code ""} ({@code trns_0001_007.ts:2140}). Blank
     *  is a non-success status, so the row is audited as a reject outcome
     *  while still being persisted. */
    @Test
    void scraping_blankTrnsStts_persistsAsFailure() {
        setUpAdmin();
        var src = acct(SHINHAN, "0001", new BigDecimal("100"), new BigDecimal("90"));
        var tgt = acct(SHINHAN, "0002", new BigDecimal("50"),  new BigDecimal("50"));
        when(acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(TENANT, SHINHAN, "0001"))
                .thenReturn(java.util.Optional.of(src));
        when(acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(TENANT, SHINHAN, "0002"))
                .thenReturn(java.util.Optional.of(tgt));
        when(legacyFunctions.nextNapasRemitSeqNo(TENANT)).thenReturn("502");
        when(napasRemitTrnsHstrRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var blank = req("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1",
                "SAME_BANK", SHINHAN, "0001", SHINHAN, null, "0002",
                "1000", "1000", "", "");

        var resp = service.recordScrapingResult(blank);

        assertThat(resp.status()).isEqualTo("failed");
        var saved = ArgumentCaptor.forClass(NapasRemitTrnsHstr.class);
        verify(napasRemitTrnsHstrRepo).saveAndFlush(saved.capture());
        // Stored verbatim — never the "99999999" filter sentinel.
        assertThat(saved.getValue().getTrnsStts()).isEmpty();
        verifyAuditAppended(OwnAccountTransferAuditActions.SCRAPING_RESULT_REJECT);
    }

    @Test
    void scraping_nonVnd_throwsInvalidCcy() {
        setUpAdmin();

        var bad = new com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.ScrapingResultIntakeRequest(
                "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                "SAME_BANK", SHINHAN, "0001", SHINHAN, null, "0002", null,
                "USD", "1000", "1000", "BANKTX001", "00000000", null,
                null, null, null, null,
                /*engineOutAcctNo*/ null, /*engineInAcctNo*/ null, /*engineCcyCd*/ null,
                null, null,
                /*acctPwd*/ null, /*trnsPwd*/ null);

        assertThatThrownBy(() -> service.recordScrapingResult(bad))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE_OWN_ACCOUNT_TRANSFER_INVALID_CCY");
    }

    @Test
    void scraping_sameBankMismatch_throws() {
        setUpAdmin();

        var bad = req("cccccccc-cccc-cccc-cccc-cccccccccccc",
                "SAME_BANK", SHINHAN, "0001", VIETIN, null, "0002",
                "1000", "1000", "BANKTX001", "00000000");

        assertThatThrownBy(() -> service.recordScrapingResult(bad))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE_OAT_SAME_BANK_MISMATCH");
    }

    @Test
    void scraping_sourceNotFound_throws() {
        setUpAdmin();
        when(acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(TENANT, SHINHAN, "0001"))
                .thenReturn(java.util.Optional.empty());

        var bad = req("dddddddd-dddd-dddd-dddd-dddddddddddd",
                "SAME_BANK", SHINHAN, "0001", SHINHAN, null, "0002",
                "1000", "1000", "BANKTX001", "00000000");

        assertThatThrownBy(() -> service.recordScrapingResult(bad))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE_OAT_SOURCE_NOT_FOUND");
    }

    @Test
    void scraping_user_withoutWithdrawAuth_throwsForbidden() {
        setUpUser();
        var src = acct(SHINHAN, "0001", new BigDecimal("100"), new BigDecimal("90"));
        when(acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(TENANT, SHINHAN, "0001"))
                .thenReturn(java.util.Optional.of(src));
        when(acctAuthRepo.findAllByAccount(TENANT, SHINHAN, "0001", "W"))
                .thenReturn(List.of());   // no W grant

        var bad = req("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
                "SAME_BANK", SHINHAN, "0001", SHINHAN, null, "0002",
                "1000", "1000", "BANKTX001", "00000000");

        assertThatThrownBy(() -> service.recordScrapingResult(bad))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE_OAT_SOURCE_FORBIDDEN");
    }

    @Test
    void scraping_happyPath_persistsAndAudits() {
        setUpAdmin();
        var src = acct(SHINHAN, "0001", new BigDecimal("100"), new BigDecimal("90"));
        var tgt = acct(SHINHAN, "0002", new BigDecimal("50"),  new BigDecimal("50"));
        when(acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(TENANT, SHINHAN, "0001"))
                .thenReturn(java.util.Optional.of(src));
        when(acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(TENANT, SHINHAN, "0002"))
                .thenReturn(java.util.Optional.of(tgt));
        when(legacyFunctions.nextNapasRemitSeqNo(TENANT)).thenReturn("123");
        when(napasRemitTrnsHstrRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var resp = service.recordScrapingResult(req("ffffffff-ffff-ffff-ffff-ffffffffffff",
                "SAME_BANK", SHINHAN, "0001", SHINHAN, null, "0002",
                "1000", "1000", "BANKTX042", "00000000"));

        assertThat(resp.alreadyRecorded()).isFalse();
        assertThat(resp.seqNo()).isEqualTo("123");
        assertThat(resp.status()).isEqualTo("success");
        verifyAuditAppended(OwnAccountTransferAuditActions.SCRAPING_RESULT_OK);
    }

    /** Legacy validateTrnsStlmId parity — a second intake carrying a
     *  bank_tx_id that already exists (unsettled) is rejected as a
     *  duplicate and nothing is persisted. Mirrors
     *  trns_0002_001_c001_act.jsp:94-99 (WBE0003 → WBE_OAT_DUPLICATE_BANK_TX_ID). */
    @Test
    void scraping_duplicateBankTxId_throwsAndDoesNotPersist() {
        setUpAdmin();
        var src = acct(SHINHAN, "0001", new BigDecimal("100"), new BigDecimal("90"));
        var tgt = acct(SHINHAN, "0002", new BigDecimal("50"),  new BigDecimal("50"));
        when(acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(TENANT, SHINHAN, "0001"))
                .thenReturn(java.util.Optional.of(src));
        when(acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(TENANT, SHINHAN, "0002"))
                .thenReturn(java.util.Optional.of(tgt));
        when(napasRemitTrnsHstrRepo.existsByBizKeyAndTrnsStlmIdAndTrnsStlmYnNot(
                TENANT, "BANKTX777", "Y")).thenReturn(true);

        var dup = req("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "SAME_BANK", SHINHAN, "0001", SHINHAN, null, "0002",
                "1000", "1000", "BANKTX777", "00000000");

        assertThatThrownBy(() -> service.recordScrapingResult(dup))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE_OAT_DUPLICATE_BANK_TX_ID");
        verify(napasRemitTrnsHstrRepo, never()).saveAndFlush(any());
    }

    /* =================================================================
     *  Legacy-parity sweep 2026-08-18 — FEE_DFR_SBJ + engine echo
     *  docs/bugs/internal-transfer-legacy-parity-sweep-2026-08-18.md
     * ================================================================= */

    /** Legacy persists the radio's own value: {@code "1"} sender pays
     *  ({@code trns_0001_007_view.jsp:177}, {@code .ts:600}, {@code :2182};
     *  {@code FeeDfrSbjType.SENDER_PAYS("1")}). The wire keeps {@code A}. */
    @Test
    void scraping_feeBearerA_persistsLegacySenderPaysCode() {
        setUpAdmin();
        var saved = stubIntakeAndCapture("601");

        service.recordScrapingResult(reqWithEcho(
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0001", "00000000", "A",
                null, null, null));

        verify(napasRemitTrnsHstrRepo).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getFeeDfrSbj()).isEqualTo("1");
    }

    /** {@code "2"} recipient pays ({@code view.jsp:181},
     *  {@code FeeDfrSbjType.RECEIPIENT_PAYS("2")}). */
    @Test
    void scraping_feeBearerB_persistsLegacyRecipientPaysCode() {
        setUpAdmin();
        var saved = stubIntakeAndCapture("602");

        service.recordScrapingResult(reqWithEcho(
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0002", "00000000", "B",
                null, null, null));

        verify(napasRemitTrnsHstrRepo).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getFeeDfrSbj()).isEqualTo("2");
    }

    /** The A/B→1/2 conversion is a pure boundary mapping: unknown values pass
     *  through, so a caller already sending the legacy code is idempotent and
     *  the unused {@code S} is never silently rewritten. */
    @Test
    void feeDfrSbjMapping_isIdempotentAndPassesUnknownsThrough() {
        assertThat(OwnAccountTransferConstants.toLegacyFeeDfrSbj("A")).isEqualTo("1");
        assertThat(OwnAccountTransferConstants.toLegacyFeeDfrSbj("B")).isEqualTo("2");
        assertThat(OwnAccountTransferConstants.toLegacyFeeDfrSbj("1")).isEqualTo("1");
        assertThat(OwnAccountTransferConstants.toLegacyFeeDfrSbj("2")).isEqualTo("2");
        assertThat(OwnAccountTransferConstants.toLegacyFeeDfrSbj("S")).isEqualTo("S");
        assertThat(OwnAccountTransferConstants.toLegacyFeeDfrSbj(null)).isNull();
    }

    /** Legacy overwrites OUT_ACCT_NO / IN_ACCT_NO / CCY_CD from
     *  {@code Result.transfer[0]} on success
     *  ({@code trns_0001_007.ts:2157-2158, 2163}). */
    @Test
    void scraping_engineEcho_persistsBankReturnedAccountsAndCurrency() {
        setUpAdmin();
        var saved = stubIntakeAndCapture("603");

        service.recordScrapingResult(reqWithEcho(
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0003", "00000000", "A",
                "DDA200543518", "DDA200612649", "VND"));

        verify(napasRemitTrnsHstrRepo).saveAndFlush(saved.capture());
        // Bank-normalized DDA form recorded verbatim …
        assertThat(saved.getValue().getOutAcctNo()).isEqualTo("DDA200543518");
        assertThat(saved.getValue().getInAcctNo()).isEqualTo("DDA200612649");
        assertThat(saved.getValue().getCcyCd()).isEqualTo("VND");
        // … while the ownership lookups still used the COMPOSED keys, so a
        // bank-normalized number can never reject an executed transfer.
        verify(acctMstrRepo).findByBizKeyAndBankCdAndAcctNo(TENANT, SHINHAN, "0001");
        verify(acctMstrRepo).findByBizKeyAndBankCdAndAcctNo(TENANT, SHINHAN, "0002");
    }

    /** Absent / blank echo ⇒ the composed values persist — legacy's failure
     *  branch ({@code trns_0001_007.ts:2167-2170}). */
    @Test
    void scraping_engineEchoAbsent_persistsComposedValues() {
        setUpAdmin();
        var saved = stubIntakeAndCapture("604");

        service.recordScrapingResult(reqWithEcho(
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0004", "8000FFFF", "B",
                null, "   ", ""));

        verify(napasRemitTrnsHstrRepo).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getOutAcctNo()).isEqualTo("0001");
        assertThat(saved.getValue().getInAcctNo()).isEqualTo("0002");
        assertThat(saved.getValue().getCcyCd()).isEqualTo("VND");
    }

    /** A non-VND currency the bank echoed is RECORDED, not rejected — the CCY
     *  guard runs on the pre-transfer {@code ccy_cd} only. */
    @Test
    void scraping_engineEchoNonVndCurrency_isRecordedNotRejected() {
        setUpAdmin();
        var saved = stubIntakeAndCapture("605");

        service.recordScrapingResult(reqWithEcho(
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0005", "00000000", "A",
                "0001", "0002", "USD"));

        verify(napasRemitTrnsHstrRepo).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getCcyCd()).isEqualTo("USD");
    }

    /** Compact factory for scraping-result request fixtures. */
    private static com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.ScrapingResultIntakeRequest
            req(String idem, String transferType,
                String srcBankCd, String srcAcctNo,
                String tgtBankCd, String tgtNapasBankCd, String tgtAcctNo,
                String requestedAmt, String actualAmt,
                String bankTxId, String trnsStts) {
        return new com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.ScrapingResultIntakeRequest(
                idem, transferType,
                srcBankCd, srcAcctNo,
                tgtBankCd, tgtNapasBankCd, tgtAcctNo, null,
                "VND", requestedAmt, actualAmt,
                bankTxId, trnsStts, null,
                null, null, null, null,
                /*engineOutAcctNo*/ null, /*engineInAcctNo*/ null, /*engineCcyCd*/ null,
                null, null,
                /*acctPwd*/ null, /*trnsPwd*/ null);
    }

    /** Variant that also sets {@code feeDfrSbj} and the three post-transfer
     *  engine-echo fields — used by the legacy-parity sweep tests. */
    private static com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.ScrapingResultIntakeRequest
            reqWithEcho(String idem, String trnsStts, String feeDfrSbj,
                        String engineOutAcctNo, String engineInAcctNo, String engineCcyCd) {
        return new com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.ScrapingResultIntakeRequest(
                idem, "SAME_BANK",
                SHINHAN, "0001",
                SHINHAN, null, "0002", null,
                "VND", "1000", "1000",
                "", trnsStts, null,
                /*fee*/ null, feeDfrSbj, /*rmrk*/ null, /*rmrk2*/ null,
                engineOutAcctNo, engineInAcctNo, engineCcyCd,
                null, null,
                /*acctPwd*/ null, /*trnsPwd*/ null);
    }

    /** Stub the two ownership lookups + the seq allocator so an intake reaches
     *  the INSERT, and capture the persisted row. */
    /* =================================================================
     *  Bank-account password persistence (Model A, 2026-08-26)
     *
     *  Regression cover for engine error 80002420 ("an account password
     *  was not entered"). The composer now collects the password on every
     *  transfer and sends it here; the service stores it to
     *  ACCT_MSTR.ACCT_PWD so the LIVE JEX system — which takes the value
     *  straight from that column and never shows a field
     *  (trns_0001_007.ts:675-682 → :1943) — keeps working on the same
     *  account.
     *
     *  Success-only is the load-bearing rule: because JEX consumes the
     *  column silently, storing a password the bank rejected would send
     *  that wrong value from the legacy screen with no chance to correct
     *  it, walking the bank's lockout ladder
     *  (80002421 → 80002422 → 80002423).
     * ================================================================= */

    @Test
    void scraping_success_persistsEncryptedAccountPassword() {
        setUpAdmin();
        stubIntakeAndCapture("701");
        when(bankCertCrypto.encrypt("s3cret")).thenReturn("ENC(s3cret)");

        service.recordScrapingResult(reqWithPasswords(
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0701", "00000000", "s3cret", null));

        // Ciphertext, never plaintext — and via the legacy-compatible
        // cipher so live JEX's decrypt() can still read it.
        verify(bankCertCrypto).encrypt("s3cret");
        verify(acctMstrRepo).applyTransferPasswords(
                eq(TENANT), eq(SHINHAN), eq("0001"),
                eq("ENC(s3cret)"), isNull(), anyString(), anyString());
    }

    @Test
    void scraping_success_persistsBothPasswordsWhenBothSent() {
        setUpAdmin();
        stubIntakeAndCapture("702");
        when(bankCertCrypto.encrypt("acctpw")).thenReturn("ENC(acctpw)");
        when(bankCertCrypto.encrypt("trnspw")).thenReturn("ENC(trnspw)");

        service.recordScrapingResult(reqWithPasswords(
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0702", "00000000", "acctpw", "trnspw"));

        verify(acctMstrRepo).applyTransferPasswords(
                eq(TENANT), eq(SHINHAN), eq("0001"),
                eq("ENC(acctpw)"), eq("ENC(trnspw)"), anyString(), anyString());
    }

    /** The bank rejected the transfer — the password may well be the
     *  reason. Storing it would poison the legacy screen's silent path. */
    @Test
    void scraping_engineFailure_doesNotPersistPassword() {
        setUpAdmin();
        stubIntakeAndCapture("703");

        service.recordScrapingResult(reqWithPasswords(
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0703", "80002421", "wrongpw", null));

        verify(bankCertCrypto, never()).encrypt(anyString());
        verify(acctMstrRepo, never()).applyTransferPasswords(
                anyString(), anyString(), anyString(),
                any(), any(), anyString(), anyString());
    }

    /** Vietcom / Vietin send no password at all — nothing to write, and
     *  the repository must not be touched (its COALESCE would be a no-op
     *  but the round-trip is still wasted work). */
    @Test
    void scraping_passwordlessBank_doesNotTouchAcctMstr() {
        setUpAdmin();
        stubIntakeAndCapture("704");

        service.recordScrapingResult(reqWithPasswords(
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0704", "00000000", null, null));

        verify(acctMstrRepo, never()).applyTransferPasswords(
                anyString(), anyString(), anyString(),
                any(), any(), anyString(), anyString());
    }

    /** A blank string is "not supplied", not "clear the stored value" —
     *  it must normalise to null so the repository's COALESCE leaves the
     *  existing ciphertext alone. */
    @Test
    void scraping_blankPassword_isTreatedAsAbsent() {
        setUpAdmin();
        stubIntakeAndCapture("705");

        service.recordScrapingResult(reqWithPasswords(
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0705", "00000000", "   ", null));

        verify(bankCertCrypto, never()).encrypt(anyString());
        verify(acctMstrRepo, never()).applyTransferPasswords(
                anyString(), anyString(), anyString(),
                any(), any(), anyString(), anyString());
    }

    /** The transfer already executed at the bank. A failed convenience
     *  write must never discard the only record of real money moving. */
    @Test
    void scraping_passwordWriteFailure_doesNotFailTheIntake() {
        setUpAdmin();
        var saved = stubIntakeAndCapture("706");
        when(bankCertCrypto.encrypt("s3cret")).thenReturn("ENC(s3cret)");
        when(acctMstrRepo.applyTransferPasswords(
                anyString(), anyString(), anyString(),
                any(), any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("db down"));

        service.recordScrapingResult(reqWithPasswords(
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0706", "00000000", "s3cret", null));

        // The history row is still written and the call still returns.
        verify(napasRemitTrnsHstrRepo).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getTrnsStts()).isEqualTo("00000000");
    }

    /** Same-bank Shinhan intake carrying the two password fields. */
    private static com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.ScrapingResultIntakeRequest
            reqWithPasswords(String idem, String trnsStts, String acctPwd, String trnsPwd) {
        return new com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.ScrapingResultIntakeRequest(
                idem, "SAME_BANK",
                SHINHAN, "0001",
                SHINHAN, null, "0002", null,
                "VND", "1000", "1000",
                "", trnsStts, null,
                /*fee*/ null, /*feeDfrSbj*/ null, /*rmrk*/ null, /*rmrk2*/ null,
                /*engineOutAcctNo*/ null, /*engineInAcctNo*/ null, /*engineCcyCd*/ null,
                null, null,
                acctPwd, trnsPwd);
    }

    private ArgumentCaptor<NapasRemitTrnsHstr> stubIntakeAndCapture(String seqNo) {
        var src = acct(SHINHAN, "0001", new BigDecimal("100"), new BigDecimal("90"));
        var tgt = acct(SHINHAN, "0002", new BigDecimal("50"),  new BigDecimal("50"));
        when(acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(TENANT, SHINHAN, "0001"))
                .thenReturn(java.util.Optional.of(src));
        when(acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(TENANT, SHINHAN, "0002"))
                .thenReturn(java.util.Optional.of(tgt));
        when(legacyFunctions.nextNapasRemitSeqNo(TENANT)).thenReturn(seqNo);
        when(napasRemitTrnsHstrRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        return ArgumentCaptor.forClass(NapasRemitTrnsHstr.class);
    }

    /* =================================================================
     *  Fixtures
     * ================================================================= */

    private void setUpAdmin() {
        TenantContext.set(ADMIN, ADMIN, TENANT, LangCd.EN,
                TenantContext.AUTH_GR_ADMIN, "F");
    }

    private void setUpUser() {
        TenantContext.set(USER, USER_NM, TENANT, LangCd.EN,
                TenantContext.AUTH_GR_USER, "F");
    }

    private void setUpHaengsungAdmin() {
        TenantContext.set(ADMIN, ADMIN, TENANT, LangCd.EN,
                TenantContext.AUTH_GR_ADMIN, "H");
    }

    private static AcctMstr acct(String bankCd, String acctNo, BigDecimal bal, BigDecimal realAmt) {
        AcctMstr a = new AcctMstr();
        a.setBizKey(TENANT);
        a.setBankCd(bankCd);
        a.setAcctNo(acctNo);
        a.setAcctTypeCd("03");
        a.setCcyCd("VND");
        a.setBal(bal);
        a.setRealAmt(realAmt);
        a.setActvYn("Y");
        a.setAcctNickNm(bankCd + " " + acctNo);
        return a;
    }

    private static AcctAuthInfo grant(String bankCd, String acctNo, String authType) {
        AcctAuthInfo g = new AcctAuthInfo();
        g.setBizKey(TENANT);
        g.setUserId(USER);
        g.setBankCd(bankCd);
        g.setAcctNo(acctNo);
        g.setAuthType(authType);
        g.setUseYn("Y");
        return g;
    }

    private void stubBankName(String bankCd, String name) {
        when(bankCdResolver.resolveBankNm(bankCd)).thenReturn(name);
    }

    private void verifyAuditAppended(String expectedAction) {
        ArgumentCaptor<AuditLogContext> ctx = ArgumentCaptor.forClass(AuditLogContext.class);
        verify(auditLog, times(1)).append(ctx.capture());
        AuditLogContext captured = ctx.getValue();
        assertThat(captured.wsvcId()).isEqualTo("own-account-transfer");
        String expectedKey = "audit.own_account_transfer."
                + expectedAction.toLowerCase() + ".detail";
        assertThat(captured.detailKey()).isEqualTo(expectedKey);
    }
}
