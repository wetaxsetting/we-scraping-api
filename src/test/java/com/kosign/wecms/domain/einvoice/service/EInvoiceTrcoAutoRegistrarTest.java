package com.kosign.wecms.domain.einvoice.service;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.einvoice.dto.ScrapedEInvoiceDetailRow;
import com.kosign.wecms.domain.einvoice.entity.TrcoActvHstr;
import com.kosign.wecms.domain.einvoice.entity.TrcoRspbInfm;
import com.kosign.wecms.domain.einvoice.repository.TrcoActvHstrRepository;
import com.kosign.wecms.domain.einvoice.repository.TrcoRspbInfmRepository;
import com.kosign.wecms.domain.setting.contacts.entity.PgroBsacLnknInfm;
import com.kosign.wecms.domain.setting.contacts.entity.PgroBsacLnknInfmId;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoAcctInfo;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoInfm;
import com.kosign.wecms.domain.setting.contacts.repository.PgroBsacLnknInfmRepository;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoAcctInfoRepository;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoInfmRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
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
 * Coverage for the e-Invoice counterparty auto-registrar (legacy
 * {@code nxisas_einvoice_c002_act.jsp:333-535,883-909} port): new-contact
 * create + group-link + bank-account + activity-history (SALES → buyer,
 * {@code 'S'}/{@code 'S003'}), reuse of an existing contact (EXPENSE →
 * seller, {@code 'B'}/{@code 'B003'}), the no-tax-id ETC path, and the
 * activity-history dedup guard.
 */
@ExtendWith(MockitoExtension.class)
class EInvoiceTrcoAutoRegistrarTest {

    private static final String BIZ = "VN_VNM0004_000001";
    private static final String USER = "tester";
    private static final String DTM = "20260603120000";

    @Mock TrcoInfmRepository trcoInfmRepo;
    @Mock TrcoAcctInfoRepository trcoAcctRepo;
    @Mock PgroBsacLnknInfmRepository pgroRepo;
    @Mock TrcoActvHstrRepository actvHstrRepo;
    @Mock TrcoRspbInfmRepository rspbRepo;
    @Mock LegacyFunctionRepository legacyFn;

    @InjectMocks EInvoiceTrcoAutoRegistrar registrar;

    /** Build a detail row populating only the counterparty fields used by
     *  the registrar; everything else (incl. the D3 registration-enrichment
     *  fields) is null. */
    private static ScrapedEInvoiceDetailRow row(
            String buyerName, String buyerTaxCode, String buyerBankAcc, String buyerBankName,
            String sellerName, String sellerTaxCode, String sellerBankAcc, String sellerBankName,
            String currency, String totalAmt) {
        return rowReg(buyerName, buyerTaxCode, buyerBankAcc, buyerBankName,
                sellerName, sellerTaxCode, sellerBankAcc, sellerBankName,
                currency, totalAmt, null, null, null);
    }

    /** Full builder including the D3 business-registration enrichment fields
     *  ({@code sellerRpprEnm} = chairman, {@code sellerPicName} = accountant,
     *  {@code trcoName} = company name). */
    private static ScrapedEInvoiceDetailRow rowReg(
            String buyerName, String buyerTaxCode, String buyerBankAcc, String buyerBankName,
            String sellerName, String sellerTaxCode, String sellerBankAcc, String sellerBankName,
            String currency, String totalAmt,
            String sellerRpprEnm, String sellerPicName, String trcoName) {
        return new ScrapedEInvoiceDetailRow(
                "1", "C25TAA", "100", "0100000001", "0",       // identity
                currency, "title", sellerName, totalAmt, null, null, null,
                buyerName, buyerTaxCode, "Hanoi, Việt Nam", buyerBankAcc, buyerBankName, null,
                "HCMC, Việt Nam", sellerBankAcc, sellerBankName, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null,
                sellerRpprEnm, sellerPicName, trcoName,        // D3 enrichment
                List.of(), List.of(), List.of());
    }

