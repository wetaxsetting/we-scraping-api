package com.kosign.wecms.domain.payment.haengsung.cardaccount;

import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.payment.cardtransactions.repository.CardTrnsHstrRepository;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HaengsungCardAccountSyncServiceTest {

    private static final String BIZ = "VN_VNM0004_000014";
    private static final String TAX_ID = "0312345678";

    @Mock BusiInfmRepository busiInfmRepository;
    @Mock AcctTrnsHstrRepository acctTrnsHstrRepository;
    @Mock CardTrnsHstrRepository cardTrnsHstrRepository;
    @Mock HaengsungCardAccountOracleRepository oracle;
    @Mock ErpTrnsHistoryWriter auditWriter;

    @InjectMocks HaengsungCardAccountSyncService service;

    /* ============================ account section ============================ */

    @Test void account_vndAndFcyCountedSeparately_andFlaggedPerRow() {
        haengsung(busi(BIZ, TAX_ID));
        AcctRow vnd = acct("A1", "VND");
        AcctRow fcy = acct("A2", "USD");
        when(acctTrnsHstrRepository.findHaengsungAcctHistoryForSync(BIZ)).thenReturn(List.of(vnd, fcy));
        when(cardTrnsHstrRepository.findHaengsungCardApprovalForSync(BIZ)).thenReturn(List.of());

        HaengsungCardAccountSyncService.Summary s = service.sync();

        verify(oracle, never()).insertCardApproval(any());
        ArgumentCaptor<OracleAcctHistory> captor = ArgumentCaptor.forClass(OracleAcctHistory.class);
        verify(oracle, org.mockito.Mockito.times(2)).insertAcctHistory(captor.capture());
        assertThat(captor.getAllValues()).extracting(OracleAcctHistory::bizNo).containsOnly(TAX_ID);
        // flagged per row by ACCT_SEQ_NO (= trSeqno)
        verify(acctTrnsHstrRepository).markErpSent(BIZ, "A1");
        verify(acctTrnsHstrRepository).markErpSent(BIZ, "A2");
        assertThat(s.acctVnd()).isEqualTo(1);
        assertThat(s.acctFcy()).isEqualTo(1);
        // audit rows VND='01', FCY='08'
        verify(auditWriter).recordBatchPush(eq(BIZ), eq("01"), anyString());
        verify(auditWriter).recordBatchPush(eq(BIZ), eq("08"), anyString());
    }

    @Test void account_columnMappingIsLegacyVerbatim() {
        haengsung(busi(BIZ, TAX_ID));
        AcctRow r = acct("A1", "VND");
        r.bankCd = "01663001";          // already CITAD-mapped by the read
        r.acctNo = "0681000001";
        r.trDt = "20260628";
        r.trSeqno = "777";
        r.ioGb = "1";
        r.trAmt = new BigDecimal("5000");
        r.trCurBal = new BigDecimal("12000");
        r.lastInqDttm = "20260628090000";
        r.acctMemo = "salary";
        when(acctTrnsHstrRepository.findHaengsungAcctHistoryForSync(BIZ)).thenReturn(List.of(r));
        when(cardTrnsHstrRepository.findHaengsungCardApprovalForSync(BIZ)).thenReturn(List.of());

        service.sync();

        ArgumentCaptor<OracleAcctHistory> c = ArgumentCaptor.forClass(OracleAcctHistory.class);
        verify(oracle).insertAcctHistory(c.capture());
        OracleAcctHistory m = c.getValue();
        assertThat(m.bizNo()).isEqualTo(TAX_ID);
        assertThat(m.bankCd()).isEqualTo("01663001");
        assertThat(m.acctNo()).isEqualTo("0681000001");
        assertThat(m.trDt()).isEqualTo("20260628");
        assertThat(m.trSeqno()).isEqualByComparingTo(new BigDecimal("777"));
        assertThat(m.ccyCd()).isEqualTo("VND");
        assertThat(m.ioGb()).isEqualTo("1");
        assertThat(m.trAmt()).isEqualByComparingTo(new BigDecimal("5000"));
        assertThat(m.trCurBal()).isEqualByComparingTo(new BigDecimal("12000"));
        assertThat(m.acctMemo()).isEqualTo("salary");
        assertThat(m.lastInqDttm()).isEqualTo("20260628090000");
    }

    @Test void account_oneRowFailureContinuesNext_andFailedRowNotFlagged() {
        haengsung(busi(BIZ, TAX_ID));
        AcctRow bad = acct("A1", "VND");
        AcctRow good = acct("A2", "VND");
        when(acctTrnsHstrRepository.findHaengsungAcctHistoryForSync(BIZ)).thenReturn(List.of(bad, good));
        when(cardTrnsHstrRepository.findHaengsungCardApprovalForSync(BIZ)).thenReturn(List.of());
        doThrow(new RuntimeException("ORA-00001")).doReturn(1)
                .when(oracle).insertAcctHistory(any());

        HaengsungCardAccountSyncService.Summary s = service.sync();

        verify(acctTrnsHstrRepository, never()).markErpSent(BIZ, "A1");
        verify(acctTrnsHstrRepository).markErpSent(BIZ, "A2");
        assertThat(s.acctFailed()).isEqualTo(1);
        assertThat(s.acctVnd()).isEqualTo(1);
    }

    /* ============================ card section ============================ */

    @Test void card_mappingOverrides_cardNmIsHolder_purchaseStIsPrchStts_andFlagged() {
        haengsung(busi(BIZ, TAX_ID));
        when(acctTrnsHstrRepository.findHaengsungAcctHistoryForSync(BIZ)).thenReturn(List.of());
        CardRow r = card("C1");
        r.bankCd = "01663001";
        r.cardNo = "1234";
        r.apprDt = "20260628";
        r.apprTm = "101010";
        r.authNo = "AUTH9";
        r.merchNm = "SHOP";
        r.cardHldrNm = "JOHN DOE";
        r.ccyCd = "VND";
        r.apprAmt = new BigDecimal("250");
        r.prchStts = "P";
        r.city = "HCMC";
        r.lastInqDttm = "20260628090000";
        when(cardTrnsHstrRepository.findHaengsungCardApprovalForSync(BIZ)).thenReturn(List.of(r));

        HaengsungCardAccountSyncService.Summary s = service.sync();

        verify(oracle, never()).insertAcctHistory(any());
        ArgumentCaptor<OracleCardApproval> c = ArgumentCaptor.forClass(OracleCardApproval.class);
        verify(oracle).insertCardApproval(c.capture());
        OracleCardApproval m = c.getValue();
        assertThat(m.bizNo()).isEqualTo(TAX_ID);
        assertThat(m.bankCd()).isEqualTo("01663001");
        assertThat(m.cardNm()).isEqualTo("JOHN DOE");   // override: CARD_NM = CARD_HLDR_NM
        assertThat(m.purchaseSt()).isEqualTo("P");      // PURCHASE_ST = PRCH_STTS
        assertThat(m.authNo()).isEqualTo("AUTH9");
        assertThat(m.apprAmt()).isEqualByComparingTo(new BigDecimal("250"));
        // flagged per row by CARD_SEQ_NO
        verify(cardTrnsHstrRepository).markErpSent(BIZ, "C1");
        assertThat(s.cardOk()).isEqualTo(1);
        // audit row CARD='07'
        verify(auditWriter).recordBatchPush(eq(BIZ), eq("07"), anyString());
    }

    @Test void card_oneRowFailureContinuesNext_andFailedRowNotFlagged() {
        haengsung(busi(BIZ, TAX_ID));
        when(acctTrnsHstrRepository.findHaengsungAcctHistoryForSync(BIZ)).thenReturn(List.of());
        CardRow bad = card("C1");
        CardRow good = card("C2");
        when(cardTrnsHstrRepository.findHaengsungCardApprovalForSync(BIZ)).thenReturn(List.of(bad, good));
        doThrow(new RuntimeException("ORA-00001")).doReturn(1)
                .when(oracle).insertCardApproval(any());

        HaengsungCardAccountSyncService.Summary s = service.sync();

        verify(cardTrnsHstrRepository, never()).markErpSent(BIZ, "C1");
        verify(cardTrnsHstrRepository).markErpSent(BIZ, "C2");
        assertThat(s.cardFailed()).isEqualTo(1);
        assertThat(s.cardOk()).isEqualTo(1);
    }

    /* ============================ audit even on 0 / fan-out / guards ============================ */

    @Test void auditRowsWritten_evenWithZeroRecords() {
        haengsung(busi(BIZ, TAX_ID));
        when(acctTrnsHstrRepository.findHaengsungAcctHistoryForSync(BIZ)).thenReturn(List.of());
        when(cardTrnsHstrRepository.findHaengsungCardApprovalForSync(BIZ)).thenReturn(List.of());

        service.sync();

        verify(auditWriter).recordBatchPush(eq(BIZ), eq("01"), anyString());
        verify(auditWriter).recordBatchPush(eq(BIZ), eq("08"), anyString());
        verify(auditWriter).recordBatchPush(eq(BIZ), eq("07"), anyString());
        verifyNoInteractions(oracle);
    }

    @Test void usesHaengsungOnlyFanout_prdtTypeH_delYnN() {
        when(busiInfmRepository.findByPrdtTypeAndDelYn("H", "N")).thenReturn(List.of());

        service.sync();

        verify(busiInfmRepository).findByPrdtTypeAndDelYn("H", "N");
        verifyNoInteractions(oracle);
        verifyNoInteractions(acctTrnsHstrRepository);
        verifyNoInteractions(cardTrnsHstrRepository);
    }

    @Test void businessWithoutTaxId_skipped() {
        haengsung(busi(BIZ, null));

        HaengsungCardAccountSyncService.Summary s = service.sync();

        verifyNoInteractions(oracle);
        verifyNoInteractions(acctTrnsHstrRepository);
        verifyNoInteractions(cardTrnsHstrRepository);
        assertThat(s.failedBusinesses()).isEqualTo(1);
    }

    /* ================================== fixtures ================================== */

    private void haengsung(BusiInfm... rows) {
        when(busiInfmRepository.findByPrdtTypeAndDelYn("H", "N")).thenReturn(List.of(rows));
        lenient().doNothing().when(auditWriter).recordBatchPush(anyString(), anyString(), anyString());
    }

    private static BusiInfm busi(String bizKey, String taxId) {
        BusiInfm b = new BusiInfm();
        b.setBizKey(bizKey);
        b.setPrdtType("H");
        b.setDelYn("N");
        b.setTaxId(taxId);
        return b;
    }

    private static AcctRow acct(String seqNo, String ccyCd) {
        AcctRow r = new AcctRow();
        r.trSeqno = seqNo;
        r.ccyCd = ccyCd;
        r.bankCd = "01663001";
        r.acctNo = "ACC";
        r.trDt = "20260628";
        r.ioGb = "1";
        r.trAmt = new BigDecimal("100");
        r.trCurBal = new BigDecimal("200");
        r.lastInqDttm = "20260628090000";
        r.acctMemo = "memo";
        return r;
    }

    private static CardRow card(String seqNo) {
        CardRow r = new CardRow();
        r.cardSeqno = seqNo;
        r.bankCd = "01663001";
        r.cardNo = "1234";
        r.apprDt = "20260628";
        r.apprTm = "101010";
        r.authNo = "AUTH";
        r.merchNm = "SHOP";
        r.cardHldrNm = "HOLDER";
        r.ccyCd = "VND";
        r.apprAmt = new BigDecimal("250");
        r.prchStts = "P";
        r.city = "HCMC";
        r.lastInqDttm = "20260628090000";
        return r;
    }

    /** Mutable concrete impl of the account-history projection for tests. */
    private static final class AcctRow implements AcctHistorySyncRow {
        String bankCd, acctNo, trDt, trSeqno, ccyCd, ioGb, lastInqDttm, acctMemo;
        BigDecimal trAmt, trCurBal;
        public String getBizKey() { return BIZ; }
        public String getBankCd() { return bankCd; }
        public String getAcctNo() { return acctNo; }
        public String getTrDt() { return trDt; }
        public String getTrSeqno() { return trSeqno; }
        public String getCcyCd() { return ccyCd; }
        public String getIoGb() { return ioGb; }
        public BigDecimal getTrAmt() { return trAmt; }
        public BigDecimal getTrCurBal() { return trCurBal; }
        public String getLastInqDttm() { return lastInqDttm; }
        public String getAcctMemo() { return acctMemo; }
    }

    /** Mutable concrete impl of the card-approval projection for tests. */
    private static final class CardRow implements CardApprovalSyncRow {
        String bankCd, cardNo, apprDt, apprTm, authNo, merchNm, cardHldrNm, ccyCd, city, lastInqDttm, cardSeqno, prchStts;
        BigDecimal apprAmt;
        public String getBizKey() { return BIZ; }
        public String getBankCd() { return bankCd; }
        public String getCardNo() { return cardNo; }
        public String getApprDt() { return apprDt; }
        public String getApprTm() { return apprTm; }
        public String getAuthNo() { return authNo; }
        public String getMerchNm() { return merchNm; }
        public String getCardHldrNm() { return cardHldrNm; }
        public String getCcyCd() { return ccyCd; }
        public BigDecimal getApprAmt() { return apprAmt; }
        public String getCity() { return city; }
        public String getLastInqDttm() { return lastInqDttm; }
        public String getCardSeqno() { return cardSeqno; }
        public String getPrchStts() { return prchStts; }
    }
}
