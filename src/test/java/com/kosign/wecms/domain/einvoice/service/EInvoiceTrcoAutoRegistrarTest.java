package com.kosign.wecms.domain.einvoice.service;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.einvoice.dto.ScrapedEInvoiceDetailRow;
import com.kosign.wecms.domain.einvoice.entity.TrcoActvHstr;
import com.kosign.wecms.domain.einvoice.repository.TrcoActvHstrRepository;
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
    @Mock LegacyFunctionRepository legacyFn;

    @InjectMocks EInvoiceTrcoAutoRegistrar registrar;

    /** Build a detail row populating only the counterparty fields used by
     *  the registrar; everything else is null. */
    private static ScrapedEInvoiceDetailRow row(
            String buyerName, String buyerTaxCode, String buyerBankAcc, String buyerBankName,
            String sellerName, String sellerTaxCode, String sellerBankAcc, String sellerBankName,
            String currency, String totalAmt) {
        return new ScrapedEInvoiceDetailRow(
                "1", "C25TAA", "100", "0100000001", "0",       // identity
                currency, "title", sellerName, totalAmt, null, null, null,
                buyerName, buyerTaxCode, "Hanoi, Việt Nam", buyerBankAcc, buyerBankName, null,
                "HCMC, Việt Nam", sellerBankAcc, sellerBankName, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null,
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

        String trcoKey = registrar.registerCounterparty(
                /*isSales*/ true, BIZ, USER, DTM, "1C25TAA-100",
                row("Buyer Co", "0312345678", "12345678", "Vietcombank",
                        "Seller Co", "0100000001", null, null, "VND", "1,500,000"),
                new BigDecimal("1500000"));

        assertThat(trcoKey).isEqualTo("1000001");

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

        String trcoKey = registrar.registerCounterparty(
                /*isSales*/ false, BIZ, USER, DTM, "1C25TAA-100",
                row("Buyer Co", "0312345678", null, null,
                        "Seller Co", "0100000001", null, null, "VND", "900,000"),
                new BigDecimal("900000"));

        assertThat(trcoKey).isEqualTo("9999");
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

        String trcoKey = registrar.registerCounterparty(
                true, BIZ, USER, DTM, "1C25TAA-100",
                row("", "", null, null, "Seller Co", "0100000001", null, null, "VND", "100,000"),
                new BigDecimal("100000"));

        assertThat(trcoKey).isEqualTo(EInvoiceTrcoAutoRegistrar.ETC_TRCO_KEY);
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
}
