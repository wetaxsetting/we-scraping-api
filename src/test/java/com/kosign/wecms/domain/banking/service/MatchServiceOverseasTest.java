package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.CreateMatchRequest;
import com.kosign.wecms.domain.banking.dto.MatchResultResponse;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstrId;
import com.kosign.wecms.domain.banking.entity.BusiMatchLog;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.repository.BusiMatchLogRepository;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnDtlsRepository;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P2 tests for the banking→payment {@link MatchService} overseas
 * ({@code payTypeCd='O'}) path — create writes the overseas detail + OVRS
 * bank-FK pair; undo of an overseas match clears the overseas detail (domestic
 * clear returns 0, overseas clear runs). Domestic behaviour is unchanged and
 * covered elsewhere.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchServiceOverseasTest {

    private static final String BIZ = "VN_VNM0004_000001";
    private static final String USER = "u1";
    private static final String SEQ = "SEQ1";
    private static final String OVRS_NO = "VO250708001";
    private static final String DETL_NO = "1";
    private static final String PAY_TRNS_ID = OVRS_NO + "_" + DETL_NO;
    private static final String SHORT_ID = "20260708_A3F2";
    private static final String FULL_ID = "fullid-abc";

    @Mock AcctTrnsHstrRepository txnRepo;
    @Mock BusiMatchLogRepository matchLogRepo;
    @Mock ScdulPymnSumrRepository sumrRepo;
    @Mock ScdulPymnSumrDetlRepository detlRepo;
    @Mock OvrsPymnDtlsRepository ovrsDetlRepo;
    @Mock com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnSumrRepository ovrsSumrRepo;
    @Mock com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver dispatchResultResolver;
    @Mock AccountAccessGuard accountAccessGuard;
    @Mock LegacyFunctionRepository legacyFunctions;

    @InjectMocks MatchService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(USER, BIZ, LangCd.EN);
        when(legacyFunctions.nextBusiMatchLogSeq(anyString())).thenReturn("1001");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_overseas_writesOverseasMatch() {
        AcctTrnsHstr bank = bankRow("N");
        when(txnRepo.findById(new AcctTrnsHstrId(BIZ, SEQ))).thenReturn(Optional.of(bank));
        when(ovrsDetlRepo.findByBizKeyAndOvrsPymnNoAndOvrsPymnDetlNo(BIZ, OVRS_NO, DETL_NO))
                .thenReturn(Optional.of(ovrsLine("N")));
        when(matchLogRepo.findFirstByBizKeyAndPayTrnsIdAndLockedYn(BIZ, PAY_TRNS_ID, "Y"))
                .thenReturn(Optional.empty());
        when(matchLogRepo.findFirstByBizKeyAndBankTrnsShortIdAndLockedYn(BIZ, SHORT_ID, "N"))
                .thenReturn(Optional.empty());
        when(txnRepo.markBankRowMatchedOverseas(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenReturn(1);
        when(ovrsDetlRepo.markOverseasDetailMatched(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString())).thenReturn(1);

        MatchResultResponse res = service.create(SEQ, SEQ,
                new CreateMatchRequest(PAY_TRNS_ID, "O", "note"));

        assertThat(res.linkMatchYn()).isEqualTo("Y");
        ArgumentCaptor<BusiMatchLog> cap = ArgumentCaptor.forClass(BusiMatchLog.class);
        verify(matchLogRepo).save(cap.capture());
        assertThat(cap.getValue().getMatchTypeCd()).isEqualTo("M");
        assertThat(cap.getValue().getLockedYn()).isEqualTo("Y");
        assertThat(cap.getValue().getPayTrnsId()).isEqualTo(PAY_TRNS_ID);
        verify(txnRepo).markBankRowMatchedOverseas(eq(BIZ), eq(SEQ), eq(OVRS_NO), eq(DETL_NO),
                eq(USER), anyString());
        verify(ovrsDetlRepo).markOverseasDetailMatched(eq(BIZ), eq(OVRS_NO), eq(DETL_NO), eq(SEQ),
                eq(SHORT_ID), eq(FULL_ID), eq(USER), anyString());
    }

    @Test
    void undo_overseasMatch_clearsOverseasDetail_whenDomesticClearIsZero() {
        AcctTrnsHstr bank = bankRow("Y");
        when(txnRepo.findById(new AcctTrnsHstrId(BIZ, SEQ))).thenReturn(Optional.of(bank));
        BusiMatchLog log = new BusiMatchLog();
        log.setMatchLogSeq("500");
        log.setPayTrnsId(PAY_TRNS_ID);
        log.setLockedYn("Y");
        when(matchLogRepo.findFirstByBizKeyAndBankTrnsShortIdAndLockedYn(BIZ, SHORT_ID, "Y"))
                .thenReturn(Optional.of(log));
        when(detlRepo.clearDetailMatch(eq(BIZ), eq(OVRS_NO), eq(DETL_NO), eq(USER), anyString()))
                .thenReturn(0);   // no domestic row → overseas branch runs
        when(txnRepo.clearBankRowMatch(eq(BIZ), eq(SEQ), eq(USER), anyString())).thenReturn(1);

        MatchResultResponse res = service.undo(SEQ, SEQ);

        assertThat(res.linkMatchYn()).isEqualTo("N");
        verify(ovrsDetlRepo).clearOverseasDetailMatch(eq(BIZ), eq(OVRS_NO), eq(DETL_NO), eq(USER), anyString());
    }

    /* --------------------------------------------------------- fixtures -- */

    private static AcctTrnsHstr bankRow(String linkMatchYn) {
        AcctTrnsHstr h = new AcctTrnsHstr();
        h.setBizKey(BIZ);
        h.setAcctSeqNo(SEQ);
        h.setBankCd("970424");
        h.setAcctNo("9988776655");
        h.setBankTrnsShortId(SHORT_ID);
        h.setBankTrnsFullId(FULL_ID);
        h.setLinkMatchYn(linkMatchYn);
        return h;
    }

    private static OvrsPymnSumrDetl ovrsLine(String linkMatchYn) {
        OvrsPymnSumrDetl d = new OvrsPymnSumrDetl();
        d.setBizKey(BIZ);
        d.setOvrsPymnNo(OVRS_NO);
        d.setOvrsPymnDetlNo(DETL_NO);
        d.setLinkMatchYn(linkMatchYn);
        return d;
    }
}
