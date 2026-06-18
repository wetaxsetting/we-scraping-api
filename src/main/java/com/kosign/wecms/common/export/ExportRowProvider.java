package com.kosign.wecms.common.export;

import java.util.Set;

/**
 * Per-screen strategy for the shared Excel export. A provider hands the
 * list service its own filter/sort path back so export and list share a
 * single source of data — every export endpoint re-runs the same query
 * its list sibling uses, never a forked one.
 *
 * <p>Implementations are {@link org.springframework.stereotype.Component}s
 * picked up by {@link ExportableScreenRegistry} on startup. Adding a new
 * exportable screen is one new {@code ExportRowProvider} class plus a
 * thin per-resource controller endpoint that forwards to
 * {@link ExcelExportService}.
 */
public interface ExportRowProvider {

    /**
     * The View Setting {@code WEB_SVC_ID} this provider serves. Must
     * match a registered screenId (validated against the kebab-case
     * regex on the controller side).
     */
    String screenId();

    /**
     * Closed set of {@code FIELD_ID}s that may appear as columns. Acts
     * as a defence-in-depth allow-list — even if the frontend request
     * carries a bogus {@code FIELD_ID}, the writer drops it before the
     * cell map is consulted. Special CHECKBOX / ETC ids are excluded.
     */
    Set<String> allowedFieldIds();

    /**
     * Run the filtered+sorted query and translate the result to one
     * map per row keyed by {@code FIELD_ID}. The backend's persisted
     * sort whitelist (e.g. {@code AccountSortField}) still validates
     * the wire {@code sortField}; unknown values fall back to the
     * legacy default ORDER BY without throwing.
     *
     * <p>Implementations MUST honour the configured row-count cap
     * (50k) by either delegating to the list service (which returns a
     * full filtered result set today) or trimming with a deterministic
     * order — but throwing {@code WBE2011} from
     * {@link ExcelExportService} is the canonical signal that the
     * dataset exceeds the cap.
     */
    ExportFetch fetch(ExcelExportRequest request);

    /**
     * Opt-in: when {@code true}, {@link ExcelExportService} writes the
     * workbook to a temp file, hands it to {@link #recordErpExport} (so the
     * provider can persist a WeCMS&rarr;ERP {@code ERP_TRNS_HSTR}
     * {@code ERP_TYPE='W'} audit row — legacy {@code pymn_*_r003} parity),
     * then streams that temp file to the response and deletes it. Defaults to
     * {@code false} so every existing exporter (Account Inquiry, Firm Banking
     * History, ERP Transaction History, …) keeps the byte-identical
     * direct-to-response stream with no temp file and no audit.
     */
    default boolean auditsErpExport() {
        return false;
    }

    /**
     * Called by {@link ExcelExportService} ONLY when {@link #auditsErpExport()}
     * is {@code true}, with the fully-written workbook temp file and the
     * download file name. Implementations persist the ERP audit row via their
     * own (domain) writer — keeping {@code common/export} free of any
     * {@code domain/setting} dependency. Must be non-blocking (swallow
     * failures), matching the legacy swallow-and-log around the export audit.
     */
    default void recordErpExport(java.nio.file.Path workbookFile, String orgFileNm) {
        // no-op by default
    }
}
