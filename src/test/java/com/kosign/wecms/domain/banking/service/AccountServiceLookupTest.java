package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.AccountLookupListResponse;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.entity.NapasBankInfo;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.repository.NapasBankInfoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kosign.wecms.domain.banking.nxisas.BankTransactionSyncService;

/**
 * Coverage for the firm-banking-settings withdrawal-account autocomplete
 * service path. Pins:
 * <ul>
 *   <li>NAPAS → internal {@code BANK_CD} bridge mirrors JEX
 *       {@code comm_1004_001_act.jsp:56-66} — load
 *       {@code NAPAS_BANK_INFO} by PK, then query {@code ACCT_MSTR}
 *       with {@code (biz_key = ? AND bank_cd = ?)}.</li>
 *   <li>Tenant scoping always reads {@code BIZ_KEY} from
 *       {@link TenantContext}.</li>
 *   <li>Server-masked account number on the wire matches the legacy
 *       {@code LogMask.acct} convention (first/last 4 + middle
 *       asterisks).</li>
 *   <li>Unknown / unmapped NAPAS code returns an empty list (legacy
 *       parity — JEX puts {@code REC = null}); never an error.</li>
 *   <li>Blank input throws {@code WBE_VALIDATION} (defence-in-depth
 *       for the controller's {@code @Pattern}).</li>
 *   <li>Missing tenant context throws {@code WBE9999}.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceLookupTest {

    @Mock AcctMstrRepository      acctRepo;
    @Mock BankCdResolver          bankResolver;
    @Mock NapasBankInfoRepository napasRepo;

    AccountService service;

    private static final String BIZ              = "VN_VNM0004_000001";
    private static final String USER             = "tester";
    private static final String NAPAS_SHINHAN    = "970424";
    private static final String INTERNAL_SHINHAN = "11704088";

    @BeforeEach
    void setUp() {
        // BankTransactionSyncService dependency added to AccountService in a
        // prior MR; not exercised by this lookup-focused test — pass a
        // pure mock so the constructor resolves.
        service = new AccountService(acctRepo, bankResolver, napasRepo,
                mock(BankTransactionSyncService.class));
        TenantContext.set(USER, BIZ, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void lookup_resolvesNapasToInternalBankCd_andProjectsRowsWithMaskedAcctNo() {
        NapasBankInfo napas = newNapas(NAPAS_SHINHAN, INTERNAL_SHINHAN);
        when(napasRepo.findById(NAPAS_SHINHAN)).thenReturn(Optional.of(napas));
        when(acctRepo.findByBizKeyAndBankCdOrderByAcctNoAsc(BIZ, INTERNAL_SHINHAN))
                .thenReturn(List.of(
                        newAcct("700005285280", "VND", null, "CONG TY TNHH WEBCASH VIET NAM"),
                        newAcct("703012202658", "VND", "Shinhan VN2658", "E-Time Deposit (VND)")
                ));

        AccountLookupListResponse response = service.lookupForFirmBanking(NAPAS_SHINHAN);

        assertThat(response.list()).hasSize(2);
        // First row — unmasked acct_no preserved for the form-submit
        // value; masked variant matches the LogMask.acct convention
        // (first 4 + asterisks + last 4).
        assertThat(response.list().get(0).acctNo())       .isEqualTo("700005285280");
        assertThat(response.list().get(0).acctNoMasked()) .startsWith("7000").endsWith("5280")
                                                          .contains("*");
        assertThat(response.list().get(0).ccyCd())        .isEqualTo("VND");
        assertThat(response.list().get(0).acctNickNm())   .isNull();
        assertThat(response.list().get(0).acctNm())       .isEqualTo("CONG TY TNHH WEBCASH VIET NAM");
        // Second row — preserves nickname for the dropdown sub-line.
        assertThat(response.list().get(1).acctNickNm())   .isEqualTo("Shinhan VN2658");
        // Tenant + internal bank predicate hit the repo verbatim.
        verify(acctRepo).findByBizKeyAndBankCdOrderByAcctNoAsc(BIZ, INTERNAL_SHINHAN);
    }

    @Test
    void lookup_unknownNapasCode_returnsEmptyList_andDoesNotQueryAcctMstr() {
        when(napasRepo.findById("999999")).thenReturn(Optional.empty());

        AccountLookupListResponse response = service.lookupForFirmBanking("999999");

        assertThat(response.list()).isEmpty();
        verify(acctRepo, never()).findByBizKeyAndBankCdOrderByAcctNoAsc(any(), any());
    }

    @Test
    void lookup_napasRowWithNullBankCd_returnsEmptyList() {
        // Defence in depth — even if NAPAS_BANK_INFO has the row but
        // its bank_cd is null/blank (data drift scenario), the lookup
        // must not throw or fall through to a broken JPQL with a
        // null parameter.
        when(napasRepo.findById(NAPAS_SHINHAN))
                .thenReturn(Optional.of(newNapas(NAPAS_SHINHAN, null)));

        AccountLookupListResponse response = service.lookupForFirmBanking(NAPAS_SHINHAN);

        assertThat(response.list()).isEmpty();
        verify(acctRepo, never()).findByBizKeyAndBankCdOrderByAcctNoAsc(any(), any());
    }

    @Test
    void lookup_blankInput_throwsWBE_VALIDATION_andSkipsRepos() {
        assertBusinessCode("WBE_VALIDATION", () -> service.lookupForFirmBanking(null));
        assertBusinessCode("WBE_VALIDATION", () -> service.lookupForFirmBanking(""));
        assertBusinessCode("WBE_VALIDATION", () -> service.lookupForFirmBanking("   "));

        verifyNoInteractions(napasRepo);
        verifyNoInteractions(acctRepo);
    }

    @Test
    void lookup_missingTenant_throwsWBE9999() {
        TenantContext.clear();
        // requireBizKey() fires BEFORE the NAPAS lookup — no stub
        // needed (Mockito strict stubs would reject an unused one).

        assertBusinessCode("WBE9999", () -> service.lookupForFirmBanking(NAPAS_SHINHAN));
        verifyNoInteractions(napasRepo);
        verifyNoInteractions(acctRepo);
    }

    @Test
    void lookup_emptyAcctMstrForBank_returnsEmptyList_butStillResolvesNapas() {
        when(napasRepo.findById(NAPAS_SHINHAN))
                .thenReturn(Optional.of(newNapas(NAPAS_SHINHAN, INTERNAL_SHINHAN)));
        when(acctRepo.findByBizKeyAndBankCdOrderByAcctNoAsc(BIZ, INTERNAL_SHINHAN))
                .thenReturn(List.of());

        AccountLookupListResponse response = service.lookupForFirmBanking(NAPAS_SHINHAN);

        assertThat(response.list()).isEmpty();
        verify(napasRepo).findById(NAPAS_SHINHAN);
        verify(acctRepo).findByBizKeyAndBankCdOrderByAcctNoAsc(BIZ, INTERNAL_SHINHAN);
    }

    /* ------------------------------------------------------------------ */

    private static NapasBankInfo newNapas(String napasBankCd, String internalBankCd) {
        NapasBankInfo n = new NapasBankInfo();
        n.setNapasBankCd(napasBankCd);
        n.setBankCd(internalBankCd);
        return n;
    }

    private static AcctMstr newAcct(String acctNo, String ccyCd, String nickNm, String acctNm) {
        AcctMstr a = new AcctMstr();
        a.setBizKey(BIZ);
        a.setBankCd(INTERNAL_SHINHAN);
        a.setAcctNo(acctNo);
        a.setCcyCd(ccyCd);
        a.setAcctNickNm(nickNm);
        a.setAcctNm(acctNm);
        return a;
    }

    private static void assertBusinessCode(String expectedCode, Runnable r) {
        assertThatThrownBy(r::run)
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(expectedCode);
    }
}
