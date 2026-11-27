package com.kosign.wecms.domain.banking.ownaccounttransfer.service;

import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.entity.BankCertInfo;
import com.kosign.wecms.domain.banking.bankcredentials.repository.BankCertInfoRepository;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCertCryptoService;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.SourceAccountListResponse;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.SourceAccountRow;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.repository.NapasBankInfoRepository;
import com.kosign.wecms.domain.banking.service.BankCdResolver;
import com.kosign.wecms.domain.setting.bankaccount.repository.AcctAuthInfoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Coverage for the {@code BANK_CD} guard added to Own Account Transfer's per-row credential
 * resolution on 2026-08-19.
 *
 * <p><b>Why the guard exists.</b> {@code BANK_CERT_INFO}'s PK is {@code (BIZ_KEY, SEQ_NO)} — the
 * seq is tenant-global, shared across every bank the tenant holds credentials for. Neither the
 * legacy IDO {@code BANK_CERT_INFO_R006} nor {@code loadBankCerts} filters {@code BANK_CD}, so a
 * stale {@code ACCT_MSTR.BANK_CERT_SEQ} can land on a number later occupied by a DIFFERENT bank's
 * credential. Without the guard, that bank's login ID and password would be projected onto these
 * accounts and the browser engine would attempt a login at bank A using bank B's credentials — a
 * guaranteed authentication failure, and each attempt feeds the 3-strike bank lockout the team is
 * specifically trying to avoid.
 *
 * <p>This is a deliberate divergence from legacy, which has no such guard. Rationale and approval:
 * {@code docs/bugs/bank-credential-delete-leaves-dangling-acct-linkage-2026-08-19.md}.
 */
@ExtendWith(MockitoExtension.class)
class OwnAccountTransferServiceCertBankGuardTest {

    private static final String TENANT  = "VN_VNM0004_000001";
    private static final String ADMIN   = "adminuser";
    private static final String SHINHAN = "11704088";
    private static final String VIETIN  = "11704001";

    @Mock private AcctMstrRepository      acctMstrRepo;
    @Mock private AcctAuthInfoRepository  acctAuthRepo;
    @Mock private BankCdResolver          bankCdResolver;
    @Mock private NapasBankInfoRepository napasBankInfoRepo;
    @Mock private com.kosign.wecms.domain.banking.repository.NapasRemitTrnsHstrRepository napasRemitTrnsHstrRepo;
    @Mock private BankCertInfoRepository  bankCertInfoRepo;
    @Mock private BankCertCryptoService   bankCertCrypto;
    @Mock private AuditLogService         auditLog;
    @Mock private com.kosign.wecms.common.legacy.LegacyFunctionRepository legacyFunctions;

    @InjectMocks
    private OwnAccountTransferService service;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("a cert whose BANK_CD matches the account resolves normally")
    void sameBank_certResolves() {
        setUpAdmin();
        stubRows(List.of(acct(SHINHAN, "700022499445", 18L)));
        stubCerts(cert(18L, SHINHAN, "WEBCASH01"));
        when(bankCertCrypto.decryptStrict("pw-cipher")).thenReturn("pw-plain");

        final SourceAccountRow row = only(service.listSourceAccounts(null, null, null));

        assertThat(row.certInfoCon()).isEqualTo("WEBCASH01");
        assertThat(row.certInfoDetlCon()).isEqualTo("pw-plain");
    }

