package com.kosign.wecms.common.export;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.setting.dto.ScreenOptionItem;
import com.kosign.wecms.domain.setting.dto.ScreenOptionListResponse;
import com.kosign.wecms.domain.setting.service.ScreenOptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orchestrates the shared Excel export pipeline. Every per-resource
 * controller endpoint (e.g. {@code POST /api/v1/banking/accounts/export})
 * forwards its validated request body to {@link #exportTo(HttpServletResponse,
 * ExcelExportRequest)}.
 *
 * <p>Pipeline (CLAUDE.md decisions: POI, 50k cap, per-resource endpoint,
 * honour unsaved View Setting reorder, backend computes subheader):
 * <ol>
 *   <li>Resolve the {@link ExportRowProvider} for {@code screenId}
 *       — unknown → {@code WBE2010}.</li>
 *   <li>Intersect the wire {@code columns} with: (a) the provider's
 *       allow-list and (b) the persisted View Setting visibility
 *       ({@code USE_YN='Y'} for the caller). Drop special CHECKBOX/ETC.
 *       <b>Order is taken from the request</b> so an unsaved popover
 *       reorder is honoured (decision 4).</li>
 *   <li>Re-run the same filtered+sorted query the list endpoint does
 *       via {@link ExportRowProvider#fetch}.</li>
 *   <li>Cap row count at {@link #MAX_ROWS} → {@code WBE2011}.</li>
 *   <li>Stream the workbook through {@link ExcelWorkbookWriter}; set
 *       {@code Content-Disposition: attachment} with RFC 5987 UTF-8
 *       filename so non-ASCII titles (Korean / Khmer) survive transit.</li>
 * </ol>
 *
 * Subheader precedence: provider-supplied (canonical) overrides the
 * frontend-supplied fallback in {@link ExcelExportRequest#subHeader()}.
 * Frontend ships only filter + sort + view-setting context; the backend
 * computes the count + totals string per decision 5.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelExportService {

    /** Hard cap on rows per export — see CLAUDE.md decision 2. */
    public static final int MAX_ROWS = 50_000;

    static final String EXPORT_TOO_LARGE = "WBE2011";
    static final String EXPORT_FAILED    = "WBE2012";
    static final String EXPORT_NO_COLUMNS = "WBE2013";

    private final ExportableScreenRegistry registry;
    private final ScreenOptionService screenOptionService;
    private final ExcelWorkbookWriter writer;

    /**
     * Stream an XLSX response into {@code response} for the given
     * request. Intentionally NOT {@code @Transactional}: each inner call
     * ({@link ScreenOptionService#load}, {@link ExportRowProvider#fetch})
     * already opens its own scope, and we don't want to hold a DB
     * transaction open across the streaming write.
     */
    public void exportTo(HttpServletResponse response, ExcelExportRequest request) {
        ExportRowProvider provider = registry.require(request.screenId());

        List<ExcelColumnSpec> columns = resolveColumns(provider, request);
        if (columns.isEmpty()) {
            throw new BusinessException(EXPORT_NO_COLUMNS, request.screenId());
        }

        ExportFetch fetch = provider.fetch(request);
        List<Map<String, Object>> rows = fetch.rows() != null ? fetch.rows() : List.of();
        if (rows.size() > MAX_ROWS) {
            throw new BusinessException(EXPORT_TOO_LARGE, rows.size(), MAX_ROWS);
        }

        String fileName = sanitizeFileName(request.fileName(), request.screenId());
        String subHeader = fetch.subHeader() != null ? fetch.subHeader() : request.subHeader();
        // Optional right-aligned subheader half — providers emit it to
        // mirror legacy `{left_title, right_title}` excel layouts (e.g.
        // Approval Box). Null = legacy single-cell centered subheader.
        String subHeaderRight = fetch.subHeaderRight();
        // In-sheet title cell: prefer the caller's `title` (the
        // on-screen page title — `계좌 조회 (VND)` / `Account Inquiry
        // (VND)`) so the spreadsheet header reads naturally. Fall back
        // to `fileName` then `screenId` when older callers don't set
        // it. The date-suffixed filename (`계좌조회(VND)_02052026`) is
        // a filesystem-only convention and never appears in row 0.
        String title;
        if (request.title() != null && !request.title().isBlank()) {
            title = request.title();
        } else if (request.fileName() != null && !request.fileName().isBlank()) {
            title = request.fileName();
        } else {
            title = request.screenId();
        }

        configureResponseHeaders(response, fileName);

        // Result-export audit path (provider opt-in only) — tee the workbook
        // through a temp file so the provider can persist a WeCMS→ERP 'W'
        // ERP_TRNS_HSTR row before the bytes are served (legacy pymn_*_r003).
        // Every non-opting exporter keeps the byte-identical direct stream.
        if (provider.auditsErpExport()) {
            writeWithErpAudit(response, provider, request, fileName,
                    title, subHeader, subHeaderRight, columns, rows);
            return;
        }

        try (OutputStream out = response.getOutputStream()) {
            writer.write(out, title, subHeader, subHeaderRight, columns, rows);
        } catch (IOException e) {
            // Once headers + bytes are partially flushed there's no
            // clean way to switch the response back to a JSON error
            // envelope. Log + rethrow as UncheckedIOException so the
            // global handler emits 500 / WBE9999 only when nothing has
            // been written yet.
            log.error("Excel export write failed for screen {}", request.screenId(), e);
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            // Convert unexpected runtime errors into the standard
            // business-error envelope when the response is still
            // committable.
            if (!response.isCommitted()) {
                throw new BusinessException(EXPORT_FAILED, request.screenId());
            }
            throw e;
        }
    }

    /**
     * Result-export variant ({@link ExportRowProvider#auditsErpExport()} =
     * {@code true}): write the workbook to a temp file, persist the WeCMS→ERP
     * {@code ERP_TYPE='W'} audit row via {@link ExportRowProvider#recordErpExport},
     * then stream the temp file to the response. Mirrors legacy {@code pymn_*_r003}
     * (a server file copy + {@code ERP_TRNS_HSTR} insert, then the file is served).
     *
     * <p>The audit runs BEFORE the response stream and is non-blocking by
     * contract (the provider's writer swallows failures), so a failed audit
     * never aborts the download — matching the legacy swallow-and-log. The temp
     * file is always deleted. No {@code domain/} dependency leaks into
     * {@code common/export}: the provider owns the writer.
     */
    private void writeWithErpAudit(
            HttpServletResponse response, ExportRowProvider provider,
            ExcelExportRequest request, String fileName,
            String title, String subHeader, String subHeaderRight,
            List<ExcelColumnSpec> columns, List<Map<String, Object>> rows) {
        java.nio.file.Path temp = null;
        try {
            temp = java.nio.file.Files.createTempFile("wecms-result-export-", ".xlsx");
            try (OutputStream fileOut = java.nio.file.Files.newOutputStream(temp)) {
                writer.write(fileOut, title, subHeader, subHeaderRight, columns, rows);
            }
            provider.recordErpExport(temp, fileName + ".xlsx");
            try (OutputStream out = response.getOutputStream();
                 java.io.InputStream in = java.nio.file.Files.newInputStream(temp)) {
                in.transferTo(out);
            }
        } catch (IOException e) {
            log.error("Excel result-export write failed for screen {}", request.screenId(), e);
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            if (!response.isCommitted()) {
                throw new BusinessException(EXPORT_FAILED, request.screenId());
            }
            throw e;
        } finally {
            if (temp != null) {
                try {
                    java.nio.file.Files.deleteIfExists(temp);
                } catch (IOException ignore) {
                    /* temp cleanup is best-effort */
                }
            }
        }
    }

    /**
     * Apply the visibility / allow-list / special-id filters to the
     * request's column list, enrich each surviving column with a
     * View-Setting-derived {@code widthChars} when the wire spec
     * didn't carry one, and return the final emit list in the
     * request's order.
     *
     * <p>Width precedence (highest → lowest):
     * <ol>
     *   <li>Wire {@link ExcelColumnSpec#widthChars()} — explicit
     *       override from the page registry (rare; for cases where
     *       the Excel content shape differs from the on-screen layout,
     *       e.g. Account Inquiry's composite BANK cell).</li>
     *   <li>{@code SIZE} from {@link ScreenOptionItem} — the
     *       on-screen CSS pixel width seeded in {@code SYS_SCR_OPTION_WECMS}
     *       and (post column-resize UI) the user's saved per-user
     *       resize in {@code USER_SCR_OPTION_WECMS}. Converted via
     *       {@link #pxToCharWidth(int)} so a 240px column lands at
     *       roughly 34 Excel-char-width units.</li>
     *   <li>Format-default minimum from {@link ExcelWorkbookWriter} —
     *       the writer's existing safety floor (TEXT 18 / AMOUNT 22 /
     *       DATETIME 22 / etc.) when both above are absent.</li>
     * </ol>
     */
    private List<ExcelColumnSpec> resolveColumns(
            ExportRowProvider provider, ExcelExportRequest request) {
        List<ExcelColumnSpec> requested = request.columns() != null
                ? request.columns()
                : List.of();
        if (requested.isEmpty()) return List.of();

        Set<String> allowed = provider.allowedFieldIds();

        // Persisted View Setting visibility for the caller's
        // (BIZ_KEY, USER_ID, screenId, dymAreaId) scope. The service is
        // the source of truth for what the user is allowed to see —
        // even an unsaved popover reorder cannot un-hide a column the
        // server says is hidden.
        //
        // Both `visibleFieldIds` and `sizeByFieldId` key against the
        // canonical refactor FIELD_ID via {@link FieldIdAlias#resolve},
        // so a DB seed using legacy JEX FIELD_IDs (e.g. `BANK_NM`,
        // `ACCT_NICK_NM`) is recognised by the provider allow-list and
        // frontend column registry which both speak the canonical form
        // (`BANK`, `ACCOUNT_NICKNAME`). Keeps Save/Reset round-trips on
        // the DB FIELD_IDs untouched — the alias only applies at this
        // export-side intersection step.
        // Persisted View Setting visibility is enforced ONLY when the screen
        // is actually configured for View Setting (i.e. seeded in
        // SYS_SCR_OPTION_WECMS). "Export-only" screens — pages that pass a
        // screenId to the export pipeline but do NOT render the View Setting
        // popover and are intentionally NOT seeded (addon e-Invoices,
        // operation-status, cash-flow, firm-banking-history, erp-transaction-
        // history) — have no persisted view; for them the request columns ∩
        // provider allow-list is authoritative. load() throws WBE2001 when the
        // screen has no sys rows; treat that as "no persisted view" and skip
        // the visibility intersection rather than failing the export.
        ScreenOptionListResponse view = null;
        try {
            view = screenOptionService.load(request.screenId(), request.dymAreaId());
        } catch (BusinessException e) {
            if (!"WBE2001".equals(e.getCode())) throw e;
        }
        boolean enforceVisibility = view != null;
        Set<String> visibleFieldIds = new HashSet<>();
        Map<String, String> sizeByFieldId = new HashMap<>();
        if (view != null) {
            for (ScreenOptionItem item : view.items()) {
                String canonical = FieldIdAlias.resolve(item.fieldId());
                if ("Y".equals(item.useYn())) visibleFieldIds.add(canonical);
                if (item.size() != null && !item.size().isBlank()) {
                    sizeByFieldId.put(canonical, item.size());
                }
            }
        }

        List<ExcelColumnSpec> out = new ArrayList<>(requested.size());
        Set<String> seen = new HashSet<>();
        for (ExcelColumnSpec col : requested) {
            String fid = col.fieldId();
            if (fid == null || fid.isBlank()) continue;
            if (!seen.add(fid)) continue; // dedupe
            if (ScreenOptionService.isSpecialFieldId(fid)) continue;
            if (allowed != null && !allowed.contains(fid)) continue;
            // Unseeded (export-only) screens have no persisted visibility to
            // enforce — the provider allow-list above already bounds the set.
            if (enforceVisibility && !visibleFieldIds.contains(fid)) continue;
            out.add(enrichWidth(col, sizeByFieldId.get(fid)));
        }
        return out;
    }

    /**
     * If the wire spec didn't set {@code widthChars}, derive one from
     * the View Setting {@code SIZE} (CSS px) so the user's on-screen
     * column width carries to the spreadsheet. When SIZE is absent /
     * unparseable, leaves the column's {@code widthChars} as {@code
     * null} so the writer falls back to its format-default minimum.
     */
    private static ExcelColumnSpec enrichWidth(ExcelColumnSpec col, String size) {
        if (col.widthChars() != null) return col;
        Integer px = parsePixelSize(size);
        if (px == null) return col;
        int chars = pxToCharWidth(px);
        return new ExcelColumnSpec(
                col.fieldId(),
                col.header(),
                col.format(),
                col.ccyCd(),
                col.align(),
                chars
        );
    }

    /**
     * Parse a CSS pixel string (e.g. {@code "240px"}, {@code " 38 px "})
     * into its integer pixel value. Returns {@code null} for blank,
     * unparseable, or non-positive values — the caller falls back to
     * the writer's format-default min.
     */
    static Integer parsePixelSize(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (s.isEmpty()) return null;
        if (s.endsWith("px")) s = s.substring(0, s.length() - 2).trim();
        if (s.isEmpty()) return null;
        try {
            int v = Integer.parseInt(s);
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Convert CSS pixel width to Excel '0'-character-width units. The
     * Excel column-width formula is
     * {@code visiblePixels = chars * MDW + 5}, where MDW (max digit
     * width) ≈ 7px for default Arial / Calibri at typical sizes. So:
     *
     * <pre>chars ≈ (px - 5) / 7</pre>
     *
     * Rounded to the nearest int with a minimum of 1. The writer
     * still applies the {@code charsToPoiWidth} +200-unit POI padding
     * compensation downstream so what we compute is what Excel renders.
     */
    static int pxToCharWidth(int px) {
        int chars = Math.round((px - 5f) / 7f);
        return Math.max(1, chars);
    }

    private static void configureResponseHeaders(HttpServletResponse response, String fileName) {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ContentDisposition cd = ContentDisposition
                .builder("attachment")
                .filename(fileName + ".xlsx", StandardCharsets.UTF_8)
                .build();
        response.setHeader("Content-Disposition", cd.toString());
        // Hint to the browser that the response is the file itself —
        // the JWT 401 response interceptor on the frontend uses
        // axios's response Content-Type check to skip JSON unwrap.
        response.setHeader("X-Content-Type-Options", "nosniff");
        // Also expose the header to fetch() callers when CORS gates
        // the response — the frontend reads the filename from here.
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
    }

    /**
     * Drop characters that browsers strip / rewrite in download
     * filenames (control chars, slashes, quotes). Falls back to the
     * screenId when the input is empty so the user always sees a
     * recognisable filename.
     */
    static String sanitizeFileName(String raw, String fallback) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) s = fallback;
        // Replace path separators + control chars with a space.
        s = s.replaceAll("[\\\\/\\u0000-\\u001f\"]+", " ").trim();
        // Avoid double extensions — caller may or may not append .xlsx.
        if (s.toLowerCase().endsWith(".xlsx")) {
            s = s.substring(0, s.length() - 5);
        }
        // Cap at 120 chars so Windows / browsers don't truncate mid-glyph.
        if (s.length() > 120) s = s.substring(0, 120).trim();
        return s.isEmpty() ? fallback : s;
    }

    /** Mediatype constant exposed for controllers that prefer it over a literal. */
    public static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
}