    @Test
    void sales_newBuyerWithTaxId_createsContactGroupAccountAndActivity() {
        when(trcoInfmRepo.findFirstByBizKeyAndTrcoTaxIdAndTrscCcyCd(BIZ, "0312345678", "VND"))
                .thenReturn(Optional.empty());
        when(legacyFn.nextTrcoKey(BIZ)).thenReturn("1000001");
        when(trcoInfmRepo.existsByBizKeyAndTrcoNickNm(anyString(), anyString())).thenReturn(false);
        when(trcoInfmRepo.saveAndFlush(any(TrcoInfm.class))).thenAnswer(i -> i.getArgument(0));
        when(pgroRepo.existsById(any(PgroBsacLnknInfmId.class))).thenReturn(false);
        when(trcoAcctRepo.findByBizKeyAndTrcoKeyAndAcctNo(eq(BIZ), eq("1000001"), anyString()))
                .thenReturn(List.of());
        when(legacyFn.nextTrcoAcctKey(BIZ, "1000001")).thenReturn("1");
        when(trcoAcctRepo.saveAndFlush(any(TrcoAcctInfo.class))).thenAnswer(i -> i.getArgument(0));
        when(actvHstrRepo.existsByBizKeyAndDvCdAndTaskCdAndApiApvKey(BIZ, "S", "S003", "1C25TAA-100"))
                .thenReturn(false);
        when(legacyFn.nextTrcoActvSeq()).thenReturn("1");
        when(actvHstrRepo.saveAndFlush(any(TrcoActvHstr.class))).thenAnswer(i -> i.getArgument(0));

        var cp = registrar.registerCounterparty(
                /*isSales*/ true, BIZ, USER, DTM, "1C25TAA-100",
                row("Buyer Co", "0312345678", "12345678", "Vietcombank",
                        "Seller Co", "0100000001", null, null, "VND", "1,500,000"),
                new BigDecimal("1500000"));

        assertThat(cp.trcoKey()).isEqualTo("1000001");

        ArgumentCaptor<TrcoInfm> contact = ArgumentCaptor.forClass(TrcoInfm.class);
        verify(trcoInfmRepo).saveAndFlush(contact.capture());
        assertThat(contact.getValue().getTrcoCmnm()).isEqualTo("Buyer Co");
        assertThat(contact.getValue().getTrcoTaxId()).isEqualTo("0312345678");
        assertThat(contact.getValue().getTrscCcyCd()).isEqualTo("VND");
        assertThat(contact.getValue().getTrcoInqYn()).isEqualTo("N");

        ArgumentCaptor<PgroBsacLnknInfm> link = ArgumentCaptor.forClass(PgroBsacLnknInfm.class);
        verify(pgroRepo).save(link.capture());
        assertThat(link.getValue().getGrpCd()).isEqualTo("S");
        assertThat(link.getValue().getUserId()).isEqualTo("ALL");

        ArgumentCaptor<TrcoAcctInfo> acct = ArgumentCaptor.forClass(TrcoAcctInfo.class);
        verify(trcoAcctRepo).saveAndFlush(acct.capture());
        assertThat(acct.getValue().getAcctNo()).isEqualTo("12345678");
        assertThat(acct.getValue().getBankNm()).isEqualTo("Vietcombank");

        ArgumentCaptor<TrcoActvHstr> hstr = ArgumentCaptor.forClass(TrcoActvHstr.class);
        verify(actvHstrRepo).saveAndFlush(hstr.capture());
        assertThat(hstr.getValue().getDvCd()).isEqualTo("S");
        assertThat(hstr.getValue().getTaskCd()).isEqualTo("S003");
        assertThat(hstr.getValue().getApiApvKey()).isEqualTo("1C25TAA-100");
        assertThat(hstr.getValue().getActvAmt()).isEqualByComparingTo("1500000");
    }

