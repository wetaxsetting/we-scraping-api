package com.kosign.wecms.domain.operation.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OSMC / NXISAS bridge credentials — bound from {@code wecms.osmc.*}.
 *
 * <p>The bridge is the legacy WeCMS pathway to the Vietnamese General
 * Department of Taxation (GDT — Tổng cục Thuế) business-registration
 * lookup. Legacy call site:
 * {@code WECMS DEV/.../aosv/aosv_0001_002_r001_act.jsp:98} —
 * {@code WABOOKSOSMCAPIUtil.callApi("TRCO_TRCO001_00003", body, false)}.
 *
 * <p>Refactor R&D is local-only; values default to dev/test endpoints
 * and may be overridden via environment variables. PROD overrides via
 * {@code WECMS_OSMC_URL} / {@code WECMS_OSMC_API_KEY} /
 * {@code WECMS_OSMC_UNIQUE_KEY}.
 */
@ConfigurationProperties(prefix = "wecms.osmc")
public record OsmcProperties(
        /** Full URL of the Coocon bigdata API endpoint that handles the
         *  GDT business-registration lookup. DEV value lives in
         *  {@code WECMS DEV/.../VI/jex.wabooks_new.user.xml} →
         *  {@code <coocon><api_url>}; matches
         *  {@code kr.co.coocon.sgw.RestClient}'s default URL. */
        String url,

        /** Bridge auth key sent on every request as the {@code ApiKey}
         *  field. From the JEX config {@code <coocon><api_key>}. */
        String apiKey,

        /** Service id sent as the {@code ApiId} field. Defaults to
         *  {@code BI09} — the business-registration lookup pinned in
         *  {@code wabooks.helper.TrcoHelper.API_ID}. */
        String apiId,

        /** Tenant identifier baked into the bridge payload's
         *  {@code UniqueKey} prefix when the caller doesn't supply
         *  one explicitly. */
        String uniqueKey
) { }
