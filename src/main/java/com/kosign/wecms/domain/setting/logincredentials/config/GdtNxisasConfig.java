package com.kosign.wecms.domain.setting.logincredentials.config;

/**
 * GDT (Vietnam tax-portal) local-engine NXiSAS module config — the single
 * supported GDT profile, supplied as code constants.
 *
 * <p>Mirrors the code-based approach of
 * {@link com.kosign.wecms.domain.banking.nxisas.NxisasBankConfig}: the
 * scrapping {@code Module} / {@code Class} are fixed Coocon protocol
 * constants, not operational settings. There is exactly one GDT profile,
 * WeCMS has no Admin UI to manage it, and the values never vary by
 * environment — so the earlier {@code @ConfigurationProperties}
 * ({@code wecms.nxisas.gdt.*}) YAML surface added an override capability
 * nobody used. These constants replace it (and the legacy JexAdmin / OSMC
 * {@code nxisas_getModuleInfo} round-trip that resolved them at runtime via
 * {@code WABOOKSOSMCAPIUtil.callApi("NTS_NTS001_00001")}).
 *
 * <p>Drives ONLY the client-side GDT engine login test (Settings &gt; Login
 * Credentials GDT save), served via
 * {@code GET /api/v1/login-credentials/gdt/engine-config}. Bank Login
 * Credentials use the server-side Coocon gateway and need no local-engine
 * Module/Class.
 *
 * <p>Legacy-confirmed GDT triple — see
 * docs/audits/login-credentials-jexadmin-removal-audit.md.
 */
public final class GdtNxisasConfig {

    private GdtNxisasConfig() { }

    /** Legacy OSMC API id for the GDT module-info lookup. */
    public static final String API_ID = "NTS_NTS001_00001";

    /** Coocon NXiSAS {@code Module} path for the GDT tax portal. */
    public static final String MODULE = "GLOBAL/VNM/PUBLIC_GDT";

    /** Coocon NXiSAS {@code Class} for the GDT tax portal. */
    public static final String CLASS_NAME = "COMMON";
}