    @Test
    void expense_existingSellerContact_reusedNotRecreated() {
        TrcoInfm existing = new TrcoInfm();
        existing.setTrcoKey("9999");
        existing.setBizKey(BIZ);
        existing.setTaxId("0100000001");
        when(trcoInfmRepo.findFirstByBizKeyAndTrcoTaxIdAndTrscCcyCd(BIZ, "0100000001", "VND"))
                .thenReturn(Optional.of(existing));
        when(pgroRepo.existsById(any(PgroBsacLnknInfmId.class))).thenReturn(true); // already linked
        when(actvHstrRepo.existsByBizKeyAndDvCdAndTaskCdAndApiApvKey(BIZ, "B", "B003", "1C25TAA-100"))
                .thenReturn(false);
        when(legacyFn.nextTrcoActvSeq()).thenReturn("7");
        when(actvHstrRepo.saveAndFlush(any(TrcoActvHstr.class))).thenAnswer(i -> i.getArgument(0));

        var cp = registrar.registerCounterparty(
                /*isSales*/ false, BIZ, USER, DTM, "1C25TAA-100",
                row("Buyer Co", "0312345678", null, null,
                        "Seller Co", "0100000001", null, null, "VND", "900,000"),
                new BigDecimal("900000"));

        assertThat(cp.trcoKey()).isEqualTo("9999");
        verify(legacyFn, never()).nextTrcoKey(anyString());
        verify(trcoInfmRepo, never()).saveAndFlush(any());
        verify(pgroRepo, never()).save(any());

        ArgumentCaptor<TrcoActvHstr> hstr = ArgumentCaptor.forClass(TrcoActvHstr.class);
        verify(actvHstrRepo).saveAndFlush(hstr.capture());
        assertThat(hstr.getValue().getDvCd()).isEqualTo("B");
        assertThat(hstr.getValue().getTaskCd()).isEqualTo("B003");
        assertThat(hstr.getValue().getTrcoKey()).isEqualTo("9999");
    }

    @Test
    void noTaxId_resolvesToEtcWithoutCreatingContact() {
        lenient().when(legacyFn.nextTrcoActvSeq()).thenReturn("1");
        when(actvHstrRepo.existsByBizKeyAndDvCdAndTaskCdAndApiApvKey(BIZ, "S", "S003", "1C25TAA-100"))
                .thenReturn(false);
        when(actvHstrRepo.saveAndFlush(any(TrcoActvHstr.class))).thenAnswer(i -> i.getArgument(0));

        var cp = registrar.registerCounterparty(
                true, BIZ, USER, DTM, "1C25TAA-100",
                row("", "", null, null, "Seller Co", "0100000001", null, null, "VND", "100,000"),
                new BigDecimal("100000"));

        assertThat(cp.trcoKey()).isEqualTo(EInvoiceTrcoAutoRegistrar.ETC_TRCO_KEY);
        verify(trcoInfmRepo, never()).findFirstByBizKeyAndTrcoTaxIdAndTrscCcyCd(anyString(), anyString(), anyString());
        verify(trcoInfmRepo, never()).saveAndFlush(any());
        verify(pgroRepo, never()).save(any());
        verify(trcoAcctRepo, never()).saveAndFlush(any());
        // Activity row is still written for parity, carrying the ETC key.
        ArgumentCaptor<TrcoActvHstr> hstr = ArgumentCaptor.forClass(TrcoActvHstr.class);
        verify(actvHstrRepo).saveAndFlush(hstr.capture());
        assertThat(hstr.getValue().getTrcoKey()).isEqualTo(EInvoiceTrcoAutoRegistrar.ETC_TRCO_KEY);
    }

    @Test
    void activityHistory_dedupedWhenAlreadyPresent() {
        TrcoInfm existing = new TrcoInfm();
        existing.setTrcoKey("9999");
        existing.setBizKey(BIZ);
        existing.setTaxId("0100000001");
        when(trcoInfmRepo.findFirstByBizKeyAndTrcoTaxIdAndTrscCcyCd(BIZ, "0100000001", "VND"))
                .thenReturn(Optional.of(existing));
        when(pgroRepo.existsById(any(PgroBsacLnknInfmId.class))).thenReturn(true);
        when(actvHstrRepo.existsByBizKeyAndDvCdAndTaskCdAndApiApvKey(BIZ, "B", "B003", "1C25TAA-100"))
                .thenReturn(true); // already exists → no insert

        registrar.registerCounterparty(
                false, BIZ, USER, DTM, "1C25TAA-100",
                row("Buyer Co", "0312345678", null, null,
                        "Seller Co", "0100000001", null, null, "VND", "900,000"),
                new BigDecimal("900000"));

        verify(actvHstrRepo, never()).saveAndFlush(any());
        verify(legacyFn, never()).nextTrcoActvSeq();
    }

