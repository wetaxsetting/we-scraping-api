package com.kosign.wecms.domain.setting.contacts.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * One address element in a Contact {@code POST} / {@code PUT} payload
 * with a discriminator field controlling upsert-vs-delete dispatch.
 *
 * <p><b>OQ-8 resolved 2026-05-12 &mdash;</b> {@code dispOrd} is the
 * current / previous discriminator ({@code 1} = current,
 * {@code 2} = previous). {@code trcoAddrKey} is a generated technical
 * key; never compared to literal {@code "1"} / {@code "2"} for
 * business meaning.
 *
 * <p>{@code dispOrd} is intentionally NOT {@code @NotNull} here:
 * Bean Validation cannot express "required when {@code op = upsert}"
 * with a single annotation. {@code ContactsValidator} enforces that
 * rule at the service layer.
 */
public record AddressOp(
        @NotBlank
        @Pattern(regexp = "^(upsert|delete)$",
                message = "address op must be 'upsert' or 'delete'")
        String op,

        @NotBlank
        @Size(max = 20)
        String trcoAddrKey,

        @Size(max = 400)
        String trcoBsunPostAdrs,

        @Min(value = 1, message = "disp_ord must be 1 (current) or 2 (previous)")
        @Max(value = 2, message = "disp_ord must be 1 (current) or 2 (previous)")
        Integer dispOrd,

        @Pattern(regexp = "^[0-9]{8}$", message = "issu_dt must be yyyyMMdd")
        String issuDt,

        @Pattern(regexp = "^[0-9]{8}$", message = "due_dt must be yyyyMMdd")
        String dueDt) { }
