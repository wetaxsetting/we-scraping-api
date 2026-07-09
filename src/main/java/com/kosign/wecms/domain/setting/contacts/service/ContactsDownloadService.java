package com.kosign.wecms.domain.setting.contacts.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.contacts.dto.ContactAccountRow;
import com.kosign.wecms.domain.setting.contacts.dto.ContactAddressRow;
import com.kosign.wecms.domain.setting.contacts.dto.ContactOverseasRow;
import com.kosign.wecms.domain.setting.contacts.dto.ContactRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Settings &gt; Contacts &mdash; Excel Download (the legacy
 * template-populated report). Ports legacy
 * {@code comm_0184_001_r006_excel_view.jsp}, which serves BOTH the
 * Local and Overseas downloads from a single view service, branching
 * on the {@code TRCO_TYPE} request parameter ({@code 'L'} / {@code 'O'}).
 *
 * <p>This is the template-based generated report decided in
 * {@code docs/audits/contacts-excel-download-audit.md}. Like
 * {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferDownloadService}
 * it deliberately does <strong>NOT</strong> reuse the shared
 * {@code common/export/ExcelExportService} pipeline (which builds an
 * empty workbook from scratch and cannot preserve the template's merged
 * title — {@code A1:I2} Local / {@code A1:K2} Overseas — column widths,
 * and styling). Instead it loads the styled locale template from the
 * backend classpath via {@link ClassPathResource} and writes data into
 * the pre-styled rows so all template formatting is preserved (legacy
 * parity). The generic {@code POST /export} endpoint is retained
 * separately and unchanged.
 *
 * <p><b>Row granularity (legacy parity, verified against the IDOs):</b>
 * <ul>
 *   <li><b>Local</b> &mdash; legacy IDO {@code TRCO_ACCT_INFO_R005} is
 *       {@code FROM TRCO_INFM LEFT JOIN TRCO_ACCT_INFO}, so it emits one
 *       spreadsheet row <em>per bank account</em>; a contact with no
 *       account still yields one row with blank Deposit Bank / Account /
 *       Currency.</li>
 *   <li><b>Overseas</b> &mdash; legacy IDO {@code TRCO_OVRS_INFM_R003} is
 *       {@code FROM TRCO_OVRS_INFM LEFT JOIN TRCO_INFM}, so it emits one
 *       row <em>per beneficiary</em>; a contact with no beneficiary yields
 *       no row.</li>
 * </ul>
 *
 * <p><b>Address discriminator:</b> the legacy JSP read the address JSON's
 * {@code TRCO_ADDR_KEY} ({@code "1"} = current, {@code "2"} = old). The
 * refactor stores generated address keys, so per the established Contacts
 * <b>OQ-8</b> the current / previous discriminator is {@code DISP_ORD}
 * ({@code 1} = current, {@code 2} = old) &mdash; see
 * {@link ContactAddressRow}. The Valid Date comes from the current
 * (DISP_ORD=1) address: {@code DUE_DT} preferred, else {@code ISSU_DT},
 * rendered {@code dd-MM-yyyy} (legacy {@code formatValidDate} parity).
 *
 * <p>Data is sourced through {@link ContactsService#listForExport} so the
 * download shares the list's tenant scope ({@code BIZ_KEY}), active-only
 * ({@code coalesce(trco_stts,'') <> 'C'}) and managed-only
 * ({@code HAND_TRCO_KEY IS NOT NULL}) filtering &mdash; exactly the legacy
 * download's dynamic WHERE. Bank names are already resolved on each
 * {@link ContactAccountRow} via the legacy NAPAS/CITAD function (OQ-7),
 * matching the legacy {@code wb_get_napas_bank_nm} column.
 *
 * <p>Locale selection mirrors legacy {@code LocaleHelper.getLanguage}
 * with one deliberate fix: {@code EN} / {@code KO} / {@code VI} each load
 * their own template; {@code KM} and any other value fall back to
 * {@code EN}. (Legacy had no KM template and errored for Khmer users
 * &mdash; see audit G3.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactsDownloadService {

    /** Classpath folder holding the six locale templates. */
    static final String TEMPLATE_DIR = "templates/contacts/";
    /** Excel header is row 3 (1-based); data rows start at row 4 → index 3
     *  (legacy {@code int rowIndex = 3} in the JSP). */
    static final int FIRST_DATA_ROW_INDEX = 3;
    /** Local sheet — 9 columns A–I. */
    static final int LOCAL_COLUMN_COUNT = 9;
    /** Overseas sheet — 11 columns A–K. */
    static final int OVERSEAS_COLUMN_COUNT = 11;

    private final ContactsService contactsService;

    /** Generated report payload — workbook bytes + the download filename. */
    public record GeneratedReport(byte[] content, String fileName) { }

    /**
     * Generate the Contacts download report for one contact type.
     *
     * @param type      {@code "L"} (Local) or {@code "O"} (Overseas);
     *                  anything other than {@code "L"} is treated as
     *                  Overseas, mirroring legacy
     *                  {@code contactType.equals("L") ? "Local" : "Overseas"}.
     * @param langParam optional UI-locale hint ({@code EN}/{@code KO}/{@code VI});
     *                  when blank the JWT {@code langCd} is used; KM/other → EN.
     */
    @Transactional(readOnly = true)
    public GeneratedReport generate(String type, String langParam) {
        boolean local = "L".equals(type);
        String lang = resolveLang(langParam);

        // Reuse the list query path — same tenant scope + active-only +
        // managed-only filtering + bank-name resolution as the list/legacy
        // download. No keyword for the download (legacy passes none).
        List<ContactRow> contacts = contactsService.listForExport(null, local ? "L" : "O");

        byte[] bytes = populate(local, lang, contacts);

        String typeLabel = local ? "Local" : "Overseas";
        String fileName = "WeCMS_" + typeLabel + "_Contacts_download_" + lang + ".xlsx";
        log.info("feature=contacts report-download bizKey={} type={} lang={} contacts={}",
                TenantContext.currentBizKey(), typeLabel, lang, contacts.size());
        return new GeneratedReport(bytes, fileName);
    }

    private byte[] populate(boolean local, String lang, List<ContactRow> contacts) {
        String typeLabel = local ? "Local" : "Overseas";
        int columnCount = local ? LOCAL_COLUMN_COUNT : OVERSEAS_COLUMN_COUNT;
        String resourcePath = TEMPLATE_DIR + "WeCMS_" + typeLabel + "_Contacts_download_" + lang + ".xlsx";

        try (InputStream in = new ClassPathResource(resourcePath).getInputStream();
             Workbook wb = WorkbookFactory.create(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.getSheetAt(0);

            // Capture the per-column cell style from the template's first
            // data row (index 3). Reusing these shared CellStyle objects on
            // every written cell preserves the template's borders, alignment
            // and fill on all rows and avoids the 64k-style limit. (Legacy
            // built a fresh uniform left-aligned bordered style; reusing the
            // template's own row-4 style is the cleaner equivalent and keeps
            // the shipped formatting intact.)
            CellStyle[] colStyle = new CellStyle[columnCount];
            Row templateRow = sheet.getRow(FIRST_DATA_ROW_INDEX);
            if (templateRow != null) {
                for (int c = 0; c < columnCount; c++) {
                    Cell cell = templateRow.getCell(c);
                    if (cell != null) colStyle[c] = cell.getCellStyle();
                }
            }

            int rowIndex = FIRST_DATA_ROW_INDEX;
            for (ContactRow contact : contacts) {
                String currentAddr = addressByDispOrd(contact.addresses(), 1);
                String oldAddr = addressByDispOrd(contact.addresses(), 2);
                String validDt = validDate(contact.addresses());

                if (local) {
                    List<ContactAccountRow> accounts = contact.accounts();
                    if (accounts == null || accounts.isEmpty()) {
                        // LEFT JOIN parity — a Local contact with no account
                        // still gets one row (blank bank / account / currency).
                        writeLocalRow(sheet, rowIndex++, colStyle, contact,
                                null, currentAddr, oldAddr, validDt);
                    } else {
                        for (ContactAccountRow a : accounts) {
                            writeLocalRow(sheet, rowIndex++, colStyle, contact,
                                    a, currentAddr, oldAddr, validDt);
                        }
                    }
                } else {
                    // Driving table parity — one row per beneficiary; a
                    // contact with no beneficiary contributes no row.
                    List<ContactOverseasRow> beneficiaries = contact.overseasBeneficiaries();
                    if (beneficiaries != null) {
                        for (ContactOverseasRow o : beneficiaries) {
                            writeOverseasRow(sheet, rowIndex++, colStyle, contact,
                                    o, currentAddr, oldAddr, validDt);
                        }
                    }
                }
            }

            // The shipped templates carry one pre-filled SAMPLE data row at
            // row 4 (index 3). Real data overwrites it from the top, but any
            // template rows below the last written row (the sample row when
            // 0–N rows are produced) must be cleared so the fake sample never
            // leaks into the download. Styles were captured above before any
            // removal, so this is safe.
            int lastRow = sheet.getLastRowNum();
            for (int r = rowIndex; r <= lastRow; r++) {
                Row leftover = sheet.getRow(r);
                if (leftover != null) sheet.removeRow(leftover);
            }

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            // Template missing from the jar, or write failure — surfaces as
            // WBE9999 via the global handler with the stack logged.
            throw new UncheckedIOException(
                    "Failed to generate Contacts download report (type=" + typeLabel + ", lang=" + lang + ")", e);
        }
    }

    /** Local layout — 9 columns A–I. {@code account} may be null when the
     *  contact has no bank account (LEFT JOIN parity). */
    private static void writeLocalRow(
            Sheet sheet, int idx, CellStyle[] colStyle, ContactRow contact,
            ContactAccountRow account, String currentAddr, String oldAddr, String validDt) {
        Row row = rowAt(sheet, idx);
        setText(row, 0, colStyle, contact.handTrcoKey());
        setText(row, 1, colStyle, contact.trcoCmnm());
        setText(row, 2, colStyle, contact.trcoTaxId());
        setText(row, 3, colStyle, account == null ? "" : account.bankNm());
        setText(row, 4, colStyle, account == null ? "" : account.acctNo());
        setText(row, 5, colStyle, account == null ? "" : account.acctCcyCd());
        setText(row, 6, colStyle, currentAddr);
        setText(row, 7, colStyle, oldAddr);
        setText(row, 8, colStyle, validDt);
    }

    /** Overseas layout — 11 columns A–K. */
    private static void writeOverseasRow(
            Sheet sheet, int idx, CellStyle[] colStyle, ContactRow contact,
            ContactOverseasRow bene, String currentAddr, String oldAddr, String validDt) {
        Row row = rowAt(sheet, idx);
        setText(row, 0, colStyle, contact.handTrcoKey());
        setText(row, 1, colStyle, contact.trcoCmnm());
        setText(row, 2, colStyle, contact.trcoTaxId());
        setText(row, 3, colStyle, bene.beneName());
        setText(row, 4, colStyle, bene.beneBankName());
        setText(row, 5, colStyle, bene.bicCode());
        setText(row, 6, colStyle, bene.beneAcctNo());
        setText(row, 7, colStyle, bene.beneAddr());
        setText(row, 8, colStyle, currentAddr);
        setText(row, 9, colStyle, oldAddr);
        setText(row, 10, colStyle, validDt);
    }

    /* ----------------------------------------------------------------- */

    /** First address with the given {@code DISP_ORD} (1 = current,
     *  2 = old) — OQ-8 discriminator. Returns "" when absent. */
    private static String addressByDispOrd(List<ContactAddressRow> addresses, int dispOrd) {
        if (addresses == null) return "";
        for (ContactAddressRow a : addresses) {
            if (a.dispOrd() != null && a.dispOrd() == dispOrd) {
                return a.trcoBsunPostAdrs() == null ? "" : a.trcoBsunPostAdrs();
            }
        }
        return "";
    }

    /** Valid Date from the current (DISP_ORD=1) address — DUE_DT preferred,
     *  else ISSU_DT, rendered {@code dd-MM-yyyy} (legacy {@code formatValidDate}). */
    private static String validDate(List<ContactAddressRow> addresses) {
        if (addresses == null) return "";
        for (ContactAddressRow a : addresses) {
            if (a.dispOrd() != null && a.dispOrd() == 1) {
                String due = formatDate8(a.dueDt());
                if (!due.isEmpty()) return due;
                return formatDate8(a.issuDt());
            }
        }
        return "";
    }

    /**
     * Resolve the template locale. Priority: explicit {@code langParam}
     * (the UI locale the frontend passes) → JWT {@code langCd} → EN.
     * Only EN / KO / VI have a template; KM and anything else fall back to
     * EN. (Legacy {@code LocaleHelper} returned {@code KM} for Khmer users
     * and then failed on the missing {@code ..._KM.xlsx} — audit G3; the
     * refactor maps KM → EN as a fix.)
     */
    private static String resolveLang(String langParam) {
        LangCd lc = (langParam != null && !langParam.isBlank())
                ? LangCd.fromCodeOrDefault(langParam)
                : TenantContext.currentLangCd();
        if (lc == null) return "EN";
        return switch (lc) {
            case KO -> "KO";
            case VI -> "VI";
            default -> "EN"; // EN + KM (no template) + any unmapped
        };
    }

    private static Row rowAt(Sheet sheet, int idx) {
        Row row = sheet.getRow(idx);
        return row != null ? row : sheet.createRow(idx);
    }

    private static void setText(Row row, int col, CellStyle[] colStyle, String value) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            cell = row.createCell(col);
            if (colStyle != null && col < colStyle.length && colStyle[col] != null) {
                cell.setCellStyle(colStyle[col]);
            }
        }
        cell.setCellValue(value == null ? "" : value);
    }

    /** {@code yyyyMMdd} → {@code dd-MM-yyyy}; blank/short → "". */
    static String formatDate8(String s) {
        String v = s == null ? "" : s.trim();
        if (v.length() < 8) return "";
        return v.substring(6, 8) + "-" + v.substring(4, 6) + "-" + v.substring(0, 4);
    }
}
