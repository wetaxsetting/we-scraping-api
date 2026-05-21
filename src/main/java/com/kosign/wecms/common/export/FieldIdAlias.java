package com.kosign.wecms.common.export;

import java.util.Map;

/**
 * DB FIELD_ID → canonical refactor FIELD_ID resolver, mirroring the
 * frontend's {@code FIELD_ID_ALIAS} map in
 * {@code wecms-web/components/shared/AccountInquiryPage.tsx}.
 *
 * <p>The DB seed is the source of truth for column metadata
 * (CLAUDE.md §13). Operations may seed {@code SYS_SCR_OPTION_WECMS}
 * with FIELD_IDs that follow the legacy JEX DEV convention
 * (e.g. {@code BANK_NM}, {@code ACCT_NICK_NM}) rather than the
 * refactor's semantic names (e.g. {@code BANK}, {@code ACCOUNT_NICKNAME}).
 * Provider allow-lists, frontend column registries, sort whitelists,
 * and the row-map keys returned by {@link ExportRowProvider#fetch}
 * all key against the canonical (semantic) form. This resolver is
 * the single place that translates DB-form values up to the canonical
 * form so the two key spaces can be intersected.
 *
 * <p><b>Where it is used today</b>: only at the persisted-View-Setting
 * visibility-check step in {@link ExcelExportService#resolveColumns}.
 * The frontend already aliases on the way out (export request ships
 * canonical FIELD_IDs), the provider allow-list is canonical, and
 * the row map is keyed by canonical — those sites do not need the
 * resolver. The DB FIELD_IDs surface only inside the visibility
 * intersection, which is where the alias-aware lookup happens.
 *
 * <p><b>Why not push the alias into {@link ScreenOptionService#load}?</b>
 * View Setting Save / Reset round-trip the DB FIELD_IDs verbatim by
 * design — translating them at the load boundary would break the
 * popover's persistence contract (the user's saved overrides are
 * keyed against the DB form). Aliasing only at the export-side
 * intersection keeps the View Setting feature itself untouched.
 *
 * <p>Identity is the default: any FIELD_ID not listed below resolves
 * to itself. Future screens that introduce new DB-form FIELD_IDs add
 * one entry here at the same time as their frontend
 * {@code FIELD_ID_ALIAS} entry.
 */
public final class FieldIdAlias {

    /** DB legacy → canonical FIELD_ID. Identity passthrough for unknown
     *  inputs (so canonical-named DB seeds need no entry). New screens
     *  that introduce a non-canonical DB seed add their entries here at
     *  the same time as the migration that lands the seed.
     *
     *  <p>Both the suffix-stripped form ({@code BANK_NM}) AND PROD's
     *  verbatim suffixed forms ({@code BANK_NM_2}, {@code ISSU_DT_4},
     *  …) resolve to the same canonical key — defensive against partial
     *  seed migrations where a legacy table-area suffix wasn't
     *  transformed.
     *
     *  <p>Mirrored on the frontend by per-page {@code FIELD_ID_ALIAS}
     *  maps (e.g. {@code AccountInquiryPage.tsx}). When pages adopt the
     *  alias pattern, the page registry and this map move in lockstep —
     *  add to both in the same MR. */
    private static final Map<String, String> ALIAS = Map.ofEntries(
            // ─── Account Inquiry (VND/FCY) ────────────────────────────
            // V46/V47 stripped form
            Map.entry("BANK_NM", "BANK"),
            Map.entry("ACCT_NICK_NM", "ACCOUNT_NICKNAME"),
            Map.entry("BAL", "BALANCE"),
            Map.entry("REAL_AMT", "AVAILABLE_BALANCE"),
            Map.entry("ACCT_HIS_LST_INQ_DT", "LAST_UPDATE"),
            // PROD verbatim `_2` form
            Map.entry("BANK_NM_2", "BANK"),
            Map.entry("ACCT_NICK_NM_2", "ACCOUNT_NICKNAME"),
            Map.entry("BAL_2", "BALANCE"),
            Map.entry("REAL_AMT_2", "AVAILABLE_BALANCE"),
            Map.entry("ACCT_HIS_LST_INQ_DT_2", "LAST_UPDATE"),
            Map.entry("CHECKBOX_2", "CHECKBOX"),

            // ─── Add-on Service > Sales / Expenses e-Invoices ─────────
            // V49/V54 stripped form. Canonical names match
            // EInvoiceExportProvider.ALLOWED_FIELD_IDS and the frontend
            // EINVOICE_FALLBACK_COLUMNS in exportConfig.ts.
            Map.entry("ISSU_DT", "DATE_TIME"),
            Map.entry("DOC_ID", "INVOICE_NO"),
            Map.entry("TRCO_CMNM", "BUYER"),
            Map.entry("TRCO_TRSC_CCY_CD", "CURRENCY"),
            Map.entry("TOTL_AMT", "TOTAL"),
            Map.entry("einv_invoiceStatus", "STATUS"),
            Map.entry("TAX_AMT_SUM", "TAX"),
            Map.entry("SPLY_AMT_SUM", "PRETAX"),
            // PROD verbatim `_4` form (defensive — same reasoning as
            // the `_2` Account Inquiry entries above)
            Map.entry("ISSU_DT_4", "DATE_TIME"),
            Map.entry("DOC_ID_4", "INVOICE_NO"),
            Map.entry("TRCO_CMNM_4", "BUYER"),
            Map.entry("TRCO_TRSC_CCY_CD_4", "CURRENCY"),
            Map.entry("TOTL_AMT_4", "TOTAL"),
            Map.entry("einv_invoiceStatus_4", "STATUS"),
            Map.entry("TAX_AMT_SUM_4", "TAX"),
            Map.entry("SPLY_AMT_SUM_4", "PRETAX"),
            Map.entry("CHECKBOX_4", "CHECKBOX")
    );

    /**
     * Resolve a (possibly DB-form) FIELD_ID to its canonical refactor
     * form. Returns {@code null} when the input is {@code null}; returns
     * the input unchanged when no alias applies (so callers can use
     * this universally without pre-checks).
     */
    public static String resolve(String fieldId) {
        if (fieldId == null) return null;
        return ALIAS.getOrDefault(fieldId, fieldId);
    }

    private FieldIdAlias() {
        // utility class — no instances
    }
}
