package com.kosign.wecms.domain.banking.bankcredentials.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.entity.BankCertInfo;
import com.kosign.wecms.domain.banking.bankcredentials.repository.BankCertInfoRepository;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the bug fixed on 2026-08-19: deleting a bank credential left every
 * linked account's {@code ACCT_MSTR.BANK_CERT_SEQ} pointing at the deleted {@code SEQ_NO}.
 *
 * <p><b>The observed symptom.</b> On the reported tenant, all four Shinhan VN ({@code 11704088})
 * accounts carried {@code BANK_CERT_SEQ = 1} while the tenant's live {@code bank_cert_info} seqs
 * ran 2…18 (Shinhan at 18). Seq 1 had been deleted; re-registering the credential minted a new
 * seq without re-linking the accounts. Own Account Transfer therefore resolved no cert for those
 * rows and disabled Transfer with <i>"Bank login credentials were deleted. Please register Login
 * Credentials again."</i> — with the credential visibly present in the table.
 *
 * <p><b>What these tests pin.</b> The credential delete and the linkage clear are one operation:
 * if the {@code clearBankCertSeq} call is ever removed, or scoped to the wrong tenant, or ordered
 * so that a failed load still mutates accounts, one of these fails. The <em>query</em> itself is
 * pinned separately by {@code ClearBankCertSeqQueryContractTest} — this class stubs it, so it can
 * only prove the caller's behaviour, never the SQL.
 *
 * @see com.kosign.wecms.domain.banking.repository.ClearBankCertSeqQueryContractTest
 */
@ExtendWith(MockitoExtension.class)
class BankCredentialServiceDeleteLinkageTest {

    private static final String TENANT  = "VN_VNM0004_000001";
    private static final String USER    = "tester";
    private static final String SHINHAN = "11704088";

    @Mock private BankCertInfoRepository repository;
    @Mock private BankCertCryptoService  crypto;
    @Mock private com.kosign.wecms.common.legacy.LegacyFunctionRepository legacyFunctions;
    @Mock private AcctMstrRepository     acctMstrRepository;

    @InjectMocks
    private BankCredentialService service;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("delete clears the linkage for the deleted seq, in the deleting tenant")
    void delete_clearsDanglingLinkage() {
        setUpTenant();
        final BankCertInfo cert = cert(new BigDecimal("18"));
        when(repository.findByBizKeyAndSeqNo(TENANT, cert.getSeqNo())).thenReturn(Optional.of(cert));
        when(acctMstrRepository.clearBankCertSeq(anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(4);

        service.delete(cert.getSeqNo());

        verify(repository).delete(cert);
        // The seq crosses the boundary as a long — ACCT_MSTR.BANK_CERT_SEQ maps to Long, while
        // BANK_CERT_INFO.SEQ_NO maps to BigDecimal. A scale mismatch here would silently match
        // nothing, which is exactly the failure this assertion rules out.
        verify(acctMstrRepository).clearBankCertSeq(eqTenant(), eqSeq(18L), eqUser(), any14CharDtm());
    }

    @Test
    @DisplayName("delete of a credential that owns no accounts still succeeds (0 unlinked)")
    void delete_withNoLinkedAccounts_isFine() {
        setUpTenant();
        final BankCertInfo cert = cert(new BigDecimal("7"));
        when(repository.findByBizKeyAndSeqNo(TENANT, cert.getSeqNo())).thenReturn(Optional.of(cert));
        when(acctMstrRepository.clearBankCertSeq(anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(0);

        service.delete(cert.getSeqNo());

        verify(repository).delete(cert);
        verify(acctMstrRepository).clearBankCertSeq(eqTenant(), eqSeq(7L), eqUser(), any14CharDtm());
    }

    @Test
    @DisplayName("the credential row is deleted before the linkage is cleared — one transaction, deterministic order")
    void delete_ordersCredentialThenLinkage() {
        setUpTenant();
        final BankCertInfo cert = cert(new BigDecimal("18"));
        when(repository.findByBizKeyAndSeqNo(TENANT, cert.getSeqNo())).thenReturn(Optional.of(cert));
        when(acctMstrRepository.clearBankCertSeq(anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(1);

        service.delete(cert.getSeqNo());

        final InOrder order = inOrder(repository, acctMstrRepository);
        order.verify(repository).delete(cert);
        order.verify(acctMstrRepository)
                .clearBankCertSeq(anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("a credential in another tenant is never deleted, and no account is unlinked")
    void delete_unknownSeq_touchesNothing() {
        setUpTenant();
        when(repository.findByBizKeyAndSeqNo(TENANT, new BigDecimal("999")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(new BigDecimal("999")))
                .isInstanceOf(BusinessException.class);

        verify(repository, never()).delete(any(BankCertInfo.class));
        // The load is tenant-checked, so a cross-tenant seq must not reach the bulk UPDATE at all.
        verifyNoInteractions(acctMstrRepository);
    }

    /* =================================================================
     *  helpers
     * ================================================================= */

    private void setUpTenant() {
        TenantContext.set(USER, "Test User", TENANT,
                com.kosign.wecms.common.enums.LangCd.EN, TenantContext.AUTH_GR_USER, "F");
    }

    private static BankCertInfo cert(BigDecimal seqNo) {
        BankCertInfo c = new BankCertInfo();
        c.setBizKey(TENANT);
        c.setSeqNo(seqNo);
        c.setBankCd(SHINHAN);
        c.setCertInfoCon("WEBCASH01");
        return c;
    }

    private static String eqTenant()  { return org.mockito.ArgumentMatchers.eq(TENANT); }
    private static String eqUser()    { return org.mockito.ArgumentMatchers.eq(USER); }
    private static long   eqSeq(long v) { return org.mockito.ArgumentMatchers.eq(v); }

    /** The audit stamp is a legacy {@code yyyyMMddHHmmss} string — assert the shape, not the clock. */
    private static String any14CharDtm() {
        return org.mockito.ArgumentMatchers.argThat(
                s -> s != null && s.length() == 14 && s.chars().allMatch(Character::isDigit));
    }
}
