package com.kosign.wecms.domain.payment.haengsung.service;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.payment.dispatch.file.VietnameseText;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl;
import com.kosign.wecms.domain.payment.haengsung.oracle.OracleTransDetail;
import com.kosign.wecms.domain.payment.haengsung.oracle.OracleTransMaster;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnDtlsRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnSumrRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ErpPaymentRefreshMasterImporterTest {

    private static final String BIZ = "VN_VNM0004_000014";
    private static final String USER = "tester";
    private static final String NOW = "20260628120000";

    @Mock ScdulPymnSumrRepository scdulSumrRepo;
    @Mock ScdulPymnSumrDetlRepository scdulDetlRepo;
    @Mock OvrsPymnSumrRepository ovrsSumrRepo;
    @Mock OvrsPymnDtlsRepository ovrsDetlRepo;
    @Mock LegacyFunctionRepository legacyFunctions;

    @InjectMocks ErpPaymentRefreshMasterImporter importer;

    private OracleTransMaster master(String fileGb, String fileNm) {
        return new OracleTransMaster("20260628", "00000123", fileGb, fileNm, "0301234567",
                "11704088", "1234567890", new BigDecimal("2"), new BigDecimal("3000"), "N");
    }

    private OracleTransDetail detail(int seq) {
        return new OracleTransDetail(new BigDecimal(seq), new BigDecimal("1500"), "VND", "O", "N",
                "Nguyễn Văn A", "9988 7766", "9704 24", "Shinhan Bank", "Hanoi", "2", "salary", "attr2");
    }

    /* ---------------- routing / type-code helpers (M-2 marked) ---------------- */

    @Test void routing_overseas_payroll_expense_vendor() {
        assertThat(ErpPaymentRefreshMasterImporter.isOverseas("AF1")).isTrue();
        assertThat(ErpPaymentRefreshMasterImporter.isOverseas("AB0")).isFalse();
        assertThat(ErpPaymentRefreshMasterImporter.deriveScheduledTypeCd(master("AC2", "x"))).isEqualTo("00"); // payroll
        // M-2 UNRESOLVED: E-prefixed FILE_NM → expense; contradicted by sample data (spec §17 M-2)
        assertThat(ErpPaymentRefreshMasterImporter.deriveScheduledTypeCd(master("AB0", "Expenses"))).isEqualTo("02");
        assertThat(ErpPaymentRefreshMasterImporter.deriveScheduledTypeCd(master("AB0", "20250903-001"))).isEqualTo("01"); // vendor
    }

    @Test void feePayer_mappings() {
        assertThat(ErpPaymentRefreshMasterImporter.mapFeePayer("1")).isEqualTo("B");
        assertThat(ErpPaymentRefreshMasterImporter.mapFeePayer("2")).isEqualTo("O");
        assertThat(ErpPaymentRefreshMasterImporter.mapFeePayer("3")).isEqualTo("S");
        assertThat(ErpPaymentRefreshMasterImporter.mapFeePayer(null)).isEqualTo("S");
    }

    /**
     * The importer's private NFD tone-stripper was replaced by the shared legacy table
     * ({@code VietnameseText.replaceTone}, the same one the dispatch codec uses), so this pins the
     * table's behaviour on the inputs the deleted implementation used to special-case.
     *
     * <p>{@code Đ/đ} (U+0110/U+0111) is the reason the NFD version existed in the form it did: those two
     * have no canonical decomposition, so NFD alone left them and an explicit {@code replace} was bolted
     * on. {@code Ơơ Ưư} (U+01A0/U+01A1/U+01AF/U+01B0) is the reverse worry and is also unfounded — the
     * horn is a combining mark (U+031B), so both implementations reduce them. The table handles all of
     * it in one pass, which is why the second implementation is gone.
     */
    @Test void toneStripping_usesTheSharedLegacyTable() {
        assertThat(VietnameseText.replaceTone("Nguyễn Đức")).isEqualTo("Nguyen Duc");
        assertThat(VietnameseText.replaceTone("Phương")).isEqualTo("Phuong");
        assertThat(VietnameseText.replaceTone("Sơn")).isEqualTo("Son");
        assertThat(VietnameseText.replaceTone("Tư")).isEqualTo("Tu");
        assertThat(VietnameseText.replaceTone("Được")).isEqualTo("Duoc");
    }

    /* ---------------- scheduled NEW insert ---------------- */

    @Test void scheduledNew_insertsSummaryAndDetail_asDraft() {
        when(scdulSumrRepo.findFirstByBizKeyAndErpFileNumAndErpFileDate(BIZ, "00000123", "20260628"))
                .thenReturn(Optional.empty());
        when(legacyFunctions.nextScdulPymnNo(BIZ)).thenReturn("100");
        when(legacyFunctions.nextScdulPymnDetlNo(BIZ)).thenReturn("5");

        MasterImportOutcome out = importer.importMaster(BIZ, USER, NOW,
                master("AB0", "20250903-001"), List.of(detail(1)));

        assertThat(out.status()).isEqualTo(MasterImportOutcome.Status.IMPORTED);
        assertThat(out.detailsImported()).isEqualTo(1);

        ArgumentCaptor<ScdulPymnSumr> sumr = ArgumentCaptor.forClass(ScdulPymnSumr.class);
        verify(scdulSumrRepo).saveAndFlush(sumr.capture());
        ScdulPymnSumr s = sumr.getValue();
        assertThat(s.getScdulPymnNo()).isEqualTo("100");
        assertThat(s.getScdulPymnTypeCd()).isEqualTo("01");      // vendor
        assertThat(s.getApprSeqNo()).isNull();                   // draft — never set
        assertThat(s.getTrnfYn()).isEqualTo("N");
        assertThat(s.getErpFileNum()).isEqualTo("00000123");
        assertThat(s.getErpFileDate()).isEqualTo("20260628");
        assertThat(s.getTotalTranAmt()).isEqualByComparingTo("3000");

        ArgumentCaptor<ScdulPymnSumrDetl> d = ArgumentCaptor.forClass(ScdulPymnSumrDetl.class);
        verify(scdulDetlRepo).save(d.capture());
        ScdulPymnSumrDetl dv = d.getValue();
        assertThat(dv.getScdulPymnNo()).isEqualTo("100");
        assertThat(dv.getScdulPymnDetlNo()).isEqualTo("5");
        assertThat(dv.getErpFileSeq()).isEqualByComparingTo("1");
        assertThat(dv.getFincMdaNo()).isEqualTo("99887766");     // spaces stripped
        assertThat(dv.getRcptNm()).isEqualTo("Nguyen Van A");    // tone removed
        assertThat(dv.getErpAttribute2()).isEqualTo("attr2");

        verify(ovrsSumrRepo, never()).saveAndFlush(any());
    }

    /* ---------------- dedup UPDATE (existing summary, no approval) ---------------- */

    @Test void scheduledDedup_updatesExisting_doesNotAllocateNewKey() {
        ScdulPymnSumr existing = new ScdulPymnSumr();
        existing.setBizKey(BIZ);
        existing.setScdulPymnNo("55");
        existing.setApprSeqNo(null);
        when(scdulSumrRepo.findFirstByBizKeyAndErpFileNumAndErpFileDate(BIZ, "00000123", "20260628"))
                .thenReturn(Optional.of(existing));
        // existing detail with erpFileSeq=1 → should be UPDATED (no new number)
        ScdulPymnSumrDetl existingDetl = new ScdulPymnSumrDetl();
        existingDetl.setBizKey(BIZ); existingDetl.setScdulPymnNo("55");
        existingDetl.setScdulPymnDetlNo("9"); existingDetl.setErpFileSeq(new BigDecimal("1"));
        when(scdulDetlRepo.findByBizKeyAndScdulPymnNo(eq(BIZ), eq("55"), any()))
                .thenReturn(List.of(existingDetl));

        MasterImportOutcome out = importer.importMaster(BIZ, USER, NOW,
                master("AB0", "20250903-001"), List.of(detail(1)));

        assertThat(out.status()).isEqualTo(MasterImportOutcome.Status.IMPORTED);
        verify(legacyFunctions, never()).nextScdulPymnNo(anyString());        // reused "55"
        verify(legacyFunctions, never()).nextScdulPymnDetlNo(anyString());    // existing line updated, no new no
        ArgumentCaptor<ScdulPymnSumr> sumr = ArgumentCaptor.forClass(ScdulPymnSumr.class);
        verify(scdulSumrRepo).saveAndFlush(sumr.capture());
        assertThat(sumr.getValue().getScdulPymnNo()).isEqualTo("55");
        ArgumentCaptor<ScdulPymnSumrDetl> d = ArgumentCaptor.forClass(ScdulPymnSumrDetl.class);
        verify(scdulDetlRepo).save(d.capture());
        assertThat(d.getValue().getScdulPymnDetlNo()).isEqualTo("9");          // reused
    }

    /* ---------------- approval-lock skip ---------------- */

    @Test void approvalLocked_skipsEntireMaster_noWrites() {
        ScdulPymnSumr locked = new ScdulPymnSumr();
        locked.setBizKey(BIZ); locked.setScdulPymnNo("77"); locked.setApprSeqNo("999");
        when(scdulSumrRepo.findFirstByBizKeyAndErpFileNumAndErpFileDate(BIZ, "00000123", "20260628"))
                .thenReturn(Optional.of(locked));

        MasterImportOutcome out = importer.importMaster(BIZ, USER, NOW,
                master("AB0", "x"), List.of(detail(1)));

        assertThat(out.status()).isEqualTo(MasterImportOutcome.Status.SKIPPED_APPROVAL_LOCK);
        verify(scdulSumrRepo, never()).saveAndFlush(any());
        verify(scdulDetlRepo, never()).save(any());
        verify(legacyFunctions, never()).nextScdulPymnNo(anyString());
    }

    /* ---------------- overseas routing + mapping ---------------- */

    @Test void overseas_insertsOvrsSummaryAndDetail() {
        when(ovrsSumrRepo.findFirstByBizKeyAndErpFileNumAndErpFileDate(BIZ, "00000123", "20260628"))
                .thenReturn(Optional.empty());
        when(legacyFunctions.nextOvrsPymnNo(BIZ)).thenReturn("20260628000001");
        when(legacyFunctions.nextOvrsPymnDetlNo(BIZ)).thenReturn("3");

        MasterImportOutcome out = importer.importMaster(BIZ, USER, NOW,
                master("AF1", "OVERSEAS-001"), List.of(detail(1)));

        assertThat(out.status()).isEqualTo(MasterImportOutcome.Status.IMPORTED);
        ArgumentCaptor<OvrsPymnSumr> sumr = ArgumentCaptor.forClass(OvrsPymnSumr.class);
        verify(ovrsSumrRepo).saveAndFlush(sumr.capture());
        assertThat(sumr.getValue().getOvrsPymnNo()).isEqualTo("20260628000001");
        assertThat(sumr.getValue().getOvrsPymnTypeCd()).isEqualTo("01");
        assertThat(sumr.getValue().getErpFileNum()).isEqualTo("00000123");

        ArgumentCaptor<OvrsPymnSumrDetl> d = ArgumentCaptor.forClass(OvrsPymnSumrDetl.class);
        verify(ovrsDetlRepo).save(d.capture());
        OvrsPymnSumrDetl dv = d.getValue();
        assertThat(dv.getOvrsPymnDetlNo()).isEqualTo("3");
        assertThat(dv.getFeePayerCd()).isEqualTo("O");           // FORI_FEE_MAN '2' → O
        assertThat(dv.getBnfcNm()).isEqualTo("Nguyen Van A");    // tone removed
        assertThat(dv.getErpFileSeq()).isEqualByComparingTo("1");
        assertThat(dv.getTrcoKey()).isEmpty();                   // contact-less import

        verify(scdulSumrRepo, never()).saveAndFlush(any());
    }
}
