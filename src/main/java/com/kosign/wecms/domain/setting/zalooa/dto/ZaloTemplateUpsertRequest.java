package com.kosign.wecms.domain.setting.zalooa.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body of {@code PUT /api/v1/settings/zalo-oa/{oa_id}/templates} — record (or
 * update) one Zalo-approved template id.
 *
 * <p>An upsert rather than separate create/update endpoints, because
 * {@code (BIZ_KEY, OA_ID, TMPL_TYPE, LANG_CD)} is the primary key: the caller
 * always knows the full identity of the row it means, so "create" and "update"
 * would differ only in whether the row happens to exist yet.
 *
 * <p>Templates are <b>not</b> created here — each is drafted in the Zalo Business
 * console and approved manually by Zalo, which issues {@link #tmplId}. This just
 * records the id so the send path can quote it.
 *
 * @param tmplType message-type code. Not constrained to a fixed set: the WeCMS
 *                 message catalogue is still an open product decision, so neither
 *                 the DB nor this DTO pins the values that
 *                 {@code ZaloTemplateType} currently proposes.
 * @param langCd   {@code VI} / {@code EN} / {@code KO} / {@code KM}. Validated
 *                 against {@code common.enums.LangCd} in the service; part of the
 *                 key so template resolution at send time is exact rather than a
 *                 filtered scan that could pick the wrong locale.
 * @param tmplId   the numeric id Zalo issued on approval
 * @param unitPrc  Zalo's per-message charge. Reference data for the panel; no
 *                 billing logic reads it.
 * @param active   {@code false} parks the template without deleting it. Defaults to
 *                 active when omitted.
 */
public record ZaloTemplateUpsertRequest(

        @NotBlank
        @Size(max = 20)
        String tmplType,

        @NotBlank
        @Size(max = 3)
        String langCd,

        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "\\d+", message = "tmpl_id must be numeric")
        String tmplId,

        @DecimalMin(value = "0", message = "unit_prc cannot be negative")
        BigDecimal unitPrc,

        Boolean active
) {
    public ZaloTemplateUpsertRequest {
        tmplType = tmplType == null ? null : tmplType.trim().toUpperCase();
        // Uppercased so `vi` from a frontend and `VI` from a script address the
        // same primary-key row rather than creating two templates for one locale.
        langCd = langCd == null ? null : langCd.trim().toUpperCase();
        tmplId = tmplId == null ? null : tmplId.trim();
    }
}
