package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService.DecryptedCredential;
import com.kosign.wecms.domain.banking.dto.RegisterAccountsRequest;
import com.kosign.wecms.domain.banking.dto.RegisterAccountsRequest.AccountEntry;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.setting.bankaccount.entity.AcctAuthInfo;
import com.kosign.wecms.domain.setting.bankaccount.repository.AcctAuthInfoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The <b>re-link</b> half of the credential lifecycle: registering a bank credential must write
 * {@code ACCT_MSTR.BANK_CERT_SEQ} for every picked account — on the new-account branch AND on the
 * existing-account branch.
 *
 * <p><b>Why this class exists (added 2026-08-19).</b> Deleting a credential now clears
 * {@code BANK_CERT_SEQ} to {@code NULL} instead of leaving it dangling
 * ({@code docs/bugs/bank-credential-delete-leaves-dangling-acct-linkage-2026-08-19.md}). That makes
 * the re-link the <b>only</b> path back to a working Transfer button — and the re-link had
 * <b>no test coverage at all</b>: the sole registration test
 * ({@code AccountRegistrationServicePrnSeqAndWhitelistTest}) covers {@code PRN_SEQ} allocation and the
 * {@code ACCT_AUTH_INFO} fan-out, and nothing anywhere asserted that {@code BANK_CERT_SEQ} is written.
 *
 * <p><b>What breaks without these tests.</b> If {@code existing.setBankCertSeq(bankCertSeq)} is ever
 * dropped in a refactor, the failure mode is now much worse than before the delete fix:
 *
 * <table border="1">
 *   <caption>Consequence of losing the existing-account re-link</caption>
 *   <tr><th></th><th>Before the delete fix</th><th>After the delete fix</th></tr>
 *   <tr><td>Accounts after delete</td><td>keep a stale seq (broken)</td><td>{@code NULL} (honest)</td></tr>
 *   <tr><td>Accounts after re-register</td><td>keep the stale seq — still broken, unchanged</td>
 *       <td><b>stay {@code NULL} forever — Transfer can never be re-enabled from the UI</b></td></tr>
 * </table>
 *
 * <p>So the delete fix and the re-link are one mechanism, and this is the half that was unguarded.
 * Legacy writes the same value on both branches — {@code fnsh_0101_001_c001_act.jsp:257} (create) and
 * {@code :321} (patch) — so this is legacy parity, not a refactor-only rule.
 */
@ExtendWith(MockitoExtension.class)
class AccountRegistrationServiceCredentialRelinkTest {

    @Mock AcctMstrRepository         acctMstrRepo;
    @Mock BankCredentialService      credentialService;
    @Mock AcctAuthInfoRepository     acctAuthRepo;
    @Mock UserBusiConnInfmRepository userBusiRepo;
    @Mock LegacyFunctionRepository   legacyFunctions;

    @Captor ArgumentCaptor<AcctMstr>       savedExisting;
    @Captor ArgumentCaptor<List<AcctMstr>> savedNew;

    AccountRegistrationService service;

    private static final String     BIZ      = "VN_VNM0004_000001";
    private static final String     USER     = "tester";
    private static final String     SHINHAN  = "11704088";
    /** The seq a re-registration mints — deliberately NOT the seq that was deleted.
     *  {@code wb_get_next_val} is per-tenant {@code MAX+1}, so a deleted seq never comes back. */
    private static final BigDecimal NEW_SEQ  = new BigDecimal("19");

