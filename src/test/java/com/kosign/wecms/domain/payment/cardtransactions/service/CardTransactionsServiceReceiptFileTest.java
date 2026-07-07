package com.kosign.wecms.domain.payment.cardtransactions.service;

import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.einvoice.entity.BusaDocMstr;
import com.kosign.wecms.domain.einvoice.repository.BusaDocMstrRepository;
import com.kosign.wecms.domain.payment.cardtransactions.dto.ReceiptFileRow;
import com.kosign.wecms.domain.payment.cardtransactions.entity.BusaDocFile;
import com.kosign.wecms.domain.payment.cardtransactions.repository.BusaDocFileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * G3/G5 — receipt / e-Invoice attachments (BUSA_DOC_FILE). Upload persist +
 * one-shot retry + validation + list projection. Mockito-only (no Spring).
 */
@ExtendWith(MockitoExtension.class)
class CardTransactionsServiceReceiptFileTest {

    @Mock BusaDocMstrRepository            busaDocMstrRepo;
    @Mock BusaDocFileRepository            busaDocFileRepo;
    @Mock CardTransactionsFileResolver     cardFileResolver;
    @Mock LegacyFunctionRepository         legacyFunctions;
    @Mock com.kosign.wecms.domain.payment.cardtransactions.repository.CardTrnsRcvPayInfmRepository cardTrnsRcvPayRepo;
    @Mock AuditLogService                  auditLogService;

    @InjectMocks CardTransactionsService service;

    private static final String BIZ  = "VN_VNM0004_000001";
    private static final String USER = "tester";
    private static final String DOC  = "R-20240523005";

    @TempDir Path tmp;

    @BeforeEach
    void setUp() {
        TenantContext.set(USER, BIZ, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void docExists() {
        BusaDocMstr d = new BusaDocMstr();
        d.setBizKey(BIZ);
        d.setDocId(DOC);
        when(busaDocMstrRepo.findByBizKeyAndDocIdIn(BIZ, List.of(DOC))).thenReturn(List.of(d));
    }

    private MultipartFile pdf() {
        MultipartFile f = org.mockito.Mockito.mock(MultipartFile.class);
        lenient().when(f.getOriginalFilename()).thenReturn("receipt.pdf");
        lenient().when(f.getSize()).thenReturn(2048L);
        return f;
    }

    @Test
    void upload_persistsBusaDocFile_withFunnelFileId() throws Exception {
        docExists();
        when(cardFileResolver.tenantRoot(BIZ)).thenReturn(tmp);
        when(cardFileResolver.resolve(eq(BIZ), anyString())).thenAnswer(i -> tmp.resolve((String) i.getArgument(1)));
        when(legacyFunctions.nextBusaDocFileFileId(BIZ, DOC)).thenReturn("1");
        when(busaDocFileRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ReceiptFileRow row = service.uploadReceiptFile(DOC, pdf());

        assertThat(row.fileId()).isEqualTo("1");
        assertThat(row.orgFileNm()).isEqualTo("receipt.pdf");
        assertThat(row.fileSize()).isEqualTo("2048");
        ArgumentCaptor<BusaDocFile> cap = ArgumentCaptor.forClass(BusaDocFile.class);
        verify(busaDocFileRepo).save(cap.capture());
        BusaDocFile saved = cap.getValue();
        assertThat(saved.getBizKey()).isEqualTo(BIZ);
        assertThat(saved.getDocId()).isEqualTo(DOC);
        assertThat(saved.getFileId()).isEqualTo("1");
        assertThat(saved.getSaveFileNm()).endsWith(".pdf"); // server UUID + ext
        assertThat(saved.getSaveFileNm()).doesNotContain("receipt"); // not the raw name
        verify(legacyFunctions).nextBusaDocFileFileId(BIZ, DOC);
    }

    @Test
    void upload_oneShotRetry_onPkCollision() throws Exception {
        docExists();
        when(cardFileResolver.tenantRoot(BIZ)).thenReturn(tmp);
        when(cardFileResolver.resolve(eq(BIZ), anyString())).thenAnswer(i -> tmp.resolve((String) i.getArgument(1)));
        when(legacyFunctions.nextBusaDocFileFileId(BIZ, DOC)).thenReturn("1").thenReturn("2");
        when(busaDocFileRepo.save(any()))
                .thenThrow(new DataIntegrityViolationException("dup pk"))
                .thenAnswer(i -> i.getArgument(0));

        ReceiptFileRow row = service.uploadReceiptFile(DOC, pdf());

        assertThat(row.fileId()).isEqualTo("2"); // re-allocated on retry
        verify(legacyFunctions, org.mockito.Mockito.times(2)).nextBusaDocFileFileId(BIZ, DOC);
        verify(busaDocFileRepo, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void upload_rejectsUnknownDoc() {
        when(busaDocMstrRepo.findByBizKeyAndDocIdIn(BIZ, List.of(DOC))).thenReturn(List.of());
        assertThatThrownBy(() -> service.uploadReceiptFile(DOC, pdf()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE_RECEIPT_FILE_DOC_NOT_FOUND");
        verify(busaDocFileRepo, never()).save(any());
    }

    @Test
    void upload_rejectsDisallowedExtension() {
        docExists();
        MultipartFile svg = org.mockito.Mockito.mock(MultipartFile.class);
        lenient().when(svg.getOriginalFilename()).thenReturn("logo.svg");
        lenient().when(svg.getSize()).thenReturn(100L);
        assertThatThrownBy(() -> service.uploadReceiptFile(DOC, svg))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE_RECEIPT_FILE_TYPE_NOT_ALLOWED");
        verify(busaDocFileRepo, never()).save(any());
    }

    @Test
    void upload_rejectsEmptyFile() {
        docExists();
        MultipartFile empty = org.mockito.Mockito.mock(MultipartFile.class);
        lenient().when(empty.getOriginalFilename()).thenReturn("receipt.pdf");
        lenient().when(empty.getSize()).thenReturn(0L);
        assertThatThrownBy(() -> service.uploadReceiptFile(DOC, empty))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE_RECEIPT_FILE_EMPTY");
    }

    @Test
    void listReceiptFiles_mapsRows() {
        docExists();
        BusaDocFile f = new BusaDocFile();
        f.setBizKey(BIZ);
        f.setDocId(DOC);
        f.setFileId("1");
        f.setOrgFileNm("receipt.pdf");
        f.setFileSize("2048");
        when(busaDocFileRepo.findByBizKeyAndDocIdOrderByFileIdAsc(BIZ, DOC)).thenReturn(List.of(f));

        List<ReceiptFileRow> rows = service.listReceiptFiles(DOC);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).fileId()).isEqualTo("1");
        assertThat(rows.get(0).orgFileNm()).isEqualTo("receipt.pdf");
    }
}
