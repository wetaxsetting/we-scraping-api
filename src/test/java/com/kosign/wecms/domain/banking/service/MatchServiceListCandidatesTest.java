package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.MatchCandidateListResponse;
import com.kosign.wecms.domain.banking.dto.MatchCandidateRow;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstrId;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * P4 tests for the banking→payment Smart Organizer candidate list
 * ({@link MatchService#listCandidates}) — legacy {@code comm_0196_001_r001} /
 * {@code OVRS_PYMN_SUMR_DETL_R006} parity. Verifies the three UNION branches
 * surface (domestic detail / payroll master / overseas detail) and that the
 * Java-side filters — dispatch success (#12) and locked-match exclusion (#11) —
 * gate rows out. The DB-side predicates (exact amount/date, TRNF_YN, header
 * unmatched) live in the native queries and are mocked here.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchServiceListCandidatesTest {

    private static final String BIZ = "VN_VNM0004_000001";
    private static final String USER = "u1";
    private static final String SEQ = "SEQ1";
    private static final String TRSC_DT = "20260708";
    private static final BigDecimal TRSC_AMT = new BigDecimal("100.000000");

    @Mock AcctTrnsHstrRepository txnRepo;
    @Mock BusiMatchLogRepository matchLogRepo;
    @Mock ScdulPymnSumrRepository sumrRepo;
    @Mock ScdulPymnSumrDetlRepository detlRepo;
    @Mock OvrsPymnDtlsRepository ovrsDetlRepo;
    @Mock OvrsPymnSumrRepository ovrsSumrRepo;
    @Mock DispatchResultResolver dispatchResultResolver;
    @Mock AccountAccessGuard accountAccessGuard;
    @Mock LegacyFunctionRepository legacyFunctions;

    @InjectMocks MatchService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(USER, BIZ, LangCd.EN);
        // Not locked by default.
        when(matchLogRepo.findFirstByBizKeyAndPayTrnsIdAndLockedYn(eq(BIZ), any(), eq("Y")))
                .thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void surfacesDomesticPayrollAndOverseas_whenFiltersPass() {
        stubBank("N", TRSC_DT, TRSC_AMT, null /* no bank time → time floor skipped */);

        // Domestic detail (API route: success = proc '0').
        ScdulPymnSumr dParent = scdulParent("D1", "01", null);
        ScdulPymnSumrDetl dLine = scdulLine("D1", "5", "77");
        when(detlRepo.findDomesticBankMatchCandidates(BIZ, TRSC_DT, TRSC_AMT))
                .thenReturn(List.of(dLine));
        when(sumrRepo.findAllById(any())).thenReturn(List.of(dParent));
        when(dispatchResultResolver.resolve(eq(dParent), anyList()))
                .thenReturn(Map.of("5", success()));

        // Payroll master (no file master → dispatch OK).
        ScdulPymnSumr pMaster = scdulParent("P1", "00", null);
        when(sumrRepo.findPayrollBankMatchCandidates(BIZ, TRSC_DT, TRSC_AMT))
                .thenReturn(List.of(pMaster));

        // Overseas detail (file route: success = proc '0' + rs '0000').
        OvrsPymnSumr oParent = ovrsParent("O1");
        OvrsPymnSumrDetl oLine = ovrsLine("O1", "3");
        when(ovrsDetlRepo.findOverseasBankMatchCandidates(BIZ, TRSC_DT, TRSC_AMT))
                .thenReturn(List.of(oLine));
        when(ovrsSumrRepo.findById(any())).thenReturn(Optional.of(oParent));
        when(dispatchResultResolver.resolveOverseas(eq(oParent), anyList()))
                .thenReturn(Map.of("3", success()));

        MatchCandidateListResponse res = service.listCandidates(SEQ, SEQ, null, null, null, "N");

        assertThat(res.totalCount()).isEqualTo(3);
        assertThat(res.rows()).extracting(MatchCandidateRow::payTrnsId)
                .containsExactlyInAnyOrder("D1_5", "P1", "O1_3");
        assertThat(res.rows()).filteredOn(r -> r.payTrnsId().equals("O1_3"))
                .extracting(MatchCandidateRow::payTypeCd).containsExactly("O");
        assertThat(res.rows()).filteredOn(r -> r.payTrnsId().equals("P1"))
                .extracting(MatchCandidateRow::scdulPymnTypeCd).containsExactly("00");
    }

    @Test
    void emptyWhenBankRowAlreadyMatched() {
        stubBank("Y", TRSC_DT, TRSC_AMT, null);
        MatchCandidateListResponse res = service.listCandidates(SEQ, SEQ, null, null, null, "N");
        assertThat(res.totalCount()).isZero();
        assertThat(res.rows()).isEmpty();
    }

    @Test
    void emptyWhenBankDateOutsideRequestedRange() {
        stubBank("N", TRSC_DT, TRSC_AMT, null);
        // Bank date 20260708 is outside [20260701, 20260705].
        MatchCandidateListResponse res =
                service.listCandidates(SEQ, SEQ, "20260701", "20260705", null, "N");
        assertThat(res.totalCount()).isZero();
    }

    @Test
    void excludesLockedAndFailedDomesticCandidates() {
        stubBank("N", TRSC_DT, TRSC_AMT, null);

        ScdulPymnSumr parent = scdulParent("D1", "01", null);
        ScdulPymnSumrDetl locked = scdulLine("D1", "1", "77");   // will be locked
        ScdulPymnSumrDetl failed = scdulLine("D1", "2", "78");   // dispatch failed
        when(detlRepo.findDomesticBankMatchCandidates(BIZ, TRSC_DT, TRSC_AMT))
                .thenReturn(List.of(locked, failed));
        when(sumrRepo.findAllById(any())).thenReturn(List.of(parent));
        when(dispatchResultResolver.resolve(eq(parent), anyList()))
                .thenReturn(Map.of("1", success(), "2", failed()));
        // "D1_1" is locked to another bank row.
        when(matchLogRepo.findFirstByBizKeyAndPayTrnsIdAndLockedYn(BIZ, "D1_1", "Y"))
                .thenReturn(Optional.of(new com.kosign.wecms.domain.banking.entity.BusiMatchLog()));

        MatchCandidateListResponse res = service.listCandidates(SEQ, SEQ, null, null, null, "N");

        // "D1_1" locked, "D1_2" failed → both excluded.
        assertThat(res.totalCount()).isZero();
    }

    /* --------------------------------------------------------- fixtures -- */

    private void stubBank(String linkMatchYn, String trscDt, BigDecimal trscAmt, String trscTm) {
        AcctTrnsHstr h = new AcctTrnsHstr();
        h.setBizKey(BIZ);
        h.setAcctSeqNo(SEQ);
        h.setBankCd("970418");
        h.setAcctNo("100001018048");
        h.setLinkMatchYn(linkMatchYn);
        h.setTrscDt(trscDt);
        h.setTrscAmt(trscAmt);
        h.setTrscTm(trscTm);
        when(txnRepo.findById(new AcctTrnsHstrId(BIZ, SEQ))).thenReturn(Optional.of(h));
    }

    private static ScdulPymnSumr scdulParent(String no, String type, String payFileSeqNo) {
        ScdulPymnSumr s = new ScdulPymnSumr();
        s.setBizKey(BIZ);
        s.setScdulPymnNo(no);
        s.setScdulPymnTypeCd(type);
        s.setCcyCd("VND");
        s.setTitl("Batch " + no);
        s.setTotalTranAmt(TRSC_AMT);
        s.setWdrlActNo("100001018048");
        s.setTrnfYn("Y");
        s.setTrnfDtm("20260708010000");   // 01:00 UTC (len ≥ 8)
        s.setPayFileSeqNo(payFileSeqNo);
        return s;
    }

    private static ScdulPymnSumrDetl scdulLine(String no, String detlNo, String amtSuffix) {
        ScdulPymnSumrDetl d = new ScdulPymnSumrDetl();
        d.setBizKey(BIZ);
        d.setScdulPymnNo(no);
        d.setScdulPymnDetlNo(detlNo);
        d.setTranAmt(TRSC_AMT);
        d.setTrnfCcyCd("VND");
        d.setRcptNm("Recipient " + amtSuffix);
        d.setLinkMatchYn("N");
        return d;
    }

    private static OvrsPymnSumr ovrsParent(String no) {
        OvrsPymnSumr s = new OvrsPymnSumr();
        s.setBizKey(BIZ);
        s.setOvrsPymnNo(no);
        s.setOvrsPymnTypeCd("01");
        s.setTrnfYn("Y");
        s.setTrnfDt("20260708010000");
        s.setPayFileSeqNo("1");            // overseas is file-route
        s.setPayTranDt(TRSC_DT);
        return s;
    }

    private static OvrsPymnSumrDetl ovrsLine(String no, String detlNo) {
        OvrsPymnSumrDetl d = new OvrsPymnSumrDetl();
        d.setBizKey(BIZ);
        d.setOvrsPymnNo(no);
        d.setOvrsPymnDetlNo(detlNo);
        d.setTrnfAmt(TRSC_AMT);
        d.setCcyCd("USD");
        d.setBnfcNm("Overseas Beneficiary");
        d.setBnfcBankCd("BNFCBANK");
        d.setBnfcAcctNo("123456");
        d.setMemo("wire memo");
        d.setLinkMatchYn("N");
        return d;
    }

    private static DispatchResult success() {
        return new DispatchResult("0", null, null, null, "0000", null);
    }

    private static DispatchResult failed() {
        return new DispatchResult("3", null, null, null, "9999", null);
    }
}
