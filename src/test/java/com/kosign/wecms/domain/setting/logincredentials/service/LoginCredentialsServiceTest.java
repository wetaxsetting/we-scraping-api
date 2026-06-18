package com.kosign.wecms.domain.setting.logincredentials.service;

import com.kosign.wecms.common.auditlog.repository.AuditLogEntryRepository;
import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.entity.BankCertInfo;
import com.kosign.wecms.domain.banking.bankcredentials.repository.BankCertInfoRepository;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCertCryptoService;
import com.kosign.wecms.domain.banking.nxisas.BankScrapingService;
import com.kosign.wecms.domain.banking.nxisas.dto.ScrapeSessionResponse;
import com.kosign.wecms.domain.banking.nxisas.dto.VerifyCredentialsRequest;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.repository.BankInfmRepository;
import com.kosign.wecms.domain.banking.service.AccountRegistrationService;
import com.kosign.wecms.domain.setting.logincredentials.dto.GdtCredentialRequest;
import com.kosign.wecms.domain.setting.logincredentials.dto.GdtCredentialUpdateRequest;
import com.kosign.wecms.domain.setting.logincredentials.entity.GdtCertInfo;
import com.kosign.wecms.domain.setting.logincredentials.repository.BankFormInfoRepository;
import com.kosign.wecms.domain.setting.logincredentials.repository.BankPickerRepository;
import com.kosign.wecms.domain.setting.logincredentials.repository.GdtCertInfoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the legacy-parity fixes on {@link LoginCredentialsService}:
 * GDT verify-before-persist (flag-gated), hard delete, and the audit
 * dual-read (slug + legacy WSVC ids).
 */
class LoginCredentialsServiceTest {

    private static final String BIZ = "VN_VNM0004_000001";
    private static final String USER = "tester";

    private BankPickerRepository bankPickerRepo;
    private BankFormInfoRepository bankFormInfoRepo;
    private BankCertInfoRepository bankRepo;
    private GdtCertInfoRepository gdtRepo;
    private BankInfmRepository bankInfmRepo;
    private AcctMstrRepository acctMstrRepo;
    private BankCertCryptoService crypto;
    private AuditLogService auditLogService;
    private AuditLogEntryRepository auditLogReadRepo;
    private BankScrapingService bankScrapingService;
    private LegacyFunctionRepository legacyFunctions;
    private AccountRegistrationService accountRegistrationService;

    private LoginCredentialsService service;
    private MockedStatic<TenantContext> tenant;

