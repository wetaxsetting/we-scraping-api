package com.kosign.wecms.domain.setting.preferences.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * PUT /api/v1/settings/preferences body. Mirrors legacy
 * {@code comm_0215_001_u001} — saves the company profile. Required
 * fields match the Figma + legacy BusiInfm annotations:
 *   - vietnameseName (CMPN_NM) — required
 *   - taxId (TAX_ID) — required
 *   - representativeEmail (EML) — required, valid email
 *   - address.{postAddress, city, state} — required (validated on the
 *     nested record)
 *
 * <p>{@code displayName} (CMPN_ENM), {@code representativeName}
 * (RPPR_NM), {@code telNo}, {@code faxNo}, {@code address.detailAddress}
 * are optional.
 *
 * <p>Logo file upload is not in scope for this MR — the panel still
 * renders the placeholder SVG. Add a separate {@code POST
 * /api/v1/settings/preferences/logo} multipart endpoint when the
 * upload UX lands.
 */
public record PreferencesUpdateRequest(
        @NotBlank @Size(max = 200) String vietnameseName,
        @Size(max = 200) String displayName,
        @NotBlank @Size(max = 40) String taxId,
        @Size(max = 100) String representativeName,
        @NotBlank @Email @Size(max = 100) String representativeEmail,
        @Size(max = 30) String telNo,
        @Size(max = 30) String faxNo,
        @Valid @NotNull(message = "address: is required") PreferencesAddress address) {
    // @NotNull (not @NotBlank) on the nested record: Bean Validation
    // applies @NotBlank only to CharSequence types, so applying it to a
    // record component throws UnexpectedTypeException at runtime.
    // @Valid cascades into the nested record so its @NotBlank fields
    // (postAddress / city / state) are still enforced.
}