    /* ── D5.1 — business-address parsing (legacy c002:361-411) ─────────── */

    @Test
    void parseBusinessAddress_blank_returnsAllNull() {
        assertThat(EInvoiceTrcoAutoRegistrar.parseBusinessAddress("   "))
                .containsExactly(null, null, null, null);
        assertThat(EInvoiceTrcoAutoRegistrar.parseBusinessAddress(null))
                .containsExactly(null, null, null, null);
    }

    @Test
    void parseBusinessAddress_fewerThanFiveParts_wholeStringIsPostalLine() {
        String[] a = EInvoiceTrcoAutoRegistrar.parseBusinessAddress("Số 1 Trần Phú, Hà Nội");
        assertThat(a[0]).isEqualTo("Việt Nam");       // st
        assertThat(a[1]).isNull();                    // city (no split under 5 parts)
        assertThat(a[2]).isEqualTo("Số 1 Trần Phú, Hà Nội"); // postAdrs
        assertThat(a[3]).isNull();                    // dtlAdrs
    }

    @Test
    void parseBusinessAddress_fivePlusPartsWithVietNamTrailer_splitsCityFromStreet() {
        // 6 parts, last = "Việt Nam" → city = parts[n-3] + ", " + parts[n-2],
        // postal = parts[0..n-4]; the "Việt Nam" trailer is dropped.
        String[] a = EInvoiceTrcoAutoRegistrar.parseBusinessAddress(
                "Số 10, Ngõ 5, Phường A, Quận B, Hà Nội, Việt Nam");
        assertThat(a[0]).isEqualTo("Việt Nam");
        assertThat(a[1]).isEqualTo("Quận B, Hà Nội");
        assertThat(a[2]).isEqualTo("Số 10, Ngõ 5, Phường A");
        assertThat(a[3]).isNull();
    }

    @Test
    void parseBusinessAddress_fivePlusPartsWithoutTrailer_usesLastTwoAsCity() {
        String[] a = EInvoiceTrcoAutoRegistrar.parseBusinessAddress(
                "Số 10, Ngõ 5, Phường A, Quận B, Hà Nội");
        assertThat(a[0]).isEqualTo("Việt Nam");
        assertThat(a[1]).isEqualTo("Quận B, Hà Nội");
        assertThat(a[2]).isEqualTo("Số 10, Ngõ 5, Phường A");
        assertThat(a[3]).isNull();
    }

