package com.kosign.wecms.domain.payment.common;

import com.kosign.wecms.common.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;

/**
 * Guards the "original Excel file is mandatory at save" rule shared by all five
 * payment Excel-upload modules (Internal Transfer / Vendor / Expense / Payroll /
 * Overseas).
 *
 * <p><b>Legacy parity.</b> Every legacy create service re-receives the uploaded
 * workbook alongside the reviewed rows and rejects the request outright when it is
 * missing:
 * <ul>
 *   <li>{@code pymn_0001_002_c001_act.jsp:54-55} — {@code validateRequired(FILE_DATA)}
 *       + {@code validateFile(FILE_DATA)} (same shape in {@code pymn_0003_002_c001},
 *       {@code pymn_0005_002_c001}, {@code pymn_0009_002_c001}).</li>
 *   <li>{@code pymn_0007_003_c001_act.jsp:48} — {@code validateRequired(FULL_PATH)}
 *       for Overseas.</li>
 * </ul>
 * The file is not incidental: legacy stores it on the batch
 * ({@code ORG_FILE_NM} / {@code SAVE_FILE_NM} / {@code FILE_SIZE}, surfaced as the
 * scheduled list's <em>File Name</em> column) and pushes it into
 * {@code ERP_TRNS_HSTR} via {@code ErpTrnsHisHelper.erpTrnsHisUploader}. The refactor
 * create paths keep both writes inside an {@code if (file != null)} branch, so a
 * fileless request used to yield a batch with a null file name and <b>no ERP audit
 * row</b> — a state legacy cannot produce.
 *
 * <p>The multipart part itself stays {@code required = false} at the controller so a
 * missing file surfaces as the standard {@code ApiResponse} business envelope (as
 * legacy's {@code validateRequired} does) instead of a framework-level
 * {@code MissingServletRequestPartException} mapped to {@code WBE9999}.
 *
 * <p>Only the Excel-upload create paths call this. Server-side batch builders that
 * legitimately have no workbook (e.g. the HAENGSUNG ERP refresh importer, which
 * writes {@code SCDUL_PYMN_SUMR_DETL} rows directly) never route through
 * {@code createDraft} and are unaffected.
 */
public final class PaymentUploadFileGuard {

    private PaymentUploadFileGuard() {
    }

    /**
     * @throws BusinessException {@code WBE_VALIDATION} when the uploaded workbook is
     *         absent or zero-length — legacy {@code validateRequired}/{@code validateFile}.
     */
    public static void requireUploadedFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("WBE_VALIDATION", "file_data: required");
        }
    }
}
