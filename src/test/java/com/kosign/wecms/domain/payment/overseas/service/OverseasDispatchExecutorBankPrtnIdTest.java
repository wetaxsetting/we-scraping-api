package com.kosign.wecms.domain.payment.overseas.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchAutoReceiveRegistrar;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchKeyGenerator;
import com.kosign.wecms.domain.payment.entity.OvrsPymnMstr;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import com.kosign.wecms.domain.setting.firmbankingsettings.repository.FirmBankingSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Overseas file-path {@code BANK_PRTN_ID} parity with legacy
 * {@code pymn_0007_001_c001_act.jsp:182/200} — the header partner id resolves
 * from {@code TB_FIRM_CONF}; a missing config row (legacy NPE-rollback) or a
 * blank {@code BANK_PRTN_ID} blocks dispatch with {@code WBE2089}, writing no
 * {@code TB_PAY_FILE_MST} / {@code TB_PAY_FILE_DATA} rows. Mirrors Vendor /
 * Expense / Internal Transfer / Payroll.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OverseasDispatchExecutorBankPrtnIdTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String FILE_SEQ_NO = "0000001";
    private static final String SHINHAN = "970424";

    @Mock TbPayFileMstRepository tbPayFileMstRepository;
    @Mock TbPayFileDataRepository tbPayFileDataRepository;
    @Mock DispatchKeyGenerator keyGenerator;
    @Mock LegacyFunctionRepository legacyFunctions;
    @Mock FirmBankingSettingRepository firmBankingSettingRepository;
    @Mock AcctMstrRepository acctMstrRepository;
    @Mock DispatchAutoReceiveRegistrar autoReceiveRegistrar;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-12T03:00:00Z"), ZoneOffset.UTC);

    private OverseasDispatchExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new OverseasDispatchExecutor(
                tbPayFileMstRepository, tbPayFileDataRepository, keyGenerator, clock,
                legacyFunctions, firmBankingSettingRepository, acctMstrRepository, autoReceiveRegistrar);
        when(tbPayFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tbPayFileDataRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(keyGenerator.nextFileSeqNo(anyString(), anyString())).thenReturn(FILE_SEQ_NO);
        when(legacyFunctions.nextTbPayFileDataSeq(anyString(), anyString())).thenReturn("1");
    }

    private OvrsPymnSumr master() {
        OvrsPymnSumr m = new OvrsPymnSumr();
        m.setBizKey(BIZ_KEY);
        m.setOvrsPymnNo("OV-TEST-1");
        m.setOutBankCd(SHINHAN);
        m.setWdrlActNo("700005285299");
        m.setWdrlCcyCd("USD");
        return m;
    }

    private OvrsPymnSumrDetl line() {
        OvrsPymnSumrDetl d = new OvrsPymnSumrDetl();
        d.setBicCd("SHBKKRSEXXX");
        d.setBnfcAcctNo("0681000001");
        d.setBnfcNm("BENEFICIARY");
        d.setTrnfAmt(new BigDecimal("10.00"));
        d.setCcyCd("USD");
        d.setMemo("memo");
        return d;
    }

    @Test
    void bankPrtnId_readFromTbFirmConf_whenPresent() {
        TbFirmConf conf = org.mockito.Mockito.mock(TbFirmConf.class);
        when(conf.getBankPrtnId()).thenReturn("PRTN1");
        when(firmBankingSettingRepository.findById(any())).thenReturn(Optional.of(conf));

        executor.execute(master(), List.of(line()));

        ArgumentCaptor<TbPayFileMst> c = ArgumentCaptor.forClass(TbPayFileMst.class);
        verify(tbPayFileMstRepository).save(c.capture());
        assertThat(c.getValue().getBankPrtnId()).isEqualTo("PRTN1");
    }

    @Test
    void fundTp_isOverseasRemittanceShinhan_S() {
        // Legacy pymn_0007_001_c001_act.jsp:192/318 writes FundTp.OVERSEAS_REMITTANCE_SHINHAN ('S');
        // live JEX DEV TB_PAY_FILE_MST carries 'S' on overseas rows.
        TbFirmConf conf = org.mockito.Mockito.mock(TbFirmConf.class);
        when(conf.getBankPrtnId()).thenReturn("PRTN1");
        when(firmBankingSettingRepository.findById(any())).thenReturn(Optional.of(conf));

        executor.execute(master(), List.of(line()));

        ArgumentCaptor<TbPayFileMst> c = ArgumentCaptor.forClass(TbPayFileMst.class);
        verify(tbPayFileMstRepository).save(c.capture());
        assertThat(c.getValue().getFundTp()).isEqualTo("S");
    }

    /* ── Audit M-11 / E1 — RCPT_USE_ADDR_SI (beneficiary town name) mapping ── */

    private void stubConf() {
        TbFirmConf conf = org.mockito.Mockito.mock(TbFirmConf.class);
        when(conf.getBankPrtnId()).thenReturn("PRTN1");
        when(firmBankingSettingRepository.findById(any())).thenReturn(Optional.of(conf));
    }

    @Test
    void rcptUseAddrSi_populatedFromBnfcTownNm_distinctFromAddress() {
        // RCPT_USE_ADDR_SI = beneficiary TOWN name (BNFC_TOWN_NM); RCPT_USE_ADDR = full address
        // (BNFC_ADDR). The two are distinct and must both be written (legacy pymn_0007:374-377).
        stubConf();
        OvrsPymnSumrDetl line = line();
        line.setBnfcAddr("123 Le Loi Street, District 1");
        line.setBnfcTownNm("Ho Chi Minh City");

        executor.execute(master(), List.of(line));

        ArgumentCaptor<TbPayFileData> c = ArgumentCaptor.forClass(TbPayFileData.class);
        verify(tbPayFileDataRepository).save(c.capture());
        assertThat(c.getValue().getRcptUseAddrSi()).isEqualTo("Ho Chi Minh City");
        assertThat(c.getValue().getRcptUseAddr()).isEqualTo("123 Le Loi Street, District 1");
    }

    @Test
    void rcptUseAddrSi_nullTownName_passedThroughAsNull() {
        // Legacy raw passthrough — no default substitution (matches pymn_0007:377).
        stubConf();
        OvrsPymnSumrDetl line = line();
        line.setBnfcTownNm(null);

        executor.execute(master(), List.of(line));

        ArgumentCaptor<TbPayFileData> c = ArgumentCaptor.forClass(TbPayFileData.class);
        verify(tbPayFileDataRepository).save(c.capture());
        assertThat(c.getValue().getRcptUseAddrSi()).isNull();
    }

    @Test
    void rcptUseAddrSi_emptyTownName_passedThroughVerbatim() {
        stubConf();
        OvrsPymnSumrDetl line = line();
        line.setBnfcTownNm("");

        executor.execute(master(), List.of(line));

        ArgumentCaptor<TbPayFileData> c = ArgumentCaptor.forClass(TbPayFileData.class);
        verify(tbPayFileDataRepository).save(c.capture());
        assertThat(c.getValue().getRcptUseAddrSi()).isEmpty();
    }

    @Test
    void rcptUseAddrSi_longTownName_cappedToTargetColumnWidth35() {
        // Item 3 (length parity): BNFC_TOWN_NM is varchar(150); RCPT_USE_ADDR_SI is varchar(35).
        // A town longer than 35 chars must be capped on write so the TB_PAY_FILE_DATA insert can
        // never blow the column (a normal SWIFT place name is well under 35 — this is a safety cap).
        stubConf();
        String town50 = "A123456789B123456789C123456789D123456789E123456789"; // 50 chars
        OvrsPymnSumrDetl line = line();
        line.setBnfcTownNm(town50);

        executor.execute(master(), List.of(line));

        ArgumentCaptor<TbPayFileData> c = ArgumentCaptor.forClass(TbPayFileData.class);
        verify(tbPayFileDataRepository).save(c.capture());
        assertThat(c.getValue().getRcptUseAddrSi()).hasSize(35);
        assertThat(c.getValue().getRcptUseAddrSi()).isEqualTo(town50.substring(0, 35));
    }

    @Test
    void rcptUseAddrSi_exactly35Chars_passedThroughVerbatim() {
        stubConf();
        String town35 = "A123456789B123456789C123456789D1234"; // exactly 35
        OvrsPymnSumrDetl line = line();
        line.setBnfcTownNm(town35);

        executor.execute(master(), List.of(line));

        ArgumentCaptor<TbPayFileData> c = ArgumentCaptor.forClass(TbPayFileData.class);
        verify(tbPayFileDataRepository).save(c.capture());
        assertThat(c.getValue().getRcptUseAddrSi()).isEqualTo(town35);
    }

    @Test
    void ownerNm_populated_rawBnfcNm() {
        // B — legacy raw BNFC_NM (pymn_0007_001_c001_act.jsp:370).
        stubConf();
        executor.execute(master(), List.of(line()));
        ArgumentCaptor<TbPayFileData> c = ArgumentCaptor.forClass(TbPayFileData.class);
        verify(tbPayFileDataRepository).save(c.capture());
        assertThat(c.getValue().getFincMdaOwnerNm()).isEqualTo("BENEFICIARY");
    }

    @Test
    void ownerNm_rawBnfcNm_blankPreserved() {
        // B — legacy raw BNFC_NM, no ACCT_MSTR/acctNo/"UNKNOWN" fallback.
        stubConf();
        OvrsPymnSumrDetl l = line();
        l.setBnfcNm("");
        executor.execute(master(), List.of(l));
        ArgumentCaptor<TbPayFileData> c = ArgumentCaptor.forClass(TbPayFileData.class);
        verify(tbPayFileDataRepository).save(c.capture());
        assertThat(c.getValue().getFincMdaOwnerNm()).isEmpty();
    }

    /* ── L — M-mode dispatch (OVRS_PYMN_TYPE='M', single OVRS_PYMN_MSTR), legacy pymn_0007:146-269 ── */

    private OvrsPymnMstr masterM(String dbtAcctCd) {
        OvrsPymnMstr m = new OvrsPymnMstr();
        m.setBizKey(BIZ_KEY);
        m.setOvrsPymnNo("OV-M-1");
        m.setDbtAcctCd(dbtAcctCd);       // OUT_BANK_CD
        m.setDbtAcctNo("700005285299");  // WDRL_ACT_NO
        m.setDbtCcyCd("USD");            // TRSC_CCY_CD
        m.setDbtAcctHldr("SENDER CO LTD"); // SENDER_NM (M-mode direct)
        m.setBnfcBankCd("SHBKKRSEXXX");  // BIC → TAR_BANK_CD + country
        m.setBnfcAcctNo("0681000001");
        m.setBnfcNm("BENEFICIARY");
        m.setTrnfAmt(new BigDecimal("10.00"));
        m.setTrnfCcyCd("USD");           // REMIT_CCY_CD
        m.setMemo("invoice-1");
        m.setBnfcAddr("1 Teheran-ro, Seoul");
        m.setFeePayerCd("S");            // → SHA
        m.setTotTrnsCnt(1);
        m.setTotalTranAmt(new BigDecimal("10.00"));
        return m;
    }

    @Test
    void mMode_writesOneMstAndOneData_withLegacyFieldMapping() {
        stubConf();
        executor.execute(masterM(SHINHAN));   // 970424 (not VCB)

        ArgumentCaptor<TbPayFileMst> mstCap = ArgumentCaptor.forClass(TbPayFileMst.class);
        verify(tbPayFileMstRepository).save(mstCap.capture());
        TbPayFileMst mst = mstCap.getValue();
        assertThat(mst.getOutBankCd()).isEqualTo(SHINHAN);          // DBT_ACCT_CD
        assertThat(mst.getWdrlActNo()).isEqualTo("700005285299");   // DBT_ACCT_NO
        assertThat(mst.getFundTp()).isEqualTo("S");
        assertThat(mst.getBankPrtnId()).isEqualTo("PRTN1");

        ArgumentCaptor<TbPayFileData> dataCap = ArgumentCaptor.forClass(TbPayFileData.class);
        verify(tbPayFileDataRepository).save(dataCap.capture());
        TbPayFileData d = dataCap.getValue();
        assertThat(d.getAntDivCd()).isEqualTo("S");                  // SWIFT
        assertThat(d.getBankChnlDiv()).isEqualTo("S");
        assertThat(d.getTrscCcyCd()).isEqualTo("USD");              // DBT_CCY_CD (no per-bank branch)
        assertThat(d.getTarBankCd()).isEqualTo("SHBKKRSEXXX");      // BIC
        assertThat(d.getFincMdaNo()).isEqualTo("0681000001");
        assertThat(d.getFincMdaOwnerNm()).isEqualTo("BENEFICIARY"); // raw BNFC_NM (B)
        assertThat(d.getSenderNm()).isEqualTo("SENDER CO LTD");     // DBT_ACCT_HLDR (M-mode)
        assertThat(d.getWdrlActRmrk()).isEqualTo("invoice-1");
        assertThat(d.getTarAcctRmrk()).isEqualTo("invoice-1");
        assertThat(d.getRcptUseAddr()).isEqualTo("1 Teheran-ro, Seoul");
        assertThat(d.getRcptUseAddrSi()).isNull();                  // M-mode does NOT set town name
        assertThat(d.getFeeBrdnClsCd()).isEqualTo("SHA");           // S → SHA
        assertThat(d.getRcptBankCntryCd()).isEqualTo("KR");        // BIC chars 5-6
        assertThat(d.getRemitCcyCd()).isEqualTo("USD");
    }

    @Test
    void mMode_vcbSource_isBlocked() {
        // legacy pymn_0007:160-162 — VCB cannot source an overseas remittance.
        OvrsPymnMstr m = masterM("970436");   // VCB
        assertThatThrownBy(() -> executor.execute(m))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE_OVERSEAS_VCB_NOT_SUPPORTED");
        verify(tbPayFileMstRepository, never()).save(any());
        verify(tbPayFileDataRepository, never()).save(any());
    }

    @Test
    void mMode_missingFirmConf_isBlocked() {
        when(firmBankingSettingRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> executor.execute(masterM(SHINHAN)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE2089");
    }

    @Test
    void dispatch_isBlocked_whenFirmConfAbsent() {
        when(firmBankingSettingRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> executor.execute(master(), List.of(line())))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE2089");

        verify(tbPayFileMstRepository, never()).save(any());
        verify(tbPayFileDataRepository, never()).save(any());
    }

    @Test
    void dispatch_isBlocked_whenBankPrtnIdBlank() {
        TbFirmConf conf = org.mockito.Mockito.mock(TbFirmConf.class);
        when(conf.getBankPrtnId()).thenReturn("   ");
        when(firmBankingSettingRepository.findById(any())).thenReturn(Optional.of(conf));

        assertThatThrownBy(() -> executor.execute(master(), List.of(line())))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE2089");

        verify(tbPayFileMstRepository, never()).save(any());
        verify(tbPayFileDataRepository, never()).save(any());
    }
}
