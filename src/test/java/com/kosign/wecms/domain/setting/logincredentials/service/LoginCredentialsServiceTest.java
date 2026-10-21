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
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.repository.BankInfmRepository;
import com.kosign.wecms.domain.setting.logincredentials.repository.BankShortNameRepository;
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

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LoginCredentialsService}.
 *
 * <p>GDT credentials are verified CLIENT-SIDE by the local NXiSAS.exe engine
 * (legacy parity — the Save action is the login test, run on the local engine,
 * not the Coocon server gateway). The backend GDT create/update actions are
 * therefore pure {@code GDT_CERT_INFO} upserts and must NEVER call
 * {@link BankScrapingService} / route {@code VIETNAM_GDT} through the Coocon
 * gateway. The former flag-gated server-side verify (which threw
 * {@code WBE2093} because {@code VIETNAM_GDT} has no {@code NxisasBankConfig}
 * entry) was removed 2026-07-13 — see
 * {@code Claude Code (AI)/notes/gdt-login-credentials-wbe2093-fix.md}.
 *
 * <p>Also covers hard delete and the audit dual-read (slug + legacy WSVC ids).
 */
class LoginCredentialsServiceTest {

    private static final String BIZ = "VN_VNM0004_000001";
    private static final String USER = "tester";

    private BankPickerRepository bankPickerRepo;
    private BankFormInfoRepository bankFormInfoRepo;
    private BankShortNameRepository bankShortNameRepo;
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
        bankShortNameRepo = mock(BankShortNameRepository.class);
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
                bankPickerRepo, bankFormInfoRepo, bankShortNameRepo, bankRepo, gdtRepo, bankInfmRepo,
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
    @DisplayName("createGdt: persists GDT_CERT_INFO (encrypted pw) WITHOUT calling BankScrapingService")
    void createGdt_persists_withoutBankScraping() {
        when(gdtRepo.findByBizKeyOrderBySeqNoAsc(BIZ)).thenReturn(List.of());
        when(legacyFunctions.nextGdtCertSeqNo(BIZ)).thenReturn("7");
        when(gdtRepo.save(any(GdtCertInfo.class))).thenAnswer(i -> i.getArgument(0));

        var resp = service.createGdt(new GdtCredentialRequest("gdtuser", "gdtpw", null));

        assertEquals(new BigDecimal("7"), resp.seqNo());
        // GDT is verified client-side by the local NXiSAS.exe engine — the
        // backend must NEVER route VIETNAM_GDT through the Coocon gateway.
        verify(bankScrapingService, never()).verify(any());

        ArgumentCaptor<GdtCertInfo> cap = ArgumentCaptor.forClass(GdtCertInfo.class);
        verify(gdtRepo).save(cap.capture());
        assertEquals("gdtuser", cap.getValue().getCertInfoCon());
        assertEquals("ENC", cap.getValue().getCertInfoDetlCon());   // password encrypted at rest
        assertEquals("1", cap.getValue().getCertInfoDv());          // legacy CERT_INFO_DV
        verify(auditLogService).append(any());
    }

    @Test
    @DisplayName("createGdt: second credential for the same company is blocked (one-per-company)")
    void createGdt_duplicate_blocked() {
        GdtCertInfo existing = new GdtCertInfo();
        existing.setBizKey(BIZ);
        existing.setSeqNo(new BigDecimal("1"));
        when(gdtRepo.findByBizKeyOrderBySeqNoAsc(BIZ)).thenReturn(List.of(existing));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createGdt(new GdtCredentialRequest("gdtuser", "gdtpw", null)));