    @Test
    @DisplayName("a cert owned by a DIFFERENT bank is suppressed — foreign credentials never reach the engine")
    void crossBank_certIsSuppressed() {
        setUpAdmin();
        // The Shinhan account still points at seq 18, but seq 18 now belongs to VietinBank.
        stubRows(List.of(acct(SHINHAN, "700022499445", 18L)));
        stubCerts(cert(18L, VIETIN, "VIETIN_USER"));

        final SourceAccountRow row = only(service.listSourceAccounts(null, null, null));

        assertThat(row.certInfoCon())
                .as("a foreign bank's login ID must never be projected onto this account")
                .isNull();
        assertThat(row.certInfoDetlCon())
                .as("a foreign bank's password must never reach the engine")
                .isNull();
        assertThat(row.nxisasGbSeqNo()).isNull();
        // The row still renders — it is simply un-credentialed, exactly like an account that was
        // never linked. The frontend gate disables Transfer with the register-credentials message.
        assertThat(row.bankCertSeq()).isEqualTo("18");
        assertThat(row.canTransfer()).isTrue();
    }

    @Test
    @DisplayName("a seq pointing at a deleted credential still projects null cert fields")
    void danglingSeq_projectsNullCert() {
        setUpAdmin();
        // The reported Shinhan state: BANK_CERT_SEQ = 1, no such row in BANK_CERT_INFO.
        stubRows(List.of(acct(SHINHAN, "700022499445", 1L)));
        stubCerts(cert(18L, SHINHAN, "WEBCASH01"));

        final SourceAccountRow row = only(service.listSourceAccounts(null, null, null));

        assertThat(row.certInfoCon()).isNull();
        assertThat(row.certInfoDetlCon()).isNull();
        assertThat(row.bankCertSeq()).isEqualTo("1");
    }

    @Test
    @DisplayName("an account with no BANK_CERT_SEQ never queries the credential table")
    void unlinkedAccount_needsNoCertLookup() {
        setUpAdmin();
        stubRows(List.of(acct(SHINHAN, "700022499445", null)));

        final SourceAccountRow row = only(service.listSourceAccounts(null, null, null));

        assertThat(row.bankCertSeq()).isNull();
        assertThat(row.certInfoCon()).isNull();
        // loadBankCerts short-circuits on an empty wanted-seq set, so the repository is untouched.
        verifyNoInteractions(bankCertInfoRepo);
    }

    /* =================================================================
     *  helpers
     * ================================================================= */

    private void setUpAdmin() {
        TenantContext.set(ADMIN, ADMIN, TENANT, LangCd.EN, TenantContext.AUTH_GR_ADMIN, "F");
    }

    private void stubRows(List<AcctMstr> rows) {
        when(acctMstrRepo.findOwnTransferSourceRows(
                eq(TENANT), anyList(), eq(false), anyString(), any(Sort.class)))
                .thenReturn(rows);
        lenient().when(bankCdResolver.resolveBankNm(anyString())).thenReturn("Bank");
    }

    private void stubCerts(BankCertInfo... certs) {
        when(bankCertInfoRepo.findByBizKeyOrderBySeqNoAsc(TENANT)).thenReturn(List.of(certs));
    }

    private static SourceAccountRow only(SourceAccountListResponse resp) {
        assertThat(resp.list()).hasSize(1);
        return resp.list().get(0);
    }

    private static AcctMstr acct(String bankCd, String acctNo, Long bankCertSeq) {
        AcctMstr a = new AcctMstr();
        a.setBizKey(TENANT);
        a.setBankCd(bankCd);
        a.setAcctNo(acctNo);
        a.setAcctTypeCd("03");
        a.setCcyCd("VND");
        a.setBal(BigDecimal.ZERO);
        a.setRealAmt(BigDecimal.ZERO);
        a.setActvYn("Y");
        a.setAcctNickNm(bankCd + " " + acctNo);
        a.setBankCertSeq(bankCertSeq);
        return a;
    }

    private static BankCertInfo cert(long seqNo, String bankCd, String loginId) {
        BankCertInfo c = new BankCertInfo();
        c.setBizKey(TENANT);
        c.setSeqNo(BigDecimal.valueOf(seqNo));
        c.setBankCd(bankCd);
        c.setCertInfoCon(loginId);
        c.setCertInfoDetlCon("pw-cipher");
        return c;
    }
}
