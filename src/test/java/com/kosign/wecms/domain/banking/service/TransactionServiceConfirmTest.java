package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.repository.UserInfmRepository;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsRcvPayInfm;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.repository.AcctTrnsRcvPayInfmRepository;
import com.kosign.wecms.domain.banking.repository.BusiMatchLogRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import com.kosign.wecms.domain.setting.chartofaccounts.repository.ChartOfAccountRepository;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoInfmRepository;
import com.kosign.wecms.domain.setting.purposesettings.repository.PurposeSettingsRepository;
import com.kosign.wecms.domain.setting.vasaccount.repository.BusiVasAcctInfmRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Confirm-workflow parity + regression test — legacy
 * {@code fnsh_0002_001_u002_act.jsp} (VND) / {@code fnsh_0006_001_u002_act.jsp}
 * (FCY).
 *
 * <p>Pins the fix for the {@code value too long for type character varying(20)}
 * outage on Transaction History → Confirm (see
 * {@code docs/audits/transaction-history-confirm-legacy-audit.md}): legacy
 * stamps {@code CNFM_BY_USER_ID = loginUser.getBizKey()} (biz_key, ≤ 20 chars),
 * NOT the login user id. The refactor previously wrote the login user id, which
 * for the HAENGSUNG admin ({@code wecmshaengsung@gmail.com}, 24 chars) overflows
 * the {@code varchar(20)} column.
 *
 * <p>The confirming user id here is a 24-char email — the exact shape that broke
 * production — so these tests fail if the service ever regresses to writing the
 * user id into {@code CNFM_BY_USER_ID}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionServiceConfirmTest {

    @Mock AcctTrnsHstrRepository       txnRepo;
    @Mock AcctTrnsRcvPayInfmRepository rcvPayRepo;
    @Mock ChartOfAccountRepository     chartAcctRepo;
    @Mock PurposeSettingsRepository    purposeRepo;
    @Mock BusiVasAcctInfmRepository    vasAcctRepo;
    @Mock TrcoInfmRepository           contactRepo;
    @Mock AcctMstrRepository           acctRepo;
    @Mock BusiMatchLogRepository       matchLogRepo;
    @Mock ScdulPymnSumrRepository      scdulPymnSumrRepo;
    @Mock UserInfmRepository           userRepo;
    @Mock AccountAccessGuard           accountAccessGuard;
    @Mock LegacyFunctionRepository     legacyFunctions;

    TransactionService service;

    // HAENGSUNG tenant — the exact values from the outage.
    private static final String BIZ      = "VN_VNM0004_000014";              // 17 chars — fits varchar(20)
    private static final String USER_ID  = "wecmshaengsung@gmail.com";       // 24 chars — overflowed varchar(20)
    private static final String USER_NM  = "Admin";
    private static final String TRNS_SEQ = "1";                              // ACCT_SEQ_NO surrogate

    @BeforeEach
    void setUp() {
        service = new TransactionService(
                txnRepo, rcvPayRepo, chartAcctRepo, purposeRepo, vasAcctRepo,
                contactRepo, acctRepo, matchLogRepo, scdulPymnSumrRepo, userRepo,
                accountAccessGuard, legacyFunctions);
        TenantContext.set(USER_ID, USER_NM, BIZ, LangCd.EN, TenantContext.AUTH_GR_ADMIN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** VND path (legacy fnsh_0002_001_u002): CNFM_BY_USER_ID = BIZ_KEY, not the 24-char user id. */
    @Test
    void confirm_vnd_stampsBizKeyIntoCnfmByUserId_notLongUserId() {
        AcctTrnsRcvPayInfm saved = confirmAndCaptureSaved("11704088", "700005285280", "VND");

        assertThat(saved.getCnfmYn()).isEqualTo("Y");
        // Legacy parity: biz_key in CNFM_BY_USER_ID — and it fits varchar(20).
        assertThat(saved.getCnfmByUserId()).isEqualTo(BIZ);
        assertThat(saved.getCnfmByUserId()).isNotEqualTo(USER_ID);
        assertThat(saved.getCnfmByUserId().length()).isLessThanOrEqualTo(20);
        // Display name still carried by CNFM_BY_USER_NM.
        assertThat(saved.getCnfmByUserNm()).isEqualTo(USER_NM);
        // Real (long) user id preserved in the varchar(100) audit column.
        assertThat(saved.getRegId()).isEqualTo(USER_ID);
    }

    /** FCY path (legacy fnsh_0006_001_u002): identical confirmer stamping — no currency-specific logic. */
    @Test
    void confirm_fcy_stampsBizKeyIntoCnfmByUserId_notLongUserId() {
        AcctTrnsRcvPayInfm saved = confirmAndCaptureSaved("11704088", "1234567890", "USD");

        assertThat(saved.getCnfmYn()).isEqualTo("Y");
        assertThat(saved.getCnfmByUserId()).isEqualTo(BIZ);
        assertThat(saved.getCnfmByUserId()).isNotEqualTo(USER_ID);
        assertThat(saved.getCnfmByUserId().length()).isLessThanOrEqualTo(20);
        assertThat(saved.getCnfmByUserNm()).isEqualTo(USER_NM);
        assertThat(saved.getRegId()).isEqualTo(USER_ID);
    }

    /* ---- fixtures ----------------------------------------------------- */

    private AcctTrnsRcvPayInfm confirmAndCaptureSaved(String bank, String acct, String ccyCd) {
        String seq = bank + "_" + acct;

        AcctMstr m = new AcctMstr();
        m.setBizKey(BIZ);
        m.setBankCd(bank);
        m.setAcctNo(acct);
        m.setCcyCd(ccyCd);
        m.setBal(new BigDecimal("1000"));
        when(acctRepo.findByBizKeyAndBankCdAndAcctNo(BIZ, bank, acct)).thenReturn(Optional.of(m));

        AcctTrnsHstr h = new AcctTrnsHstr();
        h.setBizKey(BIZ);
        h.setAcctSeqNo(TRNS_SEQ);
        h.setBankCd(bank);
        h.setAcctNo(acct);
        h.setTrscDt("20260629");
        h.setTrscTm("120000");
        h.setCcyCd(ccyCd);
        h.setInoutDv("1");
        h.setTrscAmt(new BigDecimal("100"));
        when(txnRepo.findByBizKeyAndBankCdAndAcctNoOrderByTrscDtDescTrscTmDesc(BIZ, bank, acct))
                .thenReturn(List.of(h));

        // No existing rcv/pay row → create branch (also consumed by the trailing detail() read).
        when(rcvPayRepo.findByBizKeyAndAcctSeqNo(BIZ, TRNS_SEQ)).thenReturn(List.of());
        when(legacyFunctions.nextAcctTrnsRcvPaySeqNo(BIZ, TRNS_SEQ)).thenReturn("1");

        service.confirm(seq, TRNS_SEQ, null);

        ArgumentCaptor<AcctTrnsRcvPayInfm> captor = ArgumentCaptor.forClass(AcctTrnsRcvPayInfm.class);
        verify(rcvPayRepo).save(captor.capture());
        return captor.getValue();
    }
}
