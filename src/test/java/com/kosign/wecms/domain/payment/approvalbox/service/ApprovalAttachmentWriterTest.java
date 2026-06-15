package com.kosign.wecms.domain.payment.approvalbox.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ExptResolFile;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ExptResolFileRepository;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferFileResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * M-1 (legacy parity): the shared {@link ApprovalAttachmentWriter} persists one
 * {@code EXPT_RESOL_FILE} row per uploaded file (legacy
 * {@code processFileAttachments} / REC1) and is a no-op when no files are sent
 * (the JSON-only Request Approval contract still works). Used by every Payment
 * module's Request Approval flow.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApprovalAttachmentWriterTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String USER_ID = "tester";
    private static final String APPR_SEQ_NO = "100";

    @Mock private ExptResolFileRepository      fileRepository;
    @Mock private InternalTransferFileResolver fileResolver;
    @Mock private LegacyFunctionRepository     legacyFunctions;

    private ApprovalAttachmentWriter writer;

    @TempDir Path tmp;

    @BeforeEach
    void setUp() {
        // Fixed UTC clock — ICT (+7) renders as 2026-06-13 10:00:00 → 20260613100000.
        Clock clock = Clock.fixed(Instant.parse("2026-06-13T03:00:00Z"), ZoneOffset.UTC);
        writer = new ApprovalAttachmentWriter(fileRepository, fileResolver, legacyFunctions, clock);
        TenantContext.set(USER_ID, "Tester", BIZ_KEY, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void persist_insertsOneExptResolFileRowPerFile() throws Exception {
        when(fileResolver.approvalAttachmentDirectory(BIZ_KEY, APPR_SEQ_NO)).thenReturn(tmp);
        when(fileResolver.resolveApprovalAttachment(eq(BIZ_KEY), eq(APPR_SEQ_NO), anyString()))
                .thenAnswer(inv -> tmp.resolve(inv.getArgument(2, String.class)));
        when(legacyFunctions.nextExptResolFileId(BIZ_KEY, APPR_SEQ_NO)).thenReturn("1");

        MultipartFile f1 = new MockMultipartFile("attachments", "a.pdf", "application/pdf", new byte[] {1, 2, 3});
        MultipartFile f2 = new MockMultipartFile("attachments", "b doc.png", "image/png", new byte[] {4, 5});

        writer.persist(BIZ_KEY, APPR_SEQ_NO, List.of(f1, f2));

        ArgumentCaptor<ExptResolFile> captor = ArgumentCaptor.forClass(ExptResolFile.class);
        verify(fileRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<ExptResolFile> rows = captor.getAllValues();

        // Row 1
        assertThat(rows.get(0).getBizKey()).isEqualTo(BIZ_KEY);
        assertThat(rows.get(0).getApprSeqNo()).isEqualTo(APPR_SEQ_NO);
        assertThat(rows.get(0).getFileId()).isEqualTo("1");
        assertThat(rows.get(0).getOrgFileNm()).isEqualTo("a.pdf");
        assertThat(rows.get(0).getSaveFileNm()).isEqualTo("100_1_a.pdf");
        assertThat(rows.get(0).getFileSize()).isEqualTo("3");
        assertThat(rows.get(0).getRegId()).isEqualTo(USER_ID);
        assertThat(rows.get(0).getRegDtm()).isEqualTo("20260613100000");
        // Row 2 — FILE_ID increments in Java; spaces preserved by the sanitiser.
        assertThat(rows.get(1).getFileId()).isEqualTo("2");
        assertThat(rows.get(1).getOrgFileNm()).isEqualTo("b doc.png");
        assertThat(rows.get(1).getSaveFileNm()).isEqualTo("100_2_b doc.png");
        assertThat(rows.get(1).getFileSize()).isEqualTo("2");

        // Bytes actually streamed to the per-approval directory.
        assertThat(Files.exists(tmp.resolve("100_1_a.pdf"))).isTrue();
        assertThat(Files.exists(tmp.resolve("100_2_b doc.png"))).isTrue();
    }

    @Test
    void persist_isNoOp_whenNoFiles() {
        writer.persist(BIZ_KEY, APPR_SEQ_NO, null);
        writer.persist(BIZ_KEY, APPR_SEQ_NO, List.of());

        verifyNoInteractions(fileRepository);
        verifyNoInteractions(legacyFunctions);
        verifyNoInteractions(fileResolver);
    }

    @Test
    void sanitiseOriginalFilename_stripsPathAndMetacharacters() {
        assertThat(ApprovalAttachmentWriter.sanitiseOriginalFilename("../../etc/p@ss wd.txt"))
                .isEqualTo("p_ss wd.txt");
        assertThat(ApprovalAttachmentWriter.sanitiseOriginalFilename(null)).isEqualTo("upload.xlsx");
        assertThat(ApprovalAttachmentWriter.sanitiseOriginalFilename("  ")).isEqualTo("upload.xlsx");
    }
}
