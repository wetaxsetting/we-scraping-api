package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.entity.BusiMatchLog;
import com.kosign.wecms.domain.banking.nxisas.BankTrnsIdGenerator;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.repository.BusiMatchLogRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResult;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnDtlsRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnSumrRepository;
import com.kosign.wecms.domain.setting.environmentsettings.entity.BusiEnvSetInfm;
import com.kosign.wecms.domain.setting.environmentsettings.repository.BusiEnvSetInfmRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AutoMatchService} (P1 auto-matching). Covers the
 * legacy-parity matching conditions, the exactly-one guard, the feature gate,
 * the duplicate guard, and payroll master-level matching. The DB-side
 * candidate filters (account / amount / date / transferred / type) are the
 * repository queries' responsibility and are exercised by returning controlled
 * candidate lists from the mocked repos; this test verifies the service's
 * success / time / remark / cardinality / write logic on top of them.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutoMatchServiceTest {

    private static final String BIZ = "VN_VNM0004_000001";
    private static final String USER = "u1";
    private static final String ACCT_NO = "9988776655";
    private static final String TRSC_DT = "20260708";
    private static final BigDecimal AMT = new BigDecimal("1500000");

    @Mock AcctTrnsHstrRepository txnRepo;
    @Mock BusiMatchLogRepository matchLogRepo;
    @Mock ScdulPymnSumrRepository sumrRepo;
    @Mock ScdulPymnSumrDetlRepository detlRepo;
    @Mock OvrsPymnSumrRepository ovrsSumrRepo;
    @Mock OvrsPymnDtlsRepository ovrsDetlRepo;
    @Mock DispatchResultResolver dispatchResultResolver;
    @Mock LegacyFunctionRepository legacyFunctions;
    @Mock BusiEnvSetInfmRepository envRepo;

    @InjectMocks AutoMatchService service;

    @BeforeEach
    void enableGateByDefault() {
        gate("Y");
        when(legacyFunctions.nextBusiMatchLogSeq(anyString())).thenReturn("1001");
        when(txnRepo.markBankRowMatched(anyString(), anyString(), any(), any(), anyString(), anyString()))
                .thenReturn(1);
        when(txnRepo.markBankRowMatchedOverseas(anyString(), anyString(), any(), any(),
                anyString(), anyString())).thenReturn(1);
        when(detlRepo.markDetailMatched(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString())).thenReturn(1);
        when(ovrsDetlRepo.markOverseasDetailMatched(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString())).thenReturn(1);
        // Default: no domestic / overseas candidates unless a test provides them.
        when(detlRepo.findDomesticAutoMatchCandidates(anyString(), anyString(), anyString(), any()))
                .thenReturn(List.of());
        when(ovrsDetlRepo.findOverseasAutoMatchCandidates(anyString(), anyString(), anyString(), any()))
                .thenReturn(List.of());
        // No existing match-log rows by default (fresh insert path).
        when(matchLogRepo.findFirstByBizKeyAndBankTrnsShortIdAndPayTrnsIdAndLockedYn(
                anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.empty());
    }

    /* ------------------------------------------------- §3 remark normalise -- */

    @Test
    void cleanRemark_isTheSharedNormaliser_trimUpperCollapse() {
        assertThat(BankTrnsIdGenerator.cleanRemark("  pay   from  ABC ")).isEqualTo("PAY FROM ABC");
        assertThat(BankTrnsIdGenerator.cleanRemark(null)).isEmpty();
        assertThat(BankTrnsIdGenerator.cleanRemark("   ")).isEmpty();
    }

    /* ------------------------------------------------------ core scenarios -- */

    @Test
    void exactlyOneDomesticCandidate_createsAutoMatchLog_andLinksBothSides() {
        AcctTrnsHstr debit = debit("SEQ1", "103000", "REF PAY FROM ABC 123");
        domesticCandidate("P100", "1", "pay from abc", "20260708101500", success());

        int matched = service.autoMatchNewDebits(BIZ, USER, ACCT_NO, List.of(debit));

        assertThat(matched).isEqualTo(1);
        ArgumentCaptor<BusiMatchLog> cap = ArgumentCaptor.forClass(BusiMatchLog.class);
        verify(matchLogRepo).save(cap.capture());
        BusiMatchLog log = cap.getValue();
        assertThat(log.getMatchTypeCd()).isEqualTo("A");
        assertThat(log.getLockedYn()).isEqualTo("Y");
        assertThat(log.getPayTrnsId()).isEqualTo("P100_1");
        assertThat(log.getBankTrnsShortId()).isEqualTo("SHORT_SEQ1");
        assertThat(log.getBankTrnsFullId()).isEqualTo("FULL_SEQ1");
        assertThat(log.getMatchedBy()).isEqualTo(USER);
        verify(txnRepo).markBankRowMatched(eq(BIZ), eq("SEQ1"), eq("P100"), eq("1"), eq(USER),
                anyString());
        verify(detlRepo).markDetailMatched(eq(BIZ), eq("P100"), eq("1"), eq("SEQ1"),
                eq("SHORT_SEQ1"), eq("FULL_SEQ1"), eq(USER), anyString());
    }

    @Test
    void noCandidate_doesNotMatch() {
        int matched = service.autoMatchNewDebits(BIZ, USER, ACCT_NO,
                List.of(debit("SEQ1", "103000", "ANY")));
        assertThat(matched).isZero();
        verify(matchLogRepo, never()).save(any());
        verify(txnRepo, never()).markBankRowMatched(any(), any(), any(), any(), any(), any());
    }

    @Test
    void multipleCandidates_areAmbiguous_soNoMatch() {
        AcctTrnsHstr debit = debit("SEQ1", "103000", "PAY ABC");
        ScdulPymnSumrDetl d1 = detl("P100", "1", "PAY ABC");
        ScdulPymnSumrDetl d2 = detl("P200", "1", "PAY ABC");
        when(detlRepo.findDomesticAutoMatchCandidates(BIZ, ACCT_NO, TRSC_DT, AMT))
                .thenReturn(List.of(d1, d2));
        when(sumrRepo.findById(any())).thenReturn(
                Optional.of(master("P100", "20260708101500")),
                Optional.of(master("P200", "20260708101500")));
        when(dispatchResultResolver.resolve(any(), any()))
                .thenReturn(Map.of("1", success()));

        int matched = service.autoMatchNewDebits(BIZ, USER, ACCT_NO, List.of(debit));

        assertThat(matched).isZero();
        verify(matchLogRepo, never()).save(any());
    }

    @Test
    void failedPaymentResult_doesNotMatch() {
        AcctTrnsHstr debit = debit("SEQ1", "103000", "PAY ABC");
        domesticCandidate("P100", "1", "PAY ABC", "20260708101500", failed());

        int matched = service.autoMatchNewDebits(BIZ, USER, ACCT_NO, List.of(debit));

        assertThat(matched).isZero();
        verify(matchLogRepo, never()).save(any());
    }

    @Test
    void inProgressPaymentResult_doesNotMatch() {
        AcctTrnsHstr debit = debit("SEQ1", "103000", "PAY ABC");
        // proc_stat '9' waiting, no rs_cd → not success.
        domesticCandidate("P100", "1", "PAY ABC", "20260708101500",
                new DispatchResult("9", "k", null, null, null, null));

        int matched = service.autoMatchNewDebits(BIZ, USER, ACCT_NO, List.of(debit));

        assertThat(matched).isZero();
    }

    @Test
    void notTransferredPayment_isFilteredByQuery_soNoMatch() {
        // The transferred (TRNF_YN='Y') gate lives in the candidate SQL; a
        // not-transferred payment simply never appears in the candidate list.
        when(detlRepo.findDomesticAutoMatchCandidates(BIZ, ACCT_NO, TRSC_DT, AMT))
                .thenReturn(List.of());
        int matched = service.autoMatchNewDebits(BIZ, USER, ACCT_NO,
                List.of(debit("SEQ1", "103000", "PAY ABC")));
        assertThat(matched).isZero();
    }

    @Test
    void paymentTransferTimeNotBeforeBankTime_doesNotMatch() {
        AcctTrnsHstr debit = debit("SEQ1", "090000", "PAY ABC");   // bank 09:00:00
        // payment transferred 10:15:00 — NOT < 09:00:00.
        domesticCandidate("P100", "1", "PAY ABC", "20260708101500", success());

        int matched = service.autoMatchNewDebits(BIZ, USER, ACCT_NO, List.of(debit));

        assertThat(matched).isZero();
        verify(matchLogRepo, never()).save(any());
    }

    @Test
    void sameAmountAndDateButRemarkNotContained_doesNotMatch() {
        AcctTrnsHstr debit = debit("SEQ1", "103000", "COMPLETELY DIFFERENT");
        domesticCandidate("P100", "1", "PAY FROM ABC", "20260708101500", success());

        int matched = service.autoMatchNewDebits(BIZ, USER, ACCT_NO, List.of(debit));

        assertThat(matched).isZero();
    }

    @Test
    void blankBankTime_disablesTimeFilter_stillMatches() {
        AcctTrnsHstr debit = debit("SEQ1", "", "PAY ABC");   // no bank time
        domesticCandidate("P100", "1", "PAY ABC", "20260708235900", success());

        int matched = service.autoMatchNewDebits(BIZ, USER, ACCT_NO, List.of(debit));

        assertThat(matched).isEqualTo(1);
    }

    @Test
    void duplicateLockedMatchAlreadyExists_doesNotInsertOrRelink() {
        AcctTrnsHstr debit = debit("SEQ1", "103000", "PAY ABC");
        domesticCandidate("P100", "1", "PAY ABC", "20260708101500", success());
        // A live (locked) log for this exact pair already exists.
        when(matchLogRepo.findFirstByBizKeyAndBankTrnsShortIdAndPayTrnsIdAndLockedYn(
                BIZ, "SHORT_SEQ1", "P100_1", "Y"))
                .thenReturn(Optional.of(new BusiMatchLog()));

        int matched = service.autoMatchNewDebits(BIZ, USER, ACCT_NO, List.of(debit));

        assertThat(matched).isZero();
        verify(matchLogRepo, never()).save(any());
        verify(txnRepo, never()).markBankRowMatched(any(), any(), any(), any(), any(), any());
    }

    @Test
    void unmatchedThenAutoMatch_relocksRetainedRow_noDuplicate() {
        AcctTrnsHstr debit = debit("SEQ1", "103000", "PAY ABC");
        domesticCandidate("P100", "1", "PAY ABC", "20260708101500", success());
        // A retained (unlocked) row from a prior unmatch exists for the pair.
        BusiMatchLog retained = new BusiMatchLog();
        retained.setBizKey(BIZ);
        retained.setMatchLogSeq("500");           // already has a seq → not fresh
        retained.setPayTrnsId("P100_1");
        retained.setLockedYn("N");
        when(matchLogRepo.findFirstByBizKeyAndBankTrnsShortIdAndPayTrnsIdAndLockedYn(
                BIZ, "SHORT_SEQ1", "P100_1", "N")).thenReturn(Optional.of(retained));

        int matched = service.autoMatchNewDebits(BIZ, USER, ACCT_NO, List.of(debit));

        assertThat(matched).isEqualTo(1);
        ArgumentCaptor<BusiMatchLog> cap = ArgumentCaptor.forClass(BusiMatchLog.class);
        verify(matchLogRepo).save(cap.capture());
        assertThat(cap.getValue().getMatchLogSeq()).isEqualTo("500");   // reused, not new
        assertThat(cap.getValue().getLockedYn()).isEqualTo("Y");        // re-locked
        assertThat(cap.getValue().getMatchTypeCd()).isEqualTo("A");
        // Did not allocate a fresh sequence.
        verify(legacyFunctions, never()).nextBusiMatchLogSeq(anyString());
    }

    @Test
    void payroll_isNotAutoMatched_legacyParity() {
        // Legacy R007 excludes payroll (SCDUL_PYMN_TYPE_CD='00') and has no
        // payroll code path — payroll is manual-match-only. The refactor
        // therefore never queries payroll here; even if a payroll batch shares
        // the debit's account/date/amount, it must not auto-match, and the
        // SCDUL_PYMN_SUMR master must never be written through this flow.
        AcctTrnsHstr debit = debit("SEQ1", "103000", "SALARY JUL");

        int matched = service.autoMatchNewDebits(BIZ, USER, ACCT_NO, List.of(debit));

        assertThat(matched).isZero();
        verify(matchLogRepo, never()).save(any());
        verify(sumrRepo, never()).save(any());
        verify(txnRepo, never()).markBankRowMatched(any(), any(), any(), any(), any(), any());
    }

    @Test
    void exactlyOneOverseasCandidate_createsAutoMatchLog_andLinksOverseasDetail() {
        AcctTrnsHstr debit = debit("SEQ1", "103000", "REF SWIFT PAY ACME 999");
        overseasCandidate("O500", "2", "swift pay acme", "20260708", success());

        int matched = service.autoMatchNewDebits(BIZ, USER, ACCT_NO, List.of(debit));

        assertThat(matched).isEqualTo(1);
        ArgumentCaptor<BusiMatchLog> cap = ArgumentCaptor.forClass(BusiMatchLog.class);
        verify(matchLogRepo).save(cap.capture());
        BusiMatchLog log = cap.getValue();
        assertThat(log.getMatchTypeCd()).isEqualTo("A");
        assertThat(log.getLockedYn()).isEqualTo("Y");
        assertThat(log.getPayTrnsId()).isEqualTo("O500_2");
        // Bank row linked via the OVERSEAS FK path; overseas detail updated.
        verify(txnRepo).markBankRowMatchedOverseas(eq(BIZ), eq("SEQ1"), eq("O500"), eq("2"),
                eq(USER), anyString());
        verify(ovrsDetlRepo).markOverseasDetailMatched(eq(BIZ), eq("O500"), eq("2"), eq("SEQ1"),
                eq("SHORT_SEQ1"), eq("FULL_SEQ1"), eq(USER), anyString());
        // Never touches the domestic path or any master/header table.
        verify(txnRepo, never()).markBankRowMatched(any(), any(), any(), any(), any(), any());
        verify(detlRepo, never()).markDetailMatched(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void overseasFailedResult_doesNotMatch() {
        AcctTrnsHstr debit = debit("SEQ1", "103000", "SWIFT PAY ACME");
        overseasCandidate("O500", "2", "SWIFT PAY ACME", "20260708", failed());

        int matched = service.autoMatchNewDebits(BIZ, USER, ACCT_NO, List.of(debit));

        assertThat(matched).isZero();
        verify(matchLogRepo, never()).save(any());
    }

    @Test
    void oneDomesticAndOneOverseasCandidate_areAmbiguous_soNoMatch() {
        // Cross-branch cardinality: 1 domestic + 1 overseas = 2 candidates → skip.
        AcctTrnsHstr debit = debit("SEQ1", "103000", "PAY ABC");
        domesticCandidate("P100", "1", "PAY ABC", "20260708101500", success());
        overseasCandidate("O500", "2", "PAY ABC", "20260708", success());

        int matched = service.autoMatchNewDebits(BIZ, USER, ACCT_NO, List.of(debit));

        assertThat(matched).isZero();
        verify(matchLogRepo, never()).save(any());
    }

    @Test
    void featureGateOff_shortCircuits_noMatchNoQueries() {
        gate(null);   // biz-env 00079 absent
        AcctTrnsHstr debit = debit("SEQ1", "103000", "PAY ABC");

        int matched = service.autoMatchNewDebits(BIZ, USER, ACCT_NO, List.of(debit));

        assertThat(matched).isZero();
        verify(detlRepo, never()).findDomesticAutoMatchCandidates(any(), any(), any(), any());
        verify(matchLogRepo, never()).save(any());
    }

    @Test
    void creditRows_areIgnored() {
        AcctTrnsHstr credit = debit("SEQ1", "103000", "PAY ABC");
        credit.setInoutDv("1");   // deposit, not a withdrawal
        int matched = service.autoMatchNewDebits(BIZ, USER, ACCT_NO, List.of(credit));
        assertThat(matched).isZero();
        verify(detlRepo, never()).findDomesticAutoMatchCandidates(any(), any(), any(), any());
    }

    /* ---------------------------------------------------------- fixtures -- */

    private void gate(String cont) {
        BusiEnvSetInfm env = new BusiEnvSetInfm();
        env.setBizKey(BIZ);
        env.setEnvSetInf(AutoMatchService.ENV_AUTO_MATCH);
        env.setEnvSetCont(cont);
        when(envRepo.findById(any())).thenReturn(cont == null ? Optional.empty() : Optional.of(env));
    }

    /** Register a single domestic candidate line + its parent + dispatch result. */
    private void domesticCandidate(String pymnNo, String detlNo, String tranUsag,
                                   String trnfDtm, DispatchResult result) {
        ScdulPymnSumrDetl d = detl(pymnNo, detlNo, tranUsag);
        when(detlRepo.findDomesticAutoMatchCandidates(BIZ, ACCT_NO, TRSC_DT, AMT))
                .thenReturn(List.of(d));
        when(sumrRepo.findById(any())).thenReturn(Optional.of(master(pymnNo, trnfDtm)));
        when(dispatchResultResolver.resolve(any(), any())).thenReturn(Map.of(detlNo, result));
    }

    /** Register a single overseas candidate line + its parent + dispatch result.
     *  {@code trnfDt} is the overseas batch TRNF_DT — pass an 8-char date so the
     *  transfer-time floor passes trivially (legacy overseas is date-only). */
    private void overseasCandidate(String ovrsNo, String detlNo, String memo,
                                   String trnfDt, DispatchResult result) {
        OvrsPymnSumrDetl d = ovrsDetl(ovrsNo, detlNo, memo);
        when(ovrsDetlRepo.findOverseasAutoMatchCandidates(BIZ, ACCT_NO, TRSC_DT, AMT))
                .thenReturn(List.of(d));
        when(ovrsSumrRepo.findByBizKeyAndOvrsPymnNo(BIZ, ovrsNo))
                .thenReturn(Optional.of(ovrsMaster(ovrsNo, trnfDt)));
        when(dispatchResultResolver.resolveOverseas(any(), any())).thenReturn(Map.of(detlNo, result));
    }

    private static OvrsPymnSumr ovrsMaster(String ovrsPymnNo, String trnfDt) {
        OvrsPymnSumr s = new OvrsPymnSumr();
        s.setBizKey(BIZ);
        s.setOvrsPymnNo(ovrsPymnNo);
        s.setWdrlActNo(ACCT_NO);
        s.setTrnfYn("Y");
        s.setTrnfDt(trnfDt);
        return s;
    }

    private static OvrsPymnSumrDetl ovrsDetl(String ovrsPymnNo, String ovrsPymnDetlNo, String memo) {
        OvrsPymnSumrDetl d = new OvrsPymnSumrDetl();
        d.setBizKey(BIZ);
        d.setOvrsPymnNo(ovrsPymnNo);
        d.setOvrsPymnDetlNo(ovrsPymnDetlNo);
        d.setTrnfAmt(AMT);
        d.setMemo(memo);
        d.setLinkMatchYn("N");
        return d;
    }

    private static AcctTrnsHstr debit(String seq, String trscTm, String rmrk) {
        AcctTrnsHstr h = new AcctTrnsHstr();
        h.setBizKey(BIZ);
        h.setAcctSeqNo(seq);
        h.setBankCd("970424");
        h.setAcctNo(ACCT_NO);
        h.setInoutDv("2");
        h.setTrscAmt(AMT);
        h.setTrscDt(TRSC_DT);
        h.setTrscTm(trscTm);
        h.setRmrk(rmrk);
        h.setBankTrnsShortId("SHORT_" + seq);
        h.setBankTrnsFullId("FULL_" + seq);
        h.setLinkMatchYn("N");
        return h;
    }

    private static ScdulPymnSumr master(String pymnNo, String trnfDtm) {
        ScdulPymnSumr s = new ScdulPymnSumr();
        s.setBizKey(BIZ);
        s.setScdulPymnNo(pymnNo);
        s.setWdrlActNo(ACCT_NO);
        s.setTrnfYn("Y");
        s.setScdulPymnTypeCd("01");
        s.setTotalTranAmt(AMT);
        s.setTrnfDtm(trnfDtm);
        s.setLinkMatchYn("N");
        return s;
    }

    private static ScdulPymnSumrDetl detl(String pymnNo, String detlNo, String tranUsag) {
        ScdulPymnSumrDetl d = new ScdulPymnSumrDetl();
        d.setBizKey(BIZ);
        d.setScdulPymnNo(pymnNo);
        d.setScdulPymnDetlNo(detlNo);
        d.setTranAmt(AMT);
        d.setTranUsag(tranUsag);
        d.setLinkMatchYn("N");
        return d;
    }

    private static DispatchResult success() {
        return new DispatchResult("0", "k", null, null, "0000", null);
    }

    private static DispatchResult failed() {
        return new DispatchResult("3", "k", null, null, "0999", null);
    }
}
