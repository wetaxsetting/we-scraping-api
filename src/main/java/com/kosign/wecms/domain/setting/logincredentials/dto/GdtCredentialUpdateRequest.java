package com.kosign.wecms.domain.setting.logincredentials.dto;

import jakarta.validation.constraints.Size;

/**
 * Update payload for an existing GDT credential row — every field is
 * optional. Sibling of {@link BankCredentialUpdateRequest}.
 */
public record GdtCredentialUpdateRequest(
        @Size(max = 2000) String loginId,
        @Size(max = 2000) String password,
        @Size(max = 2000) String extra
) { }
