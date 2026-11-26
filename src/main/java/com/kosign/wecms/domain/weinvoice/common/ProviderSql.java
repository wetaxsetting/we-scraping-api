package com.kosign.wecms.domain.weinvoice.common;

/**
 * SQL for "Phát hành qua" — the provider (MSTTCGP) an invoice was issued through, over the engine aliases
 * {@code i} (invoices), {@code x} (invoice_xml) and {@code p} (invoice_pdf).
 *
 * <p>The XML header {@code <MSTTCGP>} / the GDT list {@code msttcgp} are the authoritative source, but cash-register
 * invoices (QĐ 1233) never carry them — 60,831 of the 64,938 invoices without a provider on 2026-11-26. The engine still
 * knows the provider by other means, used here in this order:
 * <ol>
 *   <li>{@code <MSTTCGP>} of the XML header, else {@code msttcgp} of the GDT list ({@link #XML_PROVIDER});</li>
 *   <li>{@code MSTTCGP} written in the XML's TTKhac (e.g. WeTax cash-register invoices);</li>
 *   <li>the provider whose engine adapter fetched the original PDF ({@code invoice_pdf.source_id}, with or without the
 *       {@code email:} prefix, against {@code einvoice_providers.source_id});</li>
 *   <li>the provider the seller (parent MST) issues through most often on its other invoices.</li>
 * </ol>
 * Steps 2–4 are an inference: "needs a lookup code" ({@code ProviderLookup.needsCode}) keeps reading
 * {@link #XML_PROVIDER} only, so the "!" pill and its counts do not change.
 *
 * <p>Record: docs/bugs/weinvoice-provider-empty-for-cash-register-invoices-fix-2026-11-26.md.
 */
public final class ProviderSql {

    private ProviderSql() {
    }

    /** Provider written on the invoice itself (XML header, else GDT list); NULL when blank. */
    public static final String XML_PROVIDER = "NULLIF(COALESCE(NULLIF(x.mst_tcgp, ''), i.list_raw->>'msttcgp'), '')";

    /** {@code MSTTCGP} in the XML's TTKhac, when it looks like a tax code. */
    static final String TTKHAC_PROVIDER =
            "(CASE WHEN x.parsed->'tt_khac'->>'MSTTCGP' ~ '^[0-9]{10}(-[0-9]{3})?$' THEN x.parsed->'tt_khac'->>'MSTTCGP' END)";

    /**
     * Engine adapters whose provider never appears as an invoice's MSTTCGP, so {@code einvoice_providers} has no row for
     * them (they serve cash-register / no-code invoices only; counted 2026-11-26). MSTs are the adapters' own
     * {@code mst_tcgp} in {@code pdf_providers/}; WeTax = Webcash (TTKhac {@code MSTTCGP} of WeTax invoices).
     */
    static final String ADAPTER_PROVIDERS = """
            (VALUES ('ipos', '0104789847'), ('sapo', '0103243195'), ('vietinvoice', '0106870211'),
                    ('wetax', '1201496252'), ('mmhoadon', '0305289153'), ('thoadon', '0318511655'),
                    ('sonphat', '4601328480'), ('gohoadon', '0111245245'))""";

    /** Provider of the engine adapter that fetched the original PDF (parent MST first when several share it). */
    static final String PDF_ADAPTER_PROVIDER = """
            (SELECT a.m FROM (
                    SELECT ep.mst_tcgp AS m, ep.source_id AS s FROM {eng}.einvoice_providers ep WHERE ep.source_id IS NOT NULL
                    UNION ALL SELECT v.m, v.s FROM\s""" + ADAPTER_PROVIDERS + """
             v (s, m)) a
              WHERE p.source_id IS NOT NULL AND a.s = regexp_replace(p.source_id, '^email:', '')
              ORDER BY length(a.m), a.m LIMIT 1)""";

    /**
     * CTE {@code sprv(s, m)}: for every seller (parent MST) of the invoices of {@code :mst}, the provider it uses most
     * often. Computed once per query — a correlated subquery per row would rescan the invoices for each of the up to
     * ~60,000 cash-register rows of one MST.
     */
    public static final String SELLER_USUAL_CTE = """
            sprv AS (
                SELECT DISTINCT ON (t.s) t.s, t.m
                  FROM (SELECT split_part(i2.nbmst, '-', 1) AS s,
                               NULLIF(COALESCE(NULLIF(x2.mst_tcgp, ''), i2.list_raw->>'msttcgp'), '') AS m,
                               count(*) AS n
                          FROM {eng}.invoices i2
                          LEFT JOIN {eng}.invoice_xml x2 ON x2.invoice_id = i2.id
                         WHERE split_part(i2.nbmst, '-', 1) IN (
                                   SELECT DISTINCT split_part(i3.nbmst, '-', 1) FROM {eng}.invoices i3
                                    WHERE i3.company_mst = :mst)
                           AND NULLIF(COALESCE(NULLIF(x2.mst_tcgp, ''), i2.list_raw->>'msttcgp'), '') IS NOT NULL
                         GROUP BY 1, 2) t
                 ORDER BY t.s, t.n DESC, t.m
            )""";

    /** Join of {@link #SELLER_USUAL_CTE} onto alias {@code i}. */
    public static final String SELLER_USUAL_JOIN = "LEFT JOIN sprv ON sprv.s = split_part(i.nbmst, '-', 1)";

    /** {@link #SELLER_USUAL_CTE} for one invoice (alias {@code i}) — for single-row queries (detail). */
    public static final String SELLER_USUAL_ONE = """
            (SELECT NULLIF(COALESCE(NULLIF(x2.mst_tcgp, ''), i2.list_raw->>'msttcgp'), '')
               FROM {eng}.invoices i2
               LEFT JOIN {eng}.invoice_xml x2 ON x2.invoice_id = i2.id
              WHERE split_part(i2.nbmst, '-', 1) = split_part(i.nbmst, '-', 1)
                AND NULLIF(COALESCE(NULLIF(x2.mst_tcgp, ''), i2.list_raw->>'msttcgp'), '') IS NOT NULL
              GROUP BY 1
              ORDER BY count(*) DESC, 1
              LIMIT 1)""";

    /** The provider of the invoice, inferred when the invoice does not carry it; {@code sellerUsual} is step 4. */
    public static String effective(String sellerUsual) {
        return "COALESCE(" + XML_PROVIDER + ", " + TTKHAC_PROVIDER + ", " + PDF_ADAPTER_PROVIDER + ", " + sellerUsual + ")";
    }
}