    @BeforeEach
    void setUp() {
        bankPickerRepo = mock(BankPickerRepository.class);
        bankFormInfoRepo = mock(BankFormInfoRepository.class);
        bankRepo = mock(BankCertInfoRepository.class);
        gdtRepo = mock(GdtCertInfoRepository.class);
        bankInfmRepo = mock(BankInfmRepository.class);
        acctMstrRepo = mock(AcctMstrRepository.class);
        crypto = mock(BankCertCryptoService.class);
        auditLogService = mock(AuditLogService.class);
        auditLogReadRepo = mock(AuditLogEntryRepository.class);
        bankScrapingService = mock(BankScrapingService.class);
        legacyFunctions = mock(LegacyFunctionRepository.class);
        accountRegistrationService = mock(AccountRegistrationService.class);

        service = new LoginCredentialsService(
                bankPickerRepo, bankFormInfoRepo, bankRepo, gdtRepo, bankInfmRepo,
                acctMstrRepo, crypto, auditLogService, auditLogReadRepo,
                bankScrapingService, legacyFunctions, accountRegistrationService);

        when(crypto.encrypt(anyString())).thenReturn("ENC");
        when(crypto.decrypt(anyString())).thenReturn("plain");

        tenant = mockStatic(TenantContext.class);
        tenant.when(TenantContext::currentBizKey).thenReturn(BIZ);
        tenant.when(TenantContext::currentUserId).thenReturn(USER);
        tenant.when(TenantContext::currentLangCd).thenReturn(LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        tenant.close();
    }

    /* ----------------------------- GDT create ----------------------------- */

    @Test
    @DisplayName("createGdt: verify disabled → persists without calling the gateway")
    void createGdt_verifyDisabled_persists() {
        ReflectionTestUtils.setField(service, "gdtVerifyEnabled", false);
        when(gdtRepo.findByBizKeyOrderBySeqNoAsc(BIZ)).thenReturn(List.of());
        when(legacyFunctions.nextGdtCertSeqNo(BIZ)).thenReturn("7");
        when(gdtRepo.save(any(GdtCertInfo.class))).thenAnswer(i -> i.getArgument(0));

        var resp = service.createGdt(new GdtCredentialRequest("gdtuser", "gdtpw", null));

        assertEquals(new BigDecimal("7"), resp.seqNo());
        verify(bankScrapingService, never()).verify(any());
        verify(gdtRepo).save(any(GdtCertInfo.class));
    }

    @Test
    @DisplayName("createGdt: verify enabled + COMPLETED → persists")
    void createGdt_verifyEnabled_completed_persists() {
        ReflectionTestUtils.setField(service, "gdtVerifyEnabled", true);
        when(gdtRepo.findByBizKeyOrderBySeqNoAsc(BIZ)).thenReturn(List.of());
        when(bankScrapingService.verify(any()))
                .thenReturn(ScrapeSessionResponse.completed(UUID.randomUUID(), List.of()));
        when(legacyFunctions.nextGdtCertSeqNo(BIZ)).thenReturn("8");
        when(gdtRepo.save(any(GdtCertInfo.class))).thenAnswer(i -> i.getArgument(0));

        var resp = service.createGdt(new GdtCredentialRequest("gdtuser", "gdtpw", null));

        assertEquals(new BigDecimal("8"), resp.seqNo());
        ArgumentCaptor<VerifyCredentialsRequest> cap =
                ArgumentCaptor.forClass(VerifyCredentialsRequest.class);
        verify(bankScrapingService).verify(cap.capture());
        assertEquals("VIETNAM_GDT", cap.getValue().bankCd());
        assertEquals("gdtuser", cap.getValue().loginId());
        verify(gdtRepo).save(any(GdtCertInfo.class));
    }

    @Test
    @DisplayName("createGdt: verify enabled + ERROR → throws, row NOT saved")
    void createGdt_verifyEnabled_error_notSaved() {
        ReflectionTestUtils.setField(service, "gdtVerifyEnabled", true);
        when(gdtRepo.findByBizKeyOrderBySeqNoAsc(BIZ)).thenReturn(List.of());
        when(bankScrapingService.verify(any()))
                .thenReturn(ScrapeSessionResponse.error(UUID.randomUUID(), "80004051", "bad creds"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createGdt(new GdtCredentialRequest("gdtuser", "wrongpw", null)));

        assertEquals("WBE_LOGIN_CREDENTIAL_GDT_VERIFY_FAILED", ex.getCode());
        verify(gdtRepo, never()).save(any());
        verify(legacyFunctions, never()).nextGdtCertSeqNo(anyString());
        verify(auditLogService, never()).append(any());
    }

    @Test
    @DisplayName("createGdt: verify enabled + CAPTCHA challenge → throws, row NOT saved")
    void createGdt_verifyEnabled_challenge_notSaved() {
        ReflectionTestUtils.setField(service, "gdtVerifyEnabled", true);
        when(gdtRepo.findByBizKeyOrderBySeqNoAsc(BIZ)).thenReturn(List.of());
        when(bankScrapingService.verify(any()))
                .thenReturn(ScrapeSessionResponse.captchaChallenge(
                        UUID.randomUUID(), "enter captcha", "img", "svg+xml"));

        assertThrows(BusinessException.class,
                () -> service.createGdt(new GdtCredentialRequest("gdtuser", "gdtpw", null)));
        verify(gdtRepo, never()).save(any());
    }

    /* ----------------------------- GDT update ----------------------------- */

    @Test
    @DisplayName("updateGdt: verify enabled + ERROR → throws, row NOT updated")
    void updateGdt_verifyEnabled_error_notSaved() {
        ReflectionTestUtils.setField(service, "gdtVerifyEnabled", true);
        GdtCertInfo existing = new GdtCertInfo();
        existing.setBizKey(BIZ);
        existing.setSeqNo(new BigDecimal("3"));
        existing.setCertInfoCon("olduser");
        existing.setCertInfoDetlCon("ENC");
        when(gdtRepo.findByBizKeyAndSeqNo(BIZ, new BigDecimal("3")))
                .thenReturn(Optional.of(existing));
        when(bankScrapingService.verify(any()))
                .thenReturn(ScrapeSessionResponse.error(UUID.randomUUID(), "80004051", "bad"));

        assertThrows(BusinessException.class, () -> service.updateGdt(
                new BigDecimal("3"), new GdtCredentialUpdateRequest(null, "newpw", null)));

        verify(gdtRepo, never()).save(any());
    }

    /* ------------------------------- delete -------------------------------- */

    @Test
    @DisplayName("delete(bank): hard delete + audit row")
    void delete_bank_hardDeletes() {
        BankCertInfo row = new BankCertInfo();
        row.setBizKey(BIZ);
        row.setSeqNo(new BigDecimal("5"));
        when(bankRepo.findByBizKeyAndSeqNo(BIZ, new BigDecimal("5")))
                .thenReturn(Optional.of(row));

        service.delete("bank", new BigDecimal("5"));

        verify(bankRepo).delete(row);
        verify(auditLogService).append(any(AuditLogContext.class));
    }

    /* ----------------------------- audit read ------------------------------ */

    @Test
    @DisplayName("auditLogs: reads slug + legacy comm_0218_001/004 WSVC ids")
    void auditLogs_readsBothSlugAndLegacyWsvcIds() {
        when(auditLogReadRepo
                .findByBizKeyAndRefNoAndRefNo1AndWsvcIdInAndLangCdAndRefNo2IsNullOrderByRegDtmDesc(
                        eq(BIZ), eq("BANK_CERT_INFO"), eq("9"), any(), eq("EN")))
                .thenReturn(List.of());

        service.auditLogs("bank", new BigDecimal("9"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> cap = ArgumentCaptor.forClass(Collection.class);
        verify(auditLogReadRepo)
                .findByBizKeyAndRefNoAndRefNo1AndWsvcIdInAndLangCdAndRefNo2IsNullOrderByRegDtmDesc(
                        eq(BIZ), eq("BANK_CERT_INFO"), eq("9"), cap.capture(), eq("EN"));
        Collection<String> ids = cap.getValue();
        assertTrue(ids.contains("login-credentials"));
        assertTrue(ids.contains("comm_0218_001"));
        assertTrue(ids.contains("comm_0218_004"));
    }

    /* --------------------------- GDT engine config -------------------------- */

    @Test
    @DisplayName("gdtEngineConfig: serves the configured GDT module triple (JexAdmin-free)")
    void gdtEngineConfig_servesConfiguredTriple() {
        var cfg = service.gdtEngineConfig();
        assertEquals("NTS_NTS001_00001", cfg.apiId());
        assertEquals("GLOBAL/VNM/PUBLIC_GDT", cfg.scrappingModule());
        assertEquals("COMMON", cfg.scrappingClass());
        assertEquals("VIETNAM_GDT", cfg.bankCd());
    }
}
