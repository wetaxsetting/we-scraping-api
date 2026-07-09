package com.kosign.wecms.domain.payment.match.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstrId;
import com.kosign.wecms.domain.banking.entity.BusiMatchLog;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.repository.BusiMatchLogRepository;
import com.kosign.wecms.domain.payment.dto.OverseasMatchCandidateListResponse;
import com.kosign.wecms.domain.payment.dto.OverseasMatchResultResponse;
import com.kosign.wecms.domain.payment.dto.OverseasUnmatchAllResponse;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl;
import com.kosign.wecms.domain.payment.match.dto.PaymentLineMatchRequest;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnDtlsRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnSumrRepository;
import org.junit.jupiter.api.AfterEach;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OverseasPaymentLineMatchService} (P2). Parity with the
 * domestic match flow — candidate list, match, unmatch, unmatch-all, re-lock
 * after unmatch, duplicate-locked guard, and the already-matched guards.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OverseasPaymentLineMatchServiceTest {

    private static final String BIZ = "VN_VNM0004_000001";
    private static final String USER = "u1";
    private static final String OVRS_NO = "VO250708001";
    private static final String DETL_NO = "1";
    private static final String PAY_TRNS_ID = OVRS_NO + "_" + DETL_NO;
    private static final String ACCT_NO = "9988776655";
    private static final String SEQ = "SEQ1";
    private static final String SHORT_ID = "20260708_A3F2";
    private static final String FULL_ID = "fullid-abc";
    private static final BigDecimal AMT = new BigDecimal("1500.00");

    @Mock AcctTrnsHstrRepository txnRepo;
    @Mock BusiMatchLogRepository matchLogRepo;
    @Mock OvrsPymnSumrRepository sumrRepo;
    @Mock OvrsPymnDtlsRepository detlRepo;
    @Mock LegacyFunctionRepository legacyFunctions;

    @InjectMocks OverseasPaymentLineMatchService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(USER, BIZ, LangCd.EN);
        when(legacyFunctions.nextBusiMatchLogSeq(anyString())).thenReturn("1001");
        when(sumrRepo.findByBizKeyAndOvrsPymnNo(BIZ, OVRS_NO)).thenReturn(Optional.of(batch()));
        when(txnRepo.markBankRowMatchedOverseas(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenReturn(1);
        when(detlRepo.markOverseasDetailMatched(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString())).thenReturn(1);
        when(matchLogRepo.findFirstByBizKeyAndBankTrnsShortIdAndPayTrnsIdAndLockedYn(
                anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* --------------------------------------------------------- list ------ */

    @Test
    void listCandidates_returnsOverseasRows_withEchoHeader() {
        OvrsPymnSumrDetl line = line("N");
        when(detlRepo.findByBizKeyAndOvrsPymnNoAndOvrsPymnDetlNo(BIZ, OVRS_NO, DETL_NO))
                .thenReturn(Optional.of(line));
        when(txnRepo.findOverseasMatchCandidates(eq(BIZ), eq(ACCT_NO), eq("20260708"), eq(AMT),
                any(), any(), any())).thenReturn(List.of(bankRow()));

        OverseasMatchCandidateListResponse res = service.listCandidates(OVRS_NO, DETL_NO, null);

        assertThat(res.ovrsPymnNo()).isEqualTo(OVRS_NO);
        assertThat(res.ovrsPymnDetlNo()).isEqualTo(DETL_NO);
        assertThat(res.wdrlActNo()).isEqualTo(ACCT_NO);
        assertThat(res.totCnt()).isEqualTo(1);
        assertThat(res.candidates()).singleElement()
                .satisfies(c -> assertThat(c.acctSeqNo()).isEqualTo(SEQ));
    }

    @Test
    void listCandidates_passesTransferTimeFloor_convertedUtcToVnLocal() {
        // P3 — batch TRNF_DT 03:00:00 UTC → 10:00:00 VN (+7). Legacy parity:
        // TRSC_TM >= local payment time (putBankingCandidateIdoInOvrs).
        OvrsPymnSumr batch = batch();
        batch.setTrnfDt("20260708030000");
        when(sumrRepo.findByBizKeyAndOvrsPymnNo(BIZ, OVRS_NO)).thenReturn(Optional.of(batch));
        when(detlRepo.findByBizKeyAndOvrsPymnNoAndOvrsPymnDetlNo(BIZ, OVRS_NO, DETL_NO))
                .thenReturn(Optional.of(line("N")));
        when(txnRepo.findOverseasMatchCandidates(eq(BIZ), eq(ACCT_NO), eq("20260708"), eq(AMT),
                any(), any(), any())).thenReturn(List.of());

        service.listCandidates(OVRS_NO, DETL_NO, null);

        ArgumentCaptor<String> time = ArgumentCaptor.forClass(String.class);
        verify(txnRepo).findOverseasMatchCandidates(eq(BIZ), eq(ACCT_NO), eq("20260708"), eq(AMT),
                time.capture(), any(), any());
        assertThat(time.getValue()).isEqualTo("100000");
    }

    @Test
    void listCandidates_passesNullTransferTime_whenBatchNotTransferred() {
        // batch() has no TRNF_DT → filter skipped (null passed).
        when(detlRepo.findByBizKeyAndOvrsPymnNoAndOvrsPymnDetlNo(BIZ, OVRS_NO, DETL_NO))
                .thenReturn(Optional.of(line("N")));
        when(txnRepo.findOverseasMatchCandidates(eq(BIZ), eq(ACCT_NO), eq("20260708"), eq(AMT),
                any(), any(), any())).thenReturn(List.of());

        service.listCandidates(OVRS_NO, DETL_NO, null);

        ArgumentCaptor<String> time = ArgumentCaptor.forClass(String.class);
        verify(txnRepo).findOverseasMatchCandidates(eq(BIZ), eq(ACCT_NO), eq("20260708"), eq(AMT),
                time.capture(), any(), any());
        assertThat(time.getValue()).isNull();
    }

    @Test
    void listCandidates_throwsWhenLineAlreadyMatched() {
        when(detlRepo.findByBizKeyAndOvrsPymnNoAndOvrsPymnDetlNo(BIZ, OVRS_NO, DETL_NO))
                .thenReturn(Optional.of(line("Y")));
        assertThatThrownBy(() -> service.listCandidates(OVRS_NO, DETL_NO, null))
                .isInstanceOf(BusinessException.class);
    }

    /* -------------------------------------------------------- match ------ */

    @Test
    void match_writesLog_A_M_locked_andStampsBothSides() {
        when(detlRepo.findByBizKeyAndOvrsPymnNoAndOvrsPymnDetlNo(BIZ, OVRS_NO, DETL_NO))
                .thenReturn(Optional.of(line("N")));
        when(txnRepo.findById(new AcctTrnsHstrId(BIZ, SEQ))).thenReturn(Optional.of(bankRow()));

        OverseasMatchResultResponse res = service.match(OVRS_NO, DETL_NO, req());

        assertThat(res.linkMatchYn()).isEqualTo("Y");
        assertThat(res.ovrsPymnNo()).isEqualTo(OVRS_NO);
        assertThat(res.acctSeqNo()).isEqualTo(SEQ);

        ArgumentCaptor<BusiMatchLog> cap = ArgumentCaptor.forClass(BusiMatchLog.class);
        verify(matchLogRepo).save(cap.capture());
        assertThat(cap.getValue().getMatchTypeCd()).isEqualTo("M");
        assertThat(cap.getValue().getLockedYn()).isEqualTo("Y");
        assertThat(cap.getValue().getPayTrnsId()).isEqualTo(PAY_TRNS_ID);
        assertThat(cap.getValue().getBankTrnsShortId()).isEqualTo(SHORT_ID);

        verify(txnRepo).markBankRowMatchedOverseas(eq(BIZ), eq(SEQ), eq(OVRS_NO), eq(DETL_NO),
                eq(USER), anyString());
        verify(detlRepo).markOverseasDetailMatched(eq(BIZ), eq(OVRS_NO), eq(DETL_NO), eq(SEQ),
                eq(SHORT_ID), eq(FULL_ID), eq(USER), anyString());
    }

    @Test
    void match_throwsWhenBankAlreadyMatched() {
        when(detlRepo.findByBizKeyAndOvrsPymnNoAndOvrsPymnDetlNo(BIZ, OVRS_NO, DETL_NO))
                .thenReturn(Optional.of(line("N")));
        AcctTrnsHstr bank = bankRow();
        bank.setLinkMatchYn("Y");
        when(txnRepo.findById(new AcctTrnsHstrId(BIZ, SEQ))).thenReturn(Optional.of(bank));

        assertThatThrownBy(() -> service.match(OVRS_NO, DETL_NO, req()))
                .isInstanceOf(BusinessException.class);
        verify(matchLogRepo, never()).save(any());
    }

    @Test
    void match_duplicateLockedLog_guard() {
        when(detlRepo.findByBizKeyAndOvrsPymnNoAndOvrsPymnDetlNo(BIZ, OVRS_NO, DETL_NO))
                .thenReturn(Optional.of(line("N")));
        when(txnRepo.findById(new AcctTrnsHstrId(BIZ, SEQ))).thenReturn(Optional.of(bankRow()));
        when(matchLogRepo.findFirstByBizKeyAndBankTrnsShortIdAndPayTrnsIdAndLockedYn(
                BIZ, SHORT_ID, PAY_TRNS_ID, "Y")).thenReturn(Optional.of(new BusiMatchLog()));

        assertThatThrownBy(() -> service.match(OVRS_NO, DETL_NO, req()))
                .isInstanceOf(BusinessException.class);
        verify(txnRepo, never()).markBankRowMatchedOverseas(any(), any(), any(), any(), any(), any());
    }

    @Test
    void match_relocksRetainedRow_afterUnmatch_noNewSeq() {
        when(detlRepo.findByBizKeyAndOvrsPymnNoAndOvrsPymnDetlNo(BIZ, OVRS_NO, DETL_NO))
                .thenReturn(Optional.of(line("N")));
        when(txnRepo.findById(new AcctTrnsHstrId(BIZ, SEQ))).thenReturn(Optional.of(bankRow()));
        BusiMatchLog retained = new BusiMatchLog();
        retained.setMatchLogSeq("500");
        retained.setPayTrnsId(PAY_TRNS_ID);
        retained.setLockedYn("N");
        when(matchLogRepo.findFirstByBizKeyAndBankTrnsShortIdAndPayTrnsIdAndLockedYn(
                BIZ, SHORT_ID, PAY_TRNS_ID, "N")).thenReturn(Optional.of(retained));

        service.match(OVRS_NO, DETL_NO, req());

        ArgumentCaptor<BusiMatchLog> cap = ArgumentCaptor.forClass(BusiMatchLog.class);
        verify(matchLogRepo).save(cap.capture());
        assertThat(cap.getValue().getMatchLogSeq()).isEqualTo("500");   // reused
        assertThat(cap.getValue().getLockedYn()).isEqualTo("Y");        // re-locked
        verify(legacyFunctions, never()).nextBusiMatchLogSeq(anyString());
    }

    /* ------------------------------------------------------- unmatch ----- */

    @Test
    void unmatch_unlocksLog_andClearsBothSides() {
        OvrsPymnSumrDetl line = line("Y");
        line.setAcctSeqNo(SEQ);
        when(detlRepo.findByBizKeyAndOvrsPymnNoAndOvrsPymnDetlNo(BIZ, OVRS_NO, DETL_NO))
                .thenReturn(Optional.of(line));
        BusiMatchLog log = new BusiMatchLog();
        log.setMatchLogSeq("500");
        log.setPayTrnsId(PAY_TRNS_ID);
        log.setLockedYn("Y");
        when(matchLogRepo.findFirstByBizKeyAndPayTrnsIdAndLockedYn(BIZ, PAY_TRNS_ID, "Y"))
                .thenReturn(Optional.of(log));

        OverseasMatchResultResponse res = service.unmatch(OVRS_NO, DETL_NO);

        assertThat(res.linkMatchYn()).isEqualTo("N");
        ArgumentCaptor<BusiMatchLog> cap = ArgumentCaptor.forClass(BusiMatchLog.class);
        verify(matchLogRepo).save(cap.capture());
        assertThat(cap.getValue().getLockedYn()).isEqualTo("N");   // unlocked, not deleted
        verify(txnRepo).clearBankRowMatch(eq(BIZ), eq(SEQ), eq(USER), anyString());
        verify(detlRepo).clearOverseasDetailMatch(eq(BIZ), eq(OVRS_NO), eq(DETL_NO), eq(USER), anyString());
    }

    @Test
    void unmatch_throwsWhenNotMatched() {
        when(detlRepo.findByBizKeyAndOvrsPymnNoAndOvrsPymnDetlNo(BIZ, OVRS_NO, DETL_NO))
                .thenReturn(Optional.of(line("N")));
        assertThatThrownBy(() -> service.unmatch(OVRS_NO, DETL_NO))
                .isInstanceOf(BusinessException.class);
    }

    /* --------------------------------------------------- unmatch-all ----- */

    @Test
    void unmatchAll_clearsOnlyMatchedLines() {
        OvrsPymnSumrDetl matched = line("Y");
        matched.setAcctSeqNo(SEQ);
        OvrsPymnSumrDetl unmatched = new OvrsPymnSumrDetl();
        unmatched.setBizKey(BIZ);
        unmatched.setOvrsPymnNo(OVRS_NO);
        unmatched.setOvrsPymnDetlNo("2");
        unmatched.setLinkMatchYn("N");
        when(detlRepo.findByBizKeyAndOvrsPymnNoOrderByOvrsPymnDetlNoAsc(BIZ, OVRS_NO))
                .thenReturn(List.of(matched, unmatched));
        when(matchLogRepo.findFirstByBizKeyAndPayTrnsIdAndLockedYn(eq(BIZ), anyString(), eq("Y")))
                .thenReturn(Optional.of(new BusiMatchLog()));

        OverseasUnmatchAllResponse res = service.unmatchAll(OVRS_NO);

        assertThat(res.unmatchedCount()).isEqualTo(1);
        verify(detlRepo).clearOverseasDetailMatch(eq(BIZ), eq(OVRS_NO), eq(DETL_NO), eq(USER), anyString());
        verify(detlRepo, never()).clearOverseasDetailMatch(eq(BIZ), eq(OVRS_NO), eq("2"), any(), any());
    }

    /* --------------------------------------------------------- fixtures -- */

    private static OvrsPymnSumr batch() {
        OvrsPymnSumr s = new OvrsPymnSumr();
        s.setBizKey(BIZ);
        s.setOvrsPymnNo(OVRS_NO);
        s.setWdrlActNo(ACCT_NO);
        return s;
    }

    private static OvrsPymnSumrDetl line(String linkMatchYn) {
        OvrsPymnSumrDetl d = new OvrsPymnSumrDetl();
        d.setBizKey(BIZ);
        d.setOvrsPymnNo(OVRS_NO);
        d.setOvrsPymnDetlNo(DETL_NO);
        d.setTrnfAmt(AMT);
        d.setCcyCd("USD");
        d.setPayTranDt("20260708");
        d.setMemo("PAY ABC");
        d.setLinkMatchYn(linkMatchYn);
        return d;
    }

    private static AcctTrnsHstr bankRow() {
        AcctTrnsHstr h = new AcctTrnsHstr();
        h.setBizKey(BIZ);
        h.setAcctSeqNo(SEQ);
        h.setAcctNo(ACCT_NO);
        h.setBankTrnsShortId(SHORT_ID);
        h.setBankTrnsFullId(FULL_ID);
        h.setLinkMatchYn("N");
        h.setTrscDt("20260708");
        h.setTrscTm("103000");
        h.setTrscAmt(AMT);
        h.setCcyCd("USD");
        h.setRmrk("PAY ABC REF");
        return h;
    }

    private static PaymentLineMatchRequest req() {
        return new PaymentLineMatchRequest(SEQ, SHORT_ID, FULL_ID, "memo note");
    }
}
