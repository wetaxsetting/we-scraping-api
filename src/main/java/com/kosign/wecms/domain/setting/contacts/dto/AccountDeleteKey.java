package com.kosign.wecms.domain.setting.contacts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Folded sub-payload for the legacy {@code comm_0184_005_d001}
 * single-account delete. The refactor expresses this as a sibling
 * array on the contact update body instead of a separate endpoint.
 */
public record AccountDeleteKey(
        @NotBlank
        @Size(max = 20)
        String trcoAcctKey) { }
