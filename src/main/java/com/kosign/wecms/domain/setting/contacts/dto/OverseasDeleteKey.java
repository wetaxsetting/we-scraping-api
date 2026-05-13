package com.kosign.wecms.domain.setting.contacts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Companion to {@link AccountDeleteKey} for overseas-beneficiary rows. */
public record OverseasDeleteKey(
        @NotBlank
        @Size(max = 20)
        String trcoOvrsKey) { }
