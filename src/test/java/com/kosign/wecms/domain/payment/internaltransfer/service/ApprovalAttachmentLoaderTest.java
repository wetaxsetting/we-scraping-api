package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.domain.payment.internaltransfer.dto.ApprovalAttachmentRow;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ExptResolFile;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ExptResolPayEvidFile;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ExptResolFileRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ExptResolPayEvidFileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GAP-ATT-1 — the Approval Details attachment list must be the UNION of
 * {@code EXPT_RESOL_FILE} (requester uploads) and
 * {@code EXPT_RESOL_PAY_EVID_FILE} (payment evidence), mirroring the legacy
 * {@code IDO.EXPT_RESOL_FILE_R002} query. Each row must carry the source
 * {@code fileType} so a download can resolve the correct legacy table.
 */
@ExtendWith(MockitoExtension.class)
class ApprovalAttachmentLoaderTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String APPR_SEQ_NO = "200";

    @Mock private ExptResolFileRepository        exptResolFileRepository;
    @Mock private ExptResolPayEvidFileRepository exptResolPayEvidFileRepository;

    @InjectMocks private ApprovalAttachmentLoader loader;

    private static ExptResolFile file(String fileId, String name) {
        ExptResolFile f = new ExptResolFile();
        f.setBizKey(BIZ_KEY);
        f.setApprSeqNo(APPR_SEQ_NO);
        f.setFileId(fileId);
        f.setOrgFileNm(name);
        f.setSaveFileNm(APPR_SEQ_NO + "_" + fileId + "_" + name);
        f.setFileSize("1024");
        f.setRegNm("Requester");
        f.setRegDtm("20260101090000");
        return f;
    }

    private static ExptResolPayEvidFile evid(String evidFileId, String name) {
        ExptResolPayEvidFile f = new ExptResolPayEvidFile();
        f.setBizKey(BIZ_KEY);
        f.setApprSeqNo(APPR_SEQ_NO);
        f.setEvidFileId(evidFileId);
        f.setOrgFileNm(name);
        f.setSaveFileNm(APPR_SEQ_NO + "_" + evidFileId + "_" + name);
        f.setFileSize("2048");
        f.setRegNm("Payer");
        f.setRegDtm("20260102100000");
        return f;
    }

    @Test
    void load_combinesBothTables_withFileTypeTag_payEvidIncluded() {
        when(exptResolFileRepository.findByBizKeyAndApprSeqNoOrderByFileIdAsc(BIZ_KEY, APPR_SEQ_NO))
                .thenReturn(List.of(file("1", "invoice.pdf")));
        when(exptResolPayEvidFileRepository.findByBizKeyAndApprSeqNoOrderByEvidFileIdAsc(BIZ_KEY, APPR_SEQ_NO))
                .thenReturn(List.of(evid("1", "evidence.jpg")));

        List<ApprovalAttachmentRow> rows = loader.load(BIZ_KEY, APPR_SEQ_NO);

        // Both sources present — the pay-evidence file is no longer dropped.
        assertThat(rows).hasSize(2);

        // EXPT_RESOL_FILE rows come first, then EXPT_RESOL_PAY_EVID_FILE.
        ApprovalAttachmentRow upload = rows.get(0);
        assertThat(upload.orgFileNm()).isEqualTo("invoice.pdf");
        assertThat(upload.fileType()).isEqualTo(ApprovalAttachmentRow.TYPE_EXPT_RESOL_FILE);

        ApprovalAttachmentRow payEvid = rows.get(1);
        assertThat(payEvid.orgFileNm()).isEqualTo("evidence.jpg");
        assertThat(payEvid.fileType()).isEqualTo(ApprovalAttachmentRow.TYPE_PAY_EVID_FILE);
        assertThat(payEvid.fileId()).isEqualTo("1"); // EVID_FILE_ID surfaced as fileId

        // The fileType discriminator lets the two rows that share fileId="1"
        // be routed to the correct legacy table on download.
        assertThat(rows).extracting(ApprovalAttachmentRow::fileType)
                .containsExactly(
                        ApprovalAttachmentRow.TYPE_EXPT_RESOL_FILE,
                        ApprovalAttachmentRow.TYPE_PAY_EVID_FILE);
    }

    @Test
    void load_payEvidOnly_stillReturnsTheEvidenceFile() {
        when(exptResolFileRepository.findByBizKeyAndApprSeqNoOrderByFileIdAsc(BIZ_KEY, APPR_SEQ_NO))
                .thenReturn(List.of());
        when(exptResolPayEvidFileRepository.findByBizKeyAndApprSeqNoOrderByEvidFileIdAsc(BIZ_KEY, APPR_SEQ_NO))
                .thenReturn(List.of(evid("7", "receipt.png")));

        List<ApprovalAttachmentRow> rows = loader.load(BIZ_KEY, APPR_SEQ_NO);

        assertThat(rows).singleElement()
                .satisfies(r -> {
                    assertThat(r.orgFileNm()).isEqualTo("receipt.png");
                    assertThat(r.fileType()).isEqualTo(ApprovalAttachmentRow.TYPE_PAY_EVID_FILE);
                });
    }

    @Test
    void load_blankApprSeqNo_returnsEmpty_noQueries() {
        assertThat(loader.load(BIZ_KEY, null)).isEmpty();
        assertThat(loader.load(BIZ_KEY, "  ")).isEmpty();

        verifyNoInteractions(exptResolFileRepository, exptResolPayEvidFileRepository);
    }

    @Test
    void load_noAttachments_returnsEmpty() {
        when(exptResolFileRepository.findByBizKeyAndApprSeqNoOrderByFileIdAsc(BIZ_KEY, APPR_SEQ_NO))
                .thenReturn(List.of());
        when(exptResolPayEvidFileRepository.findByBizKeyAndApprSeqNoOrderByEvidFileIdAsc(BIZ_KEY, APPR_SEQ_NO))
                .thenReturn(List.of());

        assertThat(loader.load(BIZ_KEY, APPR_SEQ_NO)).isEmpty();
        verify(exptResolFileRepository, never()).findByBizKeyAndApprSeqNoAndFileId(anyString(), anyString(), anyString());
    }
}
