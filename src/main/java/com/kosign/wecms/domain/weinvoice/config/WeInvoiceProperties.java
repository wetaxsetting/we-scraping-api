package com.kosign.wecms.domain.weinvoice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/**
 * WeInvoice settings — bound from {@code wecms.weinvoice.*}.
 *
 * <p>WeInvoice reads the invoices the we-scraping engine collects (GDT list + XML,
 * provider PDF, mail) and keeps its own tables next to them. Both live in the
 * <b>WeInvoice database</b>, which is not the WeCMS database: see {@link WeInvoiceDb}
 * for why the connection is held outside the Spring {@code DataSource} beans.
 *
 * <p><b>Off by default.</b> With {@link #enabled} false no connection pool is opened
 * and every WeInvoice / Partner endpoint answers {@code WBE3000}. The other values
 * must still be present in the {@code .env} (bare {@code ${VAR}} rule of
 * {@code application.yml}); dummy values are fine while disabled.
 *
 * <p><b>Fail-fast.</b> When enabled, the compact constructor rejects a missing
 * connection setting, an invalid schema name or a secret key that is not 32
 * base64-encoded bytes. Messages name the setting, never its value.
 */
@ConfigurationProperties(prefix = "wecms.weinvoice")
public record WeInvoiceProperties(

        /** Master switch. Blank / missing value = off (never null after construction). */
        Boolean enabled,

        /** Connection to the WeInvoice (engine) database. */
        Db db,

        /** Schema holding the engine tables (invoices, invoice_xml, …). Read only. */
        String engineSchema,

        /** Schema holding the WeInvoice tables ({@code winv_*}). Read / write. */
        String appSchema,

        /** Engine output directory: XML / PDF / mail files, paths in the engine tables are relative to it. */
        String engineOutputDir,

        /** Root directory for files uploaded through WeInvoice. The engine reads uploads from here. */
        String storageRoot,

        /** Base64 of the 32-byte AES key that encrypts GDT passwords ({@code winv_cmpn_infm.gdt_pw_enc}). */
        String secretKey,

        /** Comma-separated API keys accepted on {@code /api/partner/v1/**} (header {@code X-Api-Key}). */
        String partnerApiKeys,

        /** Domain of the per-MST receiving address {@code hd-<mst>@<domain>}. Blank = not offered. */
        String mailDomain,

        /** Maximum number of tax codes one user account may manage (UI Spec 3.1, prototype value 5). */
        Integer maxCompanies,

        /** Enables {@code POST /test-data/load} (acceptance data set, dev / SIT only). */
        Boolean testDataEnabled,

        /**
         * The scraping engine's internal API (we-scraping {@code web/internal_api.py}): used to verify a
         * GDT account (captcha + login) before a tax code is saved. Blank = connections cannot be added.
         */
        Scraping scraping
) {

    private static final Pattern SCHEMA = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,62}");
    private static final int DEFAULT_MAX_COMPANIES = 5;

    public WeInvoiceProperties {
        // Boolean, not boolean: the .env templates are keys-only, and a blank value binds to null,
        // which a primitive rejects at startup. Blank = off.
        enabled = Boolean.TRUE.equals(enabled);
        testDataEnabled = Boolean.TRUE.equals(testDataEnabled);
        if (scraping == null) {
            scraping = new Scraping(null, null);
        }
        if (maxCompanies == null || maxCompanies <= 0) {
            maxCompanies = DEFAULT_MAX_COMPANIES;
        }
        if (enabled) {
            if (db == null) {
                throw new IllegalStateException("wecms.weinvoice.db is required when WeInvoice is enabled");
            }
            requireConfigured(db.url(), "wecms.weinvoice.db.url (WECMS_WEINVOICE_DB_URL)");
            requireConfigured(db.username(), "wecms.weinvoice.db.username (WECMS_WEINVOICE_DB_USERNAME)");
            requireConfigured(db.password(), "wecms.weinvoice.db.password (WECMS_WEINVOICE_DB_PASSWORD)");
            requireSchema(engineSchema, "wecms.weinvoice.engine-schema (WECMS_WEINVOICE_ENGINE_SCHEMA)");
            requireSchema(appSchema, "wecms.weinvoice.app-schema (WECMS_WEINVOICE_APP_SCHEMA)");
            requireConfigured(engineOutputDir, "wecms.weinvoice.engine-output-dir (WECMS_WEINVOICE_ENGINE_OUTPUT_DIR)");
            requireConfigured(storageRoot, "wecms.weinvoice.storage-root (WECMS_WEINVOICE_STORAGE_ROOT)");
            requireKey(secretKey);
        }
    }

    /** Partner keys with blanks dropped; empty when none is configured (every partner call is then refused). */
    public List<String> partnerKeyList() {
        if (partnerApiKeys == null || partnerApiKeys.isBlank() || partnerApiKeys.startsWith("${")) {
            return List.of();
        }
        return Arrays.stream(partnerApiKeys.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static void requireConfigured(String value, String name) {
        // An unresolved ${VAR} binds as the literal text (see application.yml header), so treat it as missing.
        if (value == null || value.isBlank() || value.startsWith("${")) {
            throw new IllegalStateException(name + " is required when WeInvoice is enabled");
        }
    }

    private static void requireSchema(String value, String name) {
        requireConfigured(value, name);
        if (!SCHEMA.matcher(value).matches()) {
            throw new IllegalStateException(name + " must be a plain SQL identifier");
        }
    }

    private static void requireKey(String value) {
        String name = "wecms.weinvoice.secret-key (WECMS_WEINVOICE_SECRET_KEY)";
        requireConfigured(value, name);
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(name + " must be base64");
        }
        if (raw.length != 32) {
            throw new IllegalStateException(name + " must decode to 32 bytes (AES-256)");
        }
    }

    /** Connection settings of the WeInvoice database. */
    /**
     * @param url    base URL of the engine's internal API, e.g. {@code http://127.0.0.1:8090}
     * @param apiKey shared key sent as {@code X-Api-Key} (engine side: {@code SCRAPING_INTERNAL_API_KEY})
     */
    public record Scraping(String url, String apiKey) {

        /** True when both values are set (a {@code ${...}} placeholder counts as unset). */
        public boolean configured() {
            return set(url) && set(apiKey);
        }

        private static boolean set(String v) {
            return v != null && !v.isBlank() && !v.startsWith("${");
        }

        @Override
        public String toString() {
            return "Scraping[url=" + url + ", apiKey=" + (set(apiKey) ? "***" : "<unset>") + "]";
        }
    }

    public record Db(String url, String username, String password, Integer maxPoolSize) {

        private static final int DEFAULT_MAX_POOL_SIZE = 5;

        public Db {
            if (maxPoolSize == null || maxPoolSize <= 0) {
                maxPoolSize = DEFAULT_MAX_POOL_SIZE;
            }
        }
    }
}
