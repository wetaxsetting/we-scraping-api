package com.kosign.wecms.domain.setting.purposesettings.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeExcelFieldError;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeExcelImportRequest;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeExcelImportResult;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeExcelPreview;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeExcelPreviewRow;
import com.kosign.wecms.domain.setting.purposesettings.entity.BusiUsagSet;
import com.kosign.wecms.domain.setting.purposesettings.repository.PurposeSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Settings &gt; Purpose — Excel template, preview, and import. Mirrors
 * the legacy {@code comm_0187_004} family ({@code _r001} parse +
 * validate, {@code _c001} commit) and follows the VAS Account
 * precedent ({@code VasAccountExcelService}) for the two-step
 * preview → import contract.
 *
 * <p>"Download Template" is NOT served by this service. Pre-made XLSX
 * templates ship under {@code wecms-web-main/public/templates/purpose/}
 * and the frontend's download button is a plain anchor + locale-aware
 * URL (legacy parity).
 *
 * <p>Two entry points:
 * <ul>
 *   <li>{@link #preview(MultipartFile)} — parses the XLSX, validates
 *       each row (required / length / in-file dup / DB dup / parent
 *       validation with 2-level cap), returns
 *       {@link PurposeExcelPreview} for UI rendering.</li>
 *   <li>{@link #import_(PurposeExcelImportRequest)} — persists the
 *       caller-supplied rows inside one transaction. Roots insert
 *       first so children can resolve their parent's freshly-
 *       generated {@code use_usag_cd}. Atomic — any row failure
 *       rolls back the whole batch.</li>
 * </ul>
 *
 * <h2>Validation rules (legacy parity)</h2>
 *
 * <ul>
 *   <li>{@code hand_use_usag_cd}: required, ≤ 20 chars; case-insensitive
 *       uniqueness in-file (UPPER comparison) + against active live
 *       rows in the tenant's DB. Mirrors
 *       {@code comm_0187_004_r001_act.jsp:140-164}.</li>
 *   <li>{@code use_usag_nm}: required, ≤ 255 chars.</li>
 *   <li>{@code use_usag_cont}: optional, ≤ 500 chars.</li>
 *   <li>{@code parent_use_usag_cd}: optional, ≤ 20 chars. Two-pass
 *       resolution:
 *       <ol>
 *         <li>Try to find the parent's hand code as a root in the
 *             current file (rows in the upload whose own
 *             {@code parent_use_usag_cd} is empty). If found, ok.</li>
 *         <li>Otherwise, look up an active row in the DB. If found,
 *             validate the DB row is itself a level-1 row
 *             ({@code parentUseUsagCd IS NULL}) — anything else would
 *             create a three-level hierarchy.</li>
 *       </ol>
 *       Mirrors {@code comm_0187_004_r001_act.jsp:167-208}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurposeExcelService {

    /** Maximum rows the importer accepts in one upload. Mirrors the
     *  legacy {@code comm_0187_004_r001_act.jsp:220} 500-row cap. */
    public static final int MAX_ROWS = 500;

    /** Header row position (0-based POI index). The shipped Purpose
     *  template (under {@code wecms-web-main/public/templates/purpose/})
     *  reserves row 1 for the centred {@code "PURPOSE"} title cell and
     *  row 2 as a blank spacer, with the real column headers on row 3
     *  and data starting at row 4. POI indexes that as
     *  {@code HEADER_ROW_INDEX = 2} / {@code FIRST_DATA_ROW_INDEX = 3}.
     *  Mirrors the VAS Account precedent ({@code VasAccountExcelService}
     *  also uses row 2). */
    private static final int HEADER_ROW_INDEX     = 2;
    private static final int FIRST_DATA_ROW_INDEX = 3;

    /** Column positions in the 4-column template. */
    private static final int COL_CODE        = 0;
    private static final int COL_NAME        = 1;
    private static final int COL_DESCRIPTION = 2;
    private static final int COL_PARENT      = 3;

    /** Accepted header labels per column. Locale-aware — matches the
     *  EN/KO/VI label sets in legacy
     *  {@code comm_0187_004_r001_act.jsp:49-68}. The legacy uses
     *  {@code startsWith} match so trailing decorations (e.g.
     *  {@code "Purpose Code *"}) still pass; we mirror that. */
    private static final List<String> HEADER_LABELS_CODE = List.of(
            "Purpose Code",  "용도 코드",  "Mã mục đích");
    private static final List<String> HEADER_LABELS_NAME = List.of(
            "Purpose Name",  "용도명",    "Tên mục đích");
    private static final List<String> HEADER_LABELS_DESCRIPTION = List.of(
            "Purpose Description", "용도 설명", "Mô tả mục đích");
    private static final List<String> HEADER_LABELS_PARENT = List.of(
            "Parent Purpose", "주요 용도", "Mục đích chính",
            // Common alternates the customer-edited templates carry.
            "Parent",         "상위 용도",  "Mục đích gốc");

    /** Field id constants — match the frontend {@code col.id} so the
     *  preview UI can map errors back to the right cell. */
    private static final String FIELD_CODE        = "hand_use_usag_cd";
    private static final String FIELD_NAME        = "use_usag_nm";
    private static final String FIELD_DESCRIPTION = "use_usag_cont";
    private static final String FIELD_PARENT      = "parent_use_usag_cd";

    /** Per-cell error category codes. */
    private static final String ERR_REQUIRED         = "required";
    private static final String ERR_LENGTH           = "length";
    private static final String ERR_DUP_FILE         = "dup_file";
    private static final String ERR_DUP_DB           = "dup_db";
    private static final String ERR_PARENT_NOT_FOUND = "parent_not_found";
    private static final String ERR_PARENT_NOT_ROOT  = "parent_not_root";

    /** Length caps (mirror the backend Bean Validation on Create). */
    private static final int MAX_CODE_LEN        = 20;
    private static final int MAX_NAME_LEN        = 255;
    private static final int MAX_DESCRIPTION_LEN = 500;
    private static final int MAX_PARENT_LEN      = 20;

    /** Per-feature i18n keys for cell-level error messages (resolved
     *  server-side so the preview tooltip text follows the caller's
     *  Accept-Language). */
    private static final String MSG_CODE_REQUIRED   = "settings.purpose.excel.code_required";
    private static final String MSG_CODE_LENGTH     = "settings.purpose.excel.code_length";
    private static final String MSG_CODE_DUP_FILE   = "settings.purpose.excel.code_dup_file";
    private static final String MSG_CODE_DUP_DB     = "settings.purpose.excel.code_dup_db";
    private static final String MSG_NAME_REQUIRED   = "settings.purpose.excel.name_required";
    private static final String MSG_NAME_LENGTH     = "settings.purpose.excel.name_length";
    private static final String MSG_DESC_LENGTH     = "settings.purpose.excel.description_length";
    private static final String MSG_PARENT_LENGTH   = "settings.purpose.excel.parent_length";
    private static final String MSG_PARENT_NOT_FOUND = "settings.purpose.excel.parent_not_found";
    private static final String MSG_PARENT_NOT_ROOT  = "settings.purpose.excel.parent_not_root";

    /** Audit-log keys (reuse the existing audit keys allocated in the
     *  initial Phase 2 service). */
    private static final String AUDIT_WSVC_ID           = "purpose-settings";
    private static final String REF_NO_TABLE            = "BUSI_USAG_SET_INFM";
    private static final String AUDIT_ACTIVITY_KEY      = "audit.purpose_settings.activity";
    private static final String AUDIT_UPLOAD_DETAIL_KEY = "audit.purpose_settings.upload.detail";

    private static final DateTimeFormatter MOD_DTM_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PurposeSettingsRepository repo;
    private final AuditLogService           auditLogService;
    private final MessageResolver           messages;

    /* =================================================================
     *  Preview
     * ================================================================= */

    /**
     * Parse + validate the uploaded XLSX. Errors are reported per cell
     * with pre-localized messages; invalid rows still ship in the
     * preview response so the UI can highlight the offending cells.
     *
     * @throws BusinessException {@code WBE_VALIDATION} when no file
     *         was attached.
     * @throws BusinessException {@code WBE2041} when the file exceeds
     *         {@link #MAX_ROWS} data rows.
     * @throws BusinessException {@code WBE2042} when the header row
     *         doesn't match the expected template.
     * @throws BusinessException {@code WBE2043} on corrupt / unreadable
     *         workbook bytes (wrong extension, encrypted, etc.).
     */
    @Transactional(readOnly = true)
    public PurposeExcelPreview preview(MultipartFile file) {
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

            // Load the live state once — looking up 500 rows in DB per
            // row would hammer the connection. Two views needed:
            // (1) UPPER-cased hand codes for the dup-against-DB check;
            // (2) the live row map so parent lookups can verify level-1.
            List<BusiUsagSet> live = repo.findActiveForTenant(
                    bizKey,
                    PurposeSettingsService.RESERVED_SYSTEM_CODES,
                    "");
            Set<String> liveCodes = new HashSet<>();
            Set<String> liveRootCodes = new HashSet<>();
            for (BusiUsagSet r : live) {
                String hc = r.getHandUseUsagCd();
                if (hc == null) continue;
                String up = hc.trim().toUpperCase(Locale.ROOT);
                liveCodes.add(up);
                if (r.getParentUseUsagCd() == null) liveRootCodes.add(up);
            }

            // Pass 1 — collect every row + flag in-file dups; capture
            // each row's parent (empty for roots) so pass 2 can resolve.
            List<RowStage> staged = new ArrayList<>();
            Map<String, Integer> handCdSeen = new HashMap<>();
            Set<String> inFileRoots = new HashSet<>();
            for (int r = FIRST_DATA_ROW_INDEX; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (isRowEmpty(row)) continue;

                String code = readCell(row, COL_CODE);
                String name = readCell(row, COL_NAME);
                String desc = readCell(row, COL_DESCRIPTION);
                String parent = readCell(row, COL_PARENT);

                List<PurposeExcelFieldError> errors = new ArrayList<>();
                validateCode(code, errors, handCdSeen, liveCodes, locale, r);
                validateName(name, errors, locale);
                validateDescription(desc, errors, locale);
                validateParentLengthOnly(parent, errors, locale);

                // Stage the row even if it has errors — pass 2 still
                // wants to know its declared parent so the parent
                // referenced in OTHER rows can resolve correctly.
                String upCode = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
                String upParent = parent == null ? "" : parent.trim().toUpperCase(Locale.ROOT);
                if (!upCode.isEmpty() && upParent.isEmpty()) {
                    inFileRoots.add(upCode);
                }
                staged.add(new RowStage(r + 1, code, name, desc, parent, errors, upCode, upParent));
            }

            // Pass 2 — resolve parents using the in-file root set + the
            // live DB roots. A parent reference resolves when it
            // matches EITHER:
            //   • an in-file root row's hand code (UPPER), OR
            //   • an active live row whose own parent is NULL.
            // Otherwise the row gets `parent_not_found`. When the
            // parent matches a DB row that is itself a level-2 child,
            // we flag `parent_not_root` (the legacy 2-level cap).
            Map<String, BusiUsagSet> liveByHandCd = new HashMap<>();
            for (BusiUsagSet r : live) {
                if (r.getHandUseUsagCd() != null) {
                    liveByHandCd.put(
                            r.getHandUseUsagCd().trim().toUpperCase(Locale.ROOT),
                            r);
                }
            }

            List<PurposeExcelPreviewRow> rows = new ArrayList<>();
            int completed = 0;
            int errored = 0;
            for (RowStage s : staged) {
                if (!s.upParent.isEmpty()) {
                    boolean parentIsInFileRoot = inFileRoots.contains(s.upParent);
                    boolean parentIsDbRoot = liveRootCodes.contains(s.upParent);
                    if (!parentIsInFileRoot && !parentIsDbRoot) {
                        BusiUsagSet dbCandidate = liveByHandCd.get(s.upParent);
                        if (dbCandidate == null) {
                            s.errors.add(new PurposeExcelFieldError(
                                    FIELD_PARENT, ERR_PARENT_NOT_FOUND,
                                    msg(MSG_PARENT_NOT_FOUND, locale, s.parentRaw)));
                        } else {
                            // Live row exists but isn't a level-1
                            // root — would create level-3 hierarchy.
                            s.errors.add(new PurposeExcelFieldError(
                                    FIELD_PARENT, ERR_PARENT_NOT_ROOT,
                                    msg(MSG_PARENT_NOT_ROOT, locale)));
                        }
                    }
                    // parentIsInFileRoot is enough on its own — in-
                    // file roots are level-1 by definition (we only
                    // added them to `inFileRoots` when their own
                    // parent was empty).
                }

                boolean valid = s.errors.isEmpty();
                if (valid) completed++;
                else errored++;
                rows.add(new PurposeExcelPreviewRow(
                        s.rowIndex, s.codeRaw, s.nameRaw, s.descRaw, s.parentRaw,
                        valid, s.errors));
            }

            log.info("purpose-settings excel preview bizKey={} total={} completed={} errored={}",
                    bizKey, rows.size(), completed, errored);
            return new PurposeExcelPreview(rows, rows.size(), completed, errored);

        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            log.warn("purpose-settings excel preview failed to parse file={}",
                    file.getOriginalFilename(), ex);
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
     * <p>Insert order: every root (level-1) row inserts first so its
     * generated {@code use_usag_cd} is available when child (level-2)
     * rows resolve their {@code parent_use_usag_cd}. Mirrors the
     * legacy {@code comm_0187_004_c001} two-pass commit.
     *
     * <p>Atomic — any row's PK-generation, duplicate, or parent-resolve
     * failure rolls back the entire batch (Spring @Transactional). The
     * service throws {@code WBE_VALIDATION} with the offending row's
     * 1-based index so the UI can route the user back to the preview.
     */
    @Transactional
    public PurposeExcelImportResult import_(PurposeExcelImportRequest req) {
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

        Locale locale = currentLocale();
        String nowDtm = OffsetDateTime.now(ZoneOffset.UTC).format(MOD_DTM_FORMAT);

        // Re-run validation against the LIVE state.
        List<BusiUsagSet> live = repo.findActiveForTenant(
                bizKey, PurposeSettingsService.RESERVED_SYSTEM_CODES, "");
        Set<String> liveCodes = new HashSet<>();
        Set<String> liveRootCodes = new HashSet<>();
        Map<String, BusiUsagSet> liveByHandCd = new HashMap<>();
        for (BusiUsagSet r : live) {
            String hc = r.getHandUseUsagCd();
            if (hc == null) continue;
            String up = hc.trim().toUpperCase(Locale.ROOT);
            liveCodes.add(up);
            liveByHandCd.put(up, r);
            if (r.getParentUseUsagCd() == null) liveRootCodes.add(up);
        }

        // In-file roots are rows in this payload whose `parentUseUsagCd`
        // is blank.
        Set<String> inFileRoots = new HashSet<>();
        for (PurposeExcelImportRequest.Row row : req.rows()) {
            String code = row.handUseUsagCd();
            String parent = row.parentUseUsagCd();
            if (code == null) continue;
            String upCode = code.trim().toUpperCase(Locale.ROOT);
            if (upCode.isEmpty()) continue;
            if (parent == null || parent.trim().isEmpty()) {
                inFileRoots.add(upCode);
            }
        }

        Map<String, Integer> handCdSeen = new HashMap<>();
        for (int i = 0; i < req.rows().size(); i++) {
            PurposeExcelImportRequest.Row row = req.rows().get(i);
            List<PurposeExcelFieldError> errors = new ArrayList<>();
            // Pass 0-based POI row index for the dup-file message so
            // it matches the preview-pass row numbering.
            validateCode(row.handUseUsagCd(), errors, handCdSeen, liveCodes, locale, i);
            validateName(row.useUsagNm(), errors, locale);
            validateDescription(row.useUsagCont(), errors, locale);
            validateParentLengthOnly(row.parentUseUsagCd(), errors, locale);

            String parent = row.parentUseUsagCd();
            if (parent != null && !parent.trim().isEmpty()) {
                String upParent = parent.trim().toUpperCase(Locale.ROOT);
                boolean parentIsInFileRoot = inFileRoots.contains(upParent);
                boolean parentIsDbRoot = liveRootCodes.contains(upParent);
                if (!parentIsInFileRoot && !parentIsDbRoot) {
                    BusiUsagSet dbCandidate = liveByHandCd.get(upParent);
                    if (dbCandidate == null) {
                        errors.add(new PurposeExcelFieldError(
                                FIELD_PARENT, ERR_PARENT_NOT_FOUND,
                                msg(MSG_PARENT_NOT_FOUND, locale, parent)));
                    } else {
                        errors.add(new PurposeExcelFieldError(
                                FIELD_PARENT, ERR_PARENT_NOT_ROOT,
                                msg(MSG_PARENT_NOT_ROOT, locale)));
                    }
                }
            }

            if (!errors.isEmpty()) {
                throw new BusinessException(
                        "WBE_VALIDATION",
                        "row " + (i + 1) + ": " + errors.get(0).message());
            }
        }

        // Two-pass commit: roots first so children can resolve
        // `parent_use_usag_cd` to the freshly-generated `use_usag_cd`.
        Map<String, String> systemCdByHandCdUpper = new HashMap<>();

        // Roots first.
        int inserted = 0;
        int maxDispOrd = repo.findMaxDispOrd(bizKey).orElse(0);
        for (PurposeExcelImportRequest.Row row : req.rows()) {
            String parent = row.parentUseUsagCd();
            if (parent != null && !parent.trim().isEmpty()) continue;
            BusiUsagSet saved = persistRow(
                    bizKey, userId, nowDtm,
                    row.handUseUsagCd(), row.useUsagNm(), row.useUsagCont(),
                    /* parentSystemCd */ null,
                    ++maxDispOrd);
            String upCode = row.handUseUsagCd().trim().toUpperCase(Locale.ROOT);
            systemCdByHandCdUpper.put(upCode, saved.getUseUsagCd());
            inserted++;
        }

        // Children second.
        for (PurposeExcelImportRequest.Row row : req.rows()) {
            String parent = row.parentUseUsagCd();
            if (parent == null || parent.trim().isEmpty()) continue;

            // Resolve the parent's system PK — either a row we just
            // inserted (in-file root) or a pre-existing live row.
            String upParent = parent.trim().toUpperCase(Locale.ROOT);
            String parentSystemCd = systemCdByHandCdUpper.get(upParent);
            if (parentSystemCd == null) {
                BusiUsagSet existing = liveByHandCd.get(upParent);
                if (existing != null) {
                    parentSystemCd = existing.getUseUsagCd();
                }
            }
            if (parentSystemCd == null) {
                // Race: parent vanished between preview and import.
                throw new BusinessException(
                        "WBE_VALIDATION",
                        "parent_use_usag_cd: not found (" + parent + ")");
            }

            persistRow(
                    bizKey, userId, nowDtm,
                    row.handUseUsagCd(), row.useUsagNm(), row.useUsagCont(),
                    parentSystemCd,
                    ++maxDispOrd);
            inserted++;
        }

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(AUDIT_WSVC_ID)
                .refNo(REF_NO_TABLE)
                .refNo1(String.valueOf(inserted))
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_UPLOAD_DETAIL_KEY)
                .msgArgs(new Object[]{ inserted })
                .build());

        log.info("purpose-settings excel import bizKey={} inserted={}", bizKey, inserted);
        return new PurposeExcelImportResult(inserted);
    }

    /* =================================================================
     *  Validation helpers
     * ================================================================= */

    private void validateCode(
            String raw,
            List<PurposeExcelFieldError> errors,
            Map<String, Integer> seen,
            Set<String> liveCodes,
            Locale locale,
            int currentZeroBasedIndex) {
        String code = raw == null ? "" : raw.trim();
        if (code.isEmpty()) {
            errors.add(new PurposeExcelFieldError(
                    FIELD_CODE, ERR_REQUIRED, msg(MSG_CODE_REQUIRED, locale)));
            return;
        }
        if (code.length() > MAX_CODE_LEN) {
            errors.add(new PurposeExcelFieldError(
                    FIELD_CODE, ERR_LENGTH,
                    msg(MSG_CODE_LENGTH, locale, String.valueOf(MAX_CODE_LEN))));
            return;
        }
        String up = code.toUpperCase(Locale.ROOT);
        Integer firstSeen = seen.get(up);
        if (firstSeen != null) {
            errors.add(new PurposeExcelFieldError(
                    FIELD_CODE, ERR_DUP_FILE,
                    msg(MSG_CODE_DUP_FILE, locale, String.valueOf(firstSeen + 1))));
            return;
        }
        seen.put(up, currentZeroBasedIndex);
        if (liveCodes.contains(up)) {
            errors.add(new PurposeExcelFieldError(
                    FIELD_CODE, ERR_DUP_DB, msg(MSG_CODE_DUP_DB, locale)));
        }
    }

    private void validateName(
            String raw, List<PurposeExcelFieldError> errors, Locale locale) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            errors.add(new PurposeExcelFieldError(
                    FIELD_NAME, ERR_REQUIRED, msg(MSG_NAME_REQUIRED, locale)));
            return;
        }
        if (name.length() > MAX_NAME_LEN) {
            errors.add(new PurposeExcelFieldError(
                    FIELD_NAME, ERR_LENGTH,
                    msg(MSG_NAME_LENGTH, locale, String.valueOf(MAX_NAME_LEN))));
        }
    }

    private void validateDescription(
            String raw, List<PurposeExcelFieldError> errors, Locale locale) {
        if (raw == null) return;
        if (raw.length() > MAX_DESCRIPTION_LEN) {
            errors.add(new PurposeExcelFieldError(
                    FIELD_DESCRIPTION, ERR_LENGTH,
                    msg(MSG_DESC_LENGTH, locale, String.valueOf(MAX_DESCRIPTION_LEN))));
        }
    }

    /** Only the length check fits inside the single-row scope. Whether
     *  the parent EXISTS is a 2-pass concern resolved by the caller. */
    private void validateParentLengthOnly(
            String raw, List<PurposeExcelFieldError> errors, Locale locale) {
        if (raw == null) return;
        if (raw.length() > MAX_PARENT_LEN) {
            errors.add(new PurposeExcelFieldError(
                    FIELD_PARENT, ERR_LENGTH,
                    msg(MSG_PARENT_LENGTH, locale, String.valueOf(MAX_PARENT_LEN))));
        }
    }

    /** Validate the column headers on {@link #HEADER_ROW_INDEX}.
     *  Legacy uses `startsWith` so trailing decorations
     *  ({@code "Purpose Code *"}, etc.) still pass. */
    private void validateHeader(Sheet sheet) {
        Row header = sheet.getRow(HEADER_ROW_INDEX);
        if (header == null) {
            throw new BusinessException("WBE2042", "<empty>");
        }
        String c0 = readCell(header, COL_CODE);
        String c1 = readCell(header, COL_NAME);
        String c2 = readCell(header, COL_DESCRIPTION);
        String c3 = readCell(header, COL_PARENT);
        if (!startsWithAny(c0, HEADER_LABELS_CODE)
                || !startsWithAny(c1, HEADER_LABELS_NAME)
                || !startsWithAny(c2, HEADER_LABELS_DESCRIPTION)
                || !startsWithAny(c3, HEADER_LABELS_PARENT)) {
            throw new BusinessException("WBE2042",
                    "expected [Purpose Code, Purpose Name, Purpose Description, Parent Purpose] "
                  + "got [" + c0 + ", " + c1 + ", " + c2 + ", " + c3 + "]");
        }
    }

    /* =================================================================
     *  Persistence helper
     * ================================================================= */

    /** Persist a single new purpose row. PK generation via the ported
     *  {@code wb_get_next_val} DB function. One-shot retry on
     *  {@link DataIntegrityViolationException} for PK collisions (rare
     *  under the function's bare MAX+1 — see V82 migration). */
    private BusiUsagSet persistRow(
            String bizKey, String userId, String modDtm,
            String handCd, String name, String description,
            String parentSystemCd, int dispOrd) {
        DataIntegrityViolationException lastDive = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            String useUsagCd = repo.nextUseUsagCd(bizKey);
            if (useUsagCd == null || useUsagCd.isBlank()) {
                throw new BusinessException("WBE9999");
            }
            BusiUsagSet entity = new BusiUsagSet();
            entity.setBizKey(bizKey);
            entity.setUseUsagCd(useUsagCd);
            entity.setHandUseUsagCd(handCd.trim());
            entity.setUseUsagNm(name.trim());
            entity.setUseUsagCont(description == null || description.trim().isEmpty()
                    ? null : description.trim());
            entity.setParentUseUsagCd(parentSystemCd);
            entity.setDispYn("Y");
            entity.setDispOrd(dispOrd);
            entity.setUseYn("Y");
            entity.setModId(userId);
            entity.setModDtm(modDtm);
            try {
                return repo.saveAndFlush(entity);
            } catch (DataIntegrityViolationException dive) {
                lastDive = dive;
                log.warn("purpose-settings excel-import PK collision bizKey={} useUsagCd={} attempt={}",
                        bizKey, useUsagCd, attempt + 1);
            }
        }
        throw new BusinessException("WBE9999",
                lastDive != null ? lastDive.getMessage() : null);
    }

    /* =================================================================
     *  Excel cell helpers
     * ================================================================= */

    private static boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int c = 0; c < row.getLastCellNum(); c++) {
            String v = readCell(row, c);
            if (!v.isEmpty()) return false;
        }
        return true;
    }

    /** Read a cell as a trimmed string. Coerces integer numerics so an
     *  integer {@code 111} reads as {@code "111"}, not {@code "111.0"}. */
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

    /** Legacy parity — header matching is `startsWith` (case-sensitive
     *  per the legacy implementation; the customer's templates are
     *  generated rather than hand-edited, so case drift is rare). */
    private static boolean startsWithAny(String raw, List<String> accepted) {
        if (raw == null) return false;
        String normalised = raw.trim();
        if (normalised.isEmpty()) return false;
        for (String label : accepted) {
            if (normalised.startsWith(label)) return true;
        }
        return false;
    }

    private Locale currentLocale() {
        var lang = TenantContext.currentLangCd();
        return lang == null ? Locale.ENGLISH : lang.toLocale();
    }

    private String msg(String key, Locale locale, String... args) {
        return messages.resolve(key, locale, (Object[]) args);
    }

    /** Internal pass-1 staging tuple — keeps the per-row state we need
     *  for pass-2 parent resolution. */
    private record RowStage(
            int rowIndex,                      // 1-based Excel index
            String codeRaw,
            String nameRaw,
            String descRaw,
            String parentRaw,
            List<PurposeExcelFieldError> errors,
            String upCode,
            String upParent
    ) { }
}
