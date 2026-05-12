package com.kosign.wecms.domain.setting.vasaccount.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountExcelFieldError;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountExcelImportRequest;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountExcelImportResult;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountExcelPreview;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountExcelPreviewRow;
import com.kosign.wecms.domain.setting.vasaccount.entity.BusiVasAcctInfm;
import com.kosign.wecms.domain.setting.vasaccount.repository.BusiVasAcctInfmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Settings &gt; VAS Account — Excel template, preview, and import.
 * Mirrors the legacy {@code comm_0217_002} family
 * ({@code _r001} parse / validate, {@code _c001} save — see JEX
 * citations in {@code docs/refactor-wecms/features/vas-account.md §2}).
 *
 * <p>Scope refactor vs. legacy (PO call 2026-05-11): the UI no longer
 * surfaces Type, English Name, Notes, or Status. The backend defaults
 * Type to {@code "0"} (Dual) on Create, leaves English Name / Notes
 * null, leaves Status Active.
 *
 * <p>"Download Template" is NOT served by this service. The legacy
 * project ships pre-made XLSX templates under
 * {@code WECMS_STATIC/web/file/excel/VN/WeCMS_VAS_Accounts_Bulk_Upload_{EN,KO,VI}.xlsx};
 * the refactor mirrors that approach by copying the three files into
 * {@code wecms-web-main/public/file/excel/VN/} and the frontend's
 * download button is a plain anchor + locale-aware URL (see
 * {@code components/settings/vas-account/ExcelUploadDialog.tsx}).
 *
 * <p>Two entry points:
 * <ul>
 *   <li>{@link #preview(MultipartFile)} — parses the uploaded XLSX,
 *       validates each row (length, format, in-file dup, against-DB
 *       dup), and returns a {@link VasAccountExcelPreview} for the
 *       frontend to render.</li>
 *   <li>{@link #import_(VasAccountExcelImportRequest)} — persists the
 *       caller-supplied rows under one transaction. Append mode rejects
 *       duplicates against existing live rows; Replace mode soft-
 *       deletes every live row first, then inserts the payload.</li>
 * </ul>
 *
 * <p>Audit: one row per import event via the shared
 * {@link AuditLogService} with {@code wsvc_id = "vas-account"}. Append
 * mode emits {@code audit.vas_account.create.detail}; Replace mode
 * emits a dedicated {@code audit.vas_account.excel_replace.detail}
 * key so the audit ledger distinguishes the wipe-and-reload case.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VasAccountExcelService {

    /** Maximum rows the importer accepts in one upload. Mirrors the
     *  legacy {@code comm_0217_002_r001_act.jsp:183} 500-row cap. */
    public static final int MAX_ROWS = 500;

    /** Real header row position in the template (0-based POI index).
     *  The legacy templates ship a 3-row preamble:
     *  <ul>
     *    <li>Row 0 — title banner ("LIST OF CHART OF ACCOUNTS" — merged across A:G).</li>
     *    <li>Row 1 — empty spacer.</li>
     *    <li>Row 2 — column headers (No., Account Code, Account Name, ...).</li>
     *    <li>Row 3+ — data.</li>
     *  </ul>
     *  Mirrors legacy {@code comm_0217_002_r001_act.jsp:106} which reads
     *  row index 2 as the header and starts the data loop at index 3.
     *  Verified against the shipped EN/KO/VI templates at
     *  {@code WECMS DEV/WECMS_STATIC/web/file/excel/VN/WeCMS_VAS_Accounts_Bulk_Upload_*.xlsx}. */
    private static final int HEADER_ROW_INDEX = 2;
    private static final int FIRST_DATA_ROW_INDEX = 3;

    /** Column positions in the legacy 7-column template (matches the
     *  customer-facing templates the legacy project ships). The refactor
     *  only consumes columns B (Account Code) and C (Account Name); the
     *  other 5 columns (No. / Type / English / Description / Status) are
     *  ignored — the refactor UI dropped them per the PO call 2026-05-11. */
    private static final int COL_VAS_ACCT_CD = 1;
    private static final int COL_VAS_ACCT_NM = 2;

    /** Accepted header labels for the Account Code column (B / index 1).
     *  Locale-aware: a customer who downloaded the KO or VI template
     *  can upload it as-is. Matches the EN / KO / VI label sets in
     *  legacy {@code comm_0217_002_r001_act.jsp:57-86}. The bonus
     *  "VAS Code" entry covers any future refactor template that picks
     *  the user-facing UI label instead of the legacy one. */
    private static final java.util.Set<String> HEADER_LABELS_CODE = java.util.Set.of(
            "Account Code",  // EN
            "VAS Code",      // refactor UI label (kept for forward-compat)
            "계정코드",         // KO
            "Tài khoản"      // VI
    );

    /** Accepted header labels for the Account Name column (C / index 2).
     *  Same EN / KO / VI parity as {@link #HEADER_LABELS_CODE}. */
    private static final java.util.Set<String> HEADER_LABELS_NAME = java.util.Set.of(
            "Account Name",      // EN
            "VAS Account Name",  // refactor UI label (kept for forward-compat)
            "계정과목명",           // KO
            "Tên tài khoản"      // VI
    );

    /** Field id constants — match the frontend {@code col.id} so the
     *  preview UI can map errors back to the right cell. */
    private static final String FIELD_VAS_ACCT_CD = "vas_acct_cd";
    private static final String FIELD_VAS_ACCT_NM = "vas_acct_nm";

    /** Per-cell error category codes — see
     *  {@link VasAccountExcelFieldError} class Javadoc for the
     *  contract. The Reset/Replace upload mode ignores
     *  {@link #ERR_CODE_DUP_DB} both on the frontend (client-side
     *  re-derivation when the option toggles) and on the import path
     *  (the wipe clears the conflict before insert). */
    private static final String ERR_CODE_REQUIRED = "required";
    private static final String ERR_CODE_LENGTH   = "length";
    private static final String ERR_CODE_FORMAT   = "format";
    private static final String ERR_CODE_DUP_FILE = "dup_file";
    private static final String ERR_CODE_DUP_DB   = "dup_db";

    /** Default Type assigned on import — UI no longer surfaces Type
     *  (PO call 2026-05-11). Mirrors {@link VasAccountService#DEFAULT_VAS_ACCT_TYPE}. */
    private static final String DEFAULT_VAS_ACCT_TYPE = "0";
    private static final String DEFAULT_STTS         = "A";
    private static final String DEL_YN_LIVE          = "N";
    private static final String DEL_YN_DELETED       = "Y";

    /** Audit message keys. */
    private static final String AUDIT_WSVC_ID                  = "vas-account";
    private static final String AUDIT_ACTIVITY_KEY             = "audit.vas_account.activity";
    private static final String AUDIT_EXCEL_APPEND_DETAIL_KEY  = "audit.vas_account.excel_append.detail";
    private static final String AUDIT_EXCEL_REPLACE_DETAIL_KEY = "audit.vas_account.excel_replace.detail";
    private static final String REF_NO_BUSI_VAS_ACCT_INFM      = "BUSI_VAS_ACCT_INFM";

    /** Validation message keys (per-field, resolved server-side via
     *  {@link MessageResolver} so the preview tooltip text follows the
     *  caller's Accept-Language). */
    private static final String MSG_KEY_CODE_REQUIRED  = "settings.vas_account.excel.code_required";
    private static final String MSG_KEY_CODE_LENGTH    = "settings.vas_account.excel.code_length";
    private static final String MSG_KEY_CODE_FORMAT    = "settings.vas_account.excel.code_format";
    private static final String MSG_KEY_CODE_DUP_FILE  = "settings.vas_account.excel.code_dup_file";
    private static final String MSG_KEY_CODE_DUP_DB    = "settings.vas_account.excel.code_dup_db";
    private static final String MSG_KEY_NAME_REQUIRED  = "settings.vas_account.excel.name_required";
    private static final String MSG_KEY_NAME_MAX       = "settings.vas_account.excel.name_max";

    private static final DateTimeFormatter DTM14_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final BusiVasAcctInfmRepository repository;
    private final VasAcctSeqNoGenerator     seqGen;
    private final AuditLogService           auditLogService;
    private final MessageResolver           messages;

    /* =================================================================
     *  Preview
     * ================================================================= */

    /**
     * Parse and validate the uploaded Excel file. Each row is checked
     * independently — invalid rows are still returned so the UI can
     * highlight the offending cells.
     *
     * <p>Validation per cell:
     * <ul>
     *   <li>{@code vas_acct_cd}: required; 3-15 chars; alphanumeric +
     *       underscore; not duplicated within the file; not duplicated
     *       against the current tenant's live rows.</li>
     *   <li>{@code vas_acct_nm}: required; ≤ 50 chars.</li>
     * </ul>
     *
     * @throws BusinessException {@code WBE2041} when the file has &gt;
     *         {@link #MAX_ROWS} rows.
     * @throws BusinessException {@code WBE2042} when the header row
     *         doesn't match the expected template.
     * @throws BusinessException {@code WBE2043} on a corrupt / unreadable
     *         workbook (wrong extension, non-XLSX bytes, etc.).
     */
    @Transactional(readOnly = true)
    public VasAccountExcelPreview preview(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("WBE_VALIDATION", "file: required");
        }
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) {
            throw new IllegalStateException("TenantContext is not populated");
        }

        try (InputStream in = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(in)) {

            Sheet sheet = workbook.getSheetAt(0);
            validateHeader(sheet);

            int lastRow = sheet.getLastRowNum();
            int dataRows = Math.max(0, lastRow - FIRST_DATA_ROW_INDEX + 1);
            if (dataRows > MAX_ROWS) {
                throw new BusinessException("WBE2041", String.valueOf(MAX_ROWS));
            }

            Locale locale = currentLocale();
            // Cache the live-codes set for the tenant once — looking up
            // 500 rows individually would hammer the DB. Single query +
            // in-memory contains() check stays correct because we run
            // inside @Transactional(readOnly = true).
            Set<String> liveCodes = loadLiveCodes(bizKey);
            Set<String> seenInFile = new HashSet<>();

            List<VasAccountExcelPreviewRow> rows = new ArrayList<>();
            int completed = 0;
            int errored = 0;

            for (int r = FIRST_DATA_ROW_INDEX; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (isRowEmpty(row)) continue;

                String code = readCell(row, COL_VAS_ACCT_CD);
                String name = readCell(row, COL_VAS_ACCT_NM);

                List<VasAccountExcelFieldError> errors = new ArrayList<>();
                validateCode(code, errors, seenInFile, liveCodes, locale);
                validateName(name, errors, locale);

                boolean valid = errors.isEmpty();
                if (valid) {
                    completed++;
                    // Stage code in `seenInFile` ONLY when this row's
                    // code passed every earlier check — that way a
                    // second occurrence of an already-invalid code
                    // still flags as DUP_FILE rather than silently
                    // becoming the "canonical" instance.
                    seenInFile.add(code.trim());
                } else {
                    errored++;
                }
                // Excel row index is 1-based for users; r is 0-based in POI.
                rows.add(new VasAccountExcelPreviewRow(
                        r + 1, code, name, valid, errors));
            }

            log.info("vas-account excel preview parsed bizKey={} total={} completed={} errored={}",
                    bizKey, rows.size(), completed, errored);
            return new VasAccountExcelPreview(rows, rows.size(), completed, errored);

        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            log.warn("vas-account excel preview failed to parse file={}", file.getOriginalFilename(), ex);
            throw new BusinessException("WBE2043");
        }
    }

    /* =================================================================
     *  Import
     * ================================================================= */

    /**
     * Persist the caller-supplied preview rows. Re-validates against
     * the current DB state for race-safety — never trusts the
     * {@code valid} flag from the preview pass.
     *
     * <p>Append mode ({@code upldOptn = "A"}): inserts each row; if
     * any row's code already exists in the tenant's live set OR
     * conflicts within the payload, the whole batch fails with
     * {@code WBE0003}. Atomic — partial insert on conflict is not
     * permitted (mirrors legacy {@code comm_0217_002_c001_act.jsp:147-165}).
     *
     * <p>Replace mode ({@code upldOptn = "R"}): every live row for the
     * tenant is soft-deleted first, then the payload rows are inserted.
     * Both happen inside the same transaction so a failed insert rolls
     * back the wipe.
     */
    @Transactional
    public VasAccountExcelImportResult import_(VasAccountExcelImportRequest req) {
        if (req.rows() == null || req.rows().isEmpty()) {
            throw new BusinessException("WBE_VALIDATION", "rows: at least one row is required");
        }
        if (req.rows().size() > MAX_ROWS) {
            throw new BusinessException("WBE2041", String.valueOf(MAX_ROWS));
        }
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        if (bizKey == null || bizKey.isBlank() || userId == null || userId.isBlank()) {
            throw new IllegalStateException("TenantContext is not populated");
        }

        boolean replace = "R".equals(req.upldOptn());
        Locale locale = currentLocale();
        String nowDtm = OffsetDateTime.now(ZoneOffset.UTC).format(DTM14_FORMAT);

        // Re-run the validation pass on the wire payload using the
        // current DB state. Replace mode skips DB-dup check because
        // we're about to wipe the tenant first — only the in-file dup
        // check applies. Append mode runs both.
        Set<String> liveCodes = replace ? Set.of() : loadLiveCodes(bizKey);
        Set<String> seenInFile = new HashSet<>();
        for (int i = 0; i < req.rows().size(); i++) {
            VasAccountExcelImportRequest.Row row = req.rows().get(i);
            List<VasAccountExcelFieldError> errors = new ArrayList<>();
            validateCode(row.vasAcctCd(), errors, seenInFile, liveCodes, locale);
            validateName(row.vasAcctNm(), errors, locale);
            if (!errors.isEmpty()) {
                // Atomic batch: fail the whole import the moment any
                // row trips re-validation. Surface the first error so
                // the frontend can route the user back to the preview
                // (the response carries the row index for context).
                throw new BusinessException(
                        "WBE_VALIDATION",
                        "row " + (i + 1) + ": " + errors.get(0).message());
            }
            seenInFile.add(row.vasAcctCd().trim());
        }

        int replacedCount = 0;
        if (replace) {
            replacedCount = repository.softDeleteAllLiveForTenant(bizKey, userId, nowDtm);
        }

        for (VasAccountExcelImportRequest.Row row : req.rows()) {
            long seqNo = seqGen.next(bizKey);
            BusiVasAcctInfm entity = new BusiVasAcctInfm();
            entity.setBizKey(bizKey);
            entity.setVasAcctSeqNo(seqNo);
            entity.setVasAcctCd(row.vasAcctCd().trim());
            entity.setVasAcctNm(row.vasAcctNm().trim());
            entity.setVasAcctType(DEFAULT_VAS_ACCT_TYPE);
            entity.setStts(DEFAULT_STTS);
            entity.setDelYn(DEL_YN_LIVE);
            entity.setRegId(userId);
            entity.setRegDtm(nowDtm);
            entity.setModId(userId);
            entity.setModDtm(nowDtm);
            repository.save(entity);
        }

        int insertedCount = req.rows().size();
        auditLogService.append(AuditLogContext.builder()
                .wsvcId(AUDIT_WSVC_ID)
                .refNo(REF_NO_BUSI_VAS_ACCT_INFM)
                .refNo1(String.valueOf(insertedCount))
                .refNo2(replace ? "REPLACE" : "APPEND")
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(replace ? AUDIT_EXCEL_REPLACE_DETAIL_KEY : AUDIT_EXCEL_APPEND_DETAIL_KEY)
                .build());

        log.info("vas-account excel import bizKey={} mode={} inserted={} replaced={}",
                bizKey, req.upldOptn(), insertedCount, replacedCount);
        return new VasAccountExcelImportResult(insertedCount, replacedCount);
    }

    /* =================================================================
     *  Validation
     * ================================================================= */

    private void validateCode(
            String raw,
            List<VasAccountExcelFieldError> errors,
            Set<String> seenInFile,
            Set<String> liveCodes,
            Locale locale) {
        String code = raw == null ? "" : raw.trim();
        if (code.isEmpty()) {
            errors.add(new VasAccountExcelFieldError(
                    FIELD_VAS_ACCT_CD, ERR_CODE_REQUIRED, msg(MSG_KEY_CODE_REQUIRED, locale)));
            return;
        }
        if (code.length() < 3 || code.length() > 15) {
            errors.add(new VasAccountExcelFieldError(
                    FIELD_VAS_ACCT_CD, ERR_CODE_LENGTH, msg(MSG_KEY_CODE_LENGTH, locale)));
            return;
        }
        if (!code.matches("^[A-Za-z0-9_]+$")) {
            errors.add(new VasAccountExcelFieldError(
                    FIELD_VAS_ACCT_CD, ERR_CODE_FORMAT, msg(MSG_KEY_CODE_FORMAT, locale)));
            return;
        }
        if (seenInFile.contains(code)) {
            errors.add(new VasAccountExcelFieldError(
                    FIELD_VAS_ACCT_CD, ERR_CODE_DUP_FILE, msg(MSG_KEY_CODE_DUP_FILE, locale)));
            return;
        }
        if (liveCodes.contains(code)) {
            errors.add(new VasAccountExcelFieldError(
                    FIELD_VAS_ACCT_CD, ERR_CODE_DUP_DB, msg(MSG_KEY_CODE_DUP_DB, locale)));
        }
    }

    private void validateName(
            String raw,
            List<VasAccountExcelFieldError> errors,
            Locale locale) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            errors.add(new VasAccountExcelFieldError(
                    FIELD_VAS_ACCT_NM, ERR_CODE_REQUIRED, msg(MSG_KEY_NAME_REQUIRED, locale)));
            return;
        }
        if (name.length() > 50) {
            errors.add(new VasAccountExcelFieldError(
                    FIELD_VAS_ACCT_NM, ERR_CODE_LENGTH, msg(MSG_KEY_NAME_MAX, locale)));
        }
    }

    /**
     * Validate the column headers on row {@link #HEADER_ROW_INDEX}.
     * The template ships a 3-row preamble (title + spacer + header) —
     * we look at the third row and confirm columns B (1) and C (2)
     * carry one of the accepted EN/KO/VI labels.
     *
     * <p>Header matching is case-insensitive + whitespace-tolerant
     * (the customer's templates have been edited by hand for years
     * and accumulated trailing whitespace, doubled spaces, and case
     * drift). The set-membership check accepts a label as long as ITS
     * case-insensitive collapsed-whitespace form matches any of the
     * accepted variants.
     *
     * <p>The two cells at columns B + C are the load-bearing contract;
     * the other 5 columns (No. / Type / English / Description /
     * Status) are NOT validated — the refactor ignores them.
     */
    private void validateHeader(Sheet sheet) {
        Row header = sheet.getRow(HEADER_ROW_INDEX);
        if (header == null) {
            throw new BusinessException("WBE2042", "<empty>");
        }
        String c0 = readCell(header, COL_VAS_ACCT_CD);
        String c1 = readCell(header, COL_VAS_ACCT_NM);
        if (!matchesAny(c0, HEADER_LABELS_CODE) || !matchesAny(c1, HEADER_LABELS_NAME)) {
            throw new BusinessException("WBE2042",
                    "expected one of " + HEADER_LABELS_CODE + " in column B and "
                            + HEADER_LABELS_NAME + " in column C, got [" + c0 + ", " + c1 + "]");
        }
    }

    /** Case-insensitive + whitespace-collapsing set lookup. */
    private static boolean matchesAny(String raw, java.util.Set<String> accepted) {
        if (raw == null) return false;
        String normalised = raw.trim().replaceAll("\\s+", " ");
        for (String label : accepted) {
            if (normalised.equalsIgnoreCase(label)) return true;
        }
        return false;
    }

    /* =================================================================
     *  Repository / cell helpers
     * ================================================================= */

    private Set<String> loadLiveCodes(String bizKey) {
        return new HashSet<>(repository.findLiveCodesByBizKey(bizKey));
    }

    private static boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int c = 0; c < row.getLastCellNum(); c++) {
            String v = readCell(row, c);
            if (!v.isEmpty()) return false;
        }
        return true;
    }

    /**
     * Read a single cell as a trimmed string. Coerces numeric cells
     * via {@link DataFormatter}-style conversion so an integer
     * {@code 111} in Excel reads as {@code "111"}, not {@code "111.0"}.
     * Returns {@code ""} for missing cells.
     */
    private static String readCell(Row row, int col) {
        if (row == null) return "";
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) type = cell.getCachedFormulaResultType();
        return switch (type) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    // Defensive — VAS Code shouldn't be a date. Render
                    // as an ISO-like string so validation flags it.
                    yield cell.getDateCellValue().toString();
                }
                double n = cell.getNumericCellValue();
                long l = (long) n;
                yield (n == (double) l)
                        ? Long.toString(l)
                        : BigDecimal.valueOf(n).stripTrailingZeros().toPlainString();
            }
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case BLANK, _NONE, ERROR, FORMULA -> "";
        };
    }

    private Locale currentLocale() {
        var lang = TenantContext.currentLangCd();
        return lang == null ? Locale.ENGLISH : lang.toLocale();
    }

    private String msg(String key, Locale locale) {
        return messages.resolve(key, locale);
    }
}
