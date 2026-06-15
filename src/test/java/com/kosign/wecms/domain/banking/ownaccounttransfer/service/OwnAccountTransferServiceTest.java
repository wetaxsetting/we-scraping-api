package com.kosign.wecms.domain.banking.ownaccounttransfer.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.entity.BankInfm;
import com.kosign.wecms.domain.banking.entity.NapasBankInfo;
import com.kosign.wecms.domain.banking.ownaccounttransfer.OwnAccountTransferAuditActions;
import com.kosign.wecms.domain.banking.ownaccounttransfer.OwnAccountTransferConstants;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.BalanceRefreshRequest;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.EngineConnectionCheckRequest;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.BalanceRefreshResponse;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.NapasBankInfoRow;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.SourceAccountListResponse;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.repository.BankInfmRepository;
import com.kosign.wecms.domain.banking.repository.NapasBankInfoRepository;
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
    @Mock private BankInfmRepository           bankInfmRepo;
    @Mock private NapasBankInfoRepository      napasBankInfoRepo;
    @Mock private com.kosign.wecms.domain.banking.repository.NapasRemitTrnsHstrRepository napasRemitTrnsHstrRepo;
    @Mock private AuditLogService              auditLog;
    @Mock private com.kosign.wecms.common.legacy.LegacyFunctionRepository legacyFunctions;

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
    void list_balanceTimestamps_convertedFromUtcToProjectZoneUtcPlus7() {
        // acct_mstr stores ACCT_BAL_LST_INQ_DTM / ACCT_BAL_UPD_DTM as a
        // UTC wall-clock (legacy DateTimeUtils.utcNow() + refactor
        // utcNow14()). The source-row projection must convert it to the
        // project display zone (Asia/Ho_Chi_Minh, UTC+7) — mirroring
        // legacy read-time normalizeResult(..., loginUser) — so the
        // composer "Last updated time" + main "Last Refresh" don't show
        // times 7h behind local.
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
        // 07:52 UTC → 14:52 (UTC+7), same day.
        assertThat(resp.list().get(0).acctBalLstInqDtm()).isEqualTo("20260601145200");
        assertThat(resp.list().get(0).acctBalUpdDtm()).isEqualTo("20260601145200");
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
        when(napasBankInfoRepo.findByBankCd(VIETIN)).thenReturn(java.util.Optional.empty());

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
        when(napasBankInfoRepo.findByBankCd(VIETIN)).thenReturn(java.util.Optional.empty());

        var resp = service.listTargetAccounts(SHINHAN, "0001");

        assertThat(resp.list()).hasSize(1);
        assertThat(resp.list().get(0).acctNo()).isEqualTo("0002");
    }

    /* =================================================================
     *  recordScrapingResult
     * ================================================================= */

    @Test
    void scraping_amountMismatch_throws() {
        setUpAdmin();

        var bad = req("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "SAME_BANK", SHINHAN, "0001", SHINHAN, null, "0002",
                "1000", "1001", "BANKTX001", "00000000");

        assertThatThrownBy(() -> service.recordScrapingResult(bad))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE_OAT_AMOUNT_MISMATCH");
    }

    @Test
    void scraping_nonVnd_throwsInvalidCcy() {
        setUpAdmin();

        var bad = new com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.ScrapingResultIntakeRequest(
                "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                "SAME_BANK", SHINHAN, "0001", SHINHAN, null, "0002", null,
                "USD", "1000", "1000", "BANKTX001", "00000000", null,
                null, null, null, null, null, null);

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
                null, null, null, null, null, null);
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
        BankInfm b = new BankInfm();
        b.setBankCd(bankCd);
        b.setRprsBankNm(name);
        when(bankInfmRepo.findById(bankCd)).thenReturn(Optional.of(b));
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
