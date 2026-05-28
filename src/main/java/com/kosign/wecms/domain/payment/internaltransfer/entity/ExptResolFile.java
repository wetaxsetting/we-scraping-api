package com.kosign.wecms.domain.payment.internaltransfer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One attachment uploaded with a {@code Request Approval} submission.
 * Mirrors legacy {@code EXPT_RESOL_FILE} column-for-column (V208 in
 * Flyway). Refactor scope today: Internal Transfer flow only — same
 * table is consumed by Vendor / Expense / Payroll / Overseas in
 * follow-up MRs.
 *
 * <p>Wire path:
 * <pre>
 *   POST /api/v1/payment/internal-transfer/{id}/approval   (multipart)
 *     → service writes binary to
 *         &lt;storage-root&gt;/&lt;biz_key&gt;/approval-attachments/&lt;appr_seq_no&gt;/&lt;save_file_nm&gt;
 *       and inserts one row here per file
 *   GET  /api/v1/payment/internal-transfer/{id}            (detail)
 *     → response includes attachments[]   (org_file_nm, file_size, file_id)
 *   GET  …/{id}/approval/{apprSeqNo}/attachments/{fileId}  (download)
 *     → streams the binary back
 * </pre>
 */
@Entity
@Table(name = "EXPT_RESOL_FILE")
@IdClass(ExptResolFileId.class)
@Getter
@Setter
@NoArgsConstructor
public class ExptResolFile {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "APPR_SEQ_NO", length = 15, nullable = false)
    private String apprSeqNo;

    /** Per-row file sequence within the approval — service stamps a
     *  monotonic {@code "1"}, {@code "2"}, … per upload. Legacy
     *  {@code FILE_ID} accepts up to 100 chars; the refactor uses
     *  short decimal strings. */
    @Id
    @Column(name = "FILE_ID", length = 100, nullable = false)
    private String fileId;

    /** Original filename the user picked in the picker — preserved for
     *  the display in {@code ApprovalDetailsDialog}. */
    @Column(name = "ORG_FILE_NM", length = 500, nullable = false)
    private String orgFileNm;

    /** Tenant-scoped filename on disk — composed by the service as
     *  {@code apprSeqNo + "_" + fileId + "_" + sanitiseOriginalFilename(orgFileNm)}
     *  so the per-approval folder never collides on duplicate names. */
    @Column(name = "SAVE_FILE_NM", length = 500, nullable = false)
    private String saveFileNm;

    /** Optional free-text note — unused by Internal Transfer today;
     *  carried for legacy parity. */
    @Column(name = "NOTE", length = 255)
    private String note;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    /** Display name of the registering user — stamped at insert time
     *  from {@code TenantContext.currentUserNm()}. Legacy parity:
     *  the popup renders this next to the file chip. */
    @Column(name = "REG_NM", length = 100, nullable = false)
    private String regNm;

    /** Byte count as a decimal string — legacy storage type
     *  ({@code VARCHAR(100)}). Null when unknown (e.g. an external
     *  stream without {@code Content-Length}). */
    @Column(name = "FILE_SIZE", length = 100)
    private String fileSize;
}
