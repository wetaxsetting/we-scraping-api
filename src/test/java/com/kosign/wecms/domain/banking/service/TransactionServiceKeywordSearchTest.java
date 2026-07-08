package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.memo.MemoService;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.repository.UserInfmRepository;
import com.kosign.wecms.domain.banking.dto.TransactionListResponse;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * TH-FCY keyword-search parity with legacy {@code FnshHelper.java:669-707}.
 *
 * <p>Verifies the keyword legs added for legacy parity (ACCT_NO,
 * VAS_ACCT_NM, accent-stripped RMRK via {@link TransactionService#translateText})
 * plus the preserved guarantees (existing RMRK leg, currency isolation,
 * account-access guard). The memo leg is intentionally NOT searchable —
 * {@code MEMO_INFM} is excluded from the PROD Active Table List, so the
 * refactor has no data source for it (documented gap test below).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionServiceKeywordSearchTest {

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
    @Mock MemoService                 memoService;

    TransactionService service;

    private static final String BIZ  = "VN_VNM0004_000001";
    private static final String USER = "tester";
    private static final String BANK = "11704088";          // NOT Vietcombank (11704004)
    private static final String ACCT = "5280123456";
    private static final String SEQ  = BANK + "_" + ACCT;

    @BeforeEach
    void setUp() {
        service = new TransactionService(
                txnRepo, rcvPayRepo, chartAcctRepo, purposeRepo, vasAcctRepo,
                contactRepo, acctRepo, matchLogRepo, scdulPymnSumrRepo, userRepo,
                accountAccessGuard, legacyFunctions, memoService);
        TenantContext.set(USER, USER, BIZ, LangCd.EN, TenantContext.AUTH_GR_ADMIN);
        // FCY account — USD; the row currency filter keeps only USD rows.
        stubAccount("USD");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* ---- new legacy-parity legs --------------------------------------- */

    /** Legacy {@code ATH.acct_no ilike} (:678) — keyword matches the account number. */
    @Test
    void keyword_matchesAccountNumber() {
        stubLedger(txn("u1", "no remark match here"));

        TransactionListResponse res = service.list(SEQ, null, null, null, null, "528012");

        assertThat(res.rows()).hasSize(1);
        assertThat(res.rows().get(0).acctSeqNo()).isEqualTo("u1");
    }

    /** Legacy {@code ATRPI.VAS_ACCT_NM ilike} (:692) — keyword matches the VAS account NAME. */
    @Test
    void keyword_matchesVasAccountName() {
        stubLedger(txn("u1", "random remark"));
        when(rcvPayRepo.findByBizKeyAndAcctSeqNoIn(eq(BIZ), any()))
                .thenReturn(List.of(rcvPayVas("u1", "9999", "ELECTRICITY EVN HCMC")));

        TransactionListResponse res = service.list(SEQ, null, null, null, null, "electricity");

        assertThat(res.rows()).hasSize(1);
        assertThat(res.rows().get(0).acctSeqNo()).isEqualTo("u1");
    }

    /** Legacy {@code wb_get_translate_text(ATH.rmrk) ilike} (:682) — an un-accented
     *  keyword matches an accented Vietnamese remark. */
    @Test
    void keyword_matchesAccentStrippedRemark() {
        stubLedger(txn("u1", "Thanh toán Nguyễn Văn A"));

        TransactionListResponse res = service.list(SEQ, null, null, null, null, "nguyen van a");

        assertThat(res.rows()).hasSize(1);
        assertThat(res.rows().get(0).acctSeqNo()).isEqualTo("u1");
    }

    /** Sanity: {@link TransactionService#translateText} strips Vietnamese diacritics
     *  (mirrors the legacy {@code wb_get_translate_text} DB function). */
    @Test
    void translateText_stripsVietnameseDiacritics() {
        assertThat(TransactionService.translateText("Nguyễn Văn Đức"))
                .isEqualTo("Nguyen Van Duc");
        assertThat(TransactionService.translateText(null)).isNull();
    }

    /* ---- preserved behaviour ------------------------------------------ */

    /** Existing RMRK leg still works (no regression). */
    @Test
    void keyword_matchesExistingRmrkField() {
        stubLedger(txn("u1", "Salary payment June"));

        TransactionListResponse res = service.list(SEQ, null, null, null, null, "salary");

        assertThat(res.rows()).hasSize(1);
    }

    /** FCY currency isolation still applies — a VND row under the same
     *  bank_cd+acct_no is filtered out for a USD account. */
    @Test
    void fcyCurrencyFilter_stillApplies() {
        AcctTrnsHstr usd = txn("u1", "deposit");
        AcctTrnsHstr vnd = txn("v1", "deposit");
        vnd.setCcyCd("VND");
        stubLedger(usd, vnd);

        TransactionListResponse res = service.list(SEQ, null, null, null, null, null);

        assertThat(res.rows()).hasSize(1);
        assertThat(res.rows().get(0).acctSeqNo()).isEqualTo("u1");
    }

    /** Denied account guard short-circuits with WBE1003 (HTTP 403). */
    @Test
    void deniedAccountGuard_throwsWbe1003() {
        doThrow(new BusinessException("WBE1003"))
                .when(accountAccessGuard).assertCanView(BIZ, BANK, ACCT);

        assertThatThrownBy(() -> service.list(SEQ, null, null, null, null, "anything"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("WBE1003");
    }

    /* ---- documented scope gap ----------------------------------------- */

    /**
     * Memo leg (legacy {@code exists(... memo_infm ...)} :703-705) is NOT
     * ported — {@code MEMO_INFM} is excluded from the PROD Active Table List,
     * so the refactor has no entity/repository for it. This test pins the
     * current (blocked) behaviour: a keyword that would only match a memo
     * returns no rows. Flip this test when the scope is widened (PO decision).
     */
    @Test
    void keyword_memoLeg_notPortable_returnsNoMatch() {
        stubLedger(txn("u1", "plain remark"));

        TransactionListResponse res = service.list(
                SEQ, null, null, null, null, "memo-only-text-not-on-any-field");

        assertThat(res.rows()).isEmpty();
    }

    /* ---- fixtures ----------------------------------------------------- */

    private void stubAccount(String ccyCd) {
        AcctMstr m = new AcctMstr();
        m.setBizKey(BIZ);
        m.setBankCd(BANK);
        m.setAcctNo(ACCT);
        m.setCcyCd(ccyCd);
        m.setBal(new BigDecimal("1000"));
        when(acctRepo.findByBizKeyAndBankCdAndAcctNo(BIZ, BANK, ACCT))
                .thenReturn(Optional.of(m));
    }

    private void stubLedger(AcctTrnsHstr... rows) {
        when(txnRepo.findByBizKeyAndBankCdAndAcctNoOrderByTrscDtDescTrscTmDesc(BIZ, BANK, ACCT))
                .thenReturn(List.of(rows));
    }

    private static AcctTrnsHstr txn(String acctSeqNo, String rmrk) {
        AcctTrnsHstr h = new AcctTrnsHstr();
        h.setBizKey(BIZ);
        h.setAcctSeqNo(acctSeqNo);
        h.setBankCd(BANK);
        h.setAcctNo(ACCT);
        h.setTrscDt("20260101");
        h.setTrscTm("120000");
        h.setCcyCd("USD");
        h.setInoutDv("1");
        h.setTrscAmt(new BigDecimal("100"));
        h.setRmrk(rmrk);
        return h;
    }

    private static AcctTrnsRcvPayInfm rcvPayVas(String acctSeqNo, String vasCd, String vasNm) {
        AcctTrnsRcvPayInfm rp = new AcctTrnsRcvPayInfm();
        rp.setBizKey(BIZ);
        rp.setAcctSeqNo(acctSeqNo);
        rp.setVasAcctCd(vasCd);
        rp.setVasAcctNm(vasNm);
        rp.setRegDtm("20260101120000");
        return rp;
    }
}