    @Test
    void sales_newContact_wiresParsedBusinessAddressOntoContact() {
        when(trcoInfmRepo.findFirstByBizKeyAndTrcoTaxIdAndTrscCcyCd(BIZ, "0312345678", "VND"))
                .thenReturn(Optional.empty());
        when(legacyFn.nextTrcoKey(BIZ)).thenReturn("1000001");
        when(trcoInfmRepo.existsByBizKeyAndTrcoNickNm(anyString(), anyString())).thenReturn(false);
        when(trcoInfmRepo.saveAndFlush(any(TrcoInfm.class))).thenAnswer(i -> i.getArgument(0));
        when(pgroRepo.existsById(any(PgroBsacLnknInfmId.class))).thenReturn(true);
        lenient().when(actvHstrRepo.existsByBizKeyAndDvCdAndTaskCdAndApiApvKey(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(false);
        when(legacyFn.nextTrcoActvSeq()).thenReturn("1");
        when(actvHstrRepo.saveAndFlush(any(TrcoActvHstr.class))).thenAnswer(i -> i.getArgument(0));

        registrar.registerCounterparty(
                true, BIZ, USER, DTM, "1C25TAA-100",
                // buyerAddr (SALES counterparty) is "Hanoi, Việt Nam" (2 parts).
                row("Buyer Co", "0312345678", null, null,
                        "Seller Co", "0100000001", null, null, "VND", "1,500,000"),
                new BigDecimal("1500000"));

        ArgumentCaptor<TrcoInfm> contact = ArgumentCaptor.forClass(TrcoInfm.class);
        verify(trcoInfmRepo).saveAndFlush(contact.capture());
        assertThat(contact.getValue().getTrcoBsunSt()).isEqualTo("Việt Nam");
        assertThat(contact.getValue().getTrcoBsunPostAdrs()).isEqualTo("Hanoi, Việt Nam");
        assertThat(contact.getValue().getTrcoBsunDtlAdrs()).isNull();
    }

    /* ── D5.2a — nickname derivation (legacy c002:428-435 + #38284) ────── */

    @Test
    void baseNickName_plainName_whenTaxIdHasNoBranchSuffix() {
        assertThat(EInvoiceTrcoAutoRegistrar.baseNickName("Vietnam Computer", "0312345678"))
                .isEqualTo("Vietnam Computer");
    }

    @Test
    void baseNickName_appendsThreeDigitBranchSuffix_whenTaxIdEndsWithDashNNN() {
        // "-001" trailer → nickname "<name> 001" (legacy c002:429-432).
        assertThat(EInvoiceTrcoAutoRegistrar.baseNickName("Vietnam Computer", "0123456789-001"))
                .isEqualTo("Vietnam Computer 001");
    }

    @Test
    void baseNickName_plainName_whenTrailerIsNotDashNNN() {
        // "-01" (only 2 digits) and "1-001"-style non-trailing do not match.
        assertThat(EInvoiceTrcoAutoRegistrar.baseNickName("ACME", "0123456789-01"))
                .isEqualTo("ACME");
        assertThat(EInvoiceTrcoAutoRegistrar.baseNickName("ACME", ""))
                .isEqualTo("ACME");
    }

    /* ── D5.2b — SYS display-name normalisation (legacy getCompanyNickNmBySystem) ── */

    private void stubNewContactCreateFlow() {
        when(legacyFn.nextTrcoKey(BIZ)).thenReturn("1000001");
        when(trcoInfmRepo.existsByBizKeyAndTrcoNickNm(anyString(), anyString())).thenReturn(false);
        when(trcoInfmRepo.saveAndFlush(any(TrcoInfm.class))).thenAnswer(i -> i.getArgument(0));
        when(pgroRepo.existsById(any(PgroBsacLnknInfmId.class))).thenReturn(true);
        lenient().when(actvHstrRepo.existsByBizKeyAndDvCdAndTaskCdAndApiApvKey(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(false);
        when(legacyFn.nextTrcoActvSeq()).thenReturn("1");
        when(actvHstrRepo.saveAndFlush(any(TrcoActvHstr.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void newContact_usesSystemNormalizedNickname_whenFunctionReturnsDistinct() {
        when(trcoInfmRepo.findFirstByBizKeyAndTrcoTaxIdAndTrscCcyCd(BIZ, "0312345678", "VND"))
                .thenReturn(Optional.empty());
        // Legacy getCompanyNickNmBySystem → wb_get_normalized_trco_nick_nm.
        when(legacyFn.getNormalizedTrcoNickNm("CÔNG TY TNHH ABC")).thenReturn("ABC");
        stubNewContactCreateFlow();

        registrar.registerCounterparty(
                true, BIZ, USER, DTM, "1C25TAA-100",
                row("CÔNG TY TNHH ABC", "0312345678", null, null,
                        "Seller Co", "0100000001", null, null, "VND", "100,000"),
                new BigDecimal("100000"));

        ArgumentCaptor<TrcoInfm> contact = ArgumentCaptor.forClass(TrcoInfm.class);
        verify(trcoInfmRepo).saveAndFlush(contact.capture());
        assertThat(contact.getValue().getTrcoNickNm()).isEqualTo("ABC");
    }

    @Test
    void newContact_usdGetsBracketPrefix_onNormalizedNickname() {
        when(trcoInfmRepo.findFirstByBizKeyAndTrcoTaxIdAndTrscCcyCd(BIZ, "0312345678", "USD"))
                .thenReturn(Optional.empty());
        when(legacyFn.getNormalizedTrcoNickNm("CÔNG TY TNHH ABC")).thenReturn("ABC");
        stubNewContactCreateFlow();

        registrar.registerCounterparty(
                true, BIZ, USER, DTM, "1C25TAA-100",
                row("CÔNG TY TNHH ABC", "0312345678", null, null,
                        "Seller Co", "0100000001", null, null, "USD", "100,000"),
                new BigDecimal("100000"));

        ArgumentCaptor<TrcoInfm> contact = ArgumentCaptor.forClass(TrcoInfm.class);
        verify(trcoInfmRepo).saveAndFlush(contact.capture());
        assertThat(contact.getValue().getTrcoNickNm()).isEqualTo("[USD] ABC");
    }

    @Test
    void newContact_fallsBackToNameNickname_whenNormalizerReturnsNull() {
        when(trcoInfmRepo.findFirstByBizKeyAndTrcoTaxIdAndTrscCcyCd(BIZ, "0312345678", "VND"))
                .thenReturn(Optional.empty());
        when(legacyFn.getNormalizedTrcoNickNm(anyString())).thenReturn(null); // no normalisation
        stubNewContactCreateFlow();

        registrar.registerCounterparty(
                true, BIZ, USER, DTM, "1C25TAA-100",
                row("Plain Buyer", "0312345678", null, null,
                        "Seller Co", "0100000001", null, null, "VND", "100,000"),
                new BigDecimal("100000"));

        ArgumentCaptor<TrcoInfm> contact = ArgumentCaptor.forClass(TrcoInfm.class);
        verify(trcoInfmRepo).saveAndFlush(contact.capture());
        assertThat(contact.getValue().getTrcoNickNm()).isEqualTo("Plain Buyer");
    }

    /* ── D5.3 — representative + TRCO_RSPB_INFM (legacy #39847, c002:469-502) ── */

    @Test
    void newContact_withRegistrationData_setsRepresentativeAndCreatesRspb() {
        when(trcoInfmRepo.findFirstByBizKeyAndTrcoTaxIdAndTrscCcyCd(BIZ, "0100000001", "VND"))
                .thenReturn(Optional.empty());
        // Counterparty name is blank here, so companyNickBySystem short-circuits
        // before calling the normaliser — no getNormalizedTrcoNickNm stub needed.
        stubNewContactCreateFlow();
        when(legacyFn.nextRspbKey()).thenReturn("R1");
        when(rspbRepo.saveAndFlush(any(TrcoRspbInfm.class))).thenAnswer(i -> i.getArgument(0));

        // EXPENSE → counterparty = seller; registration enrichment present.
        registrar.registerCounterparty(
                false, BIZ, USER, DTM, "1C25TAA-100",
                rowReg("Buyer Co", "0312345678", null, null,
                        "", "0100000001", null, null, "VND", "500,000",
                        /*chairman*/ "Nguyen Van A", /*accountant*/ "Tran Thi B",
                        /*companyName*/ "Seller Full Co"),
                new BigDecimal("500000"));

        ArgumentCaptor<TrcoInfm> contact = ArgumentCaptor.forClass(TrcoInfm.class);
        verify(trcoInfmRepo).saveAndFlush(contact.capture());
        // Representative both columns = chairman; company name falls back to trcoName.
        assertThat(contact.getValue().getTrcoRpprEnm()).isEqualTo("Nguyen Van A");
        assertThat(contact.getValue().getTrcoRpprNm()).isEqualTo("Nguyen Van A");
        assertThat(contact.getValue().getTrcoCmnm()).isEqualTo("Seller Full Co");

        ArgumentCaptor<TrcoRspbInfm> rspb = ArgumentCaptor.forClass(TrcoRspbInfm.class);
        verify(rspbRepo).saveAndFlush(rspb.capture());
        assertThat(rspb.getValue().getRspbKey()).isEqualTo("R1");
        assertThat(rspb.getValue().getName()).isEqualTo("Tran Thi B");
        assertThat(rspb.getValue().getTrcoKey()).isEqualTo("1000001");
    }

    @Test
    void existingContact_backfillsRepresentative_whenBlank_andCreatesRspbWhenAbsent() {
        TrcoInfm existing = new TrcoInfm();
        existing.setTrcoKey("9999");
        existing.setBizKey(BIZ);
        existing.setTaxId("0100000001");
        // rppr columns blank → eligible for back-fill.
        when(trcoInfmRepo.findFirstByBizKeyAndTrcoTaxIdAndTrscCcyCd(BIZ, "0100000001", "VND"))
                .thenReturn(Optional.of(existing));
        when(trcoInfmRepo.saveAndFlush(any(TrcoInfm.class))).thenAnswer(i -> i.getArgument(0));
        when(pgroRepo.existsById(any(PgroBsacLnknInfmId.class))).thenReturn(true);
        when(rspbRepo.existsByBizKeyAndTrcoKey(BIZ, "9999")).thenReturn(false);
        when(legacyFn.nextRspbKey()).thenReturn("R2");
        when(rspbRepo.saveAndFlush(any(TrcoRspbInfm.class))).thenAnswer(i -> i.getArgument(0));
        when(actvHstrRepo.existsByBizKeyAndDvCdAndTaskCdAndApiApvKey(BIZ, "B", "B003", "1C25TAA-100"))
                .thenReturn(false);
        when(legacyFn.nextTrcoActvSeq()).thenReturn("7");
        when(actvHstrRepo.saveAndFlush(any(TrcoActvHstr.class))).thenAnswer(i -> i.getArgument(0));

        registrar.registerCounterparty(
                false, BIZ, USER, DTM, "1C25TAA-100",
                rowReg("Buyer Co", "0312345678", null, null,
                        "Seller Co", "0100000001", null, null, "VND", "500,000",
                        "Chairman X", "Accountant Y", "Seller Full Co"),
                new BigDecimal("500000"));

        // Existing contact patched with the representative (both columns).
        ArgumentCaptor<TrcoInfm> patched = ArgumentCaptor.forClass(TrcoInfm.class);
        verify(trcoInfmRepo).saveAndFlush(patched.capture());
        assertThat(patched.getValue().getTrcoRpprEnm()).isEqualTo("Chairman X");
        assertThat(patched.getValue().getTrcoRpprNm()).isEqualTo("Chairman X");
        // Responsible-person row created (none existed).
        verify(rspbRepo).saveAndFlush(any(TrcoRspbInfm.class));
    }

    @Test
    void existingContact_doesNotOverwriteExistingRepresentative_norDuplicateRspb() {
        TrcoInfm existing = new TrcoInfm();
        existing.setTrcoKey("9999");
        existing.setBizKey(BIZ);
        existing.setTaxId("0100000001");
        existing.setTrcoRpprEnm("Original Rep");   // already set → NOT overwritten
        existing.setTrcoRpprNm("Original Rep");
        when(trcoInfmRepo.findFirstByBizKeyAndTrcoTaxIdAndTrscCcyCd(BIZ, "0100000001", "VND"))
                .thenReturn(Optional.of(existing));
        when(pgroRepo.existsById(any(PgroBsacLnknInfmId.class))).thenReturn(true);
        when(rspbRepo.existsByBizKeyAndTrcoKey(BIZ, "9999")).thenReturn(true); // already has one
        when(actvHstrRepo.existsByBizKeyAndDvCdAndTaskCdAndApiApvKey(BIZ, "B", "B003", "1C25TAA-100"))
                .thenReturn(false);
        when(legacyFn.nextTrcoActvSeq()).thenReturn("7");
        when(actvHstrRepo.saveAndFlush(any(TrcoActvHstr.class))).thenAnswer(i -> i.getArgument(0));

        registrar.registerCounterparty(
                false, BIZ, USER, DTM, "1C25TAA-100",
                rowReg("Buyer Co", "0312345678", null, null,
                        "Seller Co", "0100000001", null, null, "VND", "500,000",
                        "New Rep", "Accountant Z", "Seller Full Co"),
                new BigDecimal("500000"));

        // No representative patch (already set) and no duplicate RSPB row.
        verify(trcoInfmRepo, never()).saveAndFlush(any());
        verify(rspbRepo, never()).saveAndFlush(any());
        verify(legacyFn, never()).nextRspbKey();
        assertThat(existing.getTrcoRpprEnm()).isEqualTo("Original Rep");
    }
}