    @BeforeEach
    void setUp() {
        service = new AccountRegistrationService(
                acctMstrRepo, credentialService, acctAuthRepo, userBusiRepo, legacyFunctions,
                new com.kosign.wecms.domain.payment.overseas.service.OverseasSenderNameGuard(
                        acctMstrRepo, com.kosign.wecms.domain.payment.common.WireWidthGuards.real()));
        TenantContext.set(USER, BIZ, LangCd.EN);
        lenient().when(credentialService.loadForScraping(NEW_SEQ))
                .thenReturn(new DecryptedCredential(SHINHAN, "WEBCASH01", "pw", "c", "ID", null));
        lenient().when(legacyFunctions.nextAcctMstrPrnSeq(BIZ)).thenReturn("1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("re-link after a delete: an account with BANK_CERT_SEQ = NULL is re-pointed at the new seq")
    void relink_nullSeq_isRestored() {
        // THE RECOVERY PATH. This is the exact state the delete fix leaves behind, and the exact
        // state the user lands in when they re-register the credential: the account row survived,
        // its pointer is NULL, and the picker offers it for re-selection.
        final AcctMstr existing = existingAccount("700022499445", null);
        stubExisting(existing);

        service.register(new RegisterAccountsRequest(NEW_SEQ, List.of(entry("700022499445"))));

        verify(acctMstrRepo).save(savedExisting.capture());
        assertThat(savedExisting.getValue().getBankCertSeq())
                .as("without this write the account stays NULL forever and Transfer can never "
                        + "be re-enabled from the UI")
                .isEqualTo(19L);
    }

    @Test
    @DisplayName("re-link overwrites a STALE seq — a pre-fix dangling pointer is cured by re-registering")
    void relink_staleSeq_isOverwritten() {
        // The reported Shinhan rows: BANK_CERT_SEQ = 1, pointing at a credential deleted before the
        // fix shipped. The delete fix is forward-looking and does NOT clean these up — this is the
        // documented cure, so it must keep working.
        final AcctMstr existing = existingAccount("700022499445", 1L);
        stubExisting(existing);

        service.register(new RegisterAccountsRequest(NEW_SEQ, List.of(entry("700022499445"))));

        verify(acctMstrRepo).save(savedExisting.capture());
        assertThat(savedExisting.getValue().getBankCertSeq()).isEqualTo(19L);
    }

    @Test
    @DisplayName("re-link reactivates the account row (ACTV_YN = 'Y') without disturbing its data")
    void relink_reactivatesAndPreservesData() {
        final AcctMstr existing = existingAccount("700022499445", null);
        existing.setActvYn("N");
        existing.setRegId("originalUser");
        existing.setRegDtm("20240101010101");
        existing.setBal(new BigDecimal("123.45"));
        stubExisting(existing);

        service.register(new RegisterAccountsRequest(NEW_SEQ, List.of(entry("700022499445"))));

        verify(acctMstrRepo).save(savedExisting.capture());
        final AcctMstr saved = savedExisting.getValue();
        assertThat(saved.getActvYn()).isEqualTo("Y");
        assertThat(saved.getBankCertSeq()).isEqualTo(19L);
        // REG_* are preserved — only MOD_* move. Legacy fnsh_0101_001_c001 patches, never re-creates.
        assertThat(saved.getRegId()).isEqualTo("originalUser");
        assertThat(saved.getRegDtm()).isEqualTo("20240101010101");
        assertThat(saved.getModId()).isEqualTo(USER);
        assertThat(saved.getModDtm()).hasSize(14);
        assertThat(saved.getAcctNo()).isEqualTo("700022499445");
    }

    @Test
    @DisplayName("a brand-new account also carries the credential seq on INSERT")
    void newAccount_carriesSeqOnInsert() {
        when(acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(any(), any(), any()))
                .thenReturn(Optional.empty());

        service.register(new RegisterAccountsRequest(NEW_SEQ, List.of(entry("700099999999"))));

        verify(acctMstrRepo).saveAll(savedNew.capture());
        assertThat(savedNew.getValue()).hasSize(1);
        assertThat(savedNew.getValue().get(0).getBankCertSeq()).isEqualTo(19L);
    }

    @Test
    @DisplayName("every picked account is re-linked, not just the first")
    void relink_appliesToEveryPickedAccount() {
        // The reported tenant has four Shinhan accounts. A loop bug that re-linked only the first
        // would leave three accounts stranded at NULL — visible to the user as "some rows work".
        final AcctMstr a1 = existingAccount("700022499445", null);
        final AcctMstr a2 = existingAccount("700022964690", null);
        final AcctMstr a3 = existingAccount("703012202658", 1L);
        when(acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(BIZ, SHINHAN, "700022499445"))
                .thenReturn(Optional.of(a1));
        when(acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(BIZ, SHINHAN, "700022964690"))
                .thenReturn(Optional.of(a2));
        when(acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(BIZ, SHINHAN, "703012202658"))
                .thenReturn(Optional.of(a3));

        service.register(new RegisterAccountsRequest(NEW_SEQ, List.of(
                entry("700022499445"), entry("700022964690"), entry("703012202658"))));

        assertThat(a1.getBankCertSeq()).isEqualTo(19L);
        assertThat(a2.getBankCertSeq()).isEqualTo(19L);
        assertThat(a3.getBankCertSeq()).isEqualTo(19L);
    }

    /* =================================================================
     *  helpers
     * ================================================================= */

    private void stubExisting(AcctMstr existing) {
        when(acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(BIZ, SHINHAN, existing.getAcctNo()))
                .thenReturn(Optional.of(existing));
    }

    private static AcctMstr existingAccount(String acctNo, Long bankCertSeq) {
        AcctMstr a = new AcctMstr();
        a.setBizKey(BIZ);
        a.setBankCd(SHINHAN);
        a.setAcctNo(acctNo);
        a.setAcctTypeCd("03");
        a.setCcyCd("VND");
        a.setBal(BigDecimal.ZERO);
        a.setRealAmt(BigDecimal.ZERO);
        a.setActvYn("Y");
        a.setHandRegYn("N");
        a.setBankCertSeq(bankCertSeq);
        return a;
    }

    private static AccountEntry entry(String acctNo) {
        return new AccountEntry(acctNo, "VND", "03", "nick", "desc", "Holder", BigDecimal.ZERO,
                null, null, null);
    }
}