        assertEquals("WBE_LOGIN_CREDENTIAL_GDT_EXISTS", ex.getCode());
        verify(bankScrapingService, never()).verify(any());
        verify(gdtRepo, never()).save(any());
        verify(legacyFunctions, never()).nextGdtCertSeqNo(anyString());
        verify(auditLogService, never()).append(any());
    }

    /* ----------------------------- GDT update ----------------------------- */

    @Test
    @DisplayName("updateGdt: updates login id + re-encrypted password WITHOUT calling BankScrapingService")
    void updateGdt_persists_withoutBankScraping() {
        GdtCertInfo existing = new GdtCertInfo();
        existing.setBizKey(BIZ);
        existing.setSeqNo(new BigDecimal("3"));
        existing.setCertInfoCon("olduser");
        existing.setCertInfoDetlCon("OLD_ENC");
        when(gdtRepo.findByBizKeyAndSeqNo(BIZ, new BigDecimal("3")))
                .thenReturn(Optional.of(existing));
        when(gdtRepo.save(any(GdtCertInfo.class))).thenAnswer(i -> i.getArgument(0));

        service.updateGdt(new BigDecimal("3"),
                new GdtCredentialUpdateRequest("newuser", "newpw", null));

        verify(bankScrapingService, never()).verify(any());
        assertEquals("newuser", existing.getCertInfoCon());
        assertEquals("ENC", existing.getCertInfoDetlCon());         // new password re-encrypted
        verify(gdtRepo).save(existing);
        verify(auditLogService).append(any());
    }

    @Test
    @DisplayName("updateGdt: all-null patch rejected with WBE_VALIDATION (no save)")
    void updateGdt_allNull_rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateGdt(new BigDecimal("3"),
                        new GdtCredentialUpdateRequest(null, null, null)));

        assertEquals("WBE_VALIDATION", ex.getCode());
        verify(bankScrapingService, never()).verify(any());
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

    @Test
    @DisplayName("delete(bank): clears ACCT_MSTR.BANK_CERT_SEQ for the deleted seq (no dangling pointer)")
    void delete_bank_clearsAccountLinkage() {
        // Regression guard for the 2026-08-19 fix. Before it, deleting a credential left every
        // linked account pointing at the removed SEQ_NO, so Own Account Transfer disabled Transfer
        // with "Bank login credentials were deleted…" even though the credential had been
        // re-registered (re-registering mints a NEW seq; the accounts kept the old one).
        // Full record: docs/bugs/bank-credential-delete-leaves-dangling-acct-linkage-2026-08-19.md
        BankCertInfo row = new BankCertInfo();
        row.setBizKey(BIZ);
        row.setSeqNo(new BigDecimal("5"));
        row.setBankCd("11704088");
        when(bankRepo.findByBizKeyAndSeqNo(BIZ, new BigDecimal("5")))
                .thenReturn(Optional.of(row));
        when(acctMstrRepo.clearBankCertSeq(anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(4);

        service.delete("bank", new BigDecimal("5"));

        // The seq crosses as a long — ACCT_MSTR.BANK_CERT_SEQ maps to Long while
        // BANK_CERT_INFO.SEQ_NO maps to BigDecimal; a scale mismatch would match nothing.
        verify(acctMstrRepo).clearBankCertSeq(eq(BIZ), eq(5L), eq(USER), argThat(
                s -> s != null && s.length() == 14 && s.chars().allMatch(Character::isDigit)));
    }

    @Test
    @DisplayName("delete(gdt): never touches ACCT_MSTR — GDT credentials own no account linkage")
    void delete_gdt_leavesAccountLinkageAlone() {
        GdtCertInfo row = new GdtCertInfo();
        row.setBizKey(BIZ);
        row.setSeqNo(new BigDecimal("7"));
        when(gdtRepo.findByBizKeyAndSeqNo(BIZ, new BigDecimal("7")))
                .thenReturn(Optional.of(row));

        service.delete("gdt", new BigDecimal("7"));

        verify(gdtRepo).delete(row);
        verify(acctMstrRepo, never()).clearBankCertSeq(anyString(), anyLong(), anyString(), anyString());
    }

    /* ------------------ GDT scraping credential (silent) ------------------- */

    @Test
    @DisplayName("loadGdtScrapingCredential: returns decrypted login id + password, no password required, no audit write")
    void loadGdtScrapingCredential_returnsDecrypted() {
        GdtCertInfo row = new GdtCertInfo();
        row.setBizKey(BIZ);
        row.setSeqNo(new BigDecimal("7"));
        row.setCertInfoCon("gdt-login");
        row.setCertInfoDetlCon("ENC_PW");
        when(gdtRepo.findByBizKeyAndSeqNo(BIZ, new BigDecimal("7")))
                .thenReturn(Optional.of(row));
        when(crypto.decryptStrict("ENC_PW")).thenReturn("gdt-secret");

        var resp = service.loadGdtScrapingCredential(new BigDecimal("7"));

        assertEquals("gdt-login", resp.loginId());
        assertEquals("gdt-secret", resp.password());
        // Silent path — no reveal password-confirmation gate, no audit row
        // (legacy gdtc_0001_001_r001 wrote none either). This is what makes
        // it read-only + prompt-free, unlike reveal().
        verify(auditLogService, never()).append(any());
    }

    @Test
    @DisplayName("loadGdtScrapingCredential: unknown / cross-tenant seqNo → WBE2090 (not found), never decrypts")
    void loadGdtScrapingCredential_unknownSeq_throws() {
        when(gdtRepo.findByBizKeyAndSeqNo(BIZ, new BigDecimal("99")))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.loadGdtScrapingCredential(new BigDecimal("99")));

        assertEquals("WBE2090", ex.getCode());
        verify(crypto, never()).decryptStrict(anyString());
    }

    @Test
    @DisplayName("loadGdtScrapingCredential: GDT-only — a bank seqNo never resolves through GDT_CERT_INFO")
    void loadGdtScrapingCredential_bankSeq_notFound() {
        // The method queries GDT_CERT_INFO exclusively; a seqNo that exists
        // only in BANK_CERT_INFO is absent from gdtRepo → WBE2090, and the
        // bank repo is never consulted, so a bank credential can never leak
        // through this GDT endpoint.
        when(gdtRepo.findByBizKeyAndSeqNo(BIZ, new BigDecimal("5")))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.loadGdtScrapingCredential(new BigDecimal("5")));

        assertEquals("WBE2090", ex.getCode());
        verify(bankRepo, never()).findByBizKeyAndSeqNo(anyString(), any());
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
