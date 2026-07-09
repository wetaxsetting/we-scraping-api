package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ApprovalAttachmentRow;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ExptResolFile;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ExptResolPayEvidFile;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ExptResolFileRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ExptResolPayEvidFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * GAP-ATT-1 download routing — {@link ApprovalAttachmentDownloadService}
 * must resolve the binary from the correct legacy table based on
 * {@code fileType}, key on {@code BIZ_KEY} for tenant isolation, and
 * disambiguate a {@code fileId} shared by both tables.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApprovalAttachmentDownloadServiceTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String OTHER_BIZ = "VN_VNM0004_000999";
    private static final String APPR_SEQ_NO = "200";

    @Mock private ExptResolFileRepository        exptResolFileRepository;
    @Mock private ExptResolPayEvidFileRepository exptResolPayEvidFileRepository;
    @Mock private InternalTransferFileResolver   fileResolver;

    @InjectMocks private ApprovalAttachmentDownloadService service;

    @TempDir Path tmp;

    private Path fileA;   // EXPT_RESOL_FILE binary
    private Path fileB;   // EXPT_RESOL_PAY_EVID_FILE binary

    @BeforeEach
    void setUp() throws IOException {
        fileA = Files.writeString(tmp.resolve("save_file.dat"), "from-expt-resol-file");
        fileB = Files.writeString(tmp.resolve("save_evid.dat"), "from-pay-evid-file");
        // Resolver maps each saveFileNm to its temp binary.
        lenient().when(fileResolver.resolveApprovalAttachment(eq(BIZ_KEY), eq(APPR_SEQ_NO), eq("save_file.dat")))
                .thenReturn(fileA);
        lenient().when(fileResolver.resolveApprovalAttachment(eq(BIZ_KEY), eq(APPR_SEQ_NO), eq("save_evid.dat")))
                .thenReturn(fileB);
    }

    private static ExptResolFile fileRow() {
        ExptResolFile f = new ExptResolFile();
        f.setBizKey(BIZ_KEY);
        f.setApprSeqNo(APPR_SEQ_NO);
        f.setFileId("1");
        f.setOrgFileNm("invoice.pdf");
        f.setSaveFileNm("save_file.dat");
        f.setFileSize(null);  // exercises the on-disk size fallback
        f.setRegNm("Requester");
        f.setRegDtm("20260101090000");
        return f;
    }

    private static ExptResolPayEvidFile evidRow() {
        ExptResolPayEvidFile f = new ExptResolPayEvidFile();
        f.setBizKey(BIZ_KEY);
        f.setApprSeqNo(APPR_SEQ_NO);
        f.setEvidFileId("1");          // SAME short id as the file row
        f.setOrgFileNm("evidence.jpg");
        f.setSaveFileNm("save_evid.dat");
        f.setFileSize("18");
        f.setRegNm("Payer");
        f.setRegDtm("20260102100000");
        return f;
    }

    @Test
    void resolve_exptResolFile_fromFileTable() {
        when(exptResolFileRepository.findByBizKeyAndApprSeqNoAndFileId(BIZ_KEY, APPR_SEQ_NO, "1"))
                .thenReturn(Optional.of(fileRow()));

        var r = service.resolve(BIZ_KEY, APPR_SEQ_NO, "1", ApprovalAttachmentRow.TYPE_EXPT_RESOL_FILE);

        assertThat(r.orgFileNm()).isEqualTo("invoice.pdf");
        assertThat(r.path()).isEqualTo(fileA);
    }

    @Test
    void resolve_payEvid_fromPayEvidTable() {
        when(exptResolPayEvidFileRepository.findByBizKeyAndApprSeqNoAndEvidFileId(BIZ_KEY, APPR_SEQ_NO, "1"))
                .thenReturn(Optional.of(evidRow()));

        var r = service.resolve(BIZ_KEY, APPR_SEQ_NO, "1", ApprovalAttachmentRow.TYPE_PAY_EVID_FILE);

        assertThat(r.orgFileNm()).isEqualTo("evidence.jpg");
        assertThat(r.path()).isEqualTo(fileB);
        assertThat(r.fileSize()).isEqualTo(18L);
    }

    @Test
    void resolve_sameFileId_differentTables_routesByFileType() {
        when(exptResolFileRepository.findByBizKeyAndApprSeqNoAndFileId(BIZ_KEY, APPR_SEQ_NO, "1"))
                .thenReturn(Optional.of(fileRow()));
        when(exptResolPayEvidFileRepository.findByBizKeyAndApprSeqNoAndEvidFileId(BIZ_KEY, APPR_SEQ_NO, "1"))
                .thenReturn(Optional.of(evidRow()));

        var asFile = service.resolve(BIZ_KEY, APPR_SEQ_NO, "1", ApprovalAttachmentRow.TYPE_EXPT_RESOL_FILE);
        var asEvid = service.resolve(BIZ_KEY, APPR_SEQ_NO, "1", ApprovalAttachmentRow.TYPE_PAY_EVID_FILE);

        // The shared short fileId "1" resolves to the correct file per type.
        assertThat(asFile.orgFileNm()).isEqualTo("invoice.pdf");
        assertThat(asEvid.orgFileNm()).isEqualTo("evidence.jpg");
    }

    @Test
    void resolve_nullOrUnknownFileType_defaultsToExptResolFile() {
        when(exptResolFileRepository.findByBizKeyAndApprSeqNoAndFileId(BIZ_KEY, APPR_SEQ_NO, "1"))
                .thenReturn(Optional.of(fileRow()));

        // null fileType -> EXPT_RESOL_FILE
        assertThat(service.resolve(BIZ_KEY, APPR_SEQ_NO, "1", null).orgFileNm())
                .isEqualTo("invoice.pdf");
        // unknown fileType -> still defaults safely to EXPT_RESOL_FILE
        assertThat(service.resolve(BIZ_KEY, APPR_SEQ_NO, "1", "SOMETHING_ELSE").orgFileNm())
                .isEqualTo("invoice.pdf");
    }

    @Test
    void resolve_tenantMismatch_cannotDownload() {
        // Row exists for BIZ_KEY but the caller's tenant is OTHER_BIZ — the
        // repo lookup keys on bizKey, so it returns empty -> WBE2069.
        when(exptResolFileRepository.findByBizKeyAndApprSeqNoAndFileId(OTHER_BIZ, APPR_SEQ_NO, "1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.resolve(OTHER_BIZ, APPR_SEQ_NO, "1", ApprovalAttachmentRow.TYPE_EXPT_RESOL_FILE))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("WBE2069"));
    }

    @Test
    void resolve_missingRow_throwsWBE2069() {
        when(exptResolPayEvidFileRepository.findByBizKeyAndApprSeqNoAndEvidFileId(BIZ_KEY, APPR_SEQ_NO, "9"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.resolve(BIZ_KEY, APPR_SEQ_NO, "9", ApprovalAttachmentRow.TYPE_PAY_EVID_FILE))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("WBE2069"));
    }

    @Test
    void resolve_rowPresentButFileMissingOnDisk_throwsWBE2069() {
        ExptResolFile row = fileRow();
        row.setSaveFileNm("does_not_exist.dat");
        when(exptResolFileRepository.findByBizKeyAndApprSeqNoAndFileId(BIZ_KEY, APPR_SEQ_NO, "1"))
                .thenReturn(Optional.of(row));
        when(fileResolver.resolveApprovalAttachment(eq(BIZ_KEY), eq(APPR_SEQ_NO), eq("does_not_exist.dat")))
                .thenReturn(tmp.resolve("does_not_exist.dat"));

        assertThatThrownBy(() ->
                service.resolve(BIZ_KEY, APPR_SEQ_NO, "1", ApprovalAttachmentRow.TYPE_EXPT_RESOL_FILE))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("WBE2069"));
    }
}
