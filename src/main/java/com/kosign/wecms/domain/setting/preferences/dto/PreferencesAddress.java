package com.kosign.wecms.domain.setting.preferences.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Company address — mirrors {@code BUSI_INFM.RPRS_BSUN_*} columns 1:1.
 * The Preferences panel renders the four parts as a single textarea on
 * read mode (joined by ", ") and as discrete fields in edit mode; the
 * frontend decides the on-screen composition. Backend just round-trips
 * the four columns.
 *
 * <p>{@code postAddress}, {@code city}, {@code state} are required to
 * match the legacy {@code BusiInfm} {@code @NotBlank} validation;
 * {@code detailAddress} is optional.
 */
public record PreferencesAddress(
        @NotBlank @Size(max = 200) String postAddress,
        @Size(max = 200) String detailAddress,
        @NotBlank @Size(max = 200) String city,
        @NotBlank @Size(max = 200) String state) {
}
