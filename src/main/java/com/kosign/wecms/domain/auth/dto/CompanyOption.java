package com.kosign.wecms.domain.auth.dto;

/**
 * One row in {@link LoginResponse#companiesAvailable()} — drives the
 * company-chooser screen rendered when the authenticated user has two
 * or more active USER_BUSI_CONN_INFM links.
 *
 * <p><b>Exceptional path.</b> Per current WeCMS operational rules a
 * user belongs to exactly one active company, so the normal login
 * response never carries any {@link CompanyOption}. This record exists
 * as a safe fallback for the rare case where a user genuinely has
 * multiple active links — and as the wire surface for the local
 * chooser test fixtures (now removed by V76). Do not design business
 * logic around the routine presence of multiple options here.
 *
 * <p>{@code authGrCd} is the per-company authority resolved from
 * BUSI_ADMN_INFM at chooser-build time. Frontend can use it to
 * annotate options (e.g. an "Admin" badge next to companies the user
 * administers).
 *
 * <p>{@code prdtType} is the verbatim {@code BUSI_INFM.PRDT_TYPE}
 * letter for the option — any of the legacy 14 codes; may be
 * {@code null} when the company row has no value. Frontend may use
 * it to display the product label ("WeCMS", "WeCMS Lumens",
 * "HAENGSUNG Electronics") next to each option but must not gate
 * behaviour on it: product-type security lives in the signed JWT
 * issued only after the user picks a company.
 *
 * Wire names auto-derived from camelCase Java fields via the global
 * SNAKE_CASE Jackson naming strategy.
 */
public record CompanyOption(
        String bizKey,
        String cmpnNm,
        String authGrCd,
        String prdtType
) { }
